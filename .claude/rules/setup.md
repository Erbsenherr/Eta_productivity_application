---
paths:
  - "app/src/**/domain/setup/**"
  - "app/src/**/ui/setup/**"
  - "app/src/**/SetupRepository.kt"
  - "app/src/**/Setup*Test.kt"
---

### The setup questionnaire

`domain/setup/` holds the whole of it as pure Kotlin, with the UI in `ui/setup/`:

- **`UserSetup`** is a single-row Room entity (`SETUP_ID = 0`) and doubles as the
  questionnaire's draft — `UserSetup.draft(now)` seeds it with plausible answers.
  Its nested answers (`MealPlan`, `HousekeepingPlan`, `MindfulnessPlan`,
  `WorkSchedule`, `WeeklySlot`, `DailySlot`) each `encode()` to one string column,
  the same trick `RecurrenceRule` uses. `SetupEncodingTest` guards the round trip:
  a broken one would silently corrupt the entire configuration on next launch.
- **`SetupSchedule.kt`** flattens the answers into `SetupSpan`s (minutes from
  midnight, per weekday) and derives `conflicts()` and `freeMinutesPerWeek()` from
  them. Anything crossing midnight is *split across two weekdays*, not clipped,
  or the small hours would vanish from the maths. Two spans of the same answer
  are not a conflict — a wrapped night is one appointment, not two.
- **`SetupItems.kt`** turns the answers into recurring definitions with
  **deterministic ids** (`setup:morning`, `setup:work-0-monday`, …), which is what
  makes re-running the questionnaire an update rather than a second schedule.
  `SetupRepository.complete` — the questionnaire's path alone since step 18 —
  retires definitions that no longer follow from the answers via `completedAt`
  instead of deleting them: `planned_blocks` cascades on delete, so deleting would
  take past completions out of the Erfolgsliste. It does clear their **still-open
  future occurrences** (`PlanRepository.clearUpcoming`), or moving Sport from
  Monday to Tuesday would leave the old Monday blocks standing beside the new ones
  for as long as they were already laid down. Completed and dismissed blocks are
  left alone — they are what happened.

**A `WeeklySlot` holds a *set* of weekdays.** Sport twice a week is ordinary, and a
one-day answer made the user enter the same task twice. Each chosen day becomes its
own definition, with the day in the id (`setup:sport-tuesday`), so the
questionnaire keeps updating exactly its own rows.

**The break lives inside the work answer**, not in a question of its own. A
`WorkBlock` is a span plus an optional `pause`, and `segments()` cuts it into
work / break / work. That is what stops the lunch break from permanently colliding
with the working day, and it is the only shape that survives the per-weekday case,
where a university timetable has several stretches a day each with its own gap. A
break that does not lie inside its block is **dropped, not clamped**, and the
questionnaire says so while the user is still looking at it. With
`WorkSchedule.None` there is no break at all.

Whether a generated block pays points is decided by whether it carries a category,
and that is assigned by meaning: the frame of the day — Bettfertig machen,
Morgenzeit, Pause, Freizeit — has `category = null` and yields nothing, while
Hausputz, Sport, Kochen and Achtsamkeit carry theirs. Sleep produces no item at
all; it is configuration the planner shades (`colors.sleep`) and the free-hour
maths subtracts.

### Step 32: the order of the pages, skipping, and a weekend night

**The two pages the app cannot run without come first**: "Schlaf und Morgen", then
"Planungsphasen" — the planner shades the night and both alarms need an hour.
The welcome text is the top of the first page rather than a page of its own, so
the first thing shown is a question. Everything after those two — Essen, Hausputz,
Sport, Freie Zeit, Selbstachtsamkeit, Arbeit und Uni — carries an **"Überspringen"
button**, on a line of its own above Zurück / Weiter (three buttons in one row do
not fit a phone).

- **Skipping is kept beside the draft, not written into it.** `SetupViewModel.skipped`
  is a set of `SetupPart`; `UserSetup.skipping(parts)` in `domain/setup/Nights.kt`
  takes those answers out, and is applied to what the outlook counts and to what
  `finish` stores. What was typed on a skipped page is therefore still there behind
  "Doch beantworten".
- What "taken out" means per page: Hausputz, Sport and Achtsamkeit become null, work
  becomes `WorkSchedule.None`, the meals an **empty** `DailyCooking`, and free time a
  slot of **zero duration** — `recurringItems` and `weeklySpans` already drop both,
  which is why neither needed to become nullable. **Skipped free time takes the
  social budget with it**: they are one page.
- **`UserSetup.draft` must not collide with itself.** Someone who accepts every page
  gets no "Doppeltbelegung" — sport moved from 18:00 to 17:15 because it ran into
  the cooking at 18:30 — and `WeekendNightTest` pins it, with and without the
  suggested weekend. A new default has to pass that test.

**The weekend may have a night of its own.** `UserSetup.weekendNight` is a nullable
`NightTimes(bedPrep, sleep, wake)`, one encoded column; null means every night is
alike, which is every setup from before. "Am Wochenende → Andere Zeiten" on the
sleep page switches it on, seeded by `suggestedWeekendNight()` (an hour later to
bed, two hours longer in it). The page is shared with the settings tab, so it can
be changed there too. The morning's *length* stays one answer.

- **A night belongs to the day it ends on.** Weekend nights are the ones ending on
  Saturday and Sunday: Friday and Saturday evening, Saturday and Sunday morning.
  Sunday evening already belongs to Monday. `nightEndingOn(weekday)` is the one
  place that decides, and everything goes through it: `weeklySpans`,
  `sleepStretches(weekday)` (which gained its parameter — a Friday wakes from one
  night and goes into another), `nextWake` and `recurringItems`.
- **`NightTimes` speaks in offsets from the wake day's midnight**, negative for the
  evening before, so "before or after midnight" is settled once. Going to bed at
  00:30 puts the whole night, winding down included or not, on the right weekday.
- **Bettfertig machen and Morgenzeit become one definition per night** when there
  is a weekend night: `setup:bedprep-saturday` is the winding down of the night that
  *ends* on Saturday, a `Weekly(FRIDAY)` rule. The id carries the wake day, not the
  day the task falls on, because two nights can wind down on the same weekday (one
  after midnight, one before). With every night alike they stay the two `Daily`
  definitions `setup:bedprep` / `setup:morning`. Switching the option retires one
  set and lays down the other through the ordinary `saveSettings` path.
- **`isOwnedBySettings(id)` replaced the `SETTINGS_OWNED_ITEM_IDS` set**, since the
  owned ids are no longer two fixed strings.
