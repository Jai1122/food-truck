package com.family.mealplanner

import android.app.Application
import com.family.mealplanner.data.AppDatabase
import com.family.mealplanner.data.MealRepository
import com.family.mealplanner.data.SettingsStore

class MealApp : Application() {
    val database by lazy { AppDatabase.get(this) }
    val settings by lazy { SettingsStore(this) }
    val repository by lazy { MealRepository(database.dao(), settings) }
}
