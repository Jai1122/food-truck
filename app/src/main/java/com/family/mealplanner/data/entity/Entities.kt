package com.family.mealplanner.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.family.mealplanner.data.model.Slot
import com.family.mealplanner.data.model.Weekday
import java.time.LocalDate

/** A role carries the repeat rule (e.g. Base = can repeat, Chutney = don't). */
@Entity(tableName = "roles")
data class Role(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val canRepeat: Boolean = true,
    val repeatWindowDays: Int = 7,
)

/** A raw building block, belonging to one role, veg by default. */
@Entity(tableName = "items", indices = [Index("roleId")])
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val roleId: Long,
    val isVegetarian: Boolean = true,
)

/** A predefined named meal, composed of items via [ComboItem]. */
@Entity(tableName = "combos")
data class Combo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

/** Join: which items make up a combo. */
@Entity(tableName = "combo_items", primaryKeys = ["comboId", "itemId"], indices = [Index("itemId")])
data class ComboItem(
    val comboId: Long,
    val itemId: Long,
)

/** Which meal slots a combo is eligible for. */
@Entity(tableName = "combo_slots", primaryKeys = ["comboId", "slot"])
data class ComboSlot(
    val comboId: Long,
    val slot: Slot,
)

/** Per-weekday dietary constraint (v1: vegetarian only). */
@Entity(tableName = "day_rules")
data class DayRule(
    @PrimaryKey val weekday: Weekday,
    val vegetarianOnly: Boolean = false,
)

/** One planned (or blank) slot on a date. Past rows are history. */
@Entity(tableName = "plan_entries", indices = [Index("date")])
data class PlanEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val slot: Slot,
    val comboId: Long?,
)
