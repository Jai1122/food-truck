package com.family.mealplanner.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.family.mealplanner.data.ImportResult
import com.family.mealplanner.data.model.Slot
import com.family.mealplanner.data.model.Weekday
import com.family.mealplanner.ui.vm.AppViewModelFactory
import com.family.mealplanner.ui.vm.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val weekdayNames = listOf(
    "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday",
)

@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = viewModel(factory = AppViewModelFactory)
    val enabledSlots by vm.enabledSlots.collectAsState()
    val household by vm.householdSize.collectAsState()
    val vegOnly by vm.vegOnlyByWeekday.collectAsState()

    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showExportScope by remember { mutableStateOf(false) }
    var pendingImportText by remember { mutableStateOf<String?>(null) }
    var showReplaceConfirm by remember { mutableStateOf(false) }

    fun shareJson(json: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "meal-plan.json")
        file.writeText(json)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share meal plan"))
    }

    fun showResult(result: ImportResult) {
        val msg = when (result) {
            is ImportResult.Success ->
                if (result.replaced) {
                    "Replaced — imported ${result.combos} combos, ${result.items} items"
                } else {
                    "Added ${result.combos} combos, ${result.items} items, ${result.plans} plans"
                }
            is ImportResult.Error -> result.message
        }
        scope.launch { snackbar.showSnackbar(msg) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                }
                if (text == null) snackbar.showSnackbar("Couldn't read that file") else pendingImportText = text
            }
        }
    }

    Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text("Settings", style = MaterialTheme.typography.titleLarge)
            }

            SectionHeader("Meal slots")
            Text(
                "Which meals to plan each day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slot.entries.forEach { slot ->
                ToggleRow(
                    title = slot.label,
                    subtitle = null,
                    checked = slot in enabledSlots,
                    onCheckedChange = { vm.toggleSlot(slot, it) },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            SectionHeader("Day rules")
            Text(
                "Restrict certain days — e.g. vegetarian-only on Saturday. Enforced when planning.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Weekday.entries.forEachIndexed { index, day ->
                ToggleRow(
                    title = weekdayNames[index],
                    subtitle = "Vegetarian only",
                    checked = vegOnly[day] == true,
                    onCheckedChange = { vm.setVegOnly(day, it) },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            SectionHeader("Household")
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Family size", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "For your reference (doesn't change planning yet).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { vm.setHouseholdSize(household - 1) }) {
                    Icon(Icons.Rounded.Remove, contentDescription = "Fewer")
                }
                Text(
                    "$household",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(32.dp),
                )
                IconButton(onClick = { vm.setHouseholdSize(household + 1) }) {
                    Icon(Icons.Rounded.Add, contentDescription = "More")
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            SectionHeader("Backup & sharing")
            Text(
                "Export a file to back up or send to family. Import merges with or replaces your data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = { showExportScope = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.width(18.dp))
                    Text("  Export", maxLines = 1)
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.width(18.dp))
                    Text("  Import", maxLines = 1)
                }
            }

            Text(
                "Offline · no account · your data stays on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp, bottom = 24.dp),
            )
        }
    }

    if (showExportScope) {
        AlertDialog(
            onDismissRequest = { showExportScope = false },
            title = { Text("Export") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose what to include, then share the file.")
                    Button(
                        onClick = { showExportScope = false; vm.exportJson(false) { shareJson(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Dish library only") }
                    OutlinedButton(
                        onClick = { showExportScope = false; vm.exportJson(true) { shareJson(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Library + plans & history") }
                }
            },
            confirmButton = { TextButton(onClick = { showExportScope = false }) { Text("Cancel") } },
        )
    }

    if (pendingImportText != null && !showReplaceConfirm) {
        AlertDialog(
            onDismissRequest = { pendingImportText = null },
            title = { Text("Import") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Merge keeps your dishes and adds new ones. Replace wipes everything first.")
                    Button(
                        onClick = {
                            val t = pendingImportText!!
                            pendingImportText = null
                            vm.importJson(t, replace = false) { showResult(it) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Merge / add") }
                    OutlinedButton(
                        onClick = { showReplaceConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Replace everything") }
                }
            },
            confirmButton = { TextButton(onClick = { pendingImportText = null }) { Text("Cancel") } },
        )
    }

    if (showReplaceConfirm && pendingImportText != null) {
        AlertDialog(
            onDismissRequest = { showReplaceConfirm = false },
            title = { Text("Replace everything?") },
            text = {
                Text("This deletes your current items, combos, day rules and plans, then imports the file. This can't be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = pendingImportText!!
                    pendingImportText = null
                    showReplaceConfirm = false
                    vm.importJson(t, replace = true) { showResult(it) }
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { showReplaceConfirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
