---
paths:
  - "app/src/**/alarm/PlanningAlarmCoordinator.kt"
  - "app/src/**/data/repository/PlanningPhaseService.kt"
  - "app/src/**/domain/planning/PlanningSchedule.kt"
  - "app/src/**/ui/subtasks/SubtaskBuilder.kt"
  - "app/src/**/ui/dashboard/**"
  - "app/src/**/ui/planner/**"
  - "app/src/**/domain/model/ItemExtras.kt"
  - "app/src/**/ui/attributes/ItemAttributes.kt"
---

### Step 54: four bugfixes, two conveniences, and Ultra-High

The first of five steps cut from one `update.txt` (the rest: Timer-Task malus,
the Stoppuhr, the Regelwerk's consequences, Wochenrückblick and Balancing).
Four questions were put for the whole file; the one that is this step's is
Ultra-High. Database **version 37**: `items.ultraHigh` (0).

#### The bugfixes

- **"Anfahrt" and "Rückweg" are renamed** — in the forms
  "Anfahr-/Vorbereitungszeit" and "Rückfahr-/Nachbereitungszeit", on the
  timeline, the "Gerade" phase and the block's caption "Anfahrt/Vorbereitung"
  and "Rückfahrt/Nachbereitung". The columns keep their names
  (`travelBefore`, `returnAfter`).
- **The planning alarm stopped at midnight.** The evening's reevaluation is
  always of the calendar day, and `isCompleted(DAILY)` asks about *today* — so
  after midnight the five-minute retries asked about the new day, which is not
  settled until its own evening, and rang all day. `planningWindowOpen` (in
  `PlanningSchedule.kt`): the daily phase is open from its hour to midnight;
  outside it `onRing` treats the phase as done — dismisses and schedules the
  next regular hour. The weekly phase is not cut off. A "NOTFALL" deferred past
  midnight therefore does not ring either: the evening it was about is gone.
- **Reordering a routine's steps was one place per drag.** `SubtaskRows` drew
  its rows with a plain `forEachIndexed`; after a swap the slot had a new
  `row.key`, its `pointerInput(row.key)` restarted, and the gesture ended. The
  rows are `key(row.key)`ed now, as `RewardsScreen` always was — and the "Ziele"
  tab's list, which had the same flaw. **A reorderable list needs `key`**, the
  step-14 rule from the other side.
- **A start time changed in "Heute anstehend" is always written** (`setStart`).
  This overrules *The Heute tab*'s "`setStart` runs the same `canPlace` /
  `firstFreeStart` pair": it no longer slides or refuses. Onto occupied time it
  says so (`BlockEditFeedback.Overlapping`) and the Überschneidung box reports
  the collision like any other.

#### Two conveniences

- **A block already in a conflict gets help in its edit window.**
  `blockConflict(entry, blocks)` (top level in `DayPlannerViewModel.kt`) reads
  what the block overlaps on its day; `BlockEditDialog(conflict = …)` draws
  `ConflictHelp` at its top: "Nächster freier Platz" (`resolveToNextFree`,
  grey for a block that is not `isMovable` — a standing occurrence, an import,
  a Termin) and "Überschreiben" (`resolveOverwriting` → `makeRoomFor`, the
  block itself ignored; `ReliefLines` say what happens). Both undoable. Leaving
  it is closing the window. **Mounted in the planner and the dashboard's long
  press, not on the week timetable**: that tab's planner view model is not
  necessarily of the block's day, and the two actions look the block up in
  their own state.
- **"Gerade" empty, a tap on the "Als Nächstes" page** offers to pull that task
  forward: `offerPullForward` raises the ordinary `FollowUpQuestion` with
  `idle = true` (title "Gerade ist frei"), so the answers are the follow-up's
  — vorziehen, Pause dann vorziehen, Plan beibehalten — and `pullNextForward`
  does the rest. Only while nothing is running.

#### Ultra-High

**Asked: a flag, not a fifth priority.** `Item.ultraHigh`, carried in
`ItemExtras.ultraHigh` (so every ToDo save path writes it; `withExtras` drops
it on a standing task), asked as a `CheckRow` under the priority in
`TodoAttributeFields`.

- **The revolver** (`DayPlannerViewModel.uiState`) reads the week list **and
  every Ultra-High card of the Sammelliste**; such a card ignores its unlock
  day, stands **in front of** the tier on offer and is **not part of the
  priority gate** (it would hold its own tier shut). Placing it moves it to
  `DAY` as any card; dropped back, it goes to the week list.
- It counts as a card the week still holds for "a ToDo beside the week"
  (`canCreateTodo`).
- The flag stays on the card; nothing clears it.

**Verified by the compiler, the suite (763; 9 in `Step54Test`),
`assembleRelease`, `lint` and the schema against `37.json`.** Nobody has seen
any of it, and whether the planning alarm is quiet after midnight can only be
heard on the phone.
