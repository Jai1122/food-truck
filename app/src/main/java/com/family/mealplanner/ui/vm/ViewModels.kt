package com.family.mealplanner.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.family.mealplanner.MealApp
import com.family.mealplanner.data.ImportResult
import com.family.mealplanner.data.MealRepository
import com.family.mealplanner.data.entity.Item
import com.family.mealplanner.data.model.Slot
import com.family.mealplanner.data.model.Weekday
import com.family.mealplanner.ui.model.ComboRow
import com.family.mealplanner.ui.model.HistoryWeekUi
import com.family.mealplanner.ui.model.ItemRow
import com.family.mealplanner.ui.model.PendingItemDelete
import com.family.mealplanner.ui.model.RoleDetail
import com.family.mealplanner.ui.model.RoleOption
import com.family.mealplanner.ui.model.SwapState
import com.family.mealplanner.ui.model.ThisWeekState
import com.family.mealplanner.ui.model.WeekView
import com.family.mealplanner.util.DateUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

private val empty = SharingStarted.WhileSubscribed(5_000)

class ItemsViewModel(private val repo: MealRepository) : ViewModel() {
    val items: StateFlow<List<ItemRow>> =
        combine(repo.itemsFlow, repo.rolesFlow) { items, roles ->
            val byId = roles.associateBy { it.id }
            items.map { ItemRow(it.id, it.name, byId[it.roleId]?.name ?: "—", it.isVegetarian) }
        }.stateIn(viewModelScope, empty, emptyList())

    val roles: StateFlow<List<RoleOption>> =
        repo.rolesFlow.map { list -> list.map { RoleOption(it.id, it.name) } }
            .stateIn(viewModelScope, empty, emptyList())

    /** Roles with a live "how many items use this" count, for the manage screen. */
    val rolesDetail: StateFlow<List<RoleDetail>> =
        combine(repo.rolesFlow, repo.itemsFlow) { roles, items ->
            roles.map { r ->
                RoleDetail(r.id, r.name, r.canRepeat, r.repeatWindowDays, items.count { it.roleId == r.id })
            }
        }.stateIn(viewModelScope, empty, emptyList())

    private val _pendingItemDelete = MutableStateFlow<PendingItemDelete?>(null)
    val pendingItemDelete: StateFlow<PendingItemDelete?> = _pendingItemDelete.asStateFlow()

    fun addItem(name: String, roleId: Long, veg: Boolean) =
        viewModelScope.launch { repo.addItem(name.trim(), roleId, veg) }

    /** Ask to delete an item — loads which combos use it so the UI can warn first. */
    fun requestDeleteItem(id: Long, name: String) = viewModelScope.launch {
        _pendingItemDelete.value = PendingItemDelete(id, name, repo.combosUsingItem(id))
    }

    fun confirmDeleteItem() = viewModelScope.launch {
        _pendingItemDelete.value?.let { repo.deleteItem(it.id) }
        _pendingItemDelete.value = null
    }

    fun cancelDeleteItem() { _pendingItemDelete.value = null }

    fun addRole(name: String, canRepeat: Boolean, windowDays: Int) =
        viewModelScope.launch { repo.addRole(name.trim(), canRepeat, windowDays) }

    fun updateRole(id: Long, name: String, canRepeat: Boolean, windowDays: Int) =
        viewModelScope.launch { repo.updateRole(id, name.trim(), canRepeat, windowDays) }

    fun deleteRole(id: Long) = viewModelScope.launch { repo.deleteRole(id) }
}

class CombosViewModel(private val repo: MealRepository) : ViewModel() {
    val combos: StateFlow<List<ComboRow>> =
        repo.combosFlow.map { list ->
            list.map { cw ->
                ComboRow(
                    id = cw.combo.id,
                    name = cw.combo.name,
                    subtitle = cw.items.joinToString(" · ") { it.name }.ifEmpty { "No items" },
                    isVeg = cw.items.all { it.isVegetarian },
                )
            }
        }.stateIn(viewModelScope, empty, emptyList())

    val allItems: StateFlow<List<Item>> =
        repo.itemsFlow.stateIn(viewModelScope, empty, emptyList())

    private val _pendingDelete = MutableStateFlow<ComboRow?>(null)
    val pendingDelete: StateFlow<ComboRow?> = _pendingDelete.asStateFlow()

    fun saveCombo(name: String, itemIds: List<Long>, slots: List<Slot>) =
        viewModelScope.launch { repo.saveCombo(name.trim(), itemIds, slots) }

    fun requestDelete(row: ComboRow) { _pendingDelete.value = row }
    fun cancelDelete() { _pendingDelete.value = null }
    fun confirmDelete() = viewModelScope.launch {
        _pendingDelete.value?.let { repo.deleteCombo(it.id) }
        _pendingDelete.value = null
    }
}

class ThisWeekViewModel(private val repo: MealRepository) : ViewModel() {
    val weekStart: LocalDate = DateUtil.currentSunday()

    private val _state = MutableStateFlow(ThisWeekState(WeekView.LOADING, "", emptyList(), false))
    val state: StateFlow<ThisWeekState> = _state.asStateFlow()

    private val _swap = MutableStateFlow<SwapState?>(null)
    val swap: StateFlow<SwapState?> = _swap.asStateFlow()

    init { refresh() }

    /** Rebuilds the plan from the DB. Public so the screen can re-pull on (re)appear —
     *  the plan isn't a reactive flow, so combos/items added on other tabs need this. */
    fun refresh() = viewModelScope.launch {
        _state.value = repo.buildThisWeek(weekStart)
    }

    fun generate() = viewModelScope.launch {
        repo.generateAndSaveWeek(weekStart)
        refresh()
    }

    fun regenerateDay(date: LocalDate) = viewModelScope.launch {
        repo.regenerateDay(date, weekStart)
        refresh()
    }

    fun clearWeek() = viewModelScope.launch {
        repo.clearWeek(weekStart)
        refresh()
    }

    fun openSwap(date: LocalDate, slot: Slot) = viewModelScope.launch {
        _swap.value = repo.buildSwap(date, slot, weekStart)
    }

    fun closeSwap() { _swap.value = null }

    fun choose(comboId: Long) = viewModelScope.launch {
        val sw = _swap.value ?: return@launch
        repo.setSlot(sw.date, sw.slot, comboId)
        _swap.value = null
        refresh()
    }

    fun clearCurrentSlot() = viewModelScope.launch {
        val sw = _swap.value ?: return@launch
        repo.clearSlot(sw.date, sw.slot)
        _swap.value = null
        refresh()
    }
}

class SettingsViewModel(private val repo: MealRepository) : ViewModel() {
    val enabledSlots: StateFlow<Set<Slot>> = repo.enabledSlotsFlow
    val householdSize: StateFlow<Int> = repo.householdSize

    val vegOnlyByWeekday: StateFlow<Map<Weekday, Boolean>> =
        repo.dayRulesFlow().map { rules -> rules.associate { it.weekday to it.vegetarianOnly } }
            .stateIn(viewModelScope, empty, emptyMap())

    fun toggleSlot(slot: Slot, enabled: Boolean) = repo.setSlotEnabled(slot, enabled)
    fun setHouseholdSize(n: Int) = repo.setHouseholdSize(n)
    fun setVegOnly(weekday: Weekday, on: Boolean) =
        viewModelScope.launch { repo.setDayVegOnly(weekday, on) }

    fun exportJson(includePlans: Boolean, onReady: (String) -> Unit) =
        viewModelScope.launch { onReady(repo.exportToJson(includePlans)) }

    fun importJson(text: String, replace: Boolean, onDone: (ImportResult) -> Unit) =
        viewModelScope.launch { onDone(repo.importFromJson(text, replace)) }
}

class HistoryViewModel(private val repo: MealRepository) : ViewModel() {
    // null = loading; empty list = no history yet.
    private val _weeks = MutableStateFlow<List<HistoryWeekUi>?>(null)
    val weeks: StateFlow<List<HistoryWeekUi>?> = _weeks.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch { _weeks.value = repo.buildHistory() }
}

/** Single factory wiring ViewModels to the app's repository. */
val AppViewModelFactory = viewModelFactory {
    initializer { ItemsViewModel((this[APPLICATION_KEY] as MealApp).repository) }
    initializer { CombosViewModel((this[APPLICATION_KEY] as MealApp).repository) }
    initializer { ThisWeekViewModel((this[APPLICATION_KEY] as MealApp).repository) }
    initializer { SettingsViewModel((this[APPLICATION_KEY] as MealApp).repository) }
    initializer { HistoryViewModel((this[APPLICATION_KEY] as MealApp).repository) }
}
