package com.example.inputmonitor.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inputmonitor.domain.model.AppUsageStat
import com.example.inputmonitor.domain.model.HourlyActivityStat
import com.example.inputmonitor.domain.model.StatisticsSummary
import com.example.inputmonitor.ui.util.DateTimeUtils
import java.util.Locale

/**
 * Statistics and analytics screen displaying aggregate metrics, application breakdown,
 * and circadian hourly activity distribution (Architecture.md §57, SRS.md §51, §52).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    uiState: StatisticsUiState,
    onNavigateBack: () -> Unit,
    onSelectSession: (String?) -> Unit,
    onSetDropdownExpanded: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Interaction Statistics",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = uiState.selectedSessionName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text(
                            text = "←",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    Box {
                        FilterChip(
                            selected = uiState.selectedSessionId != null,
                            onClick = { onSetDropdownExpanded(true) },
                            label = {
                                Text(
                                    text = if (uiState.selectedSessionId == null) "All Sessions ▾" else "Session ▾",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )

                        DropdownMenu(
                            expanded = uiState.isSessionDropdownExpanded,
                            onDismissRequest = { onSetDropdownExpanded(false) }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Sessions (Global Aggregate)") },
                                onClick = { onSelectSession(null) }
                            )

                            if (uiState.availableSessions.isNotEmpty()) {
                                HorizontalDivider()
                                uiState.availableSessions.forEach { session ->
                                    val sessionLabel = session.name.takeIf { it.isNotBlank() }
                                        ?: "Session #${session.id.take(8)}"
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(sessionLabel, fontWeight = FontWeight.Medium)
                                                Text(
                                                    text = "${session.state.name} • ${formatTimestamp(session.startTime)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = { onSelectSession(session.id) }
                                    )
                                }
                            }
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
        if (!uiState.hasData) {
            EmptyStatisticsView(
                sessionName = uiState.selectedSessionName,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    OverviewCard(summary = uiState.statistics.summary)
                }

                item {
                    InteractionBreakdownCard(summary = uiState.statistics.summary)
                }

                item {
                    HourlyDistributionCard(hourlyActivity = uiState.statistics.hourlyActivity)
                }

                item {
                    Text(
                        text = "Application Activity (${uiState.statistics.appUsage.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                items(
                    items = uiState.statistics.appUsage,
                    key = { it.packageName }
                ) { appStat ->
                    AppUsageCard(appStat = appStat)
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * Primary overview summary displaying total volume, velocity, and privacy disclosures.
 */
@Composable
private fun OverviewCard(
    summary: StatisticsSummary,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total Recorded Events",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = summary.totalEvents.toString(),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Activity Rate",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (summary.eventsPerMinute > 0.0) {
                            String.format(Locale.US, "%.1f / min", summary.eventsPerMinute)
                        } else {
                            "—"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricSnippet(
                    label = "Distinct Apps",
                    value = summary.distinctApps.toString()
                )
                MetricSnippet(
                    label = "Active Duration",
                    value = formatDuration(summary.durationMinutes)
                )
                MetricSnippet(
                    label = "Time Span",
                    value = if (summary.firstEventTimestamp != null && summary.lastEventTimestamp != null) {
                        "${formatTimeOnly(summary.firstEventTimestamp)}–${formatTimeOnly(summary.lastEventTimestamp)}"
                    } else {
                        "—"
                    }
                )
            }

            if (summary.sensitiveSuppressions > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🛡️",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Column {
                            Text(
                                text = "${summary.sensitiveSuppressions} Sensitive Inputs Suppressed",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Passwords, PINs, and card numbers were detected and replaced with null per zero-credential architecture.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricSnippet(
    label: String,
    value: String
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Breakdown of interaction categories (Keyboard, Accessibility, Clicks, Focus, Scrolls, Windows).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InteractionBreakdownCard(
    summary: StatisticsSummary,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Interaction Types",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InteractionPill(glyph = "⌨", label = "Typing", count = summary.keyboardEvents, total = summary.totalEvents)
                InteractionPill(glyph = "👆", label = "Clicks", count = summary.clicks, total = summary.totalEvents)
                InteractionPill(glyph = "👁", label = "Focus", count = summary.focusEvents, total = summary.totalEvents)
                InteractionPill(glyph = "↕", label = "Scrolls", count = summary.scrollEvents, total = summary.totalEvents)
                InteractionPill(glyph = "🪟", label = "Windows", count = summary.windowChanges, total = summary.totalEvents)
                InteractionPill(glyph = "📱", label = "Accessibility", count = summary.accessibilityEvents, total = summary.totalEvents)
            }
        }
    }
}

@Composable
private fun InteractionPill(
    glyph: String,
    label: String,
    count: Long,
    total: Long
) {
    val percentage = if (total > 0L) (count.toFloat() / total) * 100f else 0f
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = glyph, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 6.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "$count (${String.format(Locale.US, "%.1f%%", percentage)})",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

/**
 * 24-Hour circadian interaction frequency histogram.
 */
@Composable
private fun HourlyDistributionCard(
    hourlyActivity: List<HourlyActivityStat>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "24-Hour Activity Distribution",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Local Time",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "Interaction frequency across hours of the day (00:00 to 23:00)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                hourlyActivity.forEach { stat ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(24.dp)
                    ) {
                        if (stat.count > 0) {
                            Text(
                                text = stat.count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        val barHeight = (64f * stat.normalizedRatio).coerceAtLeast(4f).dp
                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    if (stat.count > 0) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = String.format(Locale.US, "%02d", stat.hourOfDay),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = if (stat.count > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Per-application event volume and percentage bar (SRS.md §52).
 */
@Composable
private fun AppUsageCard(
    appStat: AppUsageStat,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appStat.applicationName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = appStat.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${appStat.count} events",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f%%", appStat.percentage),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LinearProgressIndicator(
                progress = { (appStat.percentage / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            if (appStat.lastActiveTimestamp > 0) {
                Text(
                    text = "Last active: ${formatTimestamp(appStat.lastActiveTimestamp)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Friendly empty state when no interaction events exist for the selected scope.
 */
@Composable
private fun EmptyStatisticsView(
    sessionName: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "📊",
                    style = MaterialTheme.typography.displaySmall
                )
                Text(
                    text = "No Statistical Data",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "No interaction events recorded for $sessionName. Start a monitoring session on the Dashboard and interact with apps to see live analytics.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    return DateTimeUtils.formatShortDate(timestamp)
}

private fun formatTimeOnly(timestamp: Long): String {
    return DateTimeUtils.formatTimeOnly(timestamp)
}

private fun formatDuration(minutes: Double): String {
    return DateTimeUtils.formatDuration(minutes)
}
