package com.example.inputmonitor.ui.events

import com.example.inputmonitor.domain.model.EventFilterCriteria
import com.example.inputmonitor.domain.model.EventSource
import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.InputClassification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TimelineUiState] and [EventFilterCriteria] (Architecture.md §56, SRS.md §48).
 */
class TimelineUiStateTest {

    @Test
    fun paginationCalculations_emptyState_producesCorrectRanges() {
        val state = TimelineUiState(
            totalMatchingEvents = 0,
            currentPage = 0,
            pageSize = 50
        )

        assertEquals(1, state.totalPages)
        assertFalse(state.hasNextPage)
        assertFalse(state.hasPreviousPage)
        assertEquals(0, state.pageRangeStart)
        assertEquals(0, state.pageRangeEnd)
    }

    @Test
    fun paginationCalculations_multiPage_navigatesBoundariesCorrectly() {
        // 125 total events with pageSize = 50 -> 3 pages (50, 50, 25)
        val firstPage = TimelineUiState(
            totalMatchingEvents = 125,
            currentPage = 0,
            pageSize = 50
        )
        assertEquals(3, firstPage.totalPages)
        assertTrue(firstPage.hasNextPage)
        assertFalse(firstPage.hasPreviousPage)
        assertEquals(1, firstPage.pageRangeStart)
        assertEquals(50, firstPage.pageRangeEnd)

        val secondPage = TimelineUiState(
            totalMatchingEvents = 125,
            currentPage = 1,
            pageSize = 50
        )
        assertTrue(secondPage.hasNextPage)
        assertTrue(secondPage.hasPreviousPage)
        assertEquals(51, secondPage.pageRangeStart)
        assertEquals(100, secondPage.pageRangeEnd)

        val lastPage = TimelineUiState(
            totalMatchingEvents = 125,
            currentPage = 2,
            pageSize = 50
        )
        assertFalse(lastPage.hasNextPage)
        assertTrue(lastPage.hasPreviousPage)
        assertEquals(101, lastPage.pageRangeStart)
        assertEquals(125, lastPage.pageRangeEnd)
    }

    @Test
    fun filterCriteria_hasActiveFilters_evaluatesAccurately() {
        val emptyFilter = EventFilterCriteria()
        assertFalse(emptyFilter.hasActiveFilters)

        assertTrue(emptyFilter.copy(sessionId = "sess-1").hasActiveFilters)
        assertTrue(emptyFilter.copy(packageName = "com.example.notes").hasActiveFilters)
        assertTrue(emptyFilter.copy(eventType = EventType.TEXT_COMMITTED).hasActiveFilters)
        assertTrue(emptyFilter.copy(source = EventSource.KEYBOARD).hasActiveFilters)
        assertTrue(emptyFilter.copy(classification = InputClassification.SENSITIVE_PASSWORD).hasActiveFilters)
        assertTrue(emptyFilter.copy(startTime = 1000L).hasActiveFilters)
        assertTrue(emptyFilter.copy(endTime = 2000L).hasActiveFilters)
        assertTrue(emptyFilter.copy(searchQuery = "search").hasActiveFilters)
        assertFalse(emptyFilter.copy(searchQuery = "   ").hasActiveFilters)
    }
}
