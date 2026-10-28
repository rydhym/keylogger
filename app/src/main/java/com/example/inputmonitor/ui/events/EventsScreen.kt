package com.example.inputmonitor.ui.events

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.inputmonitor.domain.model.EventSource
import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.InputClassification
import com.example.inputmonitor.domain.model.InteractionEvent
import com.example.inputmonitor.domain.model.SearchSuggestion
import com.example.inputmonitor.domain.model.SuggestionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full Event Timeline Screen displaying paginated chronological interaction events (Architecture.md §56, SRS.md §48).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    uiState: TimelineUiState,
    onNavigateBack: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onEventTypeFilterChange: (EventType?) -> Unit,
    onSourceFilterChange: (EventSource?) -> Unit,
    onPackageFilterChange: (String?) -> Unit,
    onSessionFilterChange: (String?) -> Unit,
    onClassificationFilterChange: (InputClassification?) -> Unit,
    onClearFilters: () -> Unit,
    onNextPage: () -> Unit,
    onPreviousPage: () -> Unit,
    onSelectEvent: (InteractionEvent?) -> Unit,
    onSetFilterDialogVisible: (Boolean) -> Unit,
    onApplySuggestion: (SearchSuggestion) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Interaction Events",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (uiState.totalMatchingEvents > 0) {
                                "${uiState.totalMatchingEvents} events recorded"
                            } else {
                                "No events"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text(
                            text = "←",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onSetFilterDialogVisible(true) }) {
                        Text(
                            text = if (uiState.filterCriteria.hasActiveFilters) "⚙●" else "⚙",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.filterCriteria.hasActiveFilters) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    if (uiState.filterCriteria.hasActiveFilters) {
                        IconButton(onClick = onClearFilters) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Clear Filters"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Input Row with Suggestions
            SearchBarSection(
                query = uiState.searchQueryInput.ifEmpty { uiState.filterCriteria.searchQuery ?: "" },
                suggestions = uiState.searchSuggestions,
                onQueryChange = onSearchQueryChange,
                onApplySuggestion = onApplySuggestion
            )

            // Event Type Filter Chips Row
            EventTypeChipsRow(
                selectedEventType = uiState.filterCriteria.eventType,
                onSelectEventType = onEventTypeFilterChange
            )

            // Pagination Strip
            PaginationStrip(
                currentPage = uiState.currentPage,
                totalPages = uiState.totalPages,
                rangeStart = uiState.pageRangeStart,
                rangeEnd = uiState.pageRangeEnd,
                totalCount = uiState.totalMatchingEvents,
                hasNext = uiState.hasNextPage,
                hasPrevious = uiState.hasPreviousPage,
                onNextPage = onNextPage,
                onPreviousPage = onPreviousPage
            )

            // Event List or Empty State
            if (uiState.events.isEmpty()) {
                EmptyEventsState(
                    hasFilters = uiState.filterCriteria.hasActiveFilters,
                    onClearFilters = onClearFilters,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.events, key = { it.id }) { event ->
                        EventTimelineCard(
                            event = event,
                            onClick = { onSelectEvent(event) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    // Event Detail Dialog
    uiState.selectedEventForDetail?.let { event ->
        EventDetailDialog(
            event = event,
            onDismiss = { onSelectEvent(null) }
        )
    }

    // Filter Dialog
    if (uiState.isFilterDialogVisible) {
        FilterOptionsDialog(
            uiState = uiState,
            onSourceFilterChange = onSourceFilterChange,
            onPackageFilterChange = onPackageFilterChange,
            onSessionFilterChange = onSessionFilterChange,
            onClassificationFilterChange = onClassificationFilterChange,
            onClearFilters = onClearFilters,
            onDismiss = { onSetFilterDialogVisible(false) }
        )
    }
}

@Composable
private fun SearchBarSection(
    query: String,
    suggestions: List<SearchSuggestion>,
    onQueryChange: (String) -> Unit,
    onApplySuggestion: (SearchSuggestion) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(text = "Search app, package, type, session, data...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear Search"
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        if (suggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            val suggestionScrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(suggestionScrollState),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Suggestions:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                suggestions.forEach { suggestion ->
                    val badgeLabel = when (suggestion.type) {
                        SuggestionType.APPLICATION -> "App: ${suggestion.query}"
                        SuggestionType.PACKAGE -> "Pkg: ${suggestion.query.substringAfterLast('.')}"
                        SuggestionType.EVENT_TYPE -> "Type: ${suggestion.query}"
                    }
                    Surface(
                        modifier = Modifier.clickable { onApplySuggestion(suggestion) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EventTypeChipsRow(
    selectedEventType: EventType?,
    onSelectEventType: (EventType?) -> Unit
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedEventType == null,
            onClick = { onSelectEventType(null) },
            label = { Text("All Types") }
        )
        FilterChip(
            selected = selectedEventType == EventType.TEXT_COMMITTED,
            onClick = {
                onSelectEventType(
                    if (selectedEventType == EventType.TEXT_COMMITTED) null else EventType.TEXT_COMMITTED
                )
            },
            label = { Text("⌨ Typing") }
        )
        FilterChip(
            selected = selectedEventType == EventType.VIEW_CLICKED,
            onClick = {
                onSelectEventType(
                    if (selectedEventType == EventType.VIEW_CLICKED) null else EventType.VIEW_CLICKED
                )
            },
            label = { Text("👆 Clicks") }
        )
        FilterChip(
            selected = selectedEventType == EventType.VIEW_FOCUSED,
            onClick = {
                onSelectEventType(
                    if (selectedEventType == EventType.VIEW_FOCUSED) null else EventType.VIEW_FOCUSED
                )
            },
            label = { Text("👁 Focus") }
        )
        FilterChip(
            selected = selectedEventType == EventType.VIEW_SCROLLED,
            onClick = {
                onSelectEventType(
                    if (selectedEventType == EventType.VIEW_SCROLLED) null else EventType.VIEW_SCROLLED
                )
            },
            label = { Text("↕ Scrolls") }
        )
        FilterChip(
            selected = selectedEventType == EventType.WINDOW_STATE_CHANGED,
            onClick = {
                onSelectEventType(
                    if (selectedEventType == EventType.WINDOW_STATE_CHANGED) null else EventType.WINDOW_STATE_CHANGED
                )
            },
            label = { Text("🪟 Windows") }
        )
        FilterChip(
            selected = selectedEventType == EventType.SENSITIVE_INPUT_DETECTED,
            onClick = {
                onSelectEventType(
                    if (selectedEventType == EventType.SENSITIVE_INPUT_DETECTED) null else EventType.SENSITIVE_INPUT_DETECTED
                )
            },
            label = { Text("🔒 Sensitive") }
        )
    }
}

@Composable
private fun PaginationStrip(
    currentPage: Int,
    totalPages: Int,
    rangeStart: Int,
    rangeEnd: Int,
    totalCount: Int,
    hasNext: Boolean,
    hasPrevious: Boolean,
    onNextPage: () -> Unit,
    onPreviousPage: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (totalCount > 0) {
                    "$rangeStart–$rangeEnd of $totalCount"
                } else {
                    "0 events"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onPreviousPage,
                    enabled = hasPrevious,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(text = "‹ Prev", style = MaterialTheme.typography.labelSmall)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "${currentPage + 1} / $totalPages",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = onNextPage,
                    enabled = hasNext,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(text = "Next ›", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun EventTimelineCard(
    event: InteractionEvent,
    onClick: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    val formattedTime = remember(event.timestamp) { timeFormat.format(Date(event.timestamp)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (event.inputClassification.isSensitive) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Source & Type Glyph Indicator
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(8.dp),
                color = when {
                    event.inputClassification.isSensitive -> MaterialTheme.colorScheme.error
                    event.source == EventSource.KEYBOARD -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.secondary
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = when (event.eventType) {
                            EventType.TEXT_COMMITTED -> "⌨"
                            EventType.VIEW_CLICKED -> "👆"
                            EventType.VIEW_FOCUSED -> "👁"
                            EventType.VIEW_SCROLLED -> "↕"
                            EventType.WINDOW_STATE_CHANGED -> "🪟"
                            EventType.SENSITIVE_INPUT_DETECTED -> "🔒"
                            else -> "⚡"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Header: App Label + Timestamp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = event.applicationName ?: event.packageName ?: "Unknown Application",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Subtitle: Package Name
                if (event.packageName != null) {
                    Text(
                        text = event.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Badges & Content Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Event Type Badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = event.eventType.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Source Badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = event.source.name,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Data Content / Sensitive Warning
                if (event.inputClassification.isSensitive) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Protected",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Sensitive credential field — content not stored",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else if (!event.eventData.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                    ) {
                        Text(
                            text = "\"${event.eventData}\"",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyEventsState(
    hasFilters: Boolean,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (hasFilters) "No Matching Events" else "No Interaction Events Recorded",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (hasFilters) {
                    "No events match the active search or filter criteria."
                } else {
                    "Start a monitoring session and interact with applications using the custom keyboard or system actions."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (hasFilters) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onClearFilters) {
                    Text("Clear All Filters")
                }
            }
        }
    }
}

@Composable
private fun EventDetailDialog(
    event: InteractionEvent,
    onDismiss: () -> Unit
) {
    val fullDateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US) }
    val formattedDate = remember(event.timestamp) { fullDateFormat.format(Date(event.timestamp)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (event.inputClassification.isSensitive) "🔒 Protected Event" else "Event Details",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailRow(label = "Event ID", value = event.id.toString())
                DetailRow(label = "Session ID", value = event.sessionId)
                DetailRow(label = "Timestamp", value = formattedDate)
                DetailRow(label = "Source", value = event.source.name)
                DetailRow(label = "Event Type", value = event.eventType.name)
                DetailRow(label = "Application", value = event.applicationName ?: "Unknown")
                DetailRow(label = "Package", value = event.packageName ?: "None")
                DetailRow(label = "Classification", value = event.inputClassification.name)

                if (event.inputClassification.isSensitive) {
                    OutlinedCard(
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Privacy Protection Active",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Under SECURITY.md and SRS §43, credentials and sensitive data are strictly discarded before persistence. Event content is always null.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                } else if (!event.eventData.isNullOrBlank()) {
                    Text(
                        text = "Recorded Data:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = event.eventData,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                if (!event.metadata.isNullOrBlank()) {
                    Text(
                        text = "Metadata:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = event.metadata,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            fontFamily = if (label == "Timestamp" || label == "Event ID") FontFamily.Monospace else FontFamily.Default
        )
    }
}

@Composable
private fun FilterOptionsDialog(
    uiState: TimelineUiState,
    onSourceFilterChange: (EventSource?) -> Unit,
    onPackageFilterChange: (String?) -> Unit,
    onSessionFilterChange: (String?) -> Unit,
    onClassificationFilterChange: (InputClassification?) -> Unit,
    onClearFilters: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter Timeline", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Source Filter
                Text("Event Source:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.filterCriteria.source == null,
                        onClick = { onSourceFilterChange(null) },
                        label = { Text("All") }
                    )
                    FilterChip(
                        selected = uiState.filterCriteria.source == EventSource.KEYBOARD,
                        onClick = { onSourceFilterChange(if (uiState.filterCriteria.source == EventSource.KEYBOARD) null else EventSource.KEYBOARD) },
                        label = { Text("Keyboard") }
                    )
                    FilterChip(
                        selected = uiState.filterCriteria.source == EventSource.ACCESSIBILITY,
                        onClick = { onSourceFilterChange(if (uiState.filterCriteria.source == EventSource.ACCESSIBILITY) null else EventSource.ACCESSIBILITY) },
                        label = { Text("Accessibility") }
                    )
                }

                // Package Filter
                if (uiState.distinctPackages.isNotEmpty()) {
                    Text("Package Filter:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    val scroll = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(scroll),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = uiState.filterCriteria.packageName == null,
                            onClick = { onPackageFilterChange(null) },
                            label = { Text("All Packages") }
                        )
                        uiState.distinctPackages.forEach { pkg ->
                            FilterChip(
                                selected = uiState.filterCriteria.packageName == pkg,
                                onClick = { onPackageFilterChange(if (uiState.filterCriteria.packageName == pkg) null else pkg) },
                                label = { Text(pkg.substringAfterLast('.')) }
                            )
                        }
                    }
                }

                // Session Filter
                if (uiState.sessions.isNotEmpty()) {
                    Text("Session Filter:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    val sessionScroll = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(sessionScroll),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = uiState.filterCriteria.sessionId == null,
                            onClick = { onSessionFilterChange(null) },
                            label = { Text("All Sessions") }
                        )
                        uiState.sessions.forEach { sess ->
                            FilterChip(
                                selected = uiState.filterCriteria.sessionId == sess.id,
                                onClick = { onSessionFilterChange(if (uiState.filterCriteria.sessionId == sess.id) null else sess.id) },
                                label = { Text(sess.name) }
                            )
                        }
                    }
                }

                // Classification Filter
                Text("Classification:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.filterCriteria.classification == null,
                        onClick = { onClassificationFilterChange(null) },
                        label = { Text("All") }
                    )
                    FilterChip(
                        selected = uiState.filterCriteria.classification == InputClassification.NORMAL_TEXT,
                        onClick = { onClassificationFilterChange(if (uiState.filterCriteria.classification == InputClassification.NORMAL_TEXT) null else InputClassification.NORMAL_TEXT) },
                        label = { Text("Normal") }
                    )
                    FilterChip(
                        selected = uiState.filterCriteria.classification == InputClassification.SENSITIVE_PASSWORD,
                        onClick = { onClassificationFilterChange(if (uiState.filterCriteria.classification == InputClassification.SENSITIVE_PASSWORD) null else InputClassification.SENSITIVE_PASSWORD) },
                        label = { Text("Passwords") }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onClearFilters()
                onDismiss()
            }) {
                Text("Reset All")
            }
        }
    )
}
