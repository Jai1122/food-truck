package com.family.mealplanner.data

import androidx.room.TypeConverter
import com.family.mealplanner.data.model.Slot
import com.family.mealplanner.data.model.Weekday
import java.time.LocalDate

class Converters {
    @TypeConverter fun slotToString(value: Slot): String = value.name
    @TypeConverter fun stringToSlot(value: String): Slot = Slot.valueOf(value)

    @TypeConverter fun weekdayToString(value: Weekday): String = value.name
    @TypeConverter fun stringToWeekday(value: String): Weekday = Weekday.valueOf(value)

    // ISO-8601 text keeps dates sortable/comparable with BETWEEN queries.
    @TypeConverter fun dateToString(value: LocalDate): String = value.toString()
    @TypeConverter fun stringToDate(value: String): LocalDate = LocalDate.parse(value)
}
