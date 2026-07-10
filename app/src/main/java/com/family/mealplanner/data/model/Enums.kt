package com.family.mealplanner.data.model

/** Meal slots a day can be planned for. */
enum class Slot {
    BREAKFAST, LUNCH, DINNER;

    val label: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

/** Days of the week, Sunday-first to match the Sun→Sat plan. */
enum class Weekday {
    SUN, MON, TUE, WED, THU, FRI, SAT
}
