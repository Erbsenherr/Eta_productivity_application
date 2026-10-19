---
paths:
  - "app/src/**/domain/subtask/**"
  - "app/src/**/ui/subtasks/**"
  - "app/src/**/*Subtask*.kt"
  - "app/src/**/ui/growth/**"
  - "app/src/**/GrowthService.kt"
  - "app/src/**/domain/planning/TaskStart.kt"
  - "app/src/**/alarm/TaskStartCoordinator.kt"
  - "app/src/**/ui/dashboard/DashboardBoxes.kt"
  - "app/src/**/ui/dashboard/TimerControls.kt"
---

### Step 49: a routine's steps as Timer-Tasks and with a Mengen-Inkrement

One request, four questions put first. Database **version 34**: the item's
timer, growth and quantity columns of the same names on `subtasks`
(`timerTask`, `duration`, `timerBuffer`, `pomodoroWork` / `pomodoroPause`,
`growthStart` / `Target` / `Increment` / `Every` / `Progress`, `quantity…`),
and the table `subtask_timers` (`blockId`, `subtaskId`, `goal`, `worked`,
`runningSince`; both keys cascade).

#### The answers that are rules now

- **The time lies inside the routine.** A step's timer — and its growing — does
  not lengthen the routine's block; the builder warns (`StepFitWarning`) when
  the timed steps, buffers included, are longer than the routine now or would
  be once grown. The morning routine still hangs off the night.
- **Everything a Timer-Task has** (asked): time, buffer, pomodoro, "Zielzeit"
  with increment and update condition — not dynamic placement, a step being
  placed by its routine.
- **Worked, not ticked, as a Timer-Task is.** In the "Gerade" box the step's
  timer stands where its checkbox would; "Ende" ticks it and the routine moves
  on. It grows only for a sitting whose timer counted the whole goal, unless
  Flexibilität allows otherwise, and only once the evening is settled. Points
  are untouched — a step earns nothing.
- **In the "Ziele" tab, in the existing lists** with "Subtask von …", not
  draggable. A tap opens the routine; the morning routine's row says where its
  steps are edited instead.

#### How it is built

- **Only for a standing task in Routine-Modus.** `ExtrasBox` passes
  `stepExtras = offerQuantity && routineMode` to the builder; the morning
  routine's builder always has it. `SubtaskEditDialog` shows an "Extras" fold
  with `StepExtrasFields` (`ItemAttributes.kt`), which drives the task's own
  `TimerRow`, `PomodoroRow` and `QuantityRow` through an `ItemExtras`.
  `GrowthService` advances steps of routine tasks only.
- **The arithmetic is the item's** (`domain/subtask/StepExtras.kt`):
  `Subtask.asItem()` builds a stand-in item, the growth, timer and quantity
  functions run on it, and `withItemFields` writes the answers back.
  `withStepExtras` is the save path in the item's order — time, timer,
  "Zielzeit", count, buffer on top. `duration` is the time on the timer, the
  buffer is not in it.
- **`SubtaskDraft.extras`** carries the answers; **null leaves a saved row's
  as they are**, so every path that only moves names about (merges, the
  evening's remainder) cannot wipe them. `SubtaskRepository.save` applies them
  per weekday row, each keeping where it has got to.
- **A step grows as one across its rows** — `StepFamily` (the routine's name
  and the step's): `GrowthService.advanceSteps` advances the first ticked row
  of a family and copies what moved (`withProgressOf`) onto the rest. A step
  counts when it was **ticked**, whatever became of the routine's block.
- **The step's timer is a block to the timer code.** `SubtaskTimer.asBlock`
  puts the three timer columns and the step's rhythm onto a copy of the
  routine's block (id `block:step`), so `timerRemaining`, `timerPhase`,
  `timerSignals` and `TimerControls` (with `idleGoal`) serve it unchanged; its
  frame is the routine's. `StepTimerActions` carries the four buttons by block
  and step id. Ending short shows `TimerResultDialog`.
- **It rings.** `stepTimerEntries` turns the running timers into entries named
  after the step, and `eventsOf(…, stepTimers)` adds their pomodoro turns and
  goal; `TaskStartCoordinator` reads them through the `SubtaskRepository`. A
  tick taken back deletes the step's timer row.

**Known limits.** A Timer-Task step cannot be ticked without its timer in the
"Gerade" box (start and end it); the planner's block and "Heute anstehend" do
not show a step's time or count, and the statistics do not draw steps.

**Verified by the compiler, the suite (723; 9 in `RoutineStepGoalsTest`),
`assembleRelease`, `lint` and the migration against `34.json`.** Nobody has
seen any of it, and the step timer's alarm can only be judged on the phone.
