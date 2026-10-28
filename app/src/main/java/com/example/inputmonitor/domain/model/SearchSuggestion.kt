package com.example.inputmonitor.domain.model

/**
 * Domain model representing an auto-complete search suggestion (SRS.md §50, IMPLEMENTATION_PLAN.md §52).
 */
data class SearchSuggestion(
    val query: String,
    val type: SuggestionType
)

/**
 * Categorization of search suggestions.
 */
enum class SuggestionType {
    APPLICATION,
    PACKAGE,
    EVENT_TYPE
}
