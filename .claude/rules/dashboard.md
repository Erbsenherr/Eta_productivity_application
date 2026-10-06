---
paths:
  - "app/src/**/ui/dashboard/**"
  - "app/src/**/domain/planning/PriorityGate*.kt"
  - "app/src/**/domain/planning/NowAndNext*.kt"
  - "app/src/**/domain/planning/Conflicts*.kt"
  - "app/src/**/domain/planning/PhaseStatus*.kt"
---

### Notes, priorities and the "now" box

**Notes** are two nullable columns, one per table, and the definition/occurrence
split carries them for free: `Item.note` follows every occurrence,
`PlannedBlock.note` belongs to one date. `notesInOrder()` returns the **occurrence
note first** — it is the one that says something about today. In the edit dialogs the
note sits directly under the name; the block dialog offers both fields for a
recurring task and only the occurrence one for a one-off, and "Notiz für alle
Termine" stays down with the standing configuration. **Still open:** the creation
forms have no note field, Quick-Add being name-only by design.

**Priority gating** lives in `domain/planning/PriorityGate.kt`: the revolver offers
one tier at a time, highest first, and `Priority`'s declaration order is the ranking.
The trap it had to answer: a tier that *cannot* be placed — nothing left in the day
fits it — would hold the revolver shut and hide everything below. Hence `skipped`, a
manual wave-past offered as a button rather than applied automatically, since the
whole point of the rule is that the important things come first. Items without a
priority sort last, which is what a Quick-Add is until someone says how much it
matters.

**The "now" box** reads `nowAndNext`, which only considers **open** blocks: a task
whose hour has not run out but which is already ticked off is done, and saying "you
are doing this now" would be wrong. It sits directly under box 0 — it answers "what
now", the first thing the screen is asked — and its two pages swipe. A long press on
either opens `PomodoroDialog`.

### The Heute tab

Top to bottom: box 0 ("Kritisch"), the "now" box, the points box, the deadlines box
when there is one, Quick-Add, "Heute anstehend", and `PhaseBox` — at the top from the
minute the phase is owed, at the bottom before that. The Überschneidung box goes
above everything when it has something to say.

- **"Kritisch" means about to be banned onto the Sperrliste**, not about to miss a
  deadline. `Konzept.md` attaches that sentence to the one-month rule with an arrow,
  and box 3 already covers deadlines — reading it as deadlines would make the two
  boxes redundant. See `isCriticalInCollection`.
- **`PhaseBox` insists only once the phase is owed.** `DailyPhaseStatus.owedFrom` is
  the planning time *on today's date* — not `dueAt`, which is the next planning
  strictly after now and has already moved to tomorrow by the evening it matters.
  `isOwed(now)` takes the dashboard's ticking clock, so the box lights up at the
  minute itself rather than on the next database change. Before that it is a
  countdown in plain colours with secondary buttons, standing *below* everything
  about the day being lived; from the minute itself it is a demand at the top. One
  call site, two places, rather than two boxes that could drift apart. A null
  `owedFrom` reads as owed — too loud beats silently never.
- **`DeadlinesBox` is drawn only while there is a deadline**, between the account and
  Quick-Add. It used to sit at the foot of the screen saying "Keine offenen
  Deadlines" every day; a deadline is the one thing here with a clock of its own,
  worth seeing before the day is planned around it, and an empty box is a permanent
  reminder that there is nothing to remind anyone of. Name, then date and hour, since
  a countdown alone does not say *when*.
- **Small corrections happen in "Heute anstehend" without leaving the screen** — a
  duration, or a start time whose "07:00 · 1 h" caption opens
  `EtaTimePickerDialog`. `setStart` runs the same `canPlace` / `firstFreeStart` pair
  the planner uses, so the dashboard cannot become a back door around the one rule
  the planner enforces. The button below the list is for what needs the planner:
  calling something off, moving it by more than a nudge, adding what was not
  foreseen. That is `AppFlow.PlannerToday` — a flow, not a tab.
- **Confirming what is running asks what to do with the time.** The "Gerade" box
  has a button of its own, and a tick in "Heute anstehend" goes the same way:
  *Plan beibehalten · Task vorziehen · Pause einfügen, dann vorziehen* — but only
  where the tick is about now rather than about the past. See *Step 23*.
- **A cancelled row is listed struck through, dimmed and captioned**, here and in the
  evening reevaluation — the two places that account for the day. It is not drawn in
  the planner at all, its hours having left the plan. Long-pressing offers **"Doch
  einplanen"**, which **refuses rather than slides** when the hours have since been
  given to something else: sliding is what a *drop* does, where the user is choosing
  a time and the plan may help, while un-cancelling is a claim about a slot that was
  already yours, and if it is gone the honest answer is to say so.
- **The Überschneidung box** reports overlapping blocks — all of them, whatever
  caused them, because "two things at the same time" is one problem to the person
  having it. **Today, and tomorrow only once its plan is confirmed**: before that
  tomorrow is still being planned, and reporting a collision in a plan the user is in
  the middle of making would be reporting their own work back to them. Read off the
  blocks each time rather than stored, so moving one of the two makes the warning go
  away by itself. Long press a row for **Ignorieren** or **Lösen**, which opens the
  planner of that day — the one place a block can be moved, shortened or called off.
  Ignorieren lasts **for that day alone**, and nothing enforces that rule:
  `conflict_dismissals` is keyed by the two **block ids**, and one item has one block
  per day, so the same two tasks colliding tomorrow are a different pair and ask
  again. Persisted rather than held in the screen, because a warning that came back
  on every process death is one that cannot be answered.

