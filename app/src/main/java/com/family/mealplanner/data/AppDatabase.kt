package com.family.mealplanner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.family.mealplanner.data.entity.Combo
import com.family.mealplanner.data.entity.ComboItem
import com.family.mealplanner.data.entity.ComboSlot
import com.family.mealplanner.data.entity.DayRule
import com.family.mealplanner.data.entity.Item
import com.family.mealplanner.data.entity.PlanEntry
import com.family.mealplanner.data.entity.Role

@Database(
    entities = [
        Role::class,
        Item::class,
        Combo::class,
        ComboItem::class,
        ComboSlot::class,
        DayRule::class,
        PlanEntry::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): MealPlannerDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mealplanner.db",
                ).addCallback(SeedCallback).build().also { INSTANCE = it }
            }

        // Seed a few sensible default roles on first launch (dishes stay user-entered).
        private val SeedCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "INSERT INTO roles (name, canRepeat, repeatWindowDays) VALUES " +
                        "('Base', 1, 7), ('Chutney', 0, 7), ('Side', 1, 7)",
                )
            }
        }
    }
}
