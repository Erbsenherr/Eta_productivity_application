---
paths:
  - "app/src/**/ui/components/EtaButton.kt"
  - "app/src/**/ui/planner/PlannerDialogs.kt"
  - "app/src/**/ui/dashboard/**"
  - "app/src/**/domain/recurrence/**"
  - "app/src/**/domain/contract/**"
  - "app/src/**/domain/setup/Nights.kt"
  - "app/src/**/ui/attributes/**"
---

### Step 33: a weekend of one's own choosing, an end date, and a way back in

Seven items from `update.txt`. Database **version 22**, one migration for all three
new columns. The weekend days and the after-midnight bedtime are under *Step 32* in
`setup.md`, the reinstatement under *Contracts*; what is only here:

**A long press in "Heute anstehend" opens `BlockEditDialog`** — the planner's own
window, not a second one. `DashboardScreen` is handed the **same**
`DayPlannerViewModel` the "Heute umplanen" flow uses (`viewModel(key =
"planner-today-$dateKey")`, so it is literally the one instance), and
`TodayBlockEditor` wires the dialog to it exactly as `DayPlannerScreen` does. One
implementation of edit, cancel, remove and copy rather than two that drift.

- `TasksBox.onEdit` takes the block's **id** and the dashboard resolves it against
  `todayBlocks` each time it draws — the `pointerInput` rule, and a row that was
  ticked off while the dialog stood open simply closes it.
- **Open rows only.** A cancelled row keeps its "Doch einplanen" long press; a
  finished one has left the plan and the planner does not offer it either.
- "Morgen anstehend" passes no `onEdit` and stays as it was.
- A `PlacementFeedback` the planner view model raises here (a copy into the week)
  is only shown the next time the planner opens. Harmless, and known.

**Holding "Absagen" down is calling off because of höhere Gewalt.**
`EtaHoldButton` (in `EtaButton.kt`) fills left to right over 900 ms; covered, it
fires `onHold`, and `DayPlannerViewModel.cancel(entry, forceMajeure = true)` runs.
A tap is the ordinary, charged "Absagen".

- **Letting go part-way does nothing** — neither meaning. Someone who started to
  hold and thought better of it has not asked for the charged cancellation either.
  `HOLD_TAP_FRACTION` is how far the bar may have got for a release to still be a
  tap.
- **One write.** `PlanRepository.discard(block, forceMajeure)` sets both fields:
  `discard` followed by `excuse` would each save its own copy of the row, and the
  second would undo the first.
- **No reason is asked**, unlike the evening's excuse, which demands a sentence.
  The user asked for it to be direct; the hold is the friction. The stored reason
  is `FORCE_MAJEURE_ON_THE_SPOT` ("beim Absagen angegeben"), so the evening row
  still reads as excused and says how.
- **Only where calling off costs** (`cancellationCosts`): tomorrow's planner keeps
  the plain button, there being nothing to excuse. The evening's own hold stays,
  for a cancellation that turns out to have been nobody's doing afterwards, and
  `ClearDayDialog` still points at it — "Ganzen Tag absagen" has no hold.

**"Wiederholen bis"** is `Item.repeatUntil`, the last day a standing task still
lays an occurrence down on (inclusive).

- **`expandRecurring` consults it**, which is the only thing that makes an end
  durable — see *What that idempotence does not buy* in `CLAUDE.md`.
- **It travels in `ItemExtras`**, so every save path that calls `withExtras`
  carries it without a new parameter: the Listen tab's editor, the Sammelliste's,
  the evening's concretize card and the Growth-Tasks tab. A ToDo carries none.
- **Asked in the form itself**, under Dauer (`RepeatUntilRow`), not behind
  "Extras": it answers the question the weekdays answer, from the other end. A day
  stepper with week buttons, stopping at today — ending in the past is "Beenden".
- **Saving goes through `saveGroup`**, so `relayOccurrences` takes the open
  occurrences past the end off the days that are still open. Today and a confirmed
  tomorrow keep theirs, like every edit to a standing task.
- **`ScheduleMaintenance.topUpUntil` retires what is past its end**
  (`ItemDao.retireEndedRecurring`), so the task leaves the Wiederkehrend list, the
  Wochenschema and the overlap check once it has nothing left to lay down. Retired,
  never deleted — the cascade.
- `repeatUntil` is part of `GroupKey`; the row reads "… · bis Sa, 14. November".

**Two sums that only knew the ordinary night** were corrected on the way:
`weekBudget` takes `sleepMinutesPerWeek()` (night by night), and
`ReevaluationService.sleepMinutes` sums `sleepStretches(date.dayOfWeek)` — the
night as it falls on that calendar day, the same stretches the planner shades.
