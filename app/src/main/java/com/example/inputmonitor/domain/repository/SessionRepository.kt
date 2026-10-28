package com.example.inputmonitor.domain.repository

import com.example.inputmonitor.domain.model.MonitoringSession
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for monitoring session persistence and lifecycle (Architecture.md §50).
 */
interface SessionRepository {
    suspend fun createSession(session: MonitoringSession)
    suspend fun updateSession(session: MonitoringSession)
    suspend fun getSessionById(id: String): MonitoringSession?
    fun observeSessionById(id: String): Flow<MonitoringSession?>
    suspend fun getActiveSession(): MonitoringSession?
    fun observeActiveSession(): Flow<MonitoringSession?>
    fun observeAllSessions(): Flow<List<MonitoringSession>>
    suspend fun getAllSessions(): List<MonitoringSession>
    suspend fun finishSession(id: String, endTime: Long = System.currentTimeMillis()): Boolean
    suspend fun deleteSession(id: String): Boolean
    suspend fun deleteAllSessions(): Boolean
}
