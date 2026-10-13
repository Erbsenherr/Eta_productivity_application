---
paths:
  - "app/src/**/ui/week/**"
  - "app/src/**/domain/recurrence/WeekTimetable.kt"
  - "app/src/**/domain/planning/TimelineDraft.kt"
  - "app/src/**/domain/planning/PriorityGate*.kt"
  - "app/src/**/ui/planner/**"
  - "app/src/**/ui/dashboard/DashboardScreen.kt"
  - "app/src/**/data/repository/RecurringTaskService.kt"
---

### Step 39: the dashboard's tabs, the week timetable, Flexibilität

Five items from `update.txt`, four questions put first. Database **version 28**
(`user_setup.allowPrioritySkip` / `allowTodoBesideWeek`, both 0 for everyone).

#### The answers that are rules now

- **A priority tier of which nothing fits the day is passed by itself.** With
  "Überspringen" gone by default, the alternative was a revolver held shut by a
  card that cannot be placed. `priorityTier(candidates, skipped, fits)`: the
  first tier with one card that `fits` is the one offered, `passedAbove` says how
  many were passed, and the bar says so. `fits` is `fitsSomewhere` from minute 0
  with the journeys and **without** the break — the break is the part that
  yields. A tier with one card that fits holds, the others of it included.
- **The week timetable is edited in both views.** Until step 45 what was written
  was always the standing task; **since step 45 "Aktuell" writes this week's one
  occurrence** and only "Generell" the task — see the section at the end.
- **A move on a task of several weekdays asks**: "Nur Dienstag" or "Alle Tage".
  All days, and some of the *other* days then collide: a second question —
  move them anyway, or leave exactly those days where they are.
- **The dashboard's three tabs are tapped, not swiped.** On "Woche" a swipe
  turns the weekdays.

#### Flexibilität

A settings page (`SettingsPage.FLEXIBILITY`, `FlexibilityBox`) that opens on a
sentence about the app meaning to be used on its strictest rules. Two switches,
applied at once through `setFeature` like the Advanced Features:

- **`allowPrioritySkip`** brings "Überspringen" back to `PriorityBar`.
  `DayPlannerViewModel` passes `skipped` as 0 while it is off, so a count from
  before the switch was flipped cannot linger. "Von vorn" shows only after a skip.
- **`allowTodoBesideWeek`** — see below.
- **`allowPartialTimerGrowth`** (step 42): a Timer-Task's "Zielzeit" counts a
  sitting that was ended early. See *Step 42*.

**Off for whoever upgrades as well.** Unlike the Advanced Features, where the
migration switched everything on so no tab vanished: here the round *is* that the
strict rule becomes the default, and the patch notes say where the switch is.

#### The revolver folds away

`RevolverFold`, a bar between revolver and day that is there in both states;
`revolverFolded` is `rememberSaveable` in `DayPlannerScreen`. Folded, the
priority bar, "Wochenliste füllen" and the revolver are not composed.

- **The hour that lay against the revolver lies against the bar.** Nothing does
  that: the day's `ScrollState` is untouched, the viewport grows upward, and the
  same hour is at its top. Do not "restore" a scroll position on folding.
- `overRevolver` answers false while folded — there is nothing to drop a block
  back on, and the top band's auto-scroll must work again.

#### A box held into an empty stretch

`TimelineDraft` (`domain/planning/`) is a block that does not exist yet. A long
press on a stretch nothing stands on (`DayTimeline.onEmptyLongPress`) makes one —
an hour from the grid line at or before the finger — and it is then pushed with
one finger and pinched with two. The **caller** holds it and owns the button
that ends it: `DraftFooter` in the planner (it replaces the footer, so the day
cannot be confirmed from under the box), `DraftBar` in the week tab.

- **In the day planner it asks what it is** (step 41): a ToDo, a break or a
  points entry — `NewEntryKindDialog`, then that kind's form, opened on the
  box's length. See *Step 41*.
- **No new ToDo while the week list still holds something plannable**
  (`PlannerUiState.canCreateTodo`): the "Neues ToDo" button is grey and says
  why. It used to refuse the press itself, before the box was also the way to a
  break. "Plannable" is the priority gate's own test — concretized, unlocked,
  not on this day, and fitting somewhere — so a card that cannot go on the day
  does not block the day. `allowTodoBesideWeek` lifts it.
- **In the week tab it makes a standing task**: `RecurringCreateDialog(initial =
  …)` with the box's weekday ticked, its hour and its length.
- A block consumes the press that lands on it, so the timeline's own detector
  only hears the empty stretches. `FreedStretch` and the margin frame carry no
  gestures and count as empty — which for a freed stretch is right.
- Null on the confirmed "Liste für Morgen", where a long press anywhere reopens.

#### The pinch

**Watched by the timeline, not by the block**, on the **initial** pass: a
quarter-hour block is sixteen pixels tall and no two fingers fit on it.
`pinchTarget` decides what is meant — the draft while there is one, else the
block between the fingers, one a finger is on, and last the block that was
tapped. Nothing is consumed until there are two fingers *and* a target, so every
one-finger gesture is untouched.

- `pinchedSpan` (pinned in `WeekTimetableTest`): each edge follows its own
  finger and lands on the grid; an edge that has not moved half a step stays
  where it was, so a start at five past survives; never under
  `MIN_BLOCK_MINUTES`, never out of the day.
- The block is drawn at the pinched span (`TimelineBlock.resized`), its time
  line in the accent colour, and `DurationTag` says the length at the edge.
- **Blocks are pinched only where `onBlockResized` is passed** — the week tab,
  unlocked. In the day planner only the draft is; a block's length is still the
  edit dialog's. Not asked for, and deliberately not assumed.
- `DayTimeline.movable` is the caller's answer to "may this be dragged", the
  planner's own (`block.isMovable`) by default.

#### The dashboard has tabs of its own

`EtaTab.Today` is labelled **"Dashboard"** (the entry keeps its name — a saved
tab is restored by it). `DashboardPage`: Heute · Morgen · Woche, `DashboardTabs`
across the top in the bottom bar's shape. The pager and its "wischen für morgen"
line are gone. `DashboardScreen.weekPage` is a slot; null drops the tab, which is
what the tutorial's dashboard does.

#### The week timetable

`ui/week/`, one weekday per page of a `HorizontalPager`, drawn by the planner's
own `DayTimeline` — so a block looks and behaves as it does there. One
`ScrollState` for all seven pages: the hour on screen stays through a swipe.

- **"Generell"** has no occurrences to draw, so `standingBlocksOn`
  (`domain/recurrence/WeekTimetable.kt`) makes blocks up from the definitions.
  Each wears the **definition's id** as its block id. Fortnightly and monthly
  tasks stand on their weekday every week, as in the Wochenschema.
- **"Aktuell"** is `observeRange` over Monday to Sunday of this week
  (`mondayOf`), cancellations left out, finished blocks drawn dimmed — not as
  freed stretches; this is a look at the week, not a place to plan into.
- **A long press on the night changes that weekday's standing night** (step 40),
  and "Aktuell" shades a night changed for one date as it was changed.
- **The night says its hours** (`sleepLabels`): "Schlafenszeit" where a stretch
  begins after midnight's edge, "Aufstehen" where it ends before it.
- **The lock** (`LockButton`, drawn on a `Canvas`) is not saved: locked again on
  every visit. Open, `blocksOn` sets `flexible` on what `canReschedule` — which
  is what rounds the corners — and `movable` lets it be dragged.
- **`canReschedule`**: a live standing task, an occurrence still open, and **not
  one of the two the settings own** (`isOwnedBySettings`: Bettfertig machen, the
  Morgenroutine) — the next save there would overwrite the hour.
- **Long press**: the Listen tab's `RecurringGroupEditDialog` for a standing
  task, locked or not; a sentence for a settings-owned one and for a one-off.

**What a drag or a pinch writes.** `WeekTabViewModel.reschedule(itemId, day,
start, duration?)` → `PendingReschedule` (the two questions) →
`RecurringTaskService.reschedule(ids, days, start, duration)` →
`rescheduleRows`:

- A row whose weekday is among the days is rewritten; the rest are not touched.
  Only a **daily** row stands on several days: moved on all seven it stays
  daily, moved in part it is **split into seven weekly rows** — the row itself
  keeps a day that did not move, the other six are copies and get its steps
  (`copiedFrom`, `SubtaskRepository.copy`).
- The moved days then differ from the rest, so `groupRecurring` shows them as
  an entry of their own on the Listen tab — the model doing what it was built for.
- Occurrences follow `relayOccurrences`: **today, and a confirmed tomorrow,
  keep what they have.** In "Aktuell" a block dragged on such a day therefore
  springs back; the caption, the info dialog and the first question all say so.
- `rescheduleClashes` is `recurringOverlaps` per day, the group itself ignored.
  A collision is a **warning**, as in the form: after the write the notice bar
  names it.
- **Refused, and said why:** a pinch on a growth task (growing owns its
  length), and any move of a *dynamic* growth task (it has no hour of its own).

**Known limits.** A copy made by splitting a daily row has a new id, so a
holiday rule or a Belohn-o-mat binding on the task stays with the row that kept
its id — the limit a new weekday added in the Listen tab's form always had. A
horizontal swipe that begins on an unlocked block drags the block instead of
turning the page; the weekday strip above is the way round. The draft is not
auto-scrolled at the screen's edge.

**Verified by the compiler, the suite (602, 20 in `WeekTimetableTest`, 4 new in
`PriorityGateTest`), `assembleRelease`, `lint` and the migration's columns
against `28.json`.** Nobody has seen any of it, and more than usual rests on
that: every gesture here — the pinch on the initial pass, a long press reaching
the timeline past its blocks, a pager inside a tab around a vertical scroll —
is reasoning about Compose's pointer dispatch that only a finger can confirm.

**Since step 46** "Generell" can show this week's imported appointments
("Termine anzeigen"), the timetable serves any seven days and is opened from the
weekly planning as well — see *Step 46*.

#### Step 45: "Aktuell" changes one occurrence

At the user's request: editing Thursday in "Aktuell" is to touch this week's
Thursday and no other — what the day planner does to a standing task's block,
reachable for the whole week. "Generell" is unchanged and is now the only view
that writes a definition. No schema change.

- **A drag or a pinch** (lock open) → `WeekTabViewModel.moveOccurrence(blockId,
  start, duration?)`: start and `plannedDuration` on the **block**, read fresh by
  id. No "which days" question — one day is in play. A collision is said in the
  notice bar, not refused.
- **A long press** → the planner's own `BlockEditDialog`, wired to the
  `DayPlannerViewModel` the dashboard's list uses (`plannerViewModel`, the
  instance keyed `planner-today-…`; its `edit`, `cancel`, `copyToWeek` and
  `saveSubtasks` take an entry or an id and do not care which day it is).
  `cancellationCosts` only for today. As in the planner, name and category in
  that window are the **task's** and say so.
- **`canMoveOccurrence`** replaces `canReschedule` for this view: a live standing
  task, an open block, **today or later**. The two tasks the settings own are
  *in* — one day of theirs is the user's to change — and past days are *out*
  (`refusePast`): by Thursday, Monday is a record.
- **Refused: a dynamically placed task that was not made "Flexibel"** — the next
  top-up would move it back.
- A ToDo or an appointment is still answered with the sentence about the day
  planner; a long press on an empty stretch still makes a standing task, and
  one on the night still changes the weekday's standing night, in both views.
- **Known limit, the one "Flexibel" has:** an edit to the standing task relays
  its open occurrences after today and a confirmed tomorrow, so an occurrence
  changed here for Friday is laid down again at the task's own hour if the task
  itself is edited on Tuesday.
- A changed occurrence reads as "Plan geändert" in the statistics, which is
  what it is.

**Verified by the compiler, the suite (684), `assembleRelease` and `lint`.**
Nobody has seen it.
