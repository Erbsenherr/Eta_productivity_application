---
paths:
  - "app/src/**/domain/stats/**"
  - "app/src/**/ui/stats/**"
  - "app/src/**/ui/notes/**"
  - "app/src/**/*Sleep*.kt"
  - "app/src/**/*Note*.kt"
  - "app/src/**/TrackingDao.kt"
  - "app/src/**/ui/dashboard/**"
  - "app/src/**/domain/reevaluation/DailySettlement.kt"
---

### Step 43: getting up and going to bed, Statistiken, the Notiz-Merger

Three changes and three features from `update.txt`, eight questions put first.
Database **version 31**: the tables `sleep_logs` and `notes`,
`planned_blocks.timerExtension` / `originalStart` / `originalDuration` /
`quantityDone`, `user_setup.statistics` (1) / `statsHidden` ('') / `notes` (0).

#### The answers that are rules now

- **The two half points are booked with the evening's settlement**, not when the
  button is pressed. The one for going to bed is in the settlement of **the day
  after** — the day that night ends on.
- **A morning nobody answered can be answered until the day ends**, and is
  otherwise missing from the statistics rather than counted as late.
- **"Vorgenommene Tasks" are blocks with a category, and calendar imports**
  (`isTakenOnTask`) — not the frame of the day. Höhere Gewalt counts in neither
  half of any rate.
- **Taskify leaves the note and marks it** (`Note.taskifiedAt`); doing it again
  says so first.
- The tab is called **"Notiz-Merger"**, as written.
- Of the extra statistics offered, only **"Zeit je Kategorie"** was wanted.

#### Three changes

- **A Timer-Task never started, ticked in the evening**, asks first:
  `TimerTickDialog` in `TasksStep`, `ReevaluationViewModel.completeTimerByHand`.
- **A timer never runs into time nobody planned.** `timerOverflow` now answers
  whenever the time would run past the block's own end, with or without a task
  behind it (`TimerOverflow.next` is nullable). "Task verlängern" lengthens the
  block by exactly the lateness the buffer could not take —
  **`PlannedBlock.timerExtension`**, part of `plannedDuration` and *not* of
  `fullTimerGoal`, `timerBilled` or `workedInFull`, all three of which subtract
  it — and pushes back what stands there (`pushedNext`). "Am Blockende aufhören"
  is the other answer: `shortenedGoal` is now the time until the **block's** end,
  not until the next task. This overrules the "started late" paragraph of
  *Step 42*.
- **Holding "Absagen" asks before it excuses**: a `ConfirmDialog` inside
  `BlockEditDialog`, so the planner and the dashboard's long press both have it.

#### Getting up and going to bed

`SleepLog` (`sleep_logs`), keyed by **the calendar day an answer is about**:
`wokeAt` with the `wakeDue` that applied (stored, so a later change to the
settings cannot make an on-time morning late), and `bedOnTime` for that day's
evening — also when the bedtime itself lies after midnight.

- **`sleepPrompts`** (`domain/planning/SleepCheck.kt`) works out what to ask at
  a moment, from the night as it falls on a date, overrides included. The screen
  calls it against its ticking clock (`DashboardViewModel.sleepCheck` only hands
  it the three inputs): a window opens by the minute and no row changes when it
  does.
- **Morning**: an hour either side of the hour of getting up
  (`MORNING_WINDOW_MINUTES`). `NowBox(greeting, greetingContent)` — the box is
  called "Guten Morgen!" for all of the window, answered or not — and
  `SleepGreeting` offers jetzt / pünktlich / an hour of one's own. On time is at
  most `PUNCTUAL_WAKE_MINUTES` (5) after; earlier counts too — since step 48
  the user's `punctualTolerance`, stored with the answer, and a finished morning
  routine ends the window; see *Step 48*.
- **Evening**: while winding down, `[bedPrepOffset, sleepOffset)` of **either**
  night that touches today. A bedtime after midnight puts the window into the
  small hours of the next date and it is still filed under the day before.
  "Gehe nun schlafen" is `bedOnTime = true`.
- **Unanswered**: `SleepCatchUpBoxes`, above the "Gerade" box — "Aufstehzeit –
  heute" after the window, "Schlafenszeit – Vortag" once yesterday's bedtime has
  passed, with the "i" that says what "pünktlich" means.
- **`settleDay(wokeOnTime, bedOnTime)`** → `DailySettlement.wakeBonus` /
  `bedBonus`, `PUNCTUAL_SLEEP_POINTS` each, booked as `PointsReason.PUNCTUAL_WAKE`
  / `PUNCTUAL_BED`. `ReevaluationService` reads the log of the date and of the
  day before.
- **Not in the tutorial**: the factory hands the view model no repository for a
  sandbox container, and `sleepCheck` is then null.
- **Known limits.** An answer given after its day was settled earns nothing. The
  box "Schlafenszeit – Vortag" stands on the first day after the update, there
  being no row for yesterday. Nothing is measured — both answers are the user's
  word.

#### What is stamped for the statistics

- **`originalStart` / `originalDuration`** on a block, written once by
  `expandRecurring` — the definition's hour and length. `planChanged()` is the
  comparison; null on anything older or not laid down by the schedule, which is
  "not known" and counted in neither direction. A holiday's own hour, a pinch, a
  relief cut and a timer's extension all read as changed; for a dynamically
  placed task the start is ignored.
- **`quantityDone`**, stamped by `GrowthService.advanceFor` before the count
  moves on. The Mengen-Inkrement's history therefore begins with step 43; a
  Timer-Task's is read off `plannedDuration` and reaches back further.

#### The Statistiken tab

**Drawn anew in step 47** — cards with a coloured edge, tiles, bars. See
*Step 47*; the arithmetic below is unchanged.

`domain/stats/Statistics.kt` holds the arithmetic, `ui/stats/` the screen.
`StatisticsViewModel` reads **every block once per visit** and writes nothing;
the screen cuts that history per range, since there are several pickers on it.

- **`StatRange`**: 7 Tage, Monat (30), Jahr (365), Seit Beginn. Days that are
  over — today is not in the overview.
- **`overview`**: wake and bed as shares of what was *answered*; "Zeit
  eingeplant" is what stood (`occupiesTime`) against waking minutes, over days
  that held any block, with **today's** nights for past days as well;
  "abgeschlossen" over `isTakenOnTask`; minutes per category of what was done.
- **Per task** (`tasksByName` — by name, not by the attributes the standing
  schedule groups on): `taskStats` — time, skipped, changed, as planned. "As
  planned" is the strict one: finished, not changed, and worked in full.
- **The plot** (`PensumChart`, a `Canvas` with the foundation's text measurer):
  `timerPensum` / `quantityPensum`, the x-axis always days, lettered with month
  names where `monthsFitBetter` (more than 62 days).
- **`StatSection`** is what the settings hide. `UserSetup.statsHidden` holds the
  **hidden** names, so a section added later is on for everyone.
  `SettingsPage.STATISTICS`, applied at once.

#### The Notiz-Merger

`Note` (`notes`) — its own table, not an `Item`: no one-month clock, no
Sperrliste, in no list. `ui/notes/`, `UserSetup.notes` (an Advanced Feature,
off), `EtaTab.Notes`.

- Sorted by date or title, either way round, and searched.
- **Leaving the editor with unsaved text asks** — Speichern / Verwerfen / Weiter
  bearbeiten — on a tap beside the window as well as on "Schließen".
- **"Taskify"** saves the note first, then asks: "In die Sammelliste"
  (`Item.newQuickTodo` carrying the text as its note) or "Jetzt konkretisieren"
  (the ToDo form, landing finished in the Sammelliste). **The Sperrliste is
  asked** either way.

**Verified by the compiler, the suite (677; 15 in `SleepAndStatisticsTest`),
`assembleRelease`, `lint` and the migration against `31.json`.** Nobody has seen
any of it.
