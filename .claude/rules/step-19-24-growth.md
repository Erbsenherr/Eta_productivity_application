---
paths:
  - "app/src/**/domain/growth/**"
  - "app/src/**/ui/growth/**"
  - "app/src/**/*Growth*.kt"
  - "app/src/**/*Quantity*.kt"
  - "app/src/**/TaskReminderService.kt"
---

### Step 19: Growth-Tasks, task reminders and the conflict box

Six questions went to the user first, and the answers are the design: a Growth-Task
grows in **length**, not in clock time — "Startzeit", "Zielzeit" and "Inkrement" are all
durations, the task keeping the start time every standing task has; dynamic placement is
**recomputed, always**, for every day still open, never fixed when a block is
materialized; **one reminder per task**, aimed at its next occurrence; a collision that
slips through is reported at the top of the dashboard (*The Heute tab*); and the
pomodoro setting in Extras is a **default on the task**, stamped onto every block
through `stampedWith` — one function rather than two arguments at each of the four
places a block is built, because forgetting it at one of them would be a rhythm that
silently never starts.

#### Growth-Tasks

`domain/growth/` holds the whole of it, and the reason it is small is the one decision
worth keeping: **`Item.estimatedDuration` *is* the current length.** Nothing else in the
app had to learn what a growth task is — expansion, placement, the free-hour maths,
`yieldOf` and the settlement all go on asking exactly what they asked before.
`growthStart` is kept only so the screen can say where the task began; `growthTarget`
and `growthIncrement` are what make it grow.

**The increment hangs on the settlement, not on the checkbox** — the user said "mit
jedem Abschluss (i.e. bestätigter Abschluss am Abend)", and `ReevaluationService` is
where an evening is confirmed. `GrowthService.advanceFor` runs there, before
`markSettled`, which is also the guard against growing twice, since a day already
settled is skipped and `DayPlan.settledAt` already records that. No column was needed.

**A task grows, not a weekday.** Sport on Tuesday and Thursday is two rows; growing only
the row whose occurrence was ticked off would give the two different lengths, and the
standing schedule groups by attributes — the length included — so one task would start
appearing as two. `GrowthFamily` (name plus the three growth answers) holds them
together, and `GrowthFamilyTest` guards exactly that. `growthOrder` would have been
tidier as the family key and is not usable: it is null for a growth task made outside
the Growth-Tasks tab. Occurrences already lying ahead are relaid by the confirmed-days
rule, or a task would grow and the new length would first show up in three weeks.

#### Dynamic time setting

`placeDynamicGrowth` lays one day of dynamic growth tasks down again:

- The start time is the **earliest** time. If it is taken, the task begins at the next
  slot that fits, and placement **chains onto the end of whatever was in the way rather
  than onto the next grid line** — the point of the feature is gapless stretches of
  work, and rounding each link up to the next quarter of an hour would put a gap between
  every pair of them. That is why growth placement has its own `firstFreeGrowthStart`
  rather than reusing `firstFreeStart`, which snaps.
- Two of them wanting one slot is settled by the **order of the Growth-Tasks tab**. A
  static task in the way — an ordinary standing task, an appointment, a dragged ToDo, or
  a growth task without dynamic placement — simply pushes the growth task behind it,
  after which the priority rule applies again.
- **Only open occurrences move.** A completed one is the record of what happened and
  still holds its hours; a called-off one holds nothing and is not an obstacle either.
- If nothing fits, the block **stays where it is** and shows up in the Überschneidung
  box. It is still an obstacle to the ones behind it, or they would quietly stack on it
  as well.

**It runs inside `ScheduleMaintenance.topUp()`**, so a freshly materialized occurrence of
a dynamic growth task has no hour worth keeping until this has run over it — which is
the honest way round: its hour is a property of the day it lands in, not of the task.
The consequence to know: **a dynamic growth block moved by hand moves back.** That is
what "always recomputed" means, and it is what the user chose.

**The warning at creation.** `growthTargetFit` answers "will this still fit once it has
grown all the way", live while the form is open. It simulates the weekday with every
growth task at **its target** length — the only picture in which the question has a
stable answer — placing the dynamic ones in priority order exactly as the day does. Two
kinds of answer, because they have different remedies: `NO_ROOM` for the dynamic case
(nothing after the earliest hour can ever hold the target plus its break) and
`COLLIDES` for the static one (the hour is fixed and something else will be standing in
it). A **warning, never a refusal**: the day may well be rearranged before the target is
reached, and only the user knows whether it will be.

#### The Growth-Tasks tab

`ui/growth/`, last in the bar as the user asked. Its own tab rather than a section of the
Listen tab because of the one thing it has that no list can express: the **order**.
Growth tasks are listed among the standing schedule as well — they are standing tasks,
and the row there says where the length is going.

- **Reordering is a long press and a drag**, rearranged in local state while the finger
  is down and written once on release: a write per swap would relay three weeks of
  dynamic placement at every step of one gesture.
- The number is written to **every definition** of a task. A priority that applied to the
  Tuesday row would decide Tuesday's slot and leave Thursday to chance.
- **"Growth-Task erstellen" is the first path in the app that creates a standing task
  from a button** — every other one arrives from the questionnaire or from a bare note
  the evening concretizes — so `RecurringTaskService.createGroup` is new. The dialog is
  the ordinary recurring form with the Growth-Task box already ticked.
- Editing goes through the Listen tab's `RecurringGroupEditDialog`: a growth task is a
  standing task, and two editors for one thing would be two places for the rules to
  drift.

#### Erinnerung, as an extra

Ticking it plays a reminder a configurable number of hours before the task, and what it
produces is an **ordinary row in `reminders`** — a reminder the user cannot find in the
Erinnerungen tab is one they cannot change. So something has to reconcile, and
`TaskReminderService.sync` does: the plan is the truth, the rows are derived from it.

- **Counted from the container**, so a task with a journey in front of it is announced
  before setting off rather than before arriving. An hour is meant as warning, and
  warning about a drive already under way is not one.
- **One row per task**, aimed at its next open occurrence. Per occurrence would put
  twenty-one rows in a tab whose whole job is to be read at a glance, and the
  twenty-first says nothing the first does not.
- It rides `ReminderCoordinator.reschedule()`, which already ran on every path this
  needs, so nothing has to remember to call the sync separately — the only version of
  this that stays true. On a ring the reminder that just fired is marked, so the same
  call finds the task owing one for the following occurrence.
- `reminders.itemId` / `blockId` are **plain columns, no foreign key**: `planned_blocks`
  cascades on delete, and a reminder that already rang is the record of something that
  happened — it must not vanish because the schedule was laid down again around it.
- In the tab these rows are **marked and not editable**, and the dialog says why: the app
  keeps them aimed at the next occurrence, so anything changed there would be
  overwritten on the next sync.

**Known limit:** the dashboard inline edits — a start time nudged in "Heute anstehend", a
block ticked off — re-aim the task alarm directly but not the reminder sync, which
catches up on the next flow exit or app start. For a warning an hour or more ahead that
is soon enough; it would not be for a shorter lead.

### Step 24: growth to the second, and counts that grow

Two items, one mechanism.

**Growth lengths to the second.** Startzeit, Zielzeit and Inkrement use
`EtaDurationPicker(precise = true)`: minute steps, and tapping the value opens
`DurationEntryDialog` with one field each for hours, minutes and seconds — three
fields rather than one parsed string, since "1:05" is ambiguous. The floors dropped
to **1 minute** for the start (the day is laid out in minutes; a shorter block would
occupy none) and **1 second** for the increment. Seconds survive storage
(`Converters` keeps durations in seconds) and the day's minute arithmetic floors
them. `formatShort` prints seconds **only where a duration has them**, so every
other duration in the app reads as before.

**The update condition** — "Inkrement alle X Absolvierungen", default 1 — is
`growthEvery` plus `growthProgress`, the completions counted towards the next step.
`countCompletion` and `Item.advancedByCompletion` in `domain/growth/Quantity.kt`
are the whole rule; `GrowthService.advanceFor` still runs at the settlement, so
what counts is a completion the evening **confirms**, and writes the result to every
row of the family. Moving the start resets the progress, like it resets the length.

**The Mengen-Inkrement** is an Extra on **standing tasks only** (`ExtrasBox(offerQuantity)`,
set by `RecurringAttributeFields`): a ToDo is completed once, so a count that grows
per completion has nothing to grow on. Start, increment, the same update condition,
and an **optional** target — the one addition to what the user described, off by
default. It rides inside `ItemExtras` (`quantity: QuantitySetting?`), so every save
path carried it without a new parameter; `withExtras` writes it through
`withQuantity`, which keeps the count reached across an edit that leaves the start
alone. **The count lives on the definition, not on the block**: it changes nothing
about time or points, so there is nothing to relay — every occurrence reads the
current number, and `advanceFor` only relays rows whose *length* moved. Rows advance
together per `(GrowthFamily, QuantityFamily)`.

**Shown where the note is, louder**: `QuantityText` — `bodyStrong` in the accent
colour, "Anzahl 5" or "Anzahl 5 / 20" — in the planner block (first spare line,
before steps and note), its tap bubble, the "Gerade"/"Als Nächstes" box, "Heute
anstehend", the Listen tab's rows and popups, and the standing-schedule summary.

**Known limit:** a past occurrence shows the *current* count, not the one it was done
with — the count is not stamped onto blocks. Nothing reads history from it yet.

