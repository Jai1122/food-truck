package com.family.mealplanner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.family.mealplanner.ui.model.HistoryEntryUi
import com.family.mealplanner.ui.model.HistoryWeekUi
import com.family.mealplanner.ui.vm.AppViewModelFactory
import com.family.mealplanner.ui.vm.HistoryViewModel

@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    val vm: HistoryViewModel = viewModel(factory = AppViewModelFactory)
    val weeks by vm.weeks.collectAsState()

    // Re-pull whenever History (re)appears so newly-past weeks show up.
    LaunchedEffect(Unit) { vm.refresh() }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            "History",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )
        val data = weeks
        when {
            data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            data.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nothing cooked yet", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Weeks you've planned will appear here once they're in the past.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp),
                    )
                }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                data.forEach { week ->
                    item(key = week.rangeLabel) { WeekHeader(week) }
                    items(week.entries, key = { "${week.rangeLabel}-${it.dayLabel}-${it.slotLabel}" }) { entry ->
                        EntryRow(entry)
                    }
                }
                item { Spacer(Modifier.padding(bottom = 24.dp)) }
            }
        }
    }
}

@Composable
private fun WeekHeader(week: HistoryWeekUi) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            week.rangeLabel.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (week.isLastWeek) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(
                    "Last week",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun EntryRow(entry: HistoryEntryUi) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                entry.dayLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(40.dp),
            )
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    entry.slotLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(entry.comboName, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
