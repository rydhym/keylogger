package com.example.inputmonitor.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.inputmonitor.data.entity.MonitoringSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for monitoring sessions (Architecture.md §49).
 */
@Dao
interface MonitoringSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: MonitoringSessionEntity)

    @Update
    suspend fun updateSession(session: MonitoringSessionEntity)

    @Query("SELECT * FROM monitoring_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: String): MonitoringSessionEntity?

    @Query("SELECT * FROM monitoring_sessions WHERE id = :id LIMIT 1")
    fun observeSessionById(id: String): Flow<MonitoringSessionEntity?>

    @Query("SELECT * FROM monitoring_sessions WHERE state = 'ACTIVE' ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveSession(): MonitoringSessionEntity?

    @Query("SELECT * FROM monitoring_sessions WHERE state = 'ACTIVE' ORDER BY startTime DESC LIMIT 1")
    fun observeActiveSession(): Flow<MonitoringSessionEntity?>

    @Query("SELECT * FROM monitoring_sessions ORDER BY startTime DESC")
    fun observeAllSessions(): Flow<List<MonitoringSessionEntity>>

    @Query("SELECT * FROM monitoring_sessions ORDER BY startTime DESC")
    suspend fun getAllSessions(): List<MonitoringSessionEntity>

    @Query("UPDATE monitoring_sessions SET endTime = :endTime, state = :state WHERE id = :id")
    suspend fun finishSession(id: String, endTime: Long, state: String = "STOPPED"): Int

    @Query("DELETE FROM monitoring_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: String): Int

    @Query("DELETE FROM monitoring_sessions")
    suspend fun deleteAllSessions(): Int
}
