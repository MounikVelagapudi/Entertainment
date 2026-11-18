package com.example.entertainment.core.domain

/**
 * Unified data-layer state a repository emits to its consumers: no separate
 * "data flow" + "error flow" for callers to recombine themselves.
 * Though we have seperate sealed classes per component like MediaSectionUiState which is in Presentation layer.
 * This interface stays in Domain layer and can be reused across all the components that room transmits data from domain to presentation
 * Dependency graph cannot run backwards, so we should not be using presentation components in Domain
 */
sealed interface Resource<out T> {
    data object Loading : Resource<Nothing>
    data class Success<T>(val data: T) : Resource<T>
    data class Error(val message: String) : Resource<Nothing>
}
