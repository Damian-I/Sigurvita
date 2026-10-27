package com.zemmy.sigurvita.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class ErrorMessagesTest {

    @Test
    fun unknownHostPointsAtTheAddressAndTheConnection() {
        val message = UnknownHostException("Unable to resolve host \"kavita.lan\"").toUserMessage()
        assertTrue(message, message.contains("find that server"))
        assertTrue(message, !message.contains("kavita.lan"))
    }

    @Test
    fun refusedConnectionAndTimeoutAreDistinguished() {
        assertTrue(ConnectException("refused").toUserMessage().contains("connect"))
        assertTrue(SocketTimeoutException("timeout").toUserMessage().contains("too long"))
    }

    @Test
    fun tlsProblemsMentionHttpVersusHttps() {
        assertTrue(SSLException("handshake").toUserMessage().contains("http or https"))
    }

    @Test
    fun rejectedKeyIsRecognisedFromTheStatusCode() {
        assertTrue(HttpFailure(401).toUserMessage().contains("API key"))
        assertTrue(HttpFailure(403).toUserMessage().contains("API key"))
    }

    @Test
    fun notFoundHintsAtASubPathAddress() {
        assertTrue(HttpFailure(404).toUserMessage().contains("sub-path"))
    }

    @Test
    fun serverErrorsAndOtherCodesKeepTheirNumber() {
        assertTrue(HttpFailure(503).toUserMessage().contains("(HTTP 503)"))
        assertTrue(HttpFailure(418).toUserMessage().contains("(HTTP 418)"))
    }

    @Test
    fun malformedAddressIsExplained() {
        assertTrue(IllegalArgumentException("Expected URL scheme").toUserMessage().contains("isn't valid"))
    }

    @Test
    fun unknownErrorsKeepTheirOwnMessageOrFallBack() {
        assertEquals("custom failure", Exception("custom failure").toUserMessage())
        assertEquals("Couldn't connect to the server.", Exception().toUserMessage())
        assertEquals("Couldn't connect to the server.", Exception("   ").toUserMessage())
    }
}
