package com.example.inputmonitor.data.mapper

import com.example.inputmonitor.data.entity.InteractionEventEntity
import com.example.inputmonitor.domain.model.EventSource
import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.InputClassification
import com.example.inputmonitor.domain.model.InteractionEvent

fun InteractionEventEntity.toDomain(): InteractionEvent {
    val classification = try {
        InputClassification.valueOf(inputClassification)
    } catch (_: Exception) {
        InputClassification.UNSPECIFIED
    }

    return InteractionEvent(
        id = id,
        sessionId = sessionId,
        timestamp = timestamp,
        source = try {
            EventSource.valueOf(source)
        } catch (_: Exception) {
            EventSource.SYSTEM
        },
        eventType = try {
            EventType.valueOf(eventType)
        } catch (_: Exception) {
            EventType.SENSITIVE_INPUT_DETECTED
        },
        packageName = packageName,
        applicationName = applicationName,
        eventData = if (classification.isSensitive) null else eventData,
        inputClassification = classification,
        metadata = metadata
    )
}

fun InteractionEvent.toEntity(): InteractionEventEntity {
    // Defense-in-depth: Never persist eventData if classification is sensitive
    val sanitizedData = if (inputClassification.isSensitive) null else eventData

    return InteractionEventEntity(
        id = id,
        sessionId = sessionId,
        timestamp = timestamp,
        source = source.name,
        eventType = eventType.name,
        packageName = packageName,
        applicationName = applicationName,
        eventData = sanitizedData,
        inputClassification = inputClassification.name,
        metadata = metadata
    )
}
