package com.mobileftp.domain.model

data class ConnectionProfile(
    val id: Long = 0L,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val passive: Boolean = true,
    val ftps: Boolean = false,
    val chunkCount: Int = 8,
    val lastConnectedAt: Long = 0L
)
