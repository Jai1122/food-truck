package com.family.mealplanner.generation

import com.family.mealplanner.data.model.Slot
import com.family.mealplanner.data.model.Weekday
import com.family.mealplanner.util.DateUtil
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.random.Random

/**
 * Pure implementation of GENERATION.md v1.
 * - Prefer non-repeating "don't-repeat" items (7-day window per role).
 * - Never force a repeat: leave the slot blank instead.
 * - Rotate variety via least-recently-used selection.
 * - Day rules (vegetarian-only) hard-filter eligible combos.
 */
object PlanGenerator {

    fun generateWeek(
        weekStart: LocalDate,
        enabledSlots: List<Slot>,
        combos: List<PlannableCombo>,
        vegOnlyByWeekday: Map<Weekday, Boolean>,
        history: List<PlacedCombo>,
        seed: Long,
    ): List<Placement> {
        val rng = Random(seed)
        val placed = history.toMutableList()
        val result = mutableListOf<Placement>()
        for (i in 0..6) {
            val date = weekStart.plusDays(i.toLong())
            result += fillDay(date, enabledSlots, combos, vegOnlyByWeekday, placed, rng)
        }
        return result
    }

    fun generateDay(
        date: LocalDate,
        enabledSlots: List<Slot>,
        combos: List<PlannableCombo>,
        vegOnlyByWeekday: Map<Weekday, Boolean>,
        history: List<PlacedCombo>,
        seed: Long,
    ): List<Placement> {
        val rng = Random(seed)
        val placed = history.toMutableList()
        return fillDay(date, enabledSlots, combos, vegOnlyByWeekday, placed, rng)
    }

    private fun fillDay(
        date: LocalDate,
        enabledSlots: List<Slot>,
        combos: List<PlannableCombo>,
        vegOnlyByWeekday: Map<Weekday, Boolean>,
        placed: MutableList<PlacedCombo>,
        rng: Random,
    ): List<Placement> {
        val vegOnly = vegOnlyByWeekday[DateUtil.weekdayOf(date)] == true
        val out = mutableListOf<Placement>()
        for (slot in enabledSlots) {
            val eligible = combos.filter { slot in it.slots && (!vegOnly || it.isVegetarian) }
            val fresh = eligible.filter { isFresh(it, date, placed) }
            val chosen = if (fresh.isEmpty()) null else pickLeastRecentlyUsed(fresh, date, placed, rng)
            if (chosen != null) {
                out += Placement(date, slot, chosen.id)
                placed += PlacedCombo(date, slot, chosen)
            } else {
                out += Placement(date, slot, null)
            }
        }
        return out
    }

    /**
     * True if placing [combo] on [date] repeats any "don't-repeat" item within its window.
     * Checked in **both directions** so a single-day re-roll (or a swap) also avoids clashing
     * with meals already sitting on neighbouring days, not just earlier ones. During a full
     * week generation `placed` only holds earlier days, so this matches the chronological pass.
     */
    fun wouldRepeat(date: LocalDate, combo: PlannableCombo, placed: List<PlacedCombo>): Boolean =
        combo.items.any { item ->
            !item.canRepeat && placed.any { p ->
                val gap = abs(ChronoUnit.DAYS.between(p.date, date))
                gap <= item.repeatWindowDays.toLong() && p.combo.items.any { it.id == item.id }
            }
        }

    private fun isFresh(combo: PlannableCombo, date: LocalDate, placed: List<PlacedCombo>): Boolean =
        !wouldRepeat(date, combo, placed)

    /** Bigger gap = more "rested". A combo is limited by its most-recently-used item. */
    private fun recencyScore(combo: PlannableCombo, date: LocalDate, placed: List<PlacedCombo>): Long =
        combo.items.minOf { item ->
            val last = placed.filter { p -> p.combo.items.any { it.id == item.id } }
                .maxOfOrNull { it.date }
            if (last == null) Long.MAX_VALUE else ChronoUnit.DAYS.between(last, date)
        }

    private fun pickLeastRecentlyUsed(
        fresh: List<PlannableCombo>,
        date: LocalDate,
        placed: List<PlacedCombo>,
        rng: Random,
    ): PlannableCombo {
        val scored = fresh.map { it to recencyScore(it, date, placed) }
        val best = scored.maxOf { it.second }
        val winners = scored.filter { it.second == best }.map { it.first }
        return winners[rng.nextInt(winners.size)]
    }

    /** For display: the nearest recurring "don't-repeat" item, or null. */
    fun alertFor(date: LocalDate, combo: PlannableCombo, others: List<PlacedCombo>): RepeatAlert? {
        val hits = combo.items.filter { !it.canRepeat }.mapNotNull { item ->
            val last = others.filter { p ->
                val gap = ChronoUnit.DAYS.between(p.date, date)
                gap in 0..item.repeatWindowDays.toLong() && p.combo.items.any { it.id == item.id }
            }.maxByOrNull { it.date }
            last?.let { RepeatAlert(item.name, ChronoUnit.DAYS.between(it.date, date)) }
        }
        return hits.minByOrNull { it.daysAgo }
    }
}
