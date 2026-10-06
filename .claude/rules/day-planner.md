---
paths:
  - "app/src/**/ui/planner/**"
  - "app/src/**/domain/planning/DayTimeline*.kt"
  - "app/src/**/domain/planning/DragScroll*.kt"
  - "app/src/**/domain/planning/NowAndNext*.kt"
  - "app/src/**/PlannedBlock*.kt"
---

### The day planner

`ui/planner/` builds the screen from `Paint Draft Dayplanner.png`: the revolver on
top, the day below, one gesture connecting them. `domain/planning/DayTimeline.kt`
holds everything worth testing without a screen — minute arithmetic, overlap,
snapping, and where a block can actually go.

- **A drop becomes a time.** `DayTimeline` reports its own position and how tall a
  minute is via `onGeometry`; the screen turns the finger's position into a minute
  from that. The report comes out of layout, so it stays right while the day scrolls
  under the finger — no scroll bookkeeping of our own.
- **A drop onto another movable block asks.** "Tasks gruppieren?" — with "Nur
  verschieben" as one of the answers, because the slide below is a gesture a dense
  day needs. See *Step 23*.
- **A drop onto occupied time slides.** `firstFreeStart` finds the next place the
  block fits and the user is told where it went. Refusing would make a dense day
  nearly unplannable; overwriting would quietly destroy something already planned.
- **Turn versus pull.** Both gestures live on the centre chamber, so the *first*
  direction the finger takes decides which one it is, and that decision is held
  until the finger lifts. Anything else and a hesitant drag does both. `onPullStart`
  fires at the moment the direction is decided, which is the same moment `pulling`
  becomes true — it used to fire on any touch while `onPullEnd` only fired for a
  real pull, so turning the revolver left the screen holding a drag that never
  ended.
- Corners carry meaning (`shapes.draggedBlock` vs `shapes.fixedBlock`). Only
  `BlockOrigin.DRAGGED` blocks move.
- **A drag snaps and says where it will land.** The block reports its candidate
  minute upward while the finger is down, the timeline draws the same
  `DropIndicator` a revolver drop draws, and the block's own offset follows the
  *snapped* minute rather than the finger: the imprecision was that the finger
  covers the target and nothing said what time it had reached.
- **Dragging to the top or bottom scrolls the day under the block.**
  `domain/planning/DragScroll.kt` holds how fast and from how close to the edge, and
  the speed ramps with proximity so the last quarter-hour is still placeable. The
  awkward part is keeping the drag honest while the ground moves: a block drag is
  **relative** — it accumulates the finger's own travel, which preserves the grab
  point on a two-hour block — so the screen publishes how far it has scrolled and
  the block adds that to its own travel, both read as `State` in composition. A
  revolver drag needs none of that, being **absolute**: a position on screen turned
  into a minute through the timeline's reported geometry, so it follows a scroll for
  free. The auto-scroll stands down over the revolver, since reaching it means
  dragging up out of the day.
- **Dropping a block on the revolver unplans it** — the block goes and the ToDo
  returns to `Stage.WEEK`, exactly what "Vom Tag nehmen" does in the edit dialog.
  Offered only for a movable block.
- **A tap shows the full name**, time and note in a bubble above the day, for as
  long as the block stays selected: a quarter-hour block is 16dp tall and will never
  hold its own label, and long-press already meant "edit".
- **Long-press opens `BlockEditDialog`.** Name and category are edited on the
  **item** and so change every future occurrence; start and duration on the
  **block**. The dialog says which is which rather than hiding the split, and folds
  its per-occurrence rows into an `EtaExpander`. A SPEND item gets `RateField` in
  the category's place — a category pays it nothing — and `edit` writes
  `pointsPerHour` to the item, safe there because a points entry has exactly one
  block.
- **Long-press also offers a copy into the week list**, for a task that did not
  finish inside the time it was given. That copy is a **new ToDo, not a moved one**:
  the occurrence keeps its history and stays on its day, and the copy carries name,
  category, note and the block's duration, is stamped with the current cycle via
  `takeIntoWeek`, and skips the Sperrliste check for the same reason a make-up does.
- **"Ganzen Tag absagen"** sits below the end of the day: it calls off everything
  still open and hands the dragged ToDos back to the week list — one decision rather
  than a long press per card. Its dialog counts both halves before it happens,
  because they are different acts and only one of them is reversible by re-planning.
- Confirming writes the `DayPlan`; the screen then reads as "Liste für Morgen" and a
  long press anywhere reopens it.

The revolver holds `Stage.WEEK` items, which the weekly planning phase fills.
`PlannerDay` decides which day is on the screen: the planning phase is `TOMORROW`,
`TODAY` is the same screen turned on the day already running, reached from the
dashboard's "Heute umplanen" and opening at the hour before now rather than at
seven. Only `TOMORROW` is locked by its confirmation — see *Step 10*.

The daily flow runs end to end in the order `Planungsphase.md` gives it:
`ui/reevaluation/` (phase 1) → `ui/concretize/` (phase 2) → the calendar step →
the planner (3 and 4), each handing on to the next. Phase 2 is where a Quick-Add
note gets its category, priority, target date and duration — without it a jotted
line can never reach the revolver, which only offers `isConcretized` cards.

### A block that brings its own margins

Confirmed with the user: **duration columns on the block, not extra blocks.**
`PlannedBlock.travelBefore`, `returnAfter` and `breakAfter` are nullable durations,
in the order they happen — you come back, and then you rest:

```
containerStartMinute() = start − travelBefore
containerEndMinute()   = end   + returnAfter + breakAfter
```

The block still starts when it says it starts; the **container** is what everything
about *time* is asked — `overlaps`, `canPlace`, `firstFreeStart`, `plannedMinutes`,
the weekly free-hour maths, the drawn frame and the drag. What everything about
*points* is asked stays `effectiveDuration`: margins pay nothing, and `yieldOf` never
sees them.

Why not child blocks with a parent id: each would need an `Item`, which means an
extra definition per task, two more entries in every list, two more questions in the
evening, and a moved task that has to drag two rows along atomically. Every one of
those is a place for them to come apart. As columns there is nothing to keep in step.

- **Drawn** as one enclosing box around two or three, travel above the task and the
  break below, both in a dimmed fill, with the task keeping its own box inside. The
  frame carries no gestures.
- **Dragging moves the container** by construction: the finger grabs the task, the
  task's start moves, the margins are offsets from it. (On screen the frame used to
  sit still while the task slid out of it, which is also what made the lost margins
  of step 12 look like a deletion; `DayTimeline` now knows which block is being
  dragged and offsets the frame with it.)
- **`Item.travelBefore` / `returnAfter` / `breakAfter` are the defaults**, stamped
  onto every block the definition produces, each defaulting to **15 minutes** when
  ticked. `BlockEditDialog` edits them **per occurrence** — adding a journey to today
  does not add one to every Tuesday — while the Listen tab edits the definition's
  defaults. They travel with both copies the app makes of a task,
  `Item.newMakeUpTodo` and "in die Woche kopieren", both being the same work on
  another day.
- **The break is the part that yields when the day is full.** Placement tries the
  whole container first; if only the task with its journeys fits, the user gets
  `BreakDroppedDialog` — "Es ist kein Zeitslot frei, der die vorgesehene Pause
  zulässt. Trotzdem einplanen?" — and yes places it with `breakAfter = null`. The
  **journeys are never dropped**: they are time the task actually takes, in either
  direction, and a task planned without them is planned wrong. If even that finds no
  room it is the ordinary `NoRoom` refusal.
- **"Als Nächstes" reads the container**, not the task's own span, and says which
  part is meant. `nowAndNext` used to report a dentist's appointment at 17:15 as
  starting at 17:15, by which time you should be in the chair; it now reads
  "Zahnarzt · Anfahrt" from 17:00. **Next** is the first block whose container
  begins after now, labelled by the phase at that boundary; **now** spans the whole
  container, so the Rückweg and the Pause are things you are in the middle of rather
  than gaps between blocks. `BlockPhase` is the suffix; the task itself gets none,
  being what the block is named after. The asymmetry is the user's own and it is
  right: a break belongs to what you are doing now, never to what is coming.


### The third revolver: Pause (step 29)

The footer button walks through **three** revolvers now — ToDos → Punkte → Pause —
and still names the one it leads to. `RevolverKind` carries the label and `next()`,
so a fourth is one line.

The Pause revolver holds a single chamber, "Pause · 15 min · 0 Punkte". Dropping it
calls `DayPlannerViewModel.createBreak`, **without a dialog**: a break has nothing to
ask. It is the card the dashboard already slips in after a finished task —
`Item.spontaneousBreak`, `ItemRole.BREAK`, no category — so it neither earns nor
costs, the still-active question skips it, and the evening asks about it like any
block. `PLANNED_BREAK` is `SPONTANEOUS_BREAK`: one default length, a quarter of an
hour, for both ways a break comes about.

- **A revolver of its own rather than a chamber among the points**, at the user's
  request: it is not a points entry, and it should not sit behind four others.
- **Placed like any card** — `firstFreeStart` from the dropped minute, with the
  "moved to" banner when the slot was taken.
- **A new item per break**, because the unique index allows an item one occurrence
  a day. The Sperrliste is not consulted.
- **Length and category are changed on the block**, by a long press, like any
  other; nothing here offers a second default.
- **A planned break left unticked is unplanned time** in the evening, the same as
  the spontaneous one — a break costs nothing, but its quarter of an hour is still
  a quarter of an hour the day has to account for.
- **A break is on no list.** `Item.isOneOffBreak` (a TODO carrying `ItemRole.BREAK`,
  as opposed to the questionnaire's standing Pause, which is recurring) is the
  handle. Dropped back on the revolver, or swept up by "Tag absagen", the block
  **and the item** are deleted and the banner says "vom Tag genommen" — it used to
  land in the week list like a ToDo. Called off — "Absagen" in the planner, "Fällt
  aus" in the evening — it stays a discarded block and is **not** sent to the
  Sammelliste: `consequenceOf` answers `NONE` for it. That also covers the break
  the dashboard slips in after a finished task, which had the same leak.
