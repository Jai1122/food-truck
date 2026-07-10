# Meal Planner

A private, **fully-offline** Android app that keeps your family's own dish library and
auto-generates a weekly (Sunday → Saturday) meal plan — avoiding repeats of the things you
don't want twice in a week, and never phoning home.

No accounts. No network permission. No bundled recipe set — the dishes are the ones you enter.

**Companion docs:** [`REQUIREMENTS.md`](./REQUIREMENTS.md) (v1.3) ·
[`GENERATION.md`](./GENERATION.md) (algorithm spec) ·
[`ROADMAP.md`](./ROADMAP.md) (status) ·
[`design-system.html`](./design-system.html) (visual spec)

---

## Why it exists

Deciding what to cook is a weekly tax. This app pays it for you: one tap fills the week from
combos you've already told it your family eats. It knows that idly two mornings running is
fine, but the same coconut chutney twice in a week is not — because that rule lives on the
*role* of an ingredient, not the dish name.

---

## Core concepts

The data model is three layers deep, which is what lets repeat rules be smart rather than
naive.

**Roles** carry the repeat rule. A role has a `canRepeat` flag and a look-back window in days.
`Base` can repeat (idly two days running is fine). `Chutney` cannot (flag it if it recurs
within 7 days). `Side` can repeat. Roles are user-defined, and the app seeds three sensible
ones on first launch.

**Items** are the raw building blocks. Each item has a name, belongs to exactly one role, and
carries a `vegetarian` flag (default true). *Idly* (Base, veg), *Coconut Chutney* (Chutney,
veg), *Chicken Curry* (Side, non-veg).

**Combos** are the named meals the planner actually places: a name, a list of items, and the
meal slots they're eligible for (Breakfast / Lunch / Dinner). A combo is vegetarian *iff every
one of its items is* — derived, never tagged by hand.

The payoff: the planner picks **combos**, but detects repeats at the **item** level. *Idly +
Coconut Chutney* on Monday and *Dosa + Coconut Chutney* on Tuesday are different combos, but
you still get a coconut-chutney repeat alert.

---

## Features

**Weekly generation.** One tap fills every enabled slot for a fixed Sun→Sat calendar week.
The generator prefers non-repeating choices, rotates variety with a least-recently-used
heuristic, and — critically — **leaves a slot blank rather than force a repeat**. Blank slots
are a valid, tappable outcome, not an error.

**Repeat alerts are soft; day rules are hard.** If a don't-repeat item recurs within its
window, you get an amber "Coconut Chutney · 1 day ago" badge — a warning, never a blocker,
and computed identically whether the slot was auto-generated or filled by hand. Per-weekday
dietary rules (v1: *vegetarian only*, e.g. Saturday) work the other way: non-veg combos are
hidden from suggestions and blocked from auto-fill, with no override.

**Regenerate at three scopes.** Re-roll the whole week (confirms first — it discards manual
edits), a single day, or a single slot via the swap sheet. Or clear any scope to blanks
without re-rolling. Everything outside the scope you're re-rolling counts as fixed history.

**Tap-to-swap.** Tapping any slot opens a bottom sheet ranking eligible combos fresh-first,
with repeating options listed after and badged with how long ago they last appeared.

**History.** Past weeks, newest first, read-only, grouped with a "Last week" badge. History
records what was *planned*, not what was confirmed cooked — marking meals cooked is deferred.

**Export / import.** Share your library with the rest of the family as a single `.json` file
through the Android share sheet (WhatsApp, email, Drive, Bluetooth). It doubles as a backup
and phone-migration tool. You choose the scope at export time (library only, or library +
plans and history) and the behaviour at import time (merge, skipping exact duplicates, or
replace everything behind an explicit confirm). A failed or incompatible import writes
nothing.

---

## Tech stack

Kotlin, Jetpack Compose, and Room over local SQLite. Material 3, warmed toward an Airbnb feel
— warm-greige neutrals, roomy rounded cards, curry-leaf green primary, sparing coral accent.
Light and dark both supported.

| | |
|---|---|
| Language | Kotlin 1.9.24 (JVM target 17) |
| UI | Jetpack Compose (BOM 2024.09.00), Material 3 |
| Persistence | Room 2.6.1 + KSP |
| Preferences | `SharedPreferences` via `SettingsStore` |
| Build | AGP 8.5.2, Gradle 8.7 |
| SDK | `compileSdk` / `targetSdk` 34, `minSdk` 26 |

There is deliberately **no network permission** in the manifest — the only `<provider>` is a
`FileProvider` for handing export files to the share sheet.

---

## Getting started

You'll need JDK 17 and the Android SDK (platform 34). Android Studio bundles both.

```bash
git clone <this-repo> && cd food-truck
```

`local.properties` is gitignored, so point Gradle at your SDK:

```properties
sdk.dir=/Users/you/Library/Android/sdk
```

Android Studio writes this for you when you open the project. Then:

```bash
./gradlew :app:assembleDebug          # build the debug APK
./gradlew :app:installDebug           # build + install on a running device/emulator
```

The app has been developed and verified against a Pixel 6 emulator. On first launch it seeds
the three default roles (Base / Chutney / Side); everything else starts empty, and This Week
shows an "Add your first dish" state until you add a combo.

---

## Project layout

```
app/src/main/java/com/family/mealplanner/
├── MealApp.kt                  Application; lazily owns database, settings, repository
├── MainActivity.kt             Edge-to-edge Compose host
├── data/
│   ├── AppDatabase.kt          Room database (v1) + first-run role seed
│   ├── MealPlannerDao.kt       Queries
│   ├── MealRepository.kt       Single source of truth; also export/import JSON
│   ├── SettingsStore.kt        Enabled slots, household size
│   ├── Converters.kt           Slot / Weekday / LocalDate type converters
│   ├── entity/                 Room entities + ComboWithItems relation
│   └── model/Enums.kt          Slot (BREAKFAST/LUNCH/DINNER), Weekday (SUN-first)
├── generation/
│   ├── PlanGenerator.kt        Pure implementation of GENERATION.md
│   └── PlanModels.kt           PlannableCombo, PlacedCombo, Placement, RepeatAlert
├── ui/
│   ├── AppScaffold.kt          Bottom nav: This Week · Combos · Items · History
│   ├── screens/                One file per screen; Settings opens from This Week
│   ├── vm/ViewModels.kt
│   ├── theme/                  Warm M3 tokens, light + dark
│   └── model/UiModels.kt
└── util/DateUtil.kt
```

`PlanGenerator` is a pure `object` with no Android or Room dependencies — it takes
`PlannableCombo`s, history, and a seed, and returns `Placement`s. Same seed and inputs, same
plan; that's what makes it testable and what makes each "Generate" tap (fresh seed) give a new
week.

### Room schema

```
Role(id, name, canRepeat, repeatWindowDays)
Item(id, name, roleId, isVegetarian)
Combo(id, name)                          — isVegetarian derived from its items
ComboItem(comboId, itemId)               — join
ComboSlot(comboId, slot)                 — eligible slots
DayRule(weekday, vegetarianOnly)         — per-weekday dietary constraint
PlanEntry(id, date, slot, comboId?)      — comboId null = blank; past rows = history
```

---

## How generation works

The full spec, with edge cases, is in [`GENERATION.md`](./GENERATION.md). The shape of it:

Walk days D0→D6 chronologically, and within each day walk the enabled slots in canonical
order. Each placement is appended to `placed` immediately, so it becomes history for every
later slot in the same run — within-week repeats count.

For each slot: filter the library to combos eligible for that slot **and** satisfying the
day's dietary rule. Of those, keep the *fresh* ones — a combo is fresh at a date if none of
its don't-repeat items already sits within that item's repeat window. If nothing is fresh,
the slot goes blank. Otherwise pick the least-recently-used candidate, breaking ties with the
seeded RNG.

The LRU score of a combo is the **minimum** gap-since-last-use across its items — a combo is
limited by its most-recently-used ingredient, so we favour combos where even the freshest
ingredient has rested a while. This is a deliberately cheap stand-in for the deferred
"balanced variety" feature, and it's good enough to keep idly/dosa/pongal rotating.

One wrinkle worth knowing: `wouldRepeat` checks the window **bidirectionally** (`abs` of the
day gap). During a full-week pass `placed` only ever holds earlier days, so this is
equivalent to a backward look. But when you re-roll a single day or swap one slot, the
neighbouring days *already exist* — and a bidirectional check stops the re-rolled day from
clashing with the day after it. The alert text you see on the grid stays backward-looking
("N days ago").

Defaults: **7-day** repeat window, **leave blank** rather than repeat, LRU variety.

---

## Export format

A single JSON object. `version` is currently `1`; importing a file with a higher version is
rejected with *"This backup is from a newer version. Update the app to import it."* rather
than partially applied.

```json
{
  "version": 1,
  "format": "...",
  "scope": "...",
  "roles":    [{ "name": "...", "canRepeat": true, "repeatWindowDays": 7 }],
  "items":    [{ "name": "...", "role": "...", "isVegetarian": true }],
  "combos":   [{ "name": "...", "items": ["..."], "slots": ["BREAKFAST"] }],
  "dayRules": [{ "weekday": "SAT", "vegetarianOnly": true }],
  "plans":    [{ "date": "2026-07-05", "slot": "BREAKFAST", "combo": "..." }]
}
```

`plans` is present only when you export with plans + history included. Entities reference each
other **by name**, not by row id, so merging into a device with a different set of ids works.

---

## Status

The app builds, installs, and has been verified end-to-end on-device: generation, swap, roles
CRUD, settings, history, and a full export → wipe → import round trip. See
[`ROADMAP.md`](./ROADMAP.md) for the detailed breakdown, including the QA sweep and code-review
logs.

**Known issues**

- Material 3's default container surfaces give cards, dialogs, and the nav bar a lavender
  tint; the `surfaceContainer` token family needs defining so surfaces read warm greige.
- Combos can be created and deleted, but not yet edited.
- No test suite yet. `PlanGenerator` is pure and seeded specifically so it can have one —
  freshness, LRU, day rules, and alerts are the obvious first targets.

**Deferred to a future version**

Dish imagery (illustrations and user photos, plus their storage and export bundling), the full
"balanced variety" algorithm, marking meals cooked or skipped, ratings, shopping lists and
quantities, online recipe sources, and cloud sync with accounts. The last one is deferred
permanently — being offline and account-free is the point.
