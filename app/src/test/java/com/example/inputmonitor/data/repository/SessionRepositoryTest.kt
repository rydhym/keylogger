package com.example.inputmonitor.data.repository

import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.data.dao.MonitoringSessionDao
import com.example.inputmonitor.data.entity.MonitoringSessionEntity
import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.model.SessionState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRepositoryTest {

    private val testDispatcher = Dispatchers.Unconfined
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private class FakeMonitoringSessionDao : MonitoringSessionDao {
        val sessions = mutableMapOf<String, MonitoringSessionEntity>()

        override suspend fun insertSession(session: MonitoringSessionEntity) {
            sessions[session.id] = session
        }

        override suspend fun updateSession(session: MonitoringSessionEntity) {
            sessions[session.id] = session
        }

        override suspend fun getSessionById(id: String): MonitoringSessionEntity? = sessions[id]

        override fun observeSessionById(id: String): Flow<MonitoringSessionEntity?> = flowOf(sessions[id])

        override suspend fun getActiveSession(): MonitoringSessionEntity? =
            sessions.values.firstOrNull { it.state == "ACTIVE" }

        override fun observeActiveSession(): Flow<MonitoringSessionEntity?> =
            flowOf(sessions.values.firstOrNull { it.state == "ACTIVE" })

        override fun observeAllSessions(): Flow<List<MonitoringSessionEntity>> =
            flowOf(sessions.values.toList().sortedByDescending { it.startTime })

        override suspend fun getAllSessions(): List<MonitoringSessionEntity> =
            sessions.values.toList().sortedByDescending { it.startTime }

        override suspend fun finishSession(id: String, endTime: Long, state: String): Int {
            val existing = sessions[id] ?: return 0
            sessions[id] = existing.copy(endTime = endTime, state = state)
            return 1
        }

        override suspend fun deleteSessionById(id: String): Int {
            return if (sessions.remove(id) != null) 1 else 0
        }

        override suspend fun deleteAllSessions(): Int {
            val count = sessions.size
            sessions.clear()
            return count
        }
    }

    @Test
    fun createSession_persistsAndRetrievesCorrectly() = runBlocking {
        val fakeDao = FakeMonitoringSessionDao()
        val repository = SessionRepositoryImpl(fakeDao, testDispatchers)

        val session = MonitoringSession(
            id = "sess-1",
            name = "Session Alpha",
            startTime = 1000L,
            endTime = null,
            state = SessionState.ACTIVE,
            androidVersion = "15",
            deviceModel = "Pixel 9",
            appVersion = "0.1.0"
        )

        repository.createSession(session)

        val retrieved = repository.getSessionById("sess-1")
        assertNotNull(retrieved)
        assertEquals("sess-1", retrieved?.id)
        assertEquals("Session Alpha", retrieved?.name)
        assertEquals(SessionState.ACTIVE, retrieved?.state)
    }

    @Test
    fun finishSession_updatesEndTimeAndState() = runBlocking {
        val fakeDao = FakeMonitoringSessionDao()
        val repository = SessionRepositoryImpl(fakeDao, testDispatchers)

        val session = MonitoringSession(
            id = "sess-2",
            name = "Session Beta",
            startTime = 1000L,
            endTime = null,
            state = SessionState.ACTIVE,
            androidVersion = "15",
            deviceModel = "Pixel 9",
            appVersion = "0.1.0"
        )

        repository.createSession(session)
        val finished = repository.finishSession("sess-2", endTime = 3000L)

        assertTrue(finished)
        val updated = repository.getSessionById("sess-2")
        assertEquals(3000L, updated?.endTime)
        assertEquals(SessionState.STOPPED, updated?.state)
    }

    @Test
    fun deleteSession_removesSession() = runBlocking {
        val fakeDao = FakeMonitoringSessionDao()
        val repository = SessionRepositoryImpl(fakeDao, testDispatchers)

        val session = MonitoringSession(
            id = "sess-3",
            name = "Session Gamma",
            startTime = 1000L,
            endTime = null,
            state = SessionState.ACTIVE,
            androidVersion = "15",
            deviceModel = "Pixel 9",
            appVersion = "0.1.0"
        )

        repository.createSession(session)
        assertTrue(repository.deleteSession("sess-3"))
        assertNull(repository.getSessionById("sess-3"))
    }
}
