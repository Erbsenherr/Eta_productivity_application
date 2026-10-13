---
paths:
  - "app/src/**/ui/setup/**"
  - "app/src/**/domain/setup/**"
  - "app/src/**/ui/week/**"
  - "app/src/**/ui/weekplanner/**"
  - "app/src/**/ui/lists/WeekSchemeChart.kt"
  - "app/src/**/domain/recurrence/**"
  - "app/src/**/ui/attributes/**"
  - "app/src/**/alarm/EtaSound.kt"
  - "app/src/**/ui/planner/Timeline.kt"
---

### Step 46: the setup rebuilt, every other week, a task's own colour, audio modes

Two bugs, one change, the setup, three features from `update.txt`, four
questions put first. Database **version 32**: `items.color`.

#### The answers that are rules now

- **The Morgenroutine bug is parked** ("Überspring das erstmal"): after a debug
  reset, steps added to the morning routine are said not to be saved. The save
  path reads correct and could not be reproduced by reading; it is **open**.
- **A task wears its category's colour, or one of its own.**
- **"Termine" are imported Google appointments**, nothing else.
- **Vibration and silence apply to everything but the wake alarm.**

#### A colour of the task's own

`Item.color` (ARGB, null = the category's), carried in `ItemExtras.color`, set
by `ColorRow` in the Extras box (`ACCENT_PRESETS`, `EtaSwatch` — the swatch the
Design card uses, now shared).

- **`colorOf(item)`** in `EtaTheme.kt` is the one place a card's colour is
  decided. Every screen that asked `colorOf(x.category)` asks it now; a new one
  that asks by category will not see a task's own colour.
- **The Wochenschema over the Wiederkehrend list** draws each task as a bar in
  that colour (`weekTaskSpans`, `WeekSchemeChart(tasks = …)`), which is the
  colour of the dot in front of its row. Free time is no longer green there —
  green is a category's. The chart inside a form is unchanged.

#### Every other week

The model always had `RecurrenceRule.Biweekly(weekday, parity)`; nothing let
the user choose it.

- **`RhythmField`** under the weekdays: jede Woche · gerade KW · ungerade KW (a
  monthly rhythm a task still carries is listed as itself). ISO calendar week.
- **It rides in `ItemExtras.rhythm` and is not a column.** `withExtras` ignores
  it; the three save paths read it where they build the rules
  (`edit.extras.rhythm ?: …` in `saveGroup` / `createGroup`, `extras.rhythm` in
  `concretizeRecurring`). `RecurringAttributes.slots()` defaults to it, so the
  overlap check follows a rhythm changed in the open form.
- **Drawn hatched** in `DayTimeline` — leaning left in even weeks, right in odd
  — with the **name at the top (even) or the bottom (odd)**: in "Generell" a
  fortnightly task stands on its weekday every week, so two that alternate lie
  on top of each other, and blocks are translucent enough for both to read.

#### Woche: "Termine anzeigen", and any seven days

- **`blocksOn(…, appointments)`**: in "Generell", this week's
  `CALENDAR_IMPORT` blocks over the standing week. Shown and nothing more — a
  long press gets the one-off sentence.
- **`WeekTabViewModel(from = …)`**: the seven days from any date; `dateOf(day)`
  looks the date up instead of counting from Monday.
- **`WeekTabScreen(onlyView, showAppointments, header, footer)`**: one view
  offered with the other half of the switch greyed.
- **"Anpassungen vornehmen"** on the weekly planning's "Auffüllen" page lays
  that timetable **over** the phase (`weekAdjust`, `WeekAdjustScreen`) — over,
  not as a flow, so the pager keeps its page. "Aktuell" only: the phase plans
  one week.

#### Audio: Ton, Vibration, Still

`AudioMode` in `EtaSound`, in `SharedPreferences` beside the DND switch.
`play` returns for `SILENT` and buzzes for `VIBRATE` (not under DND unless
played through, not on a phone switched fully silent); `EtaSpeech.speak`
returns false for both, so the caller falls back to `play`. The settings page
"Ansagen" is called **"Audio"** and opens with `AudioModeBox`, applied at once.
**The wake alarm asks none of it.**

#### The setup, rebuilt

`SetupPage` is a list **made from the answers**, not an enum:

1. **Welcome**, with `CalendarSettingsBox` — Google before the first question.
2. **Schlaf und Morgen**, with a **"Folgetag"** box under "Bettfertig machen"
   and "Schlafen gehen" (`NextDayBox`) and the **morning routine's steps**
   (`SleepStep(morningSteps, onMorningSteps)`; the settings pass none).
3. **Planungsphasen**, unchanged. 4. **Freie Zeit**, unchanged, with its own
   "Überspringen".
5. **Wiederkehrende Tasks** (`RecurringTasksStep`): the five as checkboxes —
   **a box unticked is `SetupPart` skipped**, the mechanism step 32 had — and
   the user's own (`CustomTask`), added by name.
6. One page per ticked box: the five keep their own pages, a custom one is
   `CustomTaskStep` — a name and `RecurringAttributeFields`.
7. Summary, then "Anlegen und Woche ansehen".

- **"Folgetag" is derived, not stored.** An hour before getting up *is* after
  midnight. The box ticks itself when such an hour is picked, and ticking it by
  hand **moves the hour**: `withSleepNextDay` (winding down follows, length
  kept), `withBedPrepNextDay` (the bedtime follows across midnight where it was
  still in the evening). `SetupAndRhythmTest` pins both.
- **`finish()`**: `complete` → `saveMorningSteps` (the routine's task has to
  exist first) → `createGroup` per custom task.
- **The last screen is not a page**: `SetupViewModel.weekReview` is set
  *before* `complete` — storing the answers is what swaps the root screen — and
  `EtaApp` then shows `SetupWeekReview` before the tutorial: the dashboard's
  timetable, "Generell" only, "Termine anzeigen" on, with a four-page box at
  its foot (the week, the lock, Aktuell/Generell and why "Aktuell" is grey,
  where to find it again). In memory only: a process that dies there lands on
  the dashboard.
- Conflicts on a page still say so, and now say they can be dragged apart on
  that last screen.

**Verified by the compiler, the suite (694; 10 in `SetupAndRhythmTest`),
`assembleRelease`, `lint` and the migration against `32.json`.** Nobody has seen
any of it — the questionnaire's pager with pages that come and go least of all.
