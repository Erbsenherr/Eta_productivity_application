---
paths:
  - "app/src/**/domain/recurrence/RecurringSchedule*.kt"
  - "app/src/**/RecurringTaskService.kt"
  - "app/src/**/ui/reminders/**"
  - "app/src/**/domain/reminder/**"
  - "app/src/**/*Reminder*.kt"
  - "app/src/**/*StillActive*.kt"
  - "app/src/**/*Pomodoro*.kt"
---

### Step 18: the standing schedule, reminders, and two new voices

Four questions went to the user first, and the answers are rules now: an edit to a
standing task leaves the **confirmed days** alone — today, and tomorrow once its plan
is confirmed — and lays everything after them down again; **every** Eta sound plays on
the alarm stream and is silent only under Do-Not-Disturb (*Sounds*); a reminder plays
`notification.mp3` once; and "über eine Stunde" for the still-active question means **an
hour or more**.

#### The standing schedule lives on the Listen tab

**One row per task, not per definition.** The model keeps one definition per weekday so
moving Tuesday does not drag Thursday along; a list of that is ten rows of "Arbeit" and
five of "Pause". `domain/recurrence/RecurringSchedule.kt` gathers definitions that agree
on everything but the weekday into a `RecurringGroup` — name, category, role, note,
time, duration, margins, end sound and **`Rhythm`** (weekly/daily is one rhythm,
fortnightly and monthly keep their parity or week). The row reads "Mo–Fr · 09:00 ·
3 h 30 min" via `formatWeekdays`.

**Saving a group reuses rows** (`reassignRows`): a weekday that stays keeps its row, a
new weekday takes a dropped day's row before it gets a fresh id, and whatever is left
over is *retired*, never deleted — the cascade would take its history out of the
Erfolgsliste. Reusing rows is what keeps holiday rules and past completions attached. A
side effect worth knowing: a questionnaire id like `setup:sport-tuesday` can end up
carrying a Wednesday rule. Nothing reads meaning into those ids any more.

`RecurringTaskService` does the writing. `PlanRepository.lastKeptDay()` is today, or
tomorrow if tomorrow is confirmed; `relayOccurrences` clears the still-open blocks
*after* that (`clearUpcoming`) and runs `topUp`. Leaving the materialized blocks alone
would have made every edit show up three weeks late, which is not what "only future
ones change" meant. Completed and called-off blocks are never touched. That line lives
on `PlanRepository` because three services draw it and three copies would be three
chances for one to drift. **The frame of the day can keep paying nothing**:
`RecurringAttributes.category` is nullable and the editor offers "Keine", or correcting
the time of Pause would have made Pause pay Fokus points.

#### Overlap warnings

`recurringOverlaps` compares the slots a form describes against every other live
definition: shared weekdays (`sharedWeekdays` — opposite fortnightly parities and
different monthly weeks never meet) and overlapping **containers**, clamped the way
blocks are, so a warning here and a collision in the planner agree. It is a **warning,
not a refusal** — a walk during a phone call is two things at once on purpose.
`OverlapWarning` sits under Dauer in `RecurringAttributeFields` and updates live; it is
mounted in the Listen tab's group editor, the Sammelliste editor and the evening's
concretize card, the three places a recurring task is created or edited.

#### Erinnerungen, a fifth tab

`Reminder` is its own table — no item, no block, no points — with an `Instant`, not
wall-clock time, since it was set for a moment. `ReminderCoordinator` is the task
alarm's shape again: **one** alarm aimed at the soonest pending reminder, re-aimed on
app start, boot, every edit on the tab and every ring. On a ring it takes everything
due, marks it `firedAt`, and plays `notification.mp3` once. `nextReminderAlarm` aims an
**overdue** reminder a moment from now, so one the phone slept through still rings
instead of being lost.

The form asks only for text and time; `defaultReminderDate` puts it on today, or
tomorrow if the minute has gone by. The "Anderes Datum" checkbox opens a day stepper
with ±1-week buttons. An existing reminder on another day opens with the box ticked, so
editing its text does not move it. Editing clears `firedAt`, so a reminder that has
rung can be set again.

#### "Bin ich noch bei der Sache?"

A settings switch plus a count per day (1–20, default 3). `StillActive.kt` chooses the
minutes. Eligible means **60 minutes or more**, not a `BREAK` or `FREE_TIME` role, not a
SPEND item, and not called off. Each task's window keeps ten minutes clear of both
ends, where the start and end sounds already speak. Two properties that are decisions:
the draw is **seeded by date and count, so it repeats** — the alarm is re-aimed on
every edit and every ring, and a draw that changed each time would never arrive, so a
new draw happens only when the plan's eligible stretch changes; and **ticked-off blocks
stay in the draw**, whether a block is still open being asked when it rings, or ticking
one task off would reshuffle the questions for every other task that day. It rides the
task alarm as `TaskEventKind.STILL_ACTIVE`.

#### Pomodoro

A long press on either page of the "now" box opens `PomodoroDialog`: set up (30/5
suggested, 5-minute steps), or switch off if the block already has a rhythm. The rhythm
is stored **on the block**, since it belongs to one sitting.

- **`pomodoroAnchor` null means the task's start**, which is what "Als Nächstes" stores,
  so moving the block moves its rhythm too. "Gerade" stores the current minute, without
  seconds, unless the task has not started yet.
- **`pomodoroBoundaries` announces every pause, and every work phase after the first.**
  Set up ahead, the ordinary `task_start` sound opens the task and `pom_work_start` is
  first heard after the first pause; set up mid-task, the user has just pressed the
  button. One rule, not two cases.
- Only **inside the task**: no boundary on or after its end, and none in the journeys or
  the break around it.
- `POMODORO_PAUSE` / `POMODORO_WORK` ride the task alarm, and the notification is
  replaced per block rather than stacked. The "Gerade" page shows "Pomodoro · Pause bis
  10:35" from `pomodoroPhaseAt`.

