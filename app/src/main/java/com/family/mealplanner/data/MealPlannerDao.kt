package com.family.mealplanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.family.mealplanner.data.entity.Combo
import com.family.mealplanner.data.entity.ComboItem
import com.family.mealplanner.data.entity.ComboSlot
import com.family.mealplanner.data.entity.ComboWithItems
import com.family.mealplanner.data.entity.DayRule
import com.family.mealplanner.data.entity.Item
import com.family.mealplanner.data.entity.PlanEntry
import com.family.mealplanner.data.entity.Role
import kotlinx.coroutines.flow.Flow

@Dao
interface MealPlannerDao {

    // ---- observable lists (list screens) ----
    @Query("SELECT * FROM roles ORDER BY name") fun rolesFlow(): Flow<List<Role>>
    @Query("SELECT * FROM items ORDER BY name") fun itemsFlow(): Flow<List<Item>>

    @Transaction
    @Query("SELECT * FROM combos ORDER BY name")
    fun combosWithItems(): Flow<List<ComboWithItems>>

    // ---- one-shot reads (generation / backup) ----
    @Query("SELECT * FROM roles") suspend fun allRoles(): List<Role>
    @Query("SELECT * FROM items") suspend fun allItems(): List<Item>
    @Query("SELECT * FROM plan_entries") suspend fun allPlanEntries(): List<PlanEntry>

    @Transaction
    @Query("SELECT * FROM combos")
    suspend fun combosWithItemsOnce(): List<ComboWithItems>

    @Query("SELECT COUNT(*) FROM combos") suspend fun comboCount(): Int
    @Query("SELECT * FROM day_rules") suspend fun allDayRules(): List<DayRule>
    @Query("SELECT * FROM day_rules") fun dayRulesFlow(): Flow<List<DayRule>>

    @Query("SELECT * FROM plan_entries WHERE date BETWEEN :start AND :end ORDER BY date")
    suspend fun entriesBetween(start: String, end: String): List<PlanEntry>

    @Query("SELECT * FROM plan_entries WHERE date < :before ORDER BY date DESC")
    suspend fun entriesBefore(before: String): List<PlanEntry>

    // ---- writes ----
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertRole(role: Role): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertItem(item: Item): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCombo(combo: Combo): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertComboItems(rows: List<ComboItem>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertComboSlots(rows: List<ComboSlot>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPlanEntry(entry: PlanEntry): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertDayRule(rule: DayRule)

    // ---- deletes ----
    @Query("SELECT c.name FROM combos c JOIN combo_items ci ON ci.comboId = c.id WHERE ci.itemId = :itemId ORDER BY c.name")
    suspend fun combosUsingItem(itemId: Long): List<String>

    @Query("DELETE FROM roles WHERE id = :id") suspend fun deleteRole(id: Long)
    @Query("DELETE FROM items WHERE id = :id") suspend fun deleteItem(id: Long)
    @Query("DELETE FROM combo_items WHERE itemId = :itemId") suspend fun clearItemFromCombos(itemId: Long)
    @Query("DELETE FROM combo_items WHERE comboId = :id") suspend fun clearComboItems(id: Long)
    @Query("DELETE FROM combo_slots WHERE comboId = :id") suspend fun clearComboSlots(id: Long)
    @Query("DELETE FROM combos WHERE id = :id") suspend fun deleteCombo(id: Long)

    @Query("DELETE FROM plan_entries WHERE date BETWEEN :start AND :end")
    suspend fun deleteEntriesBetween(start: String, end: String)

    @Query("DELETE FROM plan_entries WHERE date = :date AND slot = :slot")
    suspend fun deleteEntry(date: String, slot: String)

    // ---- wipe (used by Replace import) ----
    @Query("DELETE FROM roles") suspend fun clearRoles()
    @Query("DELETE FROM items") suspend fun clearItems()
    @Query("DELETE FROM combos") suspend fun clearCombos()
    @Query("DELETE FROM combo_items") suspend fun clearAllComboItems()
    @Query("DELETE FROM combo_slots") suspend fun clearAllComboSlots()
    @Query("DELETE FROM day_rules") suspend fun clearDayRules()
    @Query("DELETE FROM plan_entries") suspend fun clearAllPlanEntries()
}
