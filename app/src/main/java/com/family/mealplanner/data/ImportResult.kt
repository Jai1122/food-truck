package com.family.mealplanner.data

sealed interface ImportResult {
    data class Success(
        val roles: Int,
        val items: Int,
        val combos: Int,
        val plans: Int,
        val replaced: Boolean,
    ) : ImportResult

    data class Error(val message: String) : ImportResult
}
