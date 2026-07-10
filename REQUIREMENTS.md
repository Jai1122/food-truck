# Family Meal Planner — Requirements v1.3

A private, **fully-offline** Android app that stores a family's dish library and
auto-generates a weekly meal plan, warns about repeats, and can be shared between
phones via file export/import. No accounts, no internet required.

> **Changelog**
> - **v1 → v1.1** (design review): captured combo **slot-eligibility**, **local reminders**,
>   the **empty-library** state, and the **family-of-4** clarification.
> - **v1.1 → v1.2**: **imagery deferred** — dish illustrations/photos and their storage &
>   export implications are removed from v1 and moved to a future iteration (§9).
> - **v1.2 → v1.3**: added **day rules** (per-weekday dietary constraints, e.g. Saturday =
>   vegetarian only) with a **vegetarian** attribute on items; added **plan regeneration**
>   controls (reject/re-roll the whole week or a single day, plus clear). Confirmed the
>   **7-day** repeat window.
> See `design-system.html` (visual) and `GENERATION.md` (algorithm).

---

## 1. Core concept

- Keep a personal library of family meals — **your own dishes**, entered by you
  (no bundled recipe set, no online source).
- One tap generates a **Sunday → Saturday** plan across the meal slots you enable.
- Repeats are **allowed but flagged** with a soft alert (not a blocker).
- Everything is stored locally on the device.

## 2. Data model — Items → Roles → Combos

A meal is not a flat name; it is a **combo of items**, and repeat rules live on the
**role**, so different parts of a meal follow different rules.

### Roles (carry the repeat rule)
- Each role has a **can-repeat** flag and a **look-back window** (days).
- Examples: `Base` → can repeat (Idly two days running is fine) · `Chutney` → don't
  repeat (alert if it recurs within the window) · `Side` → can repeat.
- User-defined; add/edit/**delete** (deleting a role in use is guarded — see §10).

### Items (raw building blocks)
- Each item has a **name**, belongs to **one role**, and carries a **`vegetarian`** flag
  (default true; e.g. Chicken = non-veg).
- Examples: Idly (Base, veg), Dosa (Base, veg), Coconut Chutney (Chutney, veg), Sambar (Side, veg).

### Combos (the predefined named meals)
- A combo has a **name**, a **list of items**, and one or more **eligible meal slots**
  (Breakfast / Lunch / Dinner) so the planner knows where it belongs.
- A combo is **vegetarian iff all its items are vegetarian** (derived, not tagged separately).
- Examples: "Idly + Coconut Chutney" (Breakfast), "Dosa + Sambar" (Breakfast).
- The planner draws from **combos** filtered by slot. Repeat detection runs at the
  **item** level: *Idly + Coconut Chutney* then *Dosa + Coconut Chutney* on consecutive
  days triggers a **coconut-chutney repeat alert**, even though the combos differ.

## 3. Meal slots

- **Configurable** in settings: enable any of Breakfast / Lunch / Dinner per day.
- The planner only fills enabled slots, and only with combos eligible for that slot.

## 4. Weekly plan generation

- Week = **Sunday → Saturday**, 7 days at a time (fixed calendar week).
- One tap **auto-generates** the week from the combo library.
- Generator **prefers non-repeating** choices for "don't repeat" roles (default look-back
  **7 days**), within eligible combos, and rotates variety (least-recently-used).
- If it cannot fill a slot without repeating, it **leaves the slot blank** (never forces a repeat).
- **Soft alert** whenever a "don't repeat" role item recurs within its window —
  on both auto-generated and manual choices.
- **Day rules** — per-weekday dietary constraints (v1: **vegetarian only**, e.g. Saturday).
  **Hard rule**: on a constrained day, non-veg combos are not selectable in auto-gen *or*
  manual add (no override). Unlike repeat-alerts (soft), dietary day-rules are enforced.
- **Reject & regenerate** at three scopes, all undoable:
  - **Whole week** — "Regenerate week" re-rolls all 7 days (discards manual edits; confirms first).
  - **One day** — "Regenerate day" re-rolls that day's enabled slots only.
  - **One slot** — swap/fill/clear a single slot.
  - **Clear** week or day to blanks without re-rolling.
- User can **swap, fill, or clear any slot** manually at any time.
- *Full algorithm, including the LRU variety heuristic and day-rule filter, is in* `GENERATION.md`.

## 5. History & reminders

- Visible **history view** of past weeks' plans, newest first; read-only.
- History records **what was planned** (not confirmed-cooked) and powers repeat alerts.
  Marking meals cooked/skipped is **deferred** (§9).
- **Local reminders (optional, off by default):** an on-device reminder (e.g. Sunday
  morning) to plan the coming week. Uses the local scheduler / notifications — **no push,
  no network**. Toggle in Settings.

## 6. Export / Import (share with family)

- **Share-on-demand, file-based** (not live sync — consistent with offline design).
- **Export** writes a single `.json` file, sent via the Android share sheet
  (WhatsApp / email / Drive / Bluetooth). Also a backup / phone-migration tool.
  - **Scope chosen at export time:** "library only" (items, roles, combos) or
    "include plans + history".
- **Import** opens the received file.
  - **Behavior chosen at import time:** **Merge/add** (skip exact duplicates, keep existing)
    or **Replace everything**. Replace requires an explicit confirm. Nothing is written on a
    failed/incompatible import.

## 7. Visual direction

- **Material Design 3, warmed toward an Airbnb feel:** cosy warm-greige neutrals, generous
  spacing, roomy rounded cards, curry-leaf green as the primary, a sparing coral accent.
- Native Android mechanics retained (bottom nav, bottom sheets, snackbars, predictive back,
  dynamic type, light/dark). Full spec + tokens in **`design-system.html`**.
- Note: **rich dish imagery is deferred** (§9); v1 cards are text-forward on warm surfaces.

## 8. Household & tech

- **"Family of 4"** is context, not a driver: with no shopping list or serving sizes, the
  household size is a **display-only profile value** in v1 (default 4). Reserved for future
  scaling of quantities/shopping.
- **Kotlin + Jetpack Compose + Room** (local SQLite). Local notifications for reminders.
  **No backend, no network permission** for core features.

## 9. Deferred / out of scope (v1)

- **Dish imagery** — category illustrations and optional user photos per item/combo, plus
  their offline storage and export bundling. *(Planned next iteration.)*
- "Balanced variety" logic (how categories/roles spread across the week).
- Mark meals **cooked / skipped**; ratings.
- Shopping list, ingredients, quantities, online recipe sources.
- Live cloud sync and user accounts.

## 10. Screens & key edge cases

**Screens:** This Week (home; per-**week** and per-**day** Regenerate/Clear actions) · Combos ·
Items (grouped by role; **Roles managed here** via "Manage roles"; each item has a veg toggle) ·
History · Settings (slots, repeat window, **day rules**, reminders, export/import, theme).

**Edge cases:**
- **Empty library** → This Week shows "Add your first dish" (not "Generate"); the two empty
  states are distinct: *no dishes* vs *dishes but no plan yet*.
- **Delete a role in use** → confirm dialog listing affected items/combos; block or reassign.
- **Delete an item used in combos** → confirm, listing affected combos.
- **Not enough fresh combos** → blank slots, inline "add more combos" hint (never an error).
- **Import a newer file version** → "Update the app to import this."

---

## Proposed Room tables (for implementation)

- `Role(id, name, canRepeat, repeatWindowDays)`
- `Item(id, name, roleId, isVegetarian=true)`
- `Combo(id, name)`                        — `isVegetarian` derived from its items
- `ComboItem(comboId, itemId)`            — join: many items per combo
- `ComboSlot(comboId, slot)`              — eligible slots (BREAKFAST/LUNCH/DINNER)
- `DayRule(weekday, vegetarianOnly)`      — per-weekday dietary constraint (extensible)
- `PlanEntry(id, date, slot, comboId?)`   — `comboId` null = blank; past rows = history
- Preferences: enabled slots · default repeat window 7d (per-role override optional) ·
  reminder on/off + time · theme override · household size
