package com.family.mealplanner.data.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/** A combo with its items (via the junction) and its eligible slots. */
data class ComboWithItems(
    @Embedded val combo: Combo,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ComboItem::class,
            parentColumn = "comboId",
            entityColumn = "itemId",
        ),
    )
    val items: List<Item>,
    @Relation(parentColumn = "id", entityColumn = "comboId")
    val slots: List<ComboSlot>,
)
