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

