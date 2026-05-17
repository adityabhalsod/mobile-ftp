package com.mobileftp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mobileftp.domain.model.ConnectionProfile

@Entity(tableName = "connection_profiles")
data class ConnectionProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val passive: Boolean,
    val ftps: Boolean,
    val chunkCount: Int,
    val lastConnectedAt: Long
) {
    fun toDomain(): ConnectionProfile = ConnectionProfile(
        id = id,
        name = name,
        host = host,
        port = port,
        username = username,
        password = password,
        passive = passive,
        ftps = ftps,
        chunkCount = chunkCount,
        lastConnectedAt = lastConnectedAt
    )

    companion object {
        fun fromDomain(p: ConnectionProfile): ConnectionProfileEntity = ConnectionProfileEntity(
            id = p.id,
            name = p.name,
            host = p.host,
            port = p.port,
            username = p.username,
            password = p.password,
            passive = p.passive,
            ftps = p.ftps,
            chunkCount = p.chunkCount,
            lastConnectedAt = p.lastConnectedAt
        )
    }
}
