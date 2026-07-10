package com.family.mealplanner.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EggAlt
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.family.mealplanner.ui.screens.CombosScreen
import com.family.mealplanner.ui.screens.HistoryScreen
import com.family.mealplanner.ui.screens.ItemsScreen
import com.family.mealplanner.ui.screens.SettingsScreen
import com.family.mealplanner.ui.screens.ThisWeekScreen

private data class Destination(val label: String, val icon: ImageVector)

private val destinations = listOf(
    Destination("This Week", Icons.Rounded.CalendarMonth),
    Destination("Combos", Icons.Rounded.Restaurant),
    Destination("Items", Icons.Rounded.EggAlt),
    Destination("History", Icons.Rounded.History),
)

@Composable
fun AppScaffold() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEachIndexed { index, dest ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        val screenModifier = Modifier.padding(innerPadding)
        when (selected) {
            0 -> ThisWeekScreen(screenModifier, onOpenSettings = { showSettings = true })
            1 -> CombosScreen(screenModifier)
            2 -> ItemsScreen(screenModifier)
            else -> HistoryScreen(screenModifier)
        }
    }
}
