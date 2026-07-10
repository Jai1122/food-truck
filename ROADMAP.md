# Family Meal Planner — Roadmap

_Last updated: 2026-07-05_

An offline-first Android app that auto-generates a weekly (Sun→Sat) meal plan from your
own dish library, avoids repeating "don't-repeat" items, and stays fully on-device.

**Companion docs:** [`REQUIREMENTS.md`](./REQUIREMENTS.md) (v1.3) ·
[`GENERATION.md`](./GENERATION.md) (algorithm) · [`design-system.html`](./design-system.html) (visual spec).

**Status legend:** ✅ done & verified · 🟡 partial · ⬜ not started · 💤 deferred (v2+)

---

## Current state

A **working app** running on the Pixel 6 emulator: you can add roles/items/combos and
generate a real week that honours the repeat rules. Built with **Kotlin + Jetpack Compose
+ Room**, `compileSdk 34 / minSdk 26`, no network permissions.

- Build: `./gradlew :app:assembleDebug` → **BUILD SUCCESSFUL**
- Verified end-to-end on-device: generation, swap, roles CRUD.

---

## ✅ Accomplished

### Specs & design
- ✅ Requirements finalised (`REQUIREMENTS.md` v1.3)
- ✅ Generation algorithm spec (`GENERATION.md` v1)
- ✅ Material 3 design system, "Airbnb-warmed", light + dark (`design-system.html`)

### Project & platform
- ✅ Gradle/Compose/Room project scaffolded; compiles, installs, launches
- ✅ Warm Material 3 theme wired to brand tokens (light + dark)
- ✅ Bottom-navigation shell: This Week · Combos · Items · History
- ✅ Adaptive launcher icon; edge-to-edge host activity

### Data layer (Room)
- ✅ Entities: `Role`, `Item`, `Combo`, `ComboItem`, `ComboSlot`, `DayRule`, `PlanEntry`
- ✅ Type converters (Slot, Weekday, LocalDate); DAO; repository
- ✅ First-run seed of default roles (Base / Chutney / Side)

### Generation engine
- ✅ `PlanGenerator` implements `GENERATION.md`: 7-day repeat window, LRU variety,
  blank-not-repeat, day-rule (veg-only) filter, source-agnostic repeat alerts
- ✅ **Verified on-device** against the DB (chutneys never repeat; can-repeat sides rotate)

### Screens & flows
- ✅ **This Week** — empty / no-plan / plan states; Generate week; per-day & per-week
  regenerate; clear; today highlight; blank-slot hint
- ✅ **Tap-to-swap** bottom sheet — fresh combos ranked first, repeats flagged with
  "N days ago"; clear-slot
- ✅ **Items** — add/delete items with role + vegetarian toggle
- ✅ **Manage Roles** — full CRUD; edit dialog with conditional window field;
  **in-use delete guard**
- ✅ **Combos** — create (name, pick items, tag slots) + delete

---

## 🟡 / ⬜ Pending

### Near-term (next milestones)
- 🟡 **Combos: editing** existing combos (currently create + delete only)
- 🟡 **Settings screen** — ✅ built & verified on-device (2026-07-06)
  - ✅ Configurable meal slots (Breakfast/Lunch/Dinner) → flows to the plan grid
  - ✅ **Day rules** — per-weekday vegetarian-only (e.g. Saturday); persisted + read by generator
  - ✅ Household size (display-only stepper)
  - ⬜ Global repeat-window default (per-role override already editable)
  - ⬜ Theme override (follow system / light / dark)
- ✅ **History screen** — real past-weeks list, grouped newest-first with a "Last week" badge
  (built & verified 2026-07-06)
- ✅ **Export / Import** — share-with-family `.json` via the Android share sheet (FileProvider),
  scope-at-export (library / +plans), merge-or-replace-at-import with a replace confirm.
  Round-trip verified on-device 2026-07-06 (export → wipe → merge restored everything).

### Polish
- ⬜ **Theming: fix the lavender tint** on cards/dialogs/nav (define the `surfaceContainer`
  token family so surfaces read warm greige, not M3-default purple)
- ⬜ Snackbars + **Undo** for deletes / clears / regenerate
- ⬜ Delete-confirm dialogs (items, combos); unsaved-changes guard in editors
- ⬜ Motion: container-transform into editors, staggered week reveal, predictive back
- ⬜ Local **reminders** (optional, off by default — "plan next week")
- ⬜ Pick a final app name (candidates in chat: Tiffin, Thali, Sappadu, …) + wire `app_name`

### Quality
- ⬜ Unit tests for `PlanGenerator` (freshness, LRU, day-rule, alerts)
- ⬜ Repository/DAO instrumentation tests
- ⬜ Accessibility pass (content descriptions, touch targets, TalkBack)

---

## 💤 Deferred to future (v2+)

- 💤 **Dish imagery** — category illustrations + optional user photos (offline storage,
  export bundling). _Explicitly parked; see `REQUIREMENTS.md` §9._
- 💤 **Balanced variety** algorithm (no same category two days running; category caps)
- 💤 Mark meals **cooked / skipped**; ratings
- 💤 Shopping list, ingredients, quantities
- 💤 Online recipe sources
- 💤 Cloud sync & user accounts (app is intentionally offline / no-auth)

---

## Known issues
- Lavender tint on Material 3 default container surfaces (theming polish item above).
- Combos can be created & deleted but not yet edited.

## Code review log (2026-07-06)
- ✅ **Fixed:** per-day regenerate & swap "Fresh" now check repeats **bidirectionally**
  (`PlanGenerator.wouldRepeat`), so a re-rolled day avoids clashing with neighbouring days,
  not just earlier ones. On-grid alert text stays backward-looking ("N days ago").
- ⬜ **Deferred:** add a unique index on `PlanEntry(date, slot)` (needs a schema bump/migration).
- ⬜ **Deferred:** wrap `saveCombo` / `generateAndSaveWeek` multi-writes in a DB transaction.
- ⬜ **Deferred:** recompute `weekStart` on resume so it doesn't go stale past midnight.

## QA pass log (2026-07-06) — full on-device CTA sweep
Verified via real UI taps + DB assertions on a clean install. **All CTAs pass** except the
noted gaps.
- ✅ Bottom nav (4 tabs) · empty-library vs no-plan states · Generate · plan grid · today
  highlight · blanks + hint · overflow → Regenerate/Clear week · Clear week
- ✅ Swap sheet: fill blank, choose, fresh-first ranking, "N days ago" alerts, clear slot
- ✅ Manual fill → amber repeat alert renders
- ✅ Add Item dialog (name / role chips / veg / enable-guard / persist) · item delete
- ✅ Combo editor (name / slots / items / Save-guard / persist) · combo delete
- ✅ Roles: list / add / edit (conditional window) / delete-unused / delete-in-use guard
- 🐛→✅ **Fixed:** This Week showed a **stale empty state** after adding combos on another
  tab (imperative VM never re-pulled). Added on-appear `refresh()` (`LaunchedEffect`) —
  verified fixed on-device.

### Gaps found during QA
- 🐛→✅ **Fixed:** deleting an item now shows a **confirm listing affected combos** and
  **cascade-cleans `combo_items`** (no orphaned junction rows; the item is removed from any
  combo using it). Verified on-device — no orphans, no crash. (`REQUIREMENTS.md §10`)
- 🐛→✅ **Fixed:** combo delete now shows a confirm dialog (accidental-tap guard); verified it
  removes the combo with no orphaned `combo_items` / `combo_slots`. All three deletes
  (item / combo / role) now confirm and cascade cleanly.
- ✅ History screen implemented (past weeks, grouped, "Last week" badge).

---

## Suggested build order
1. Settings (meal slots + Saturday-veg day rule) — unlocks the rest of the plan model
2. Combo editing + delete-confirm dialogs
3. Theming polish (lavender fix) + snackbars/Undo
4. History screen
5. Export / Import
6. Tests + accessibility pass
