package com.family.mealplanner.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.family.mealplanner.ui.model.DayUi
import com.family.mealplanner.ui.model.SlotUi
import com.family.mealplanner.ui.model.SwapState
import com.family.mealplanner.ui.model.ThisWeekState
import com.family.mealplanner.ui.model.WeekView
import com.family.mealplanner.ui.vm.AppViewModelFactory
import com.family.mealplanner.ui.vm.ThisWeekViewModel

@Composable
fun ThisWeekScreen(modifier: Modifier = Modifier, onOpenSettings: () -> Unit = {}) {
    val vm: ThisWeekViewModel = viewModel(factory = AppViewModelFactory)
    val state by vm.state.collectAsState()
    val swap by vm.swap.collectAsState()

    // The plan isn't a reactive flow; re-pull whenever This Week (re)enters composition
    // so library changes made on other tabs are reflected here.
    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (state.view == WeekView.PLAN) {
                ExtendedFloatingActionButton(
                    onClick = { vm.generate() },
                    icon = { Icon(Icons.Rounded.Autorenew, contentDescription = null) },
                    text = { Text("Generate week") },
                )
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
            Header(
                state,
                onOpenSettings = onOpenSettings,
                onRegenerateWeek = { vm.generate() },
                onClearWeek = { vm.clearWeek() },
            )

            when (state.view) {
                WeekView.LOADING -> CenterBox { CircularProgressIndicator() }
                WeekView.EMPTY_LIBRARY -> EmptyState(
                    title = "No dishes yet",
                    body = "Add a few combos in the Combos tab, then come back to plan your week.",
                )
                WeekView.NO_PLAN -> EmptyState(
                    title = "No plan yet",
                    body = "Generate a week from your combos, and swap anything you don't fancy.",
                    action = "Generate this week",
                    onAction = { vm.generate() },
                )
                WeekView.PLAN -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.days, key = { it.date.toString() }) { day ->
                        DayCard(
                            day = day,
                            onSlotClick = { slot -> vm.openSwap(day.date, slot.slot) },
                            onRegenerateDay = { vm.regenerateDay(day.date) },
                        )
                    }
                    if (state.hasBlanks) {
                        item {
                            Text(
                                "Some slots are blank — add more combos to fill every day.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                    item { Spacer(Modifier.padding(bottom = 80.dp)) }
                }
            }
        }
    }

    swap?.let { SwapSheet(it, onChoose = { id -> vm.choose(id) }, onClear = { vm.clearCurrentSlot() }, onDismiss = { vm.closeSwap() }) }
}

@Composable
private fun Header(
    state: ThisWeekState,
    onOpenSettings: () -> Unit,
    onRegenerateWeek: () -> Unit,
    onClearWeek: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 12.dp, bottom = 8.dp)) {
        if (state.rangeLabel.isNotEmpty()) {
            Text(
                state.rangeLabel.uppercase() + "  ·  THIS WEEK",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("This Week", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings")
            }
            if (state.view == WeekView.PLAN) {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Regenerate week") }, onClick = { menu = false; onRegenerateWeek() })
                        DropdownMenuItem(text = { Text("Clear week") }, onClick = { menu = false; onClearWeek() })
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCard(day: DayUi, onSlotClick: (SlotUi) -> Unit, onRegenerateDay: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            DayChip(day)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                day.slots.forEachIndexed { index, slot ->
                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    SlotRow(slot, onClick = { onSlotClick(slot) })
                }
            }
            IconButton(onClick = onRegenerateDay) {
                Icon(Icons.Rounded.Autorenew, contentDescription = "Regenerate ${day.label}")
            }
        }
    }
}

@Composable
private fun DayChip(day: DayUi) {
    val bg = if (day.isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (day.isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(color = bg, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(44.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(day.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = fg)
            Text("${day.dayOfMonth}", style = MaterialTheme.typography.titleMedium, color = fg, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SlotRow(slot: SlotUi, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp)) {
        Text(
            slot.slot.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (slot.isBlank) {
            Text("Tap to fill", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        } else {
            Text(slot.comboName ?: "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            slot.alert?.let { AlertPill(it) }
        }
    }
}

@Composable
private fun AlertPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
            Icon(
                Icons.Rounded.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(13.dp),
            )
            Text(
                "  $text",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwapSheet(swap: SwapState, onChoose: (Long) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(
                "${swap.slot.label} · choose a combo",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            swap.currentName?.let {
                Text(
                    "Currently: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (swap.options.isEmpty()) {
                Text(
                    "No combos for this slot yet — create one in the Combos tab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                LazyColumn(Modifier.padding(top = 8.dp)) {
                    items(swap.options, key = { it.id }) { opt ->
                        ListItem(
                            headlineContent = { Text(opt.name) },
                            supportingContent = {
                                Text(opt.alert ?: (if (opt.fresh) "Fresh · no repeats" else opt.subtitle))
                            },
                            modifier = Modifier.clickable { onChoose(opt.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text("Clear this slot") }
        }
    }
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun EmptyState(title: String, body: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(28.dp), modifier = Modifier.size(96.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                }
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            )
            if (action != null && onAction != null) {
                Button(onClick = onAction) { Text(action) }
            }
        }
    }
}
