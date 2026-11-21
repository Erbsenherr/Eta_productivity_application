---
paths:
  - "app/src/**/domain/model/SleepLog.kt"
  - "app/src/**/domain/planning/SleepCheck.kt"
  - "app/src/**/ui/dashboard/SleepBoxes.kt"
  - "app/src/**/domain/stats/Affirmation.kt"
  - "app/src/**/ui/stats/AffirmationBox.kt"
  - "app/src/**/ui/reevaluation/**"
  - "app/src/**/ReevaluationService.kt"
  - "app/src/**/GrowthService.kt"
  - "app/src/**/ui/growth/**"
  - "app/src/**/domain/reevaluation/DailySettlement.kt"
---

### Step 59: graded sleep points, "Stand setzen", affirmations, and "Sich der Task verschreiben"

Four items from `update.txt`, four questions put first; the answers are the
design. Database **version 42**: `sleep_logs.bedAt` / `bedDue` /
`bedTolerance` (all `INTEGER`, nullable), `planned_blocks.committedAt`
(`INTEGER`, nullable) / `committedSettledOn` (`TEXT`, nullable).

#### "Stand setzen" in the "Ziele" tab

**Asked: set the stand directly — not a sitting logged.** For a task done
outside the app: every row of a goal (a task with a "Zielzeit", a
Mengen-Inkrement, a routine's step) has a "Stand setzen" button →
`GoalStandDialog`. **No completion, no points, nothing billed.**

- `Item.withTimeSetTo` (`GrowthTask.kt`): within `MIN_GROWTH_DURATION` and the
  target, the buffer kept behind it, `growthProgress` back to 0, **a malus
  lifted** (`timerMalusBase = null` — the user has said where it stands).
- `Item.withQuantitySetTo` (`Quantity.kt`): at least `MIN_QUANTITY`, at most
  the target, `quantityProgress` back to 0.
- `GrowthService.setStand` writes it per family, like an increment, and relays
  the occurrences ahead when the time moved; `setStepStand` does the same for
  a step's `StepFamily` through `withProgressOf`.

#### Graded points for getting up and going to bed

**Asked: the tolerance (`punctualTolerance`, default 5) is the zone that pays
in full; quarters *begun* count; counted up to two hours after the hour.**
`sleepPoints(late, tolerance, full)` in `SleepLog.kt` is the whole rule:

| minutes late | points |
| --- | --- |
| ≤ tolerance (earlier too) | full (`PUNCTUAL_WAKE_POINTS` / `PUNCTUAL_BED_POINTS`) |
| the next `SLEEP_ZERO_MINUTES` (5) | 0 |
| after that, per quarter begun | −`SLEEP_LATE_PENALTY` (Balancing `SLEEP_LATE`, 0.5) |
| from `SLEEP_LATE_CAP_MINUTES` (120) on | no further — −4 with the defaults |

- **The bedtime is now an hour, not a yes/no.** `SleepLog.bedAt` with the
  `bedDue` and `bedTolerance` that applied; `bedLateMinutes` reads across
  midnight the nearer way round. `bedOnTime` is still written (within the
  tolerance), for the statistics and the sleep streak, which are unchanged.
  A row with only `bedOnTime` — every row before step 59 — keeps the old
  rule: true is the full half point, false nothing.
- **"Gehe nun schlafen" stays on offer for two hours** past the bedtime
  (`eveningReach` in `sleepPrompts`), recording the hour it was pressed; the
  box "Schlafenszeit – Vortag" waits as long and offers "Pünktlich" or
  "Bin um … Uhr schlafen gegangen" (no "Unpünktlich" any more).
  `DashboardViewModel.recordBed(day, at, punctual)` works out the bedtime of
  that evening (`nightEndingOn(day + 1)`).
- `SleepLog.wakePoints` / `bedPoints` → `settleDay(wakePoints, bedPoints)`,
  which stand in for the two booleans' half points. **A deduction is booked
  under the same `PointsReason`** (`PUNCTUAL_WAKE` / `PUNCTUAL_BED`), negative;
  the Abrechnung says "Zu spät aufgestanden" / "Zu spät schlafen gegangen".
  Only positive ones flow into the Belohn-o-mat (`sleepEarnings`, as before).

#### Affirmations

`domain/stats/Affirmation.kt`, `ui/stats/AffirmationBox.kt`. `moodOf(figures)`:
more figures worse than better → `Mood.WORSE` (encouraging: "Jeder braucht mal
eine Pause.", "Nicht aufgeben, du schaffst das!" …), more better → `BETTER`,
otherwise `USUAL` (both praise); null where nothing is known. `affirmation(mood,
seed)` picks from a short list, seeded by the date so it holds still.

- **Wochenrückblick**: over `WeekReview.figures`, above the lines (mine).
- **Tagesabschluss**: `dayReview(today, earlier)` — "Aufgaben abgeschlossen"
  and "Planmäßig erledigt" against every day before (the sleep figures left
  out: one answer is a share of one). At the top of the journal page, the one
  page always shown after the tasks are answered (mine). The history is read
  once per visit (`ReevaluationViewModel.earlier`).

#### "Sich der Task verschreiben"

**Asked: the next evening asks; done is paid then, not done costs nothing.**

- **Offered** in the evening's first page under every open block of today
  whose container has not ended yet — the Abendroutine after the
  Tagesabschluss. `PlanRepository.commit` sets `committedAt`; "Zurück" takes
  it back.
- **That evening**: a committed open block counts as answered (the gate's
  `openBlocks`), **holds its hours** (`heldItsTime`, so no unplanned charge),
  is not harvested, leaves a task streak **standing** (`taskDoneOn`), and is
  left out of the day's affirmation. Its routine steps are not advanced then
  (`advanceSteps` skips it).
- **Done afterwards**: `harvestLate` books nothing for it
  (`isCommitmentOwed`) — it waits for the next settlement.
- **The next evening**: `owedCommitments(date)` (`date < today`,
  `committedSettledOn` null) are listed under **"Verzögerte Tasks"** on the
  first page and must be answered — ticked, or "Nicht erledigt" (the ordinary
  discard: a ToDo goes back, a standing task offers "Nachholen"; no charge,
  that day's settlement being over). The done ones are
  `DailySettlement.deferred` (`DeferredTask`: name and `yieldOf`), shown in
  the Abrechnung as "Verzögerte Tasks" with a line each, booked one row each
  as **`PointsReason.DEFERRED_TASK`** with the name as the note, poured into
  the Belohn-o-mat, grown (`GrowthService.advanceDeferred`, steps included)
  and retired. Then every owed one gets `committedSettledOn`
  (`settleCommitment`; still open → discarded).
- A block committed and then answered before "Abschließen" after all is
  settled that same evening like any other.

**Known limits.** A commitment is only offered for today's own blocks, so a
bedtime routine after midnight belongs to the next date and is not offered.
The tutorial shows the button without a script for it.

**Verified by the compiler (from scratch), the suite (794; 11 in
`Step59Test`), `assembleRelease`, `lint` and the schema against `42.json`.**
Nobody has seen any of it.
