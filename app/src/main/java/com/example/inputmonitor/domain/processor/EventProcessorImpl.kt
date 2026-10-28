package com.example.inputmonitor.domain.processor

import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.domain.context.ContextResolver
import com.example.inputmonitor.domain.model.InteractionEvent
import com.example.inputmonitor.domain.model.RawInteractionEvent
import com.example.inputmonitor.domain.privacy.PrivacyFilter
import com.example.inputmonitor.domain.repository.EventRepository
import com.example.inputmonitor.domain.session.SessionGate
import com.example.inputmonitor.domain.session.SessionManager
import kotlinx.coroutines.withContext

/**
 * Production implementation of [EventProcessor] (Architecture.md §37, §38, SECURITY.md §4).
 *
 * Coordinates:
 * 1. Session gating (drops events when inactive, paused, or stopped)
 * 2. Privacy filter sanitization (nullifies sensitive content, changes eventType)
 * 3. Application context resolution with caching
 * 4. Safe persistent event construction
 * 5. Repository persistence with defense-in-depth safety validation
 */
class EventProcessorImpl(
    private val sessionGate: SessionGate,
    private val sessionManager: SessionManager,
    private val privacyFilter: PrivacyFilter,
    private val contextResolver: ContextResolver,
    private val eventRepository: EventRepository,
    private val dispatchers: DispatcherProvider
) : EventProcessor {

    override suspend fun process(event: RawInteractionEvent): Boolean {
        return withContext(dispatchers.default) {
            try {
                // 1. Session Gate check
                if (!sessionGate.shouldProcess(event)) {
                    return@withContext false
                }

                // 2. Validate active session is present
                val currentSession = sessionManager.currentSession.value ?: return@withContext false

                // 3. Privacy Filter sanitization (strips credentials, sets SENSITIVE_INPUT_DETECTED)
                val sanitized = privacyFilter.sanitize(event)

                // 4. Resolve human-readable application label
                val contextInfo = contextResolver.resolve(sanitized.packageName)

                // 5. Serialize metadata safely if present
                val metadataString = if (sanitized.metadata.isNotEmpty()) {
                    sanitized.metadata.entries.joinToString(separator = ",", prefix = "{", postfix = "}") {
                        val key = it.key.replace("\\", "\\\\").replace("\"", "\\\"")
                        val value = it.value.replace("\\", "\\\\").replace("\"", "\\\"")
                        "\"$key\":\"$value\""
                    }
                } else null

                // 6. Build persistent domain event
                val interactionEvent = InteractionEvent(
                    id = 0L,
                    sessionId = currentSession.id,
                    timestamp = sanitized.timestamp,
                    source = sanitized.source,
                    eventType = sanitized.eventType,
                    packageName = sanitized.packageName,
                    applicationName = contextInfo.applicationLabel,
                    eventData = sanitized.eventData,
                    inputClassification = sanitized.inputClassification,
                    metadata = metadataString
                )

                // 7. Persist to repository (defense-in-depth safety validation is applied by repository)
                val insertedId = eventRepository.insertEvent(interactionEvent)
                insertedId > 0
            } catch (_: Exception) {
                // Fail-safe: persistence or processing errors must never crash caller
                false
            }
        }
    }
}
