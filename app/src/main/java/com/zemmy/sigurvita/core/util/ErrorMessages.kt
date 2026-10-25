package com.zemmy.sigurvita.core.util

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** A Kavita server answered with an HTTP error. Carries the status code so the advice can depend on it. */
class HttpFailure(val code: Int, message: String = "HTTP $code") : IOException(message)

/** Turns a connection or sign-in failure into something a person can act on. */
fun Throwable.toUserMessage(): String = when (this) {
    is UnknownHostException ->
        "Can't find that server. Check the address and your internet connection."
    is ConnectException ->
        "Couldn't connect to the server. Check the address and port, and that Kavita is running."
    is SocketTimeoutException ->
        "The server took too long to respond. Try again."
    is SSLException ->
        "A secure connection couldn't be made. Check whether the address should use http or https, and that the certificate is valid."
    is HttpFailure -> when (code) {
        401, 403 -> "The server rejected the API key. Check that it was copied correctly."
        404 -> "That address doesn't look like a Kavita server. Addresses with a sub-path (such as /kavita) aren't supported."
        in 500..599 -> "The server reported an error (HTTP $code). Try again in a moment."
        else -> "Sign-in failed (HTTP $code)."
    }
    is IllegalArgumentException ->
        "That server address isn't valid. Include http:// or https://."
    else -> message?.takeIf { it.isNotBlank() } ?: "Couldn't connect to the server."
}
