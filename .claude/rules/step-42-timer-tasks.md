---
paths:
  - "app/src/**/domain/growth/**"
  - "app/src/**/ui/growth/**"
  - "app/src/**/*Growth*.kt"
  - "app/src/**/*Timer*.kt"
  - "app/src/**/ui/dashboard/**"
  - "app/src/**/domain/planning/TaskStart.kt"
---

### Step 42: Timer-Tasks, and the "Ziele" tab

Two items from `update.txt`, four questions put first. Database **version 30**:
`items.timerTask` / `timerBuffer`, `planned_blocks.timerGoal` / `timerWorked` /
`timerRunningSince`, `user_setup.allowPartialTimerGrowth`.

**"Growth-Task" is gone as a word.** What it was is a **Timer-Task with a
"Zielzeit"**. The code keeps its names — `growth…` columns, `GrowthSetting`,
`isGrowthTask`, `ui/growth/`, `EtaTab.Growth`, `UserSetup.growthTasks` — and
`step-19-24-growth.md` still describes how growing works. The migration sets
`timerTask` on every growth task there is.

#### The answers that are rules now

- **Standing tasks only.** A ToDo cannot be a Timer-Task.
- **The increment comes every X completions**, as before — not by calendar days.
- **Billed is what the timer counted, pomodoro pauses and the buffer included,
  pauses by hand not.**
- **The pomodoro turns still ring, off the timer; the goal rings with the
  pomodoro-pause sound.**

#### The model

`domain/growth/TimerTask.kt`. `Item.timerTask` is the flag, `timerBuffer` the
slack behind the time on the timer.

- **`estimatedDuration` includes the buffer**, so expansion, overlap, the week
  and the yield learned nothing. `Item.timerDuration` is the time on the timer;
  `growthCurrent`, `isFullyGrown` and `grownDuration` work on it and put the
  buffer back.
- **The form's "Dauer" is the timer's time** (`RecurringAttributes.of` reads
  `timerDuration`). The three paths that save a standing task from a form —
  `saveGroup`, `createGroup`, `concretizeRecurring` — end with
  **`withBufferAdded()`**, after `withGrowth`. A fourth path has to as well.
- **The setting travels in `ItemExtras.timer`** (`TimerSetting`), so no save
  path got a parameter. `withExtras` writes flag and buffer and not the length.
- **A "Zielzeit" needs the timer.** `RecurringAttributeFields` clears `growth`
  in the same `onChange` that unticks the timer — one write, because two
  callbacks would each copy the same stale `value`.
- `TimerRow` in the Extras box replaced `GrowthRow`: Timer-Task → Pufferzeit →
  Zielzeit (the old three lengths, update condition, dynamic placement).

#### The timer

`domain/planning/TaskTimer.kt`, pinned in `TaskTimerTest` (18).

- **Three columns and no ticking number.** `timerGoal` is what the sitting
  counts down from (null: never started), `timerWorked` what was counted at the
  last stop, `timerRunningSince` when it was last set going. `timerElapsed(now)`
  is right however long the app was closed, and is capped at the goal.
- **The pomodoro phase is counted on the timer** (`timerPhase`), not on the
  clock: a pause by hand stops the rhythm with everything else. `eventsOf`
  therefore reads `timerSignals()` for a Timer-Task instead of
  `pomodoroBoundaries()` — **nothing rings until "Start", nothing while paused**
  — and adds `TaskEventKind.TIMER_DONE` (`pom_pause_start`). Every start, pause
  and resume calls `taskStartCoordinator.reschedule()`.
- **`fullTimerGoal(buffer)`** is the block's own length minus the buffer.
- **Ending**: `timerBilled` = counted + buffer, at most the plan, written to
  `actualDuration` — null when worked in full. So **`workedInFull` is
  `actualDuration == null || actualDuration >= plannedDuration`**, with no
  column of its own.
- **`GrowthService.advanceFor` passes `timeCounts = workedInFull`** to
  `advancedByCompletion` — unless `allowPartialTimerGrowth` (Flexibilität, off).
  A Mengen-Inkrement advances either way: the task was done.

#### On the dashboard

`ui/dashboard/TimerControls.kt`, handed a `TimerActions` (the ticking `now`
and four callbacks taking a block **id**).

- **In the "Gerade" box where "Erledigt" stands** — on both pages and in every
  phase — **and under every open Timer-Task in "Heute anstehend"**: a sitting
  begun before or after its hour is not in the box at all.
- "Start · 2 h" → the countdown. With a rhythm the large figure is the phase,
  the small one the total. "Pause" / "Weiter".
- **"Ende" is behind a long press** (`EtaButton(onLongClick = …)`,
  `combinedClickable`) until the goal is counted down or the block's frame has
  run out; then it stands beside the button.
- **Ended short**: `TimerResultDialog` — and, when the frame had run out, the
  advice to make the buffer larger. Worked in full it is an ordinary completion
  and the follow-up question is put.
- **Started late** (`timerOverflow`): only when the time would run past the
  block's own end **and into another block**. `TimerOverflowDialog`: "Heute
  früher aufhören" (`timerGoal` = the time until the next container; not a full
  sitting) or "… später beginnen" (`trimmedAfter` on the next block — it ends
  when it did). A calendar import is never the one to give way. Worked out again
  when answered.
- **Ticking it off** in "Heute anstehend": with the timer started it is "Ende";
  never started, `TimerTickDialog` asks — "Ja, volle Zeit", or a time of one's
  own, stored the way the timer stores its count. Taking the tick back clears
  the timer (`timerCleared`).
- **In the evening** a tick on a Timer-Task whose timer was started and never
  ended bills what it counted; one never started is taken at its word.

#### The "Ziele" tab

`EtaTab.Growth`, labelled "Ziele" (the entry keeps its name — a saved tab is
restored by it). Timer-Tasks with a "Zielzeit" in their dragged order, as
before, and under them `GrowthTasksUiState.counted`: standing tasks with a
Mengen-Inkrement that are not also in the first list. The button makes a
Timer-Task with both boxes ticked; a plain one is made on the Listen tab. The
Advanced Feature reads "Timer-Tasks und Ziele".

**Known limits.** A Timer-Task's `actualDuration` can still be stepped up under
a finished row, which makes a short sitting read as a full one. The block is
not moved or lengthened when a late timer runs past it into free time. A timer
left running overnight is capped at its goal and ends by the evening's tick.
Nothing shows a running timer outside the dashboard — the day planner draws the
block as it was planned.

**Verified by the compiler, the suite (661), `assembleRelease`, `lint` and the
migration's columns against `30.json`.** Nobody has seen any of it — and here
that covers the alarm following a timer, which is `AlarmManager` re-aimed on
every button press.
