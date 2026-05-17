package com.mobileftp.data.repository

import com.mobileftp.data.local.dao.ConnectionProfileDao
import com.mobileftp.data.local.entity.ConnectionProfileEntity
import com.mobileftp.domain.model.ConnectionProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionProfileRepository @Inject constructor(
    private val dao: ConnectionProfileDao
) {
    fun observeAll(): Flow<List<ConnectionProfile>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun get(id: Long): ConnectionProfile? = dao.getById(id)?.toDomain()

    suspend fun upsert(profile: ConnectionProfile): Long =
        dao.insert(ConnectionProfileEntity.fromDomain(profile))

    suspend fun delete(profile: ConnectionProfile) {
        dao.delete(ConnectionProfileEntity.fromDomain(profile))
    }

    suspend fun touch(id: Long) {
        dao.touch(id, System.currentTimeMillis())
    }
}
