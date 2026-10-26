package com.zemmy.sigurvita.core.model

data class ServerConfig(
    val serverUrl: String = "",
    val apiKey: String = "",
    val jwtToken: String = "",
    val userId: Int = 0,
    val isLoggedIn: Boolean = false
)
