package com.family.mealplanner.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.family.mealplanner.data.entity.Item
import com.family.mealplanner.data.model.Slot
import com.family.mealplanner.ui.model.ComboRow
import com.family.mealplanner.ui.vm.AppViewModelFactory
import com.family.mealplanner.ui.vm.CombosViewModel

@Composable
fun CombosScreen(modifier: Modifier = Modifier) {
    val vm: CombosViewModel = viewModel(factory = AppViewModelFactory)
    val combos by vm.combos.collectAsState()
    val allItems by vm.allItems.collectAsState()
    val pendingDelete by vm.pendingDelete.collectAsState()
    var editing by remember { mutableStateOf(false) }

    if (editing) {
        ComboEditor(
            allItems = allItems,
            modifier = modifier,
            onCancel = { editing = false },
            onSave = { name, itemIds, slots ->
                vm.saveCombo(name, itemIds, slots)
                editing = false
            },
        )
        return
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "New combo")
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
            Text(
                "Combos",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            if (combos.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Build your first combo", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Name a meal like \"Idly + Coconut Chutney\", pick its items and slots.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(combos, key = { it.id }) { row ->
                        ComboCard(row, onDelete = { vm.requestDelete(row) })
                    }
                    item { Spacer(Modifier.padding(bottom = 72.dp)) }
                }
            }
        }
    }

    pendingDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { vm.cancelDelete() },
            title = { Text("Delete \"${row.name}\"?") },
            text = { Text("This removes the combo from your library. Your items and any past plans stay.") },
            confirmButton = { TextButton(onClick = { vm.confirmDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { vm.cancelDelete() }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ComboCard(row: ComboRow, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = row.subtitle + if (row.isVeg) "  ·  Veg" else "  ·  Non-veg",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete ${row.name}")
            }
        }
    }
}

@Composable
private fun ComboEditor(
    allItems: List<Item>,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
    onSave: (String, List<Long>, List<Slot>) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val selectedItems = remember { mutableStateListOf<Long>() }
    val selectedSlots = remember { mutableStateListOf<Slot>() }
    val canSave = name.isNotBlank() && selectedItems.isNotEmpty() && selectedSlots.isNotEmpty()

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Cancel")
            }
            Text("New combo", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { onSave(name, selectedItems.toList(), selectedSlots.toList()) }, enabled = canSave) {
                Text("Save")
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Combo name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Meal slots", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Slot.entries.forEach { slot ->
                val on = slot in selectedSlots
                FilterChip(
                    selected = on,
                    onClick = { if (on) selectedSlots.remove(slot) else selectedSlots.add(slot) },
                    label = { Text(slot.label) },
                )
            }
        }

        Text("Items", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
        if (allItems.isEmpty()) {
            Text(
                "No items yet — add some in the Items tab first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(allItems, key = { it.id }) { item ->
                    val on = item.id in selectedItems
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { if (on) selectedItems.remove(item.id) else selectedItems.add(item.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = null)
                        Text(item.name, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                item { Spacer(Modifier.padding(bottom = 24.dp)) }
            }
        }
    }
}
