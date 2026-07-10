package com.family.mealplanner.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.family.mealplanner.ui.model.ItemRow
import com.family.mealplanner.ui.model.RoleDetail
import com.family.mealplanner.ui.model.RoleOption
import com.family.mealplanner.ui.vm.AppViewModelFactory
import com.family.mealplanner.ui.vm.ItemsViewModel

@Composable
fun ItemsScreen(modifier: Modifier = Modifier) {
    val vm: ItemsViewModel = viewModel(factory = AppViewModelFactory)
    val items by vm.items.collectAsState()
    val roles by vm.roles.collectAsState()
    val rolesDetail by vm.rolesDetail.collectAsState()
    val pendingDelete by vm.pendingItemDelete.collectAsState()

    var showItemDialog by remember { mutableStateOf(false) }
    var showRoles by remember { mutableStateOf(false) }

    if (showRoles) {
        RolesManager(
            roles = rolesDetail,
            modifier = modifier,
            onBack = { showRoles = false },
            onAdd = { name, canRepeat, window -> vm.addRole(name, canRepeat, window) },
            onUpdate = { id, name, canRepeat, window -> vm.updateRole(id, name, canRepeat, window) },
            onDelete = { id -> vm.deleteRole(id) },
        )
        return
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { showItemDialog = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "Add item")
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Items", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { showRoles = true }) { Text("Roles") }
            }

            if (items.isEmpty()) {
                EmptyHint("No items yet", "Add your building blocks — Idly, Dosa, a chutney — each with a role.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.id }) { row ->
                        ItemCard(row, onDelete = { vm.requestDeleteItem(row.id, row.name) })
                    }
                    item { Spacer(Modifier.padding(bottom = 72.dp)) }
                }
            }
        }
    }

    if (showItemDialog) {
        AddItemDialog(
            roles = roles,
            onDismiss = { showItemDialog = false },
            onAdd = { name, roleId, veg ->
                vm.addItem(name, roleId, veg)
                showItemDialog = false
            },
        )
    }

    pendingDelete?.let { pd ->
        AlertDialog(
            onDismissRequest = { vm.cancelDeleteItem() },
            title = { Text("Delete \"${pd.name}\"?") },
            text = {
                Text(
                    if (pd.affectedCombos.isEmpty()) {
                        "This item isn't used in any combo."
                    } else {
                        "Used in ${pd.affectedCombos.size} combo(s): " +
                            "${pd.affectedCombos.joinToString(", ")}. It'll be removed from them."
                    },
                )
            },
            confirmButton = { TextButton(onClick = { vm.confirmDeleteItem() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { vm.cancelDeleteItem() }) { Text("Cancel") } },
        )
    }
}

// ---------- Manage Roles ----------

@Composable
private fun RolesManager(
    roles: List<RoleDetail>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onAdd: (String, Boolean, Int) -> Unit,
    onUpdate: (Long, String, Boolean, Int) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RoleDetail?>(null) }
    var confirmDelete by remember { mutableStateOf<RoleDetail?>(null) }
    var blockedDelete by remember { mutableStateOf<RoleDetail?>(null) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "New role")
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text("Roles", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                "Roles carry the repeat rule. Turn off \"can repeat\" for things that shouldn't recur (like a chutney).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(roles, key = { it.id }) { role ->
                    RoleCard(
                        role = role,
                        onEdit = { editing = role },
                        onDelete = { if (role.itemCount > 0) blockedDelete = role else confirmDelete = role },
                    )
                }
                item { Spacer(Modifier.padding(bottom = 72.dp)) }
            }
        }
    }

    if (showAdd) {
        RoleDialog(
            existing = null,
            onDismiss = { showAdd = false },
            onConfirm = { name, canRepeat, window -> onAdd(name, canRepeat, window); showAdd = false },
        )
    }
    editing?.let { role ->
        RoleDialog(
            existing = role,
            onDismiss = { editing = null },
            onConfirm = { name, canRepeat, window -> onUpdate(role.id, name, canRepeat, window); editing = null },
        )
    }
    confirmDelete?.let { role ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete \"${role.name}\"?") },
            text = { Text("This role isn't used by any items, so it's safe to remove.") },
            confirmButton = {
                TextButton(onClick = { onDelete(role.id); confirmDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
    blockedDelete?.let { role ->
        AlertDialog(
            onDismissRequest = { blockedDelete = null },
            title = { Text("Can't delete \"${role.name}\"") },
            text = {
                Text("${role.itemCount} item(s) use this role. Reassign or delete those items first, then remove the role.")
            },
            confirmButton = { TextButton(onClick = { blockedDelete = null }) { Text("Got it") } },
        )
    }
}

@Composable
private fun RoleCard(role: RoleDetail, onEdit: () -> Unit, onDelete: () -> Unit) {
    val rule = if (role.canRepeat) "Can repeat" else "Don't repeat · ${role.repeatWindowDays}d"
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(role.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "$rule  ·  ${role.itemCount} item(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, contentDescription = "Edit ${role.name}") }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete ${role.name}") }
        }
    }
}

@Composable
private fun RoleDialog(
    existing: RoleDetail?,
    onDismiss: () -> Unit,
    onConfirm: (String, Boolean, Int) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var canRepeat by remember { mutableStateOf(existing?.canRepeat ?: true) }
    var windowText by remember { mutableStateOf((existing?.repeatWindowDays ?: 7).toString()) }
    val windowValid = canRepeat || (windowText.toIntOrNull()?.let { it in 1..365 } == true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New role" else "Edit role") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Role name (e.g. Gravy)") },
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Can repeat", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Off = warn when it recurs within the window",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = canRepeat, onCheckedChange = { canRepeat = it })
                }
                if (!canRepeat) {
                    OutlinedTextField(
                        value = windowText,
                        onValueChange = { windowText = it.filter(Char::isDigit).take(3) },
                        label = { Text("Don't-repeat window (days)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(220.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, canRepeat, windowText.toIntOrNull() ?: 7) },
                enabled = name.isNotBlank() && windowValid,
            ) { Text(if (existing == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------- Items ----------

@Composable
private fun ItemCard(row: ItemRow, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(row.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = row.roleName + if (row.isVeg) "  ·  Veg" else "  ·  Non-veg",
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
private fun EmptyHint(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun AddItemDialog(
    roles: List<RoleOption>,
    onDismiss: () -> Unit,
    onAdd: (String, Long, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var roleId by remember { mutableLongStateOf(roles.firstOrNull()?.id ?: -1L) }
    var veg by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item name") },
                    singleLine = true,
                )
                Text("Role", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    roles.forEach { role ->
                        FilterChip(
                            selected = roleId == role.id,
                            onClick = { roleId = role.id },
                            label = { Text(role.name) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Vegetarian", Modifier.weight(1f))
                    Switch(checked = veg, onCheckedChange = { veg = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name, roleId, veg) },
                enabled = name.isNotBlank() && roleId > 0,
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
