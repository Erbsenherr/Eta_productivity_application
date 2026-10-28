---
paths:
  - "app/src/**/ui/planner/**"
  - "app/src/**/domain/planning/PlanUndo.kt"
  - "app/src/**/ui/quickadd/**"
  - "app/src/**/ui/concretize/**"
  - "app/src/**/ui/lists/ListDialogs.kt"
  - "app/src/**/ui/rules/**"
  - "app/src/**/Rule*.kt"
  - "app/src/**/ui/dashboard/DashboardBoxes.kt"
  - "app/src/**/ui/reevaluation/**"
  - "app/src/**/alarm/EtaSound.kt"
---

### Step 53: Rückgängig, a move onto occupied time, one Quick-Add, and the Regelwerk

Seven items from `update.txt`; no questions were put, the decisions that were
mine are marked. Database **version 36**: the table `rules`,
`user_setup.rulebook` (0).

#### "Rückgängig" in the day planner

`PlanSnapshot` / `planRestore` (`domain/planning/PlanUndo.kt`, pinned in
`Step53Test`), driven by `DayPlannerViewModel.undoable` / `undo`.

- **By rows, not by action** (mine): before a change the view model takes the
  day's blocks — every state — and every card a change can reach (the ones
  behind those blocks, the week list, the Sammelliste). Undo writes back what
  differs: the blocks the change laid down go first (the one-a-day index),
  cards before their blocks, and a card **made** after the snapshot
  (`createdAt >= takenAt`) is deleted. Nine actions reach the day; an undo per
  action would be nine that drift.
- **Undoable**: every placement and its three answers, a move and the new
  move answers, "Vom Tag nehmen" and the drop on the revolver, "Absagen",
  "Ganzen Tag absagen", "In die Woche kopieren", the edit dialog. A change that
  changed nothing (`sameRowsAs`) pushes nothing.
- **Not undoable, and they end the stack** (`forgetUndo`): steps saved from the
  block, a group (`fold`), a night changed or reset. Their rows (subtasks,
  overrides) are outside the snapshot, and an older snapshot written over them
  would undo half.
- **In memory, `PLAN_UNDO_DEPTH` (20) deep**, one `Mutex` so no snapshot is
  taken in the middle of another change. Leaving the planner and coming back
  is a new sitting. The dashboard's long-press editor uses the same view model
  instance, so its edits are on the same stack.
- **The button** is "↶ Rückgängig" in `PlannerFooter`, in the caption's place —
  two buttons at most — and not on a confirmed "Liste für Morgen". Undone,
  the banner says so (`PlacementFeedback.Undone`).
- **Known limit**: folded ToDos of a new card made in the planner come back
  only if they were in the week list or the Sammelliste — which is where
  `foldable` takes them from.

#### A move onto occupied time asks

**This overrules "a block moved onto occupied time slides"** (`day-planner.md`)
and step 41's "not changed, deliberately: moving a block". `moveAnyway` — also
the "Nur verschieben" answer of "Gruppieren?" — writes where the block was
dropped if that is free, and otherwise sets a `MovePrompt` → `MoveDialog`
("Platz belegt"):

- **"Nächster freier Platz"** — `firstFreeStart` from the drop, the old slide.
  Grey, with its reason, when the day has none.
- **"Überschreiben"** — `planRelief` for the block's span, the block itself
  ignored (`makeRoomFor(…, ignoreId)`); the lines say what happens to each
  block in the way (`ReliefLines`, shared with `OverlapDialog`). A calendar
  import stays.
- **"Zurück in die Wochenliste"** for a hand-placed card (`returnToWeek`; a
  break is deleted as ever); for a flexible standing occurrence the button is
  "Abbrechen" and it stays where it stood. Tapping beside the window leaves the
  block in place.

#### A routine's steps, shown on a tap

A tap on the name of a routine with steps — in the "Gerade" box (both pages)
and on its row in "Heute anstehend" — opens `RoutineDetailDialog`
(`DashboardBoxes.kt`): every step in order, its checkbox, its time or count,
when it was ticked. Ticks go through `setSubtaskChecked`, so they can be set
or taken back afterwards — **a Timer-Task step included**, which the box itself
only ends by its timer. On that row the tap no longer unfolds the name (the
dialog's title carries it); on every other row it still does.

#### A routine's rest in the evening

For a task in Routine-Modus the remainder card no longer offers "Reste
übernehmen": **"Erledigt"** ticks the open steps on that day
(`completeRemainder`), **"Verfallen"** leaves them open. Other groups keep
"Reste übernehmen" / "Lassen". A remainder answered "Lassen" or "Verfallen"
stays answered (`dismissedRemainders`); it used to come back with the next tick.

#### One Quick-Add, and the switch in the form

- **`QuickAddPanel` is one page** — no pager, no dots, no swipe. Every note is
  a ToDo (`addQuickRecurring` and `AddedRecurring` are gone;
  `Item.newQuickRecurring` stays for the tests and old rows).
- **`KindSwitch`** ("Einmalig · Wiederkehrend", `ItemAttributes.kt`, the pill of
  "Aktuell · Generell"), and **`Item.asKind`**: a standing task loses priority
  and unlock day, a ToDo its rule, hour and "bis"; name, note and the
  one-month clock stay.
- **On the evening's cards it is written at once** (`ConcretizeViewModel
  .switchKind`) — a card has no "Abbrechen" and the kind decides its form.
  **In `ItemEditDialog` it is the form's**, for a card in the Sammelliste, and
  is written with "Sichern" (`saveTodo` / `saveRecurring` call `asKind`).
- **The Quickstart** notes both names in the one box (`TutorialFacts
  .notesTaken`, counted and remembered), and its first evening step is a `TASK`
  "Einmalig oder wiederkehrend": the switch, gated by
  `TutorialGate.CONCRETIZE_KIND`. `TutorialSpot.QUICK_ADD_PAGES` is gone.

#### Notifications replace one another

`postNotification` cancels Eta's earlier notifications before posting
(`clearEarlierNotifications`), so the lock screen holds the latest. Spared: an
**ongoing** one (the wake alarm) and anything posted within
`SAME_MOMENT_MILLIS` (10 s) — two tasks starting on one minute both stay
(mine). A planning reminder can be replaced by a task's; its five-minute
retries bring it back.

#### The Regelwerk

An Advanced Feature, **off for everyone** (`UserSetup.rulebook`,
`Features.rulebook`, `EtaTab.Rules` "Regelwerk" with `EtaIcon.RULEBOOK`, after
Streaks). `Rule` (`rules`: `question`, `answer`, `position`), `RuleDao`,
`RuleRepository`, `ui/rules/`. A tap folds the answer out and back
(`rememberSaveable` per rule), a long press opens `RuleEditDialog` (Frage,
Regel, "Löschen" → `ConfirmDialog`), "Regel hinzufügen" writes one last in the
list. Nothing in the app reads a rule. Cleared by the debug reset; off in the
tutorial.

**Verified by the compiler, the suite (754; 7 in `Step53Test`),
`assembleRelease`, `lint` and the migration against `36.json`.** Nobody has
seen any of it, and whether a launcher honours cancelling notifications from a
receiver can only be seen on the phone.
