package com.example.inputmonitor.data.mapper

import com.example.inputmonitor.data.entity.MonitoringSessionEntity
import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.model.SessionState

fun MonitoringSessionEntity.toDomain(): MonitoringSession {
    return MonitoringSession(
        id = id,
        name = name,
        startTime = startTime,
        endTime = endTime,
        state = try {
            SessionState.valueOf(state)
        } catch (_: Exception) {
            SessionState.STOPPED
        },
        androidVersion = androidVersion,
        deviceModel = deviceModel,
        appVersion = appVersion,
        notes = notes
    )
}

fun MonitoringSession.toEntity(): MonitoringSessionEntity {
    return MonitoringSessionEntity(
        id = id,
        name = name,
        startTime = startTime,
        endTime = endTime,
        state = state.name,
        androidVersion = androidVersion,
        deviceModel = deviceModel,
        appVersion = appVersion,
        notes = notes
    )
}
