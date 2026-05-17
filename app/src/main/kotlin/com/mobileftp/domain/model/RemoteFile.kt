package com.mobileftp.domain.model

data class RemoteFile(
    val name: String,
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val modifiedTimestamp: Long,
    val permissions: String,
    val owner: String = "",
    val group: String = ""
)
