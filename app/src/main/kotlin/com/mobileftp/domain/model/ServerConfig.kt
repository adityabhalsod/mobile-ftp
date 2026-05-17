package com.mobileftp.domain.model

data class ServerConfig(
    val port: Int = 2121,
    val username: String = "mobile",
    val password: String = "ftp",
    val rootDirectoryUri: String = "",
    val rootDirectoryPath: String = "",
    val pasvPortStart: Int = 50000,
    val pasvPortEnd: Int = 51000,
    val maxConnections: Int = 10,
    val maxConnectionsPerIp: Int = 4,
    val anonymousAccess: Boolean = false,
    val chunkCount: Int = 8,
    val ftpsEnabled: Boolean = false
)
