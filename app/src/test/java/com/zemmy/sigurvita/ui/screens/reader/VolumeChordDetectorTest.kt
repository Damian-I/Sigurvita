package com.zemmy.sigurvita.ui.screens.reader

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class VolumeChordDetectorTest {

    /** A manual clock: nothing runs until [advance] moves time past a task's deadline. */
    private class FakeScheduler : VolumeChordDetector.Scheduler {
        private class Task(val at: Long, val action: () -> Unit) {
            var cancelled = false
        }

        private val tasks = mutableListOf<Task>()
        private var now = 0L

        override fun postDelayed(delayMillis: Long, action: () -> Unit): VolumeChordDetector.Cancellable {
            val task = Task(now + delayMillis, action)
            tasks += task
            return VolumeChordDetector.Cancellable { task.cancelled = true }
        }

        fun advance(millis: Long) {
            val target = now + millis
            while (true) {
                val next = tasks.filter { !it.cancelled && it.at <= target }.minByOrNull { it.at } ?: break
                now = next.at
                tasks.remove(next)
                next.action()
            }
            now = target
        }
    }

    private lateinit var scheduler: FakeScheduler
    private lateinit var detector: VolumeChordDetector
    private val steps = mutableListOf<Boolean>()
    private var chords = 0

    @Before
    fun setUp() {
        scheduler = FakeScheduler()
        steps.clear()
        chords = 0
        detector = VolumeChordDetector(
            scheduler = scheduler,
            onStep = { steps += it },
            onChord = { chords++ }
        )
    }

    @Test
    fun aSinglePressStepsAfterTheGraceWindow() {
        detector.onKeyDown(isVolumeUp = true, isRepeat = false)
        scheduler.advance(VolumeChordDetector.DEFAULT_GRACE_MILLIS - 1)
        assertEquals(emptyList<Boolean>(), steps)
        scheduler.advance(1)
        assertEquals(listOf(true), steps)
        detector.onKeyUp(isVolumeUp = true)
        assertEquals(listOf(true), steps)
        assertEquals(0, chords)
    }

    @Test
    fun aTapShorterThanTheGraceWindowStepsOnReleaseAndOnlyOnce() {
        detector.onKeyDown(isVolumeUp = false, isRepeat = false)
        scheduler.advance(20)
        assertEquals(emptyList<Boolean>(), steps)
        detector.onKeyUp(isVolumeUp = false)
        assertEquals(listOf(false), steps)
        scheduler.advance(1_000)
        assertEquals(listOf(false), steps)
    }

    @Test
    fun rapidTapsKeepTheirOrder() {
        detector.onKeyDown(isVolumeUp = true, isRepeat = false)
        scheduler.advance(10)
        detector.onKeyUp(isVolumeUp = true)
        detector.onKeyDown(isVolumeUp = false, isRepeat = false)
        scheduler.advance(10)
        detector.onKeyUp(isVolumeUp = false)
        scheduler.advance(1_000)
        assertEquals(listOf(true, false), steps)
    }

    @Test
    fun aHeldSingleKeyKeepsAutoRepeatingAndNeverChords() {
        detector.onKeyDown(isVolumeUp = true, isRepeat = false)
        scheduler.advance(VolumeChordDetector.DEFAULT_GRACE_MILLIS)
        detector.onKeyDown(isVolumeUp = true, isRepeat = true)
        detector.onKeyDown(isVolumeUp = true, isRepeat = true)
        scheduler.advance(10_000)
        assertEquals(listOf(true, true, true), steps)
        assertEquals(0, chords)
    }

    @Test
    fun holdingBothKeysFiresTheChordOnceAndNeverSteps() {
        detector.onKeyDown(isVolumeUp = true, isRepeat = false)
        scheduler.advance(15)
        detector.onKeyDown(isVolumeUp = false, isRepeat = false)
        scheduler.advance(VolumeChordDetector.DEFAULT_HOLD_MILLIS - 1)
        assertEquals(0, chords)
        scheduler.advance(1)
        assertEquals(1, chords)
        scheduler.advance(10_000)
        assertEquals(1, chords)
        assertEquals(emptyList<Boolean>(), steps)
    }

    @Test
    fun autoRepeatIsSwallowedWhileBothKeysAreHeldAndAfterTheChordFired() {
        detector.onKeyDown(isVolumeUp = true, isRepeat = false)
        detector.onKeyDown(isVolumeUp = false, isRepeat = false)
        detector.onKeyDown(isVolumeUp = false, isRepeat = true)
        scheduler.advance(VolumeChordDetector.DEFAULT_HOLD_MILLIS)
        assertEquals(1, chords)

        // One key is released but the other is still down: its repeats must still not page.
        detector.onKeyUp(isVolumeUp = false)
        detector.onKeyDown(isVolumeUp = true, isRepeat = true)
        assertEquals(emptyList<Boolean>(), steps)
    }

    @Test
    fun releasingEarlyAbandonsTheChordWithoutAnyStep() {
        detector.onKeyDown(isVolumeUp = true, isRepeat = false)
        detector.onKeyDown(isVolumeUp = false, isRepeat = false)
        scheduler.advance(1_500)
        detector.onKeyUp(isVolumeUp = false)
        detector.onKeyUp(isVolumeUp = true)
        scheduler.advance(10_000)
        assertEquals(0, chords)
        assertEquals(emptyList<Boolean>(), steps)
    }

    @Test
    fun aSecondKeyArrivingAfterTheGraceWindowStillChordsButTheFirstStepHappened() {
        detector.onKeyDown(isVolumeUp = true, isRepeat = false)
        scheduler.advance(VolumeChordDetector.DEFAULT_GRACE_MILLIS + 20)
        assertEquals(listOf(true), steps)
        detector.onKeyDown(isVolumeUp = false, isRepeat = false)
        scheduler.advance(VolumeChordDetector.DEFAULT_HOLD_MILLIS)
        assertEquals(1, chords)
        assertEquals(listOf(true), steps)
    }

    @Test
    fun theChordCanFireAgainOnlyAfterBothKeysWereReleased() {
        detector.onKeyDown(isVolumeUp = true, isRepeat = false)
        detector.onKeyDown(isVolumeUp = false, isRepeat = false)
        scheduler.advance(VolumeChordDetector.DEFAULT_HOLD_MILLIS)
        assertEquals(1, chords)

        // Re-pressing one key while the other stays down does not re-arm it.
        detector.onKeyUp(isVolumeUp = false)
        detector.onKeyDown(isVolumeUp = false, isRepeat = false)
        scheduler.advance(VolumeChordDetector.DEFAULT_HOLD_MILLIS * 2)
        assertEquals(1, chords)

        detector.onKeyUp(isVolumeUp = true)
        detector.onKeyUp(isVolumeUp = false)
        detector.onKeyDown(isVolumeUp = true, isRepeat = false)
        detector.onKeyDown(isVolumeUp = false, isRepeat = false)
        scheduler.advance(VolumeChordDetector.DEFAULT_HOLD_MILLIS)
        assertEquals(2, chords)
        assertEquals(emptyList<Boolean>(), steps)
    }

    @Test
    fun resetForgetsHeldKeysAndCancelsPendingWork() {
        detector.onKeyDown(isVolumeUp = true, isRepeat = false)
        detector.reset()
        scheduler.advance(10_000)
        assertEquals(emptyList<Boolean>(), steps)

        detector.onKeyDown(isVolumeUp = true, isRepeat = false)
        detector.onKeyDown(isVolumeUp = false, isRepeat = false)
        detector.reset()
        scheduler.advance(10_000)
        assertEquals(0, chords)

        // After a reset a lone key behaves normally again (it is no longer "the other key is still held").
        detector.onKeyDown(isVolumeUp = false, isRepeat = false)
        scheduler.advance(VolumeChordDetector.DEFAULT_GRACE_MILLIS)
        assertEquals(listOf(false), steps)
    }
}
