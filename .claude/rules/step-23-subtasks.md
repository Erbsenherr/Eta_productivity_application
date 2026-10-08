---
paths:
  - "app/src/**/domain/subtask/**"
  - "app/src/**/ui/subtasks/**"
  - "app/src/**/*Subtask*.kt"
  - "app/src/**/ui/planner/MergeDialogs.kt"
  - "app/src/**/FoldedTasks.kt"
  - "app/src/**/*FollowUp*.kt"
  - "app/src/**/*Signature*.kt"
---

### Step 23: Subtasks, and a signature that is one

The tenth `update.txt`: a Subtask-Builder reachable from every route, a confirmation
button that asks what to do with the time it freed, and three smaller things. Seven
questions went to the user first, and the answers are the design.

#### Subtasks are not Items

`subtasks` and `subtask_checks` are tables of their own, and a subtask is **name,
note and nothing else**: no duration, no category, no priority, no deadline, no role,
no block. It earns nothing, is never planned, never reaches the Sperrliste and
appears in none of the six lists. Making it an `Item` with a parent id would have
meant teaching every list query, the expansion, the Erfolgsliste and the settlement
to skip children — six places for one of them to be forgotten. With a table of its
own, "every task is a group with an empty subtask list" is literally true: nothing
about an item changes by gaining one.

**The list belongs to the definition, the ticks to the day.** The same split the
whole model rests on: editing the steps in the planner changes every occurrence still
to come, and `subtask_checks` is keyed by **block**, so a weekly group worked through
every Tuesday starts each Tuesday empty. A tick *is* the row's existence — unticking
deletes it, which makes "what is still open" a difference of two sets rather than a
state to keep in step. Both foreign keys cascade.

`SubtaskRepository.save` **reconciles** rather than replaces: a row the user kept
keeps its id and with it every tick already standing against it, and the order in the
list *is* `position`. Writing happens in `ItemRepository.concretize` /
`concretizeRecurring` / `add` and in `RecurringTaskService.saveGroup` / `createGroup`
rather than in the six screens that finish a card, because a group's list has to land
on **every** definition of a family — one per weekday — and a screen that had to know
that is a screen that can get it wrong.

**Database 18**, `MIGRATION_17_18`: the two tables, nothing on `items`.

Since step 34 a task's steps can be worked through one at a time — the
**Routine-Modus** — and `save` no longer trusts a draft's id across cards; see
*Step 34* for both.

#### The arithmetic, and the two decisions in it

`domain/subtask/Subtasks.kt`, tested in `SubtaskTest`:

- **A merge adds the *pure* durations.** The group's length is the sum with no margin
  in it, and it **inherits the outer margins** — the first part's journey there, the
  last part's journey back and break. Only the margins *between* the parts fall away.
  Corrected by the user mid-build, and the reason matters: margins pay no points and
  task time does, so absorbing a commute into the duration would have turned 30
  minutes of driving into half a Fokus point and made grouping the most lucrative
  gesture in the app. This way it is worth exactly what the parts were worth.
- **The margins are derived once**, at the merge, from the order the list ends up in
  (`orderedParts`, matched by name — a draft that has never been saved has no id).
  Afterwards they belong to the group and are edited in Extras: re-deriving them on
  every reorder would let a drag in the builder silently change when the block starts.
- **`remainderDuration` is a share.** What the evening carries over is proportional to
  what is left, rounded to five minutes, never longer than the group nor shorter than
  a block. A subtask has no length of its own, so a share is the only guess available
  — and asking for a length at the end of a long day is the wrong question at the
  worst moment.

#### One builder, four ways in

`ui/subtasks/SubtaskBuilderDialog` is the whole menu — group name, the list, drag to
reorder, tap to edit, "Weitere Subtask hinzufügen", "Task zur Subtask reduzieren" —
and every route mounts it, because they are the same question with a different run-up.
**Nothing in it writes anything** except from the block dialog: the answer goes back
to the form that opened it and is saved with the card, which is what lets the whole
thing be cancelled.

**Tap opens a row, a long press drags it.** The note asks for the long press to edit,
but reordering needs it too and one gesture cannot do both; the Growth-Tasks tab
already reorders with a long press, and a tap has nothing else to do here.

1. **Extras → Subtasks** (the note's option b) in all six forms that create or edit a
   card. The list rides inside `TodoAttributes` / `RecurringAttributes`, so every
   existing save path carries it; `groupName`/`onGroupName` is how the builder renames
   the task, the name field belonging to the screen around the form.
2. **Dropping one movable block on another** (option a) — see below.
3. **The block dialog's "Subtasks" fold** in the planner. A deviation from the note,
   which asks for the long press to open the builder directly: that would have hidden
   the only place where this occurrence's time, duration and margins can be changed.
   One tap more, no lost screen. This is also the only route that offers **"In die
   Wochenliste"** for a step, because it is the only one with a card to take it out of
   and a week list to catch it — `Item.releasedSubtask` makes an *unfinished* ToDo
   (name and note are all a step ever had) stamped with today, which never blocks the
   week from taking on more.
4. **"Task zur Subtask reduzieren"**, inside the builder: the week's goals openly and
   the Sammelliste behind a fold, never anything already standing on a day — those are
   the planner's, and the way to fold one in is to drag it. Ticked and confirmed (the
   dialog names every card and the added length) they become rows at once, but are
   **retired when the card is saved**, so backing out leaves them standing.
   `ItemDao.retireFolded` retires rather than deletes, although the note says
   "gelöscht": deleting cascades through `planned_blocks` and would take a past
   completion out of the Erfolgsliste.

#### Merging in the planner

`DayPlannerViewModel.move` asks `coveringMinute` — the function the long press already
uses — whether the finger landed on another **movable, open** block. Recurring
occurrences and imported appointments are neither draggable nor droppable-on, which is
what keeps a merge from being able to swallow a standing task.

Three shapes, and **all three offer "Nur verschieben"**, because dropping onto
occupied time to reach the next free slot is a gesture the planner has always had and
a dense day needs:

| what was dropped on what | the question |
| --- | --- |
| both plain | "Tasks gruppieren?" → the builder, plus category, priority, duration and the deadline question |
| one is a group | "Zur Gruppe hinzufügen?" — one question, as the note asks; the group keeps everything and grows by the other's pure duration |
| both groups | "Gruppen zusammenlegen?", with a button per group to dissolve |

`SubtaskGroupService.fold` is the single write: the survivor takes the answers, the
dissolved block is **deleted** so its hours come back, its steps are copied into the
survivor and cleared, and its item is **retired, not deleted**. It takes ids and reads
both rows itself — a drag outlives the composition it came from.

**The group starts where the target block started** — the drop names a time — and since
it is longer than either part it may not fit there any more: then it slides like a
drop, and if the day holds no such stretch it stays put, overlaps, and says so
(`PlacementFeedback.Overlapping`). Taking the answer back at the last step because the
afternoon is full would be worse than a collision the dashboard reports anyway.

**What a merge destroys is named first** — not only the deadline the note mentions:
category, priority, margins, reminder, pomodoro rhythm and growth, whichever the
folded card carried. The deadline question appears only when **both** sides have one;
where only one does the group inherits it and there is nothing to ask.

#### On the day, and in the evening

- **The block draws its steps**, `✓` or `·`, as many as its height holds and then
  "+n weitere"; where there are steps the note line gives up its place, and the tap
  bubble lists them all — it is the only place a quarter-hour group is readable.
- **The "Gerade" box ticks them off**, with real checkboxes, for that day. On the "Als
  Nächstes" page they are read-only: nothing has begun, and a checkbox there would
  invite ticking off what has not happened.
- **A confirmation button** sits under the running task ("Gruppe erledigt" where there
  are steps), only during the task itself — confirming a journey or a break would be
  confirming the frame rather than the thing — and the screen asks again, naming the
  steps still open and saying that points are booked in the evening.
- **Then the follow-up question**, from that button and from a tick in "Heute
  anstehend" alike: *Plan beibehalten · Task vorziehen · Pause einfügen, dann
  vorziehen*. Asked only where the tick is about now: a block on another day is being
  caught up, and a tick the day has already moved past — `confirmedRetroactively`, true
  once a later block has begun — says nothing about what comes next. That rule beats a
  fixed grace period, which would have been arbitrary and would break on a day with
  long gaps.
- **Only the next task moves, and only as far as it fits.** `pullForward` is unsnapped,
  because the minute comes off the clock — `firstFreeStart` gained a `snap` flag for
  it. If the break does not fit, **nothing** happens: the user asked for a break *and*
  the task, and quietly giving them only the task answers a different question. The
  break itself is an ordinary card with `ItemRole.BREAK` and no category, so it pays
  nothing and the evening asks about it like any block — including charging its
  quarter of an hour as unplanned time if nobody ticks it off.
- **The evening carries the rest over.** A block that has been answered — done *or*
  dropped — with steps still unticked offers a new group for the week list, with the
  open steps in it. Read off the blocks rather than latched when it happened, so
  ticking the last step makes the question disappear by itself. For a recurring task it
  **replaces** "Nachholen von …", and shares its `makeUpItemId`: that is what makes the
  two mutually exclusive and the carry-over idempotent in one column.

#### The three smaller ones

- **The signature is drawn.** `EtaSignaturePad` collects strokes with a finger and
  `domain/contract/Signature` encodes them into the **existing** `signature` TEXT
  column — the `RecurrenceRule` trick again, so no migration and a signature that
  travels with an ordinary backup. Coordinates are normalized, so it is drawn correctly
  at any size; points are thinned as they arrive and capped, since a resting finger
  would otherwise add one per frame. A contract signed before this holds a typed name:
  `decode` returns null on it and `SignatureView` prints it instead, so nothing had to
  be converted. `SignatureTest` guards the round trip for the same reason
  `SetupEncodingTest` does.
- **Recurring tasks are out of the Erfolgsliste.** The query joins `items` and excludes
  `RECURRING`: a list filling up with Morgenzeit, Pause and Freizeit every day is a
  list nobody reads. What was worth carrying over still arrives, as the ToDo the
  evening made of it.
- **"Gebrochene Verträge" and "Abgeschlossen" are folded away**, each in its own
  `EtaExpander` — both are history, and the three slots are what the tab is opened
  for. Separate boxes, because a breach and an expiry say opposite things about the
  same promise; that is the confusion step 17 split apart.

**Verified by the compiler, the suite (409, of which 31 new: `SubtaskTest`,
`FollowUpTest`, `SignatureTest`), `assembleRelease` and `lint`.** Nobody has seen any
of it: not the steps on a block, not the merge dialogs, not the signature pad — and
the pad is the one thing here that cannot be judged at all without a finger.

