---
paths:
  - "app/src/**/domain/setup/Nights.kt"
  - "app/src/**/ui/setup/SetupSteps.kt"
  - "app/src/**/NightService.kt"
  - "app/src/**/ui/planner/NightEditDialog.kt"
  - "app/src/**/domain/planning/BreakCheck.kt"
  - "app/src/**/PlannerExtras*.kt"
  - "app/src/**/ui/planner/DayPlannerScreen.kt"
  - "app/src/**/ui/planner/DayPlannerViewModel.kt"
  - "app/src/**/ui/week/**"
---

### Step 40: nights changed by hand, a night per weekday, the Pausenchecker

Five items from `update.txt`, four questions put first. Database **version 29**:
`user_setup.nightsByDay` / `breakChecker` / `breakPerTwoHours` /
`breakMaxStretch`, and the tables `night_overrides` and `planner_dismissals`.

#### The answers that are rules now

- **A night changed in the day planner takes everything that hangs off it**: the
  morning routine, Bettfertig machen, the wake alarm and the evening's sums.
- **A night changed on the week timetable is that weekday's alone** — never
  "all weekdays" — and so puts the setup on a night per weekday.
- **Rest, to the Pausenchecker**, is breaks, free time, meals, mindfulness, and
  a **Custom Spend** (the user's addition). On for everyone.

#### Three ways of answering the nights

`NightMode` (`domain/setup/Nights.kt`), derived by `UserSetup.nightMode`, never
stored: `SAME`, `WEEKEND`, `PER_DAY`.

- **`nightsByDay`** is a `Map<DayOfWeek, NightTimes>?`, one encoded column, by
  the day each night **ends** on. Non-null it overrules `weekendNight`, which is
  *kept* — `withNightMode(WEEKEND)` finds it again. Always all seven
  (`allNights`). `nightEndingOn(weekday)` is still the one place that decides.
- **The weekend is Friday and Saturday evening again.** The picker from step 33
  is gone and `withNightMode(WEEKEND)` writes `DEFAULT_WEEKEND`. A setup from
  between that still carries other `weekendDays` **reads as `PER_DAY`** and keeps
  sleeping exactly as it did — `nightEndingOn` still honours the set — until its
  first edit writes it as seven nights. No data migration, and `WeekendNightTest`
  stands unchanged for that reason.
- **`recurringItems` asks `nightMode == SAME`** where it asked `weekendNight ==
  null`: one pair of tasks per night in both other modes.
- **`SleepStep`**: "Schlafrhythmus" with the three options. Choosing the third
  asks first (`ConfirmDialog`, "Trotzdem") — offered and advised against in one
  breath, as asked. In `PER_DAY` the three ordinary pickers are hidden (they
  would be an eighth answer that means nothing) and each night is an
  `EtaExpander` named after the day it ends on.
- **The advice** (`RHYTHM_ADVICE`, in `NightEditDialog.kt`) is shown wherever
  nights lie **more than** an hour apart: `rhythmSpread` is the wider of the gap
  between bedtimes — as offsets, so 23:30 and 00:30 are an hour apart — and the
  gap between hours of getting up. `suggestedWeekendNight` is now an hour later
  at both ends, so the app's own suggestion does not set off its own warning.

#### One window, two acts

A long press on the shaded night (`DayTimeline.onSleepLongPress`; inside a sleep
stretch it takes the press from `onEmptyLongPress`) opens `NightEditDialog`: two
hours, the length of the night they make, the advice if it applies. Winding down
keeps its length and follows the bedtime — `NightTimes.movedTo`. `nightDateAt`
says which night was pressed: the morning's stretch ends today, the evening's
tomorrow.

**From the day planner: this night and no other.** `NightService.overrideNight`
writes a `NightOverride`, keyed by the date the night ends on.

- **Hours that are the standing ones again delete the row** rather than storing
  a copy of the rule — a night that only looked overridden would stop following
  the settings.
- **Read through `nightEndingOn(date, overrides)`** by the planner's shading
  (`sleepStretches(date, overrides)`), `nextWake`, `ReevaluationService
  .sleepMinutes`, and the week tab's "Aktuell". The standing week — Wochenschema,
  "Generell", `weeklySpans`, the overlap warnings — asks by weekday and never
  sees one.
- **The two blocks move** (`moveNightBlocks`): the morning routine on that date
  to the new hour of getting up, Bettfertig machen to the new start. Open blocks
  only, and winding down **only while it stays on the calendar day its block
  stands on** — a block cannot change its date without expansion laying the old
  one down again. A bedtime dragged across midnight therefore leaves that block
  where it was. Known, and said here rather than fixed.
- An edit to the settings relays the open occurrences after the kept days, which
  puts a moved block on an unconfirmed tomorrow back at its standing hour. The
  shading and the alarm keep the override.

**From the week timetable: the weekday's standing night.**
`NightService.saveWeekdayNight` → `withNightEndingOn` → `SetupRepository
.saveSettings`, the settings' own path, so the two tasks are regenerated. The
window there reads the standing night in both views.

`SettingsViewModel` now follows the stored row while its draft is untouched —
the setup can change behind the tab's back, and a later "Sichern" would
otherwise write the old nights over the new.

#### The Pausenchecker

`checkBreaks(blocks, perTwoHours, maxStretch)` in `domain/planning/BreakCheck.kt`,
pinned in `NightsAndBreaksTest`. `BlockWithItem.breakKind()` sorts every block
into `REST`, `TASK` or `NEUTRAL` — **role before category**, because cooking
carries "Nebenbei" so that it pays and is still the meal.

- **In total**: for every *full* two hours of tasks, `breakPerTwoHours` of rest.
  Full, so one hour of work does not ask for seven and a half minutes.
- **By stretch**: more than `breakMaxStretch` of tasks between two rests, before
  the first or after the last. **Task time, not the clock** — an empty afternoon
  is not three hours of work. A `NEUTRAL` block (the morning, winding down, a
  frame block without category) neither adds nor ends one.
- Rest is every `REST` block and every "Pause danach". **Pomodoro pauses do not
  count**, by the user's rule. A points entry at or below zero an hour is rest,
  above it work. Called-off blocks are not read; finished ones are.
- **Shown in the day planner** above the footer (`BreakWarnings`), each with
  "Ignorieren": a `PlannerDismissal` for that date and `PlannerWarning`, stored
  for the reason `conflict_dismissals` is. Not on the confirmed "Liste für
  Morgen".
- **Settings → Pausenchecker** (`BreakCheckerBox`), applied at once.
- **The questionnaire's own working day sets it off**: 09:00–12:30 is three and
  a half hours. That is the rule as given, with the default the user named.
- Off in the tutorial's practice setup.

**Verified by the compiler, the suite (626, 24 in `NightsAndBreaksTest`),
`assembleRelease`, `lint` and the migration against `29.json`.** Nobody has seen
any of it.
