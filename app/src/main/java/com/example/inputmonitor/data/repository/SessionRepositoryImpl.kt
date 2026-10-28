package com.example.inputmonitor.data.repository

import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.data.dao.MonitoringSessionDao
import com.example.inputmonitor.data.mapper.toDomain
import com.example.inputmonitor.data.mapper.toEntity
import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Implementation of [SessionRepository] backed by Room [MonitoringSessionDao].
 */
class SessionRepositoryImpl(
    private val sessionDao: MonitoringSessionDao,
    private val dispatchers: DispatcherProvider
) : SessionRepository {

    override suspend fun createSession(session: MonitoringSession) = withContext(dispatchers.io) {
        sessionDao.insertSession(session.toEntity())
    }

    override suspend fun updateSession(session: MonitoringSession) = withContext(dispatchers.io) {
        sessionDao.updateSession(session.toEntity())
    }

    override suspend fun getSessionById(id: String): MonitoringSession? = withContext(dispatchers.io) {
        sessionDao.getSessionById(id)?.toDomain()
    }

    override fun observeSessionById(id: String): Flow<MonitoringSession?> {
        return sessionDao.observeSessionById(id)
            .map { it?.toDomain() }
            .flowOn(dispatchers.io)
    }

    override suspend fun getActiveSession(): MonitoringSession? = withContext(dispatchers.io) {
        sessionDao.getActiveSession()?.toDomain()
    }

    override fun observeActiveSession(): Flow<MonitoringSession?> {
        return sessionDao.observeActiveSession()
            .map { it?.toDomain() }
            .flowOn(dispatchers.io)
    }

    override fun observeAllSessions(): Flow<List<MonitoringSession>> {
        return sessionDao.observeAllSessions()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(dispatchers.io)
    }

    override suspend fun getAllSessions(): List<MonitoringSession> = withContext(dispatchers.io) {
        sessionDao.getAllSessions().map { it.toDomain() }
    }

    override suspend fun finishSession(id: String, endTime: Long): Boolean = withContext(dispatchers.io) {
        sessionDao.finishSession(id, endTime) > 0
    }

    override suspend fun deleteSession(id: String): Boolean = withContext(dispatchers.io) {
        sessionDao.deleteSessionById(id) > 0
    }

    override suspend fun deleteAllSessions(): Boolean = withContext(dispatchers.io) {
        sessionDao.deleteAllSessions() >= 0
    }
}
