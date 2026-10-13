---
paths:
  - "app/src/**/ui/planner/**"
  - "app/src/**/domain/planning/OverlapRelief.kt"
  - "app/src/**/domain/planning/ConflictRelief.kt"
---

### Step 41: an overlap is asked about, one revolver, and a note at creation

Three items from `update.txt`. No schema change — the database stays at
**version 29**. This step overrules three things `day-planner.md` used to say:
a drop onto occupied time no longer slides, the break is no longer asked about
by a dialog of its own, and there is one revolver, not three.

#### Something new on occupied time asks

Everything new reaches the day through `DayPlannerViewModel.request(pending,
wanted)`: a card out of the revolver, and the three things a box can become.
Where the `PlacementSpan` runs into nothing it is written at once; where it does,
`OverlapPrompt` → `OverlapDialog`, three answers and "Abbrechen":

- **"Dennoch setzen"** — where it was put. The Überschneidungs-Box reports it.
- **"ToDo anpassen"** — `fittedSpan`: the card gives way. In order: without its
  break where it stands; cut to the free stretch its own span touches that
  leaves most of it (a stretch is used as far as it reaches, so a card dropped
  on the end of a block starts after it and keeps its length); else the next
  free stretch. Journeys are never dropped. Null — button grey — when nothing of
  `MIN_BLOCK_DURATION` is left. **The block is cut, the item keeps its
  estimate**: handed back to the week it is its old length again.
- **"Plan anpassen"** — `planRelief`, one `PlanRelief` per block in the path: a
  block reaching in from before `EndsEarlier` (`shortenedBefore(claimStart)`),
  one reaching out behind `StartsLater` (`trimmedAfter` — it **ends when it
  did**, unlike the calendar's `startedAfter`, which keeps the length), one
  around the card keeps its longer side, front on a tie. Under a quarter of an
  hour left: `Removed`. **A calendar import is `Kept`** — never cut, the overlap
  with it stays, and with only those in the path the button is grey.
- **`Removed` goes by `removalFate`**, the split "Ganzen Tag absagen" always
  made (`clearFromDay` serves both): a one-off break is deleted, a hand-placed
  ToDo goes back to the week list, everything else is **called off** — which on
  today's planner costs like any "Absagen", and the dialog says so.
- **The dialog says what each answer would do** before it is given; the view
  model works both out **again** from the day as it stands when answered.
- **The item first, then the room, then the block** (`commit`): the Sperrliste
  can still refuse a new card, and a plan cut open for nothing would be worse.
- **Gone with it**: `BreakPrompt` / `BreakDroppedDialog` (a break that does not
  fit is now this question, and "ToDo anpassen" answers it), and the "passte
  dort nicht — jetzt um …" banner for new cards. `Moved` is still what a group
  says, `NoRoom` what a moved block says.
- **Not changed, deliberately: moving a block already on the day.**
  `moveAnyway` still slides, and a drop on another hand-placed block still asks
  "Gruppieren?". The note asked about *new* cards.
- `fitsSomewhere` — the priority gate's and `canCreateTodo`'s test — is
  unchanged: "fits" still means without overlap.

Pinned in `OverlapReliefTest` (17).

#### One revolver; a box asks what it is

`RevolverKind`, the footer's switch button, `DragPayload`, `SpendKind.SOCIAL`
and `SOCIAL_POINTS_PER_HOUR` are gone. The revolver holds the week's ToDos.

- **A long press on an empty stretch always leaves a box** now. After
  "Bestätigen": `NewEntryKindDialog` — "Neues ToDo", "Pause", "Custom"; "Custom"
  turns the same dialog to "Punkte verdienen" / "Punkte ausgeben". Then the
  form: `NewTodoDialog`, `BreakDialog`, `SpendDialog`.
- **What is refused is shown grey with its reason**, not left out: "Neues ToDo"
  while `canCreateTodo` is false (the week-first rule of step 39 — it used to
  refuse the press itself, which would now lock out breaks too), "Punkte
  ausgeben" while `canSpend` is false. `PlacementFeedback.WeekFirst` and
  `NoPoints` are gone.
- **"Custom" is hidden with the points** (`LocalPointsVisible`); the neutral
  "Spontan" revolver that stayed behind in step 35 has no successor. An hour
  that neither earns nor costs is a Custom entry with its rate stepped to 0 —
  which needs the points in sight; with them hidden there is no neutral entry.
- **The box's length is the entry's**, for all three. `DEFAULT_SPEND_DURATION`
  and `PLANNED_BREAK` are gone; a break is an hour unless pinched, the box's
  default. A Social entry already on a day stays what it is.
- **The Quickstart** lost its one planner `TASK` beside placing a card ("zum
  Revolver »Punkte« wechseln", `TutorialSignal.PLANNER_REVOLVER`); four `TEXT`
  steps describe the box, Custom, breaks and the overlap question.

#### A note when the card is made

`NewTodoDialog`, `SpendDialog` and `BreakDialog` have a "Notiz" field
(`NoteField`), directly under the name. It is written to **`PlannedBlock.note`**
— these cards have one occurrence, and that is the field `BlockEditDialog` shows
for a one-off. Consequence, known: "Vom Tag nehmen" deletes the block and the
note with it. Quick-Add stays name-only.

**Verified by the compiler, the suite (643), `assembleRelease` and `lint`.**
Nobody has seen any of it.
