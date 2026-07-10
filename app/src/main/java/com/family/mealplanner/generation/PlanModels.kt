package com.family.mealplanner.generation

import com.family.mealplanner.data.model.Slot
import java.time.LocalDate

/** An item as the generator sees it — carries its role's repeat rule + veg flag. */
data class PlannableItem(
    val id: Long,
    val name: String,
    val isVegetarian: Boolean,
    val canRepeat: Boolean,
    val repeatWindowDays: Int,
)

/** A combo the generator can place. Vegetarian iff every item is. */
data class PlannableCombo(
    val id: Long,
    val name: String,
    val slots: Set<Slot>,
    val items: List<PlannableItem>,
) {
    val isVegetarian: Boolean get() = items.all { it.isVegetarian }
}

/** A combo already placed on a date+slot (history or earlier in this run). */
data class PlacedCombo(val date: LocalDate, val slot: Slot, val combo: PlannableCombo)

/** The generator's output for one slot. Null comboId = intentional blank. */
data class Placement(val date: LocalDate, val slot: Slot, val comboId: Long?)

/** A repeat detected on a "don't-repeat" item. */
data class RepeatAlert(val itemName: String, val daysAgo: Long)
