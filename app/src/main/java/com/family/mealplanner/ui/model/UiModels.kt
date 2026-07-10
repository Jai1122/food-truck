package com.family.mealplanner.ui.model

import com.family.mealplanner.data.model.Slot
import java.time.LocalDate

enum class WeekView { LOADING, EMPTY_LIBRARY, NO_PLAN, PLAN }

data class SlotUi(
    val slot: Slot,
    val comboId: Long?,
    val comboName: String?,
    val alert: String?,
) {
    val isBlank: Boolean get() = comboId == null
}

data class DayUi(
    val date: LocalDate,
    val label: String,
    val dayOfMonth: Int,
    val isToday: Boolean,
    val slots: List<SlotUi>,
)

data class ThisWeekState(
    val view: WeekView,
    val rangeLabel: String,
    val days: List<DayUi>,
    val hasBlanks: Boolean,
)

data class ComboOption(
    val id: Long,
    val name: String,
    val subtitle: String,
    val alert: String?,
    val fresh: Boolean,
)

data class SwapState(
    val date: LocalDate,
    val slot: Slot,
    val currentName: String?,
    val options: List<ComboOption>,
)

data class ComboRow(
    val id: Long,
    val name: String,
    val subtitle: String,
    val isVeg: Boolean,
)

data class ItemRow(
    val id: Long,
    val name: String,
    val roleName: String,
    val isVeg: Boolean,
)

data class RoleOption(val id: Long, val name: String)

data class HistoryEntryUi(
    val dayLabel: String,
    val slotLabel: String,
    val comboName: String,
)

data class HistoryWeekUi(
    val rangeLabel: String,
    val isLastWeek: Boolean,
    val entries: List<HistoryEntryUi>,
)

data class PendingItemDelete(
    val id: Long,
    val name: String,
    val affectedCombos: List<String>,
)

data class RoleDetail(
    val id: Long,
    val name: String,
    val canRepeat: Boolean,
    val repeatWindowDays: Int,
    val itemCount: Int,
)
