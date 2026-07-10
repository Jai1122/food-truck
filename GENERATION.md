# Weekly Plan Generation — Algorithm Spec v1

How "Generate week" turns the combo library into a Sun→Saturday plan. Companion to
`REQUIREMENTS.md`. Goal: fill enabled slots with combos, **prefer not repeating**
"don't-repeat" role items, **leave a slot blank rather than force a repeat**, and keep
variety pleasant without (yet) building the deferred "balanced variety" feature.

---

## 1. Inputs

- **weekStart** — the Sunday of the target week; days D0..D6 = Sun..Sat.
- **enabledSlots** — ordered subset of {Breakfast, Lunch, Dinner} (from settings).
- **library** — combos; each combo has `items[]` and eligible `slots[]`.
  Each item has a `role` (with `canRepeat: Boolean`, `repeatWindowDays: Int`) and an
  `isVegetarian: Boolean` (default true). A combo is **vegetarian iff all its items are**.
- **dayRules** — optional per-weekday dietary constraint. v1 supports `vegetarianOnly`
  (e.g. Saturday → vegetarian only). Extensible to other rule types later.
- **history** — all past `PlanEntry` rows (previous weeks). During generation we also
  treat entries placed *earlier in this same run* as history (so within-week repeats count).
- **seed** — fresh random seed per "Generate" tap (so regenerating gives a new plan);
  deterministic given the same seed + inputs (for tests).

## 2. Core definitions

**Don't-repeat item** — an item whose `role.canRepeat == false` (e.g. Coconut Chutney).
Only these can cause a "repeat". Can-repeat items (e.g. Idly) never block and never alert.

**Violation(combo C, date D)** — C would repeat a don't-repeat item at D if any item `i`
in C with `!i.role.canRepeat` already appears in `placed` on a date `p` where
`0 ≤ (D − p) ≤ i.role.repeatWindowDays`. (Gap 0 = same day, earlier slot — counts.)

**Fresh(C, D)** — C has **no** violation at D. Freshness ignores can-repeat items entirely.

## 3. Processing order

Chronological and deterministic: **day D0→D6**, and within each day the slots in
`enabledSlots` order (Breakfast → Lunch → Dinner). Each placement is appended to `placed`
immediately, so it becomes history for every later slot in the same run.

## 4. Per-slot selection

```
for day in D0..D6:
  for slot in enabledSlots:
     eligible = library.combos where slot ∈ combo.slots
                                   AND satisfiesDayRule(combo, weekday(date))   // §4b
     if eligible is empty:            -> BLANK(date, slot); continue     // nothing to offer
     fresh = eligible where Fresh(combo, date)
     if fresh is empty:               -> BLANK(date, slot); continue     // don't force a repeat
     chosen = pickLeastRecentlyUsed(fresh, date, placed, seed)
     place PlanEntry(date, slot, chosen); placed += it
```

**Blank slots** are valid outcomes (a dashed, tappable placeholder). If the week has any
blanks, show one summary hint: *"Add more combos to fill every day."*

## 4b. Day rules (dietary constraints)

```
satisfiesDayRule(combo, weekday):
   rule = dayRules[weekday]
   if rule == null: return true
   if rule.vegetarianOnly and not combo.isVegetarian: return false
   return true
```

- Rules are set per weekday in Settings (e.g. **Saturday → Vegetarian only**).
- The filter applies to **auto-generation, single-day re-rolls, and the manual swap sheet**:
  on a veg-only day, non-veg combos are hidden from suggestions and blocked from auto-fill.
- If a rule leaves too few eligible combos, the usual **blank** outcome + hint applies
  ("Add more vegetarian combos for Saturday.").
- The rule is a **hard block**: non-veg combos are not selectable on a veg-only day, in
  **both** auto-generation and the manual swap sheet (they're hidden/disabled with the reason
  "Saturday is vegetarian"). No manual override — a firm rule stays firm. *(Unlike the
  repeat-alert, which is soft, dietary day-rules are enforced.)*

## 4c. Regeneration scopes — reject &amp; re-roll

The user can reject and re-roll at three scopes; each re-runs §4 over just that scope while
treating everything outside the scope as fixed history. All are undoable (5s snackbar).

| Scope | Trigger | Effect |
|---|---|---|
| **Whole week** | "Generate week" when a plan exists → "Regenerate week" (confirm) | Clears all 7 days and re-rolls from scratch (new seed). Manual edits are discarded — hence the confirm. |
| **One day** | Day card overflow → "Regenerate day" | Re-rolls every enabled slot for that day only; other days untouched and counted as history. |
| **One slot** | Slot tap → swap sheet (existing) | Re-picks a single slot. |
| **Clear** | Week/day overflow → "Clear" | Empties the scope to blanks without re-rolling. |

## 5. Variety heuristic — Least-Recently-Used rotation

Among **fresh** candidates, pick the one whose items have gone **unused the longest**, so
Idly/Dosa/Pongal rotate naturally instead of the same one every day. This is a light
stand-in for the deferred "balanced variety" rule — cheap and good enough for v1.

```
recencyScore(C, D) =
   min over items i in C of gapSinceLastUse(i, D, placed)
   // gapSinceLastUse = D − (latest date i was used); never used ⇒ +∞

pickLeastRecentlyUsed(fresh, D, placed, seed):
   maxScore = max recencyScore over fresh          // largest gap = most "rested"
   winners  = fresh where recencyScore == maxScore
   return winners.randomPick(seed)                 // seeded tie-break ⇒ fresh variety, reproducible
```

*(Rationale: a combo is limited by its most-recently-used item — using `min` over items
means we favour combos where even the freshest ingredient has rested a while.)*

## 6. Repeat alerts (display) — one pure function, source-agnostic

Alerts are computed the **same way regardless of how a slot was filled** (auto or manual).
Because §4 never forces a violation, auto-generated plans normally show **no** alerts; the
alert surfaces on **manual** fills/swaps, or if the library/window later changes.

```
alertFor(entry E):
   candidates = for each don't-repeat item i in E.combo:
        lastPrior = latest OTHER placed entry containing i
                    with 0 ≤ (E.date − p) ≤ i.role.repeatWindowDays
        -> (i, gap = E.date − lastPrior.date)   if lastPrior exists
   return the candidate with the smallest gap   // "Coconut Chutney · 1 day ago"
   (null if none)
```

## 7. Manual swap / fill (reuses the same engine)

The slot bottom sheet lists eligible combos **ranked**: **fresh first** (by LRU, §5), then
**repeating** ones (each shown with its `alertFor` badge), then **"＋ New combo"**. Selection
is non-destructive until confirmed; picking a repeating combo is allowed (soft rule) and
just carries the alert.

## 8. Confirmed defaults

| Setting | Value | Note |
|---|---|---|
| **Repeat window** (don't-repeat roles) | **7 days** ✅ | One week — a don't-repeat item won't recur within the same week. Global default, optionally overridable per role. |
| **No-fresh-option policy** | **Leave blank** ✅ | Never force a repeat; the slot stays a tappable blank. Control over a "full week" comes from **manual fill + §4c regeneration**, not auto-repeat. |
| **Variety** | **LRU rotation (§5)** | Full "balanced variety" (no same category two days running, category caps) stays deferred. |
| **Day rules** | **Per-weekday, `vegetarianOnly`** (§4b) | e.g. Saturday = vegetarian only. **Hard block** — enforced in auto-gen and manual, no override. |

## 9. Edge cases

- **No enabled slots** → nothing to generate; Settings nudges to enable ≥1 slot.
- **Empty library** → every slot blank ⇒ This Week shows the "Add your first dish" empty
  state, not a blank grid.
- **Fewer fresh combos than slots** → some blanks (expected, hinted).
- **Window ≥ combo supply** (everything repeats) → many blanks; hint suggests adding combos
  or lowering the window. (Or flip the §8 policy toggle, if enabled.)
- **Regenerate** → new seed ⇒ different valid plan; existing history still respected.
- **Partial regenerate / single-slot swap** → treated as one placement against current
  `placed`; same freshness + alert rules.

## 10. Complexity

Per slot: O(eligible × placed-within-window). With a family library (tens of combos, a few
weeks of history) this is trivially fast and runs fully on-device, synchronously, no spinner.
