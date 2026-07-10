package com.family.mealplanner.data

import android.content.Context
import com.family.mealplanner.data.model.Slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lightweight preferences (enabled meal slots, household size) backed by SharedPreferences. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("meal_settings", Context.MODE_PRIVATE)

    private val _enabledSlots = MutableStateFlow(readSlots())
    val enabledSlots: StateFlow<Set<Slot>> = _enabledSlots.asStateFlow()

    private val _householdSize = MutableStateFlow(prefs.getInt(KEY_HOUSEHOLD, 4))
    val householdSize: StateFlow<Int> = _householdSize.asStateFlow()

    private fun readSlots(): Set<Slot> {
        val names = prefs.getStringSet(KEY_SLOTS, null) ?: setOf(Slot.BREAKFAST.name)
        return names.mapNotNull { runCatching { Slot.valueOf(it) }.getOrNull() }.toSet()
    }

    /** Enabled slots in canonical order (Breakfast → Lunch → Dinner). */
    fun enabledSlotsOrdered(): List<Slot> = Slot.entries.filter { it in _enabledSlots.value }

    fun setSlotEnabled(slot: Slot, enabled: Boolean) {
        val next = _enabledSlots.value.toMutableSet()
        if (enabled) next.add(slot) else next.remove(slot)
        if (next.isEmpty()) return // always keep at least one slot
        prefs.edit().putStringSet(KEY_SLOTS, next.map { it.name }.toSet()).apply()
        _enabledSlots.value = next
    }

    fun setHouseholdSize(n: Int) {
        val clamped = n.coerceIn(1, 20)
        prefs.edit().putInt(KEY_HOUSEHOLD, clamped).apply()
        _householdSize.value = clamped
    }

    private companion object {
        const val KEY_SLOTS = "enabled_slots"
        const val KEY_HOUSEHOLD = "household_size"
    }
}
