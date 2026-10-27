package com.zemmy.sigurvita.ui.screens.reader

/**
 * Tells "one volume key" apart from "both volume keys held together" for the reader.
 *
 * - A single key behaves exactly like before: [onStep] once per press, plus once per auto-repeat while held.
 * - Holding **both** keys for [holdMillis] calls [onChord] once.
 *
 * Android delivers the two key-downs a few milliseconds apart, so a single key's first step is deferred by
 * [graceMillis]. If the second key arrives inside that window the step is dropped, which is what stops every
 * chord from also turning a page. A tap shorter than the window still acts, immediately on release and in order.
 * While a chord is held (or after it fired) auto-repeat is swallowed, so waiting for the hold never pages.
 *
 * Pure Kotlin on purpose: timing comes from the injected [Scheduler], so the logic is unit-testable without
 * Android. All calls must come from one thread (the main thread in `MainActivity`).
 */
class VolumeChordDetector(
    private val scheduler: Scheduler,
    private val onStep: (isVolumeUp: Boolean) -> Unit,
    private val onChord: () -> Unit,
    private val holdMillis: Long = DEFAULT_HOLD_MILLIS,
    private val graceMillis: Long = DEFAULT_GRACE_MILLIS
) {
    fun interface Cancellable {
        fun cancel()
    }

    fun interface Scheduler {
        fun postDelayed(delayMillis: Long, action: () -> Unit): Cancellable
    }

    private var upHeld = false
    private var downHeld = false
    private var chordFired = false
    private var pendingStep: Cancellable? = null
    private var pendingStepIsUp = false
    private var chordTimer: Cancellable? = null

    private val bothHeld: Boolean get() = upHeld && downHeld

    fun onKeyDown(isVolumeUp: Boolean, isRepeat: Boolean) {
        if (isRepeat) {
            // Auto-repeat belongs to the key pressed last. While a chord is held it must never page.
            if (!bothHeld && !chordFired) onStep(isVolumeUp)
            return
        }

        if (isVolumeUp) upHeld = true else downHeld = true

        if (bothHeld) {
            // The first key's deferred step was only waiting to see whether a second key followed.
            cancelPendingStep()
            if (!chordFired && chordTimer == null) {
                chordTimer = scheduler.postDelayed(holdMillis) {
                    chordTimer = null
                    chordFired = true
                    onChord()
                }
            }
            return
        }

        // Pressing a key again while the other is still held after a chord has fired does nothing.
        if (chordFired) return

        flushPendingStep()
        pendingStepIsUp = isVolumeUp
        pendingStep = scheduler.postDelayed(graceMillis) {
            pendingStep = null
            onStep(isVolumeUp)
        }
    }

    fun onKeyUp(isVolumeUp: Boolean) {
        if (isVolumeUp) upHeld = false else downHeld = false

        // A tap shorter than the grace period still has to act.
        if (pendingStep != null && pendingStepIsUp == isVolumeUp) flushPendingStep()

        // Releasing either key abandons a chord that has not fired yet.
        cancelChordTimer()

        if (!upHeld && !downHeld) chordFired = false
    }

    /** Forgets every held key and cancels pending work. Call when key-up events may have been lost (pause, focus loss). */
    fun reset() {
        cancelPendingStep()
        cancelChordTimer()
        upHeld = false
        downHeld = false
        chordFired = false
    }

    private fun flushPendingStep() {
        val pending = pendingStep ?: return
        pending.cancel()
        pendingStep = null
        onStep(pendingStepIsUp)
    }

    private fun cancelPendingStep() {
        pendingStep?.cancel()
        pendingStep = null
    }

    private fun cancelChordTimer() {
        chordTimer?.cancel()
        chordTimer = null
    }

    companion object {
        /** How long both keys must be held to toggle bubble navigation mode. */
        const val DEFAULT_HOLD_MILLIS = 500L

        /**
         * Window in which a second key turns a first key's step into a chord. It is also the delay before a single
         * press acts: raise it if chords still turn a page, lower it if single presses feel sluggish.
         */
        const val DEFAULT_GRACE_MILLIS = 80L
    }
}
