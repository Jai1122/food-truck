package com.family.mealplanner.data

import com.family.mealplanner.data.entity.Combo
import com.family.mealplanner.data.entity.ComboItem
import com.family.mealplanner.data.entity.ComboSlot
import com.family.mealplanner.data.entity.DayRule
import com.family.mealplanner.data.entity.Item
import com.family.mealplanner.data.entity.PlanEntry
import com.family.mealplanner.data.entity.Role
import com.family.mealplanner.data.model.Slot
import com.family.mealplanner.data.model.Weekday
import com.family.mealplanner.generation.PlacedCombo
import com.family.mealplanner.generation.PlannableCombo
import com.family.mealplanner.generation.PlannableItem
import com.family.mealplanner.generation.PlanGenerator
import com.family.mealplanner.ui.model.ComboOption
import com.family.mealplanner.ui.model.DayUi
import com.family.mealplanner.ui.model.HistoryEntryUi
import com.family.mealplanner.ui.model.HistoryWeekUi
import com.family.mealplanner.ui.model.SlotUi
import com.family.mealplanner.ui.model.SwapState
import com.family.mealplanner.ui.model.ThisWeekState
import com.family.mealplanner.ui.model.WeekView
import com.family.mealplanner.util.DateUtil
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class MealRepository(
    private val dao: MealPlannerDao,
    private val settings: SettingsStore,
) {

    // Observable streams for the list screens.
    val rolesFlow = dao.rolesFlow()
    val itemsFlow = dao.itemsFlow()
    val combosFlow = dao.combosWithItems()

    // ---- settings passthroughs ----
    val enabledSlotsFlow = settings.enabledSlots
    val householdSize = settings.householdSize
    fun setSlotEnabled(slot: Slot, enabled: Boolean) = settings.setSlotEnabled(slot, enabled)
    fun setHouseholdSize(n: Int) = settings.setHouseholdSize(n)

    // ---- day rules ----
    fun dayRulesFlow() = dao.dayRulesFlow()
    suspend fun setDayVegOnly(weekday: Weekday, vegOnly: Boolean) =
        dao.upsertDayRule(DayRule(weekday, vegOnly))

    // ---- library edits ----
    suspend fun addRole(name: String, canRepeat: Boolean, windowDays: Int = 7) =
        dao.upsertRole(Role(name = name, canRepeat = canRepeat, repeatWindowDays = windowDays))

    suspend fun updateRole(id: Long, name: String, canRepeat: Boolean, windowDays: Int) =
        dao.upsertRole(Role(id = id, name = name, canRepeat = canRepeat, repeatWindowDays = windowDays))

    suspend fun deleteRole(id: Long) = dao.deleteRole(id)

    suspend fun addItem(name: String, roleId: Long, isVegetarian: Boolean) =
        dao.upsertItem(Item(name = name, roleId = roleId, isVegetarian = isVegetarian))

    suspend fun combosUsingItem(itemId: Long): List<String> = dao.combosUsingItem(itemId)

    suspend fun deleteItem(id: Long) {
        dao.clearItemFromCombos(id) // cascade: never orphan a combo_items row
        dao.deleteItem(id)
    }

    suspend fun saveCombo(name: String, itemIds: List<Long>, slots: List<Slot>) {
        val comboId = dao.upsertCombo(Combo(name = name))
        dao.insertComboItems(itemIds.map { ComboItem(comboId, it) })
        dao.insertComboSlots(slots.map { ComboSlot(comboId, it) })
    }

    suspend fun deleteCombo(id: Long) {
        dao.clearComboItems(id)
        dao.clearComboSlots(id)
        dao.deleteCombo(id)
    }

    // ---- generation-facing model ----
    private suspend fun plannableCombos(): List<PlannableCombo> {
        val rolesById = dao.allRoles().associateBy { it.id }
        return dao.combosWithItemsOnce().map { cw ->
            PlannableCombo(
                id = cw.combo.id,
                name = cw.combo.name,
                slots = cw.slots.map { it.slot }.toSet(),
                items = cw.items.map { item ->
                    val role = rolesById[item.roleId]
                    PlannableItem(
                        id = item.id,
                        name = item.name,
                        isVegetarian = item.isVegetarian,
                        canRepeat = role?.canRepeat ?: true,
                        repeatWindowDays = role?.repeatWindowDays ?: 7,
                    )
                },
            )
        }
    }

    private suspend fun vegOnlyMap() =
        dao.allDayRules().associate { it.weekday to it.vegetarianOnly }

    private suspend fun placedIn(start: LocalDate, end: LocalDate, byId: Map<Long, PlannableCombo>) =
        dao.entriesBetween(start.toString(), end.toString())
            .mapNotNull { e -> e.comboId?.let { byId[it] }?.let { PlacedCombo(e.date, e.slot, it) } }

    // ---- This Week ----
    suspend fun buildThisWeek(weekStart: LocalDate): ThisWeekState {
        val enabledSlots = settings.enabledSlotsOrdered()
        val range = DateUtil.rangeLabel(weekStart)
        if (dao.comboCount() == 0) {
            return ThisWeekState(WeekView.EMPTY_LIBRARY, range, emptyList(), false)
        }
        val weekEnd = weekStart.plusDays(6)
        val entries = dao.entriesBetween(weekStart.toString(), weekEnd.toString())
        if (entries.isEmpty()) {
            return ThisWeekState(WeekView.NO_PLAN, range, emptyList(), false)
        }
        val byId = plannableCombos().associateBy { it.id }
        val placedAll = placedIn(weekStart.minusDays(14), weekEnd, byId)
        val today = LocalDate.now()

        val days = (0..6).map { i ->
            val date = weekStart.plusDays(i.toLong())
            val slots = enabledSlots.map { slot ->
                val entry = entries.firstOrNull { it.date == date && it.slot == slot }
                val combo = entry?.comboId?.let { byId[it] }
                val alert = combo?.let {
                    val others = placedAll.filter { !(it.date == date && it.slot == slot) }
                    PlanGenerator.alertFor(date, combo, others)
                        ?.let { a -> "${a.itemName} · ${DateUtil.daysAgoLabel(a.daysAgo)}" }
                }
                SlotUi(slot, combo?.id, combo?.name, alert)
            }
            DayUi(date, DateUtil.shortLabel(date), date.dayOfMonth, date == today, slots)
        }
        val hasBlanks = days.any { d -> d.slots.any { it.isBlank } }
        return ThisWeekState(WeekView.PLAN, range, days, hasBlanks)
    }

    suspend fun generateAndSaveWeek(weekStart: LocalDate) {
        val enabledSlots = settings.enabledSlotsOrdered()
        val combos = plannableCombos()
        val byId = combos.associateBy { it.id }
        val history = placedIn(weekStart.minusDays(14), weekStart.minusDays(1), byId)
        val placements = PlanGenerator.generateWeek(
            weekStart, enabledSlots, combos, vegOnlyMap(), history, System.nanoTime(),
        )
        dao.deleteEntriesBetween(weekStart.toString(), weekStart.plusDays(6).toString())
        // Persist blanks too, so a mostly-empty week still shows the grid.
        placements.forEach { dao.upsertPlanEntry(PlanEntry(date = it.date, slot = it.slot, comboId = it.comboId)) }
    }

    suspend fun regenerateDay(date: LocalDate, weekStart: LocalDate) {
        val enabledSlots = settings.enabledSlotsOrdered()
        val combos = plannableCombos()
        val byId = combos.associateBy { it.id }
        val weekEnd = weekStart.plusDays(6)
        val history = placedIn(weekStart.minusDays(14), weekEnd, byId).filter { it.date != date }
        val placements = PlanGenerator.generateDay(
            date, enabledSlots, combos, vegOnlyMap(), history, System.nanoTime(),
        )
        dao.deleteEntriesBetween(date.toString(), date.toString())
        placements.forEach { dao.upsertPlanEntry(PlanEntry(date = it.date, slot = it.slot, comboId = it.comboId)) }
    }

    suspend fun setSlot(date: LocalDate, slot: Slot, comboId: Long) {
        dao.deleteEntry(date.toString(), slot.name)
        dao.upsertPlanEntry(PlanEntry(date = date, slot = slot, comboId = comboId))
    }

    suspend fun clearSlot(date: LocalDate, slot: Slot) {
        dao.deleteEntry(date.toString(), slot.name)
        dao.upsertPlanEntry(PlanEntry(date = date, slot = slot, comboId = null))
    }

    suspend fun clearWeek(weekStart: LocalDate) =
        dao.deleteEntriesBetween(weekStart.toString(), weekStart.plusDays(6).toString())

    // ---- Export / Import (share with family) ----
    // Cross-references use names (not ids) so a bundle merges cleanly on any device.

    suspend fun exportToJson(includePlans: Boolean): String {
        val roles = dao.allRoles()
        val roleNameById = roles.associate { it.id to it.name }
        val items = dao.allItems()
        val combos = dao.combosWithItemsOnce()
        val dayRules = dao.allDayRules()

        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("scope", if (includePlans) "full" else "library")

        root.put("roles", JSONArray().apply {
            roles.forEach {
                put(
                    JSONObject().put("name", it.name)
                        .put("canRepeat", it.canRepeat)
                        .put("repeatWindowDays", it.repeatWindowDays),
                )
            }
        })
        root.put("items", JSONArray().apply {
            items.forEach {
                put(
                    JSONObject().put("name", it.name)
                        .put("role", roleNameById[it.roleId] ?: "")
                        .put("isVegetarian", it.isVegetarian),
                )
            }
        })
        root.put("combos", JSONArray().apply {
            combos.forEach { cw ->
                put(
                    JSONObject().put("name", cw.combo.name)
                        .put("items", JSONArray().apply { cw.items.forEach { put(it.name) } })
                        .put("slots", JSONArray().apply { cw.slots.forEach { put(it.slot.name) } }),
                )
            }
        })
        root.put("dayRules", JSONArray().apply {
            dayRules.forEach {
                put(JSONObject().put("weekday", it.weekday.name).put("vegetarianOnly", it.vegetarianOnly))
            }
        })
        if (includePlans) {
            val comboNameById = combos.associate { it.combo.id to it.combo.name }
            root.put("plans", JSONArray().apply {
                dao.allPlanEntries().forEach { e ->
                    val name = e.comboId?.let { comboNameById[it] } ?: return@forEach
                    put(JSONObject().put("date", e.date.toString()).put("slot", e.slot.name).put("combo", name))
                }
            })
        }
        return root.toString(2)
    }

    suspend fun importFromJson(text: String, replace: Boolean): ImportResult {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            return ImportResult.Error("This file isn't a Meal Planner export.")
        }
        if (root.optString("format") != FORMAT) {
            return ImportResult.Error("This file isn't a Meal Planner export.")
        }
        if (root.optInt("version", 1) > VERSION) {
            return ImportResult.Error("This backup is from a newer version. Update the app to import it.")
        }

        val rolesJson = root.optJSONArray("roles") ?: JSONArray()
        val itemsJson = root.optJSONArray("items") ?: JSONArray()
        val combosJson = root.optJSONArray("combos") ?: JSONArray()
        val dayRulesJson = root.optJSONArray("dayRules") ?: JSONArray()
        val plansJson = root.optJSONArray("plans") ?: JSONArray()

        if (replace) {
            dao.clearAllPlanEntries()
            dao.clearAllComboItems()
            dao.clearAllComboSlots()
            dao.clearCombos()
            dao.clearItems()
            dao.clearDayRules()
            dao.clearRoles()
        }

        var addedRoles = 0
        var addedItems = 0
        var addedCombos = 0
        var addedPlans = 0

        val roleIdByName = dao.allRoles().associate { it.name to it.id }.toMutableMap()
        for (i in 0 until rolesJson.length()) {
            val o = rolesJson.getJSONObject(i)
            val name = o.getString("name")
            if (roleIdByName.containsKey(name)) continue
            val id = dao.upsertRole(
                Role(name = name, canRepeat = o.optBoolean("canRepeat", true), repeatWindowDays = o.optInt("repeatWindowDays", 7)),
            )
            roleIdByName[name] = id
            addedRoles++
        }

        val itemIdByName = dao.allItems().associate { it.name to it.id }.toMutableMap()
        for (i in 0 until itemsJson.length()) {
            val o = itemsJson.getJSONObject(i)
            val name = o.getString("name")
            if (itemIdByName.containsKey(name)) continue
            val roleId = roleIdByName[o.optString("role")] ?: roleIdByName.values.firstOrNull() ?: continue
            val id = dao.upsertItem(Item(name = name, roleId = roleId, isVegetarian = o.optBoolean("isVegetarian", true)))
            itemIdByName[name] = id
            addedItems++
        }

        val existingComboNames = dao.combosWithItemsOnce().map { it.combo.name }.toMutableSet()
        for (i in 0 until combosJson.length()) {
            val o = combosJson.getJSONObject(i)
            val name = o.getString("name")
            if (existingComboNames.contains(name)) continue
            val comboId = dao.upsertCombo(Combo(name = name))
            val itemsArr = o.optJSONArray("items") ?: JSONArray()
            val ciRows = (0 until itemsArr.length()).mapNotNull { j ->
                itemIdByName[itemsArr.getString(j)]?.let { ComboItem(comboId, it) }
            }
            if (ciRows.isNotEmpty()) dao.insertComboItems(ciRows)
            val slotsArr = o.optJSONArray("slots") ?: JSONArray()
            val csRows = (0 until slotsArr.length()).mapNotNull { j ->
                runCatching { Slot.valueOf(slotsArr.getString(j)) }.getOrNull()?.let { ComboSlot(comboId, it) }
            }
            if (csRows.isNotEmpty()) dao.insertComboSlots(csRows)
            existingComboNames.add(name)
            addedCombos++
        }

        for (i in 0 until dayRulesJson.length()) {
            val o = dayRulesJson.getJSONObject(i)
            val weekday = runCatching { Weekday.valueOf(o.getString("weekday")) }.getOrNull() ?: continue
            dao.upsertDayRule(DayRule(weekday, o.optBoolean("vegetarianOnly", false)))
        }

        val comboIdByName = dao.combosWithItemsOnce().associate { it.combo.name to it.combo.id }
        val existingPlanKeys = dao.allPlanEntries().map { "${it.date}|${it.slot.name}" }.toMutableSet()
        for (i in 0 until plansJson.length()) {
            val o = plansJson.getJSONObject(i)
            val date = runCatching { LocalDate.parse(o.getString("date")) }.getOrNull() ?: continue
            val slot = runCatching { Slot.valueOf(o.getString("slot")) }.getOrNull() ?: continue
            val comboId = comboIdByName[o.optString("combo")] ?: continue
            val key = "$date|${slot.name}"
            if (existingPlanKeys.contains(key)) continue
            dao.upsertPlanEntry(PlanEntry(date = date, slot = slot, comboId = comboId))
            existingPlanKeys.add(key)
            addedPlans++
        }

        return ImportResult.Success(addedRoles, addedItems, addedCombos, addedPlans, replace)
    }

    private companion object {
        const val FORMAT = "family-meal-planner"
        const val VERSION = 1
    }

    /** Past weeks (before the current week), grouped newest-first, filled slots only. */
    suspend fun buildHistory(): List<HistoryWeekUi> {
        val weekStart = DateUtil.currentSunday()
        val past = dao.entriesBefore(weekStart.toString()).filter { it.comboId != null }
        if (past.isEmpty()) return emptyList()
        val nameById = plannableCombos().associate { it.id to it.name }
        val previousWeek = weekStart.minusWeeks(1)
        return past.groupBy { DateUtil.currentSunday(it.date) }
            .toSortedMap(compareByDescending { it })
            .map { (sunday, entries) ->
                HistoryWeekUi(
                    rangeLabel = DateUtil.rangeLabel(sunday),
                    isLastWeek = sunday == previousWeek,
                    entries = entries
                        .sortedWith(compareBy({ it.date }, { it.slot.ordinal }))
                        .map { e ->
                            HistoryEntryUi(
                                dayLabel = DateUtil.shortLabel(e.date),
                                slotLabel = e.slot.label,
                                comboName = nameById[e.comboId] ?: "(removed)",
                            )
                        },
                )
            }
    }

    suspend fun buildSwap(
        date: LocalDate,
        slot: Slot,
        weekStart: LocalDate,
    ): SwapState {
        val combos = plannableCombos()
        val byId = combos.associateBy { it.id }
        val weekEnd = weekStart.plusDays(6)
        val vegOnly = vegOnlyMap()[DateUtil.weekdayOf(date)] == true
        val eligible = combos.filter { slot in it.slots && (!vegOnly || it.isVegetarian) }

        val placedAll = placedIn(weekStart.minusDays(14), weekEnd, byId)
            .filter { !(it.date == date && it.slot == slot) }

        val currentName = dao.entriesBetween(date.toString(), date.toString())
            .firstOrNull { it.slot == slot }?.comboId?.let { byId[it]?.name }

        val options = eligible.map { c ->
            val alert = PlanGenerator.alertFor(date, c, placedAll)
                ?.let { "${it.itemName} · ${DateUtil.daysAgoLabel(it.daysAgo)}" }
            // Freshness is bidirectional (clash with any neighbouring day); the alert text
            // stays backward-looking for a natural "you had this N days ago" phrasing.
            ComboOption(
                id = c.id,
                name = c.name,
                subtitle = c.items.joinToString(" · ") { it.name },
                alert = alert,
                fresh = !PlanGenerator.wouldRepeat(date, c, placedAll),
            )
        }.sortedWith(compareByDescending<ComboOption> { it.fresh }.thenBy { it.name })

        return SwapState(date, slot, currentName, options)
    }
}
