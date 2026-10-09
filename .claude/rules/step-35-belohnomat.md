---
paths:
  - "app/src/**/ui/rewards/**"
  - "app/src/**/*Reward*.kt"
  - "app/src/**/ui/components/PointsVisibility.kt"
  - "app/src/**/ui/reevaluation/**"
---

### Step 35: the Belohn-o-mat, a points system that can be put out of sight, and three long presses

Three items from `update.txt`. Database **version 24**. Eight questions went to the
user first; the answers are the rules below.

#### The Belohn-o-mat

A tab of its own (`ui/rewards/`, between Growth-Tasks and Einstellungen): long-term
rewards with a name and a price in points, worked off from the top.

- **A counter beside the account, not a withdrawal from it.** The points that fill
  a reward are the same ones the evening credits to the balance; filling and
  redeeming book nothing. Hence `rewards` is its own table and not a
  `PointsReason`.
- **`Reward.progress` is a column**, not a sum over anything: points poured into a
  reward stay with it whatever the order does afterwards, and a number on the row
  is the only shape in which that is true by construction. `reorder` writes
  positions and nothing else.
- **Only position 1 is worked on** — the first reward that is neither full nor
  redeemed. `pourInto` in `domain/reward/RewardFill.kt` is the whole rule and
  `RewardFillTest` pins it. What position 1 does not accept goes **nowhere**; it
  does not run on to the reward below.
- **Unless position 1 is earned in full.** Then what is left of the points that
  filled it runs on into the next reward, under *that* reward's binding. The
  surplus is taken from every contributing task **in proportion**: points have no
  order within one evening, and a rule that depended on row order would pay the
  same day differently twice. A full reward waits to be redeemed and is no longer
  position 1.
- **What counts** (`earningsOf`): the yield of every **completed block**, each on
  its own and **before anything is taken off** — ToDos, standing tasks, growth
  tasks, calendar appointments, Custom Earn. Not contracts, not free time given
  up, not a correction by hand; and not a Custom Spend, whose yield is negative —
  points spent, not earned — so it takes nothing back out either.
- **`ReevaluationService` is the only caller of `RewardRepository.pour`**, in
  `settle` and in `harvestLate`: a reward fills exactly when `HARVEST` is written
  and from the same blocks, so a day settled once fills once. A caught-up day
  (`CatchUpService`) goes through `settle` and fills too.

**Aufgabenbindung.** A reward bound to standing tasks counts only those; unbound,
it counts everything.

- **Stored as row ids, read as names.** `reward_tasks.itemId` is a plain column
  with no foreign key. A standing task is one row per weekday and editing its
  weekdays adds and retires rows, so `RewardRepository.boundNames` resolves the ids
  to **normalized names** — the Sperrliste's identity — and both the evening
  (`RewardTarget.accepts`) and the tab (`isBoundBy`) match by name. A retired row
  is still found by id and still names its task.
- **A binding to tasks that no longer exist counts as none.** Names are kept only
  while a live standing definition carries them; a reward bound solely to ended
  tasks would otherwise never fill again.
- **Only a standing task matches**: a one-off ToDo that happens to share the name
  does not (`Earning.standing`).
- The editor writes the binding back as the rows the tasks have **now**
  (`RewardDraft.of`), which is also what brings an old binding up to date.
- The binding menu can make a standing task (`RecurringCreateDialog`); it comes
  back ticked.

**The tab.** The card *is* the bar: what has been earned washes in from the left
(`accentSoft`, `successSoft` once full), the way a held cancellation fills its row,
with "12.5 / 25" and the percentage always shown.

- **Tap edits, ≡ drags, long press deletes.** The drag starts on the handle at once
  rather than after a long press on the row, because the long press already means
  "löschen". `reorderHandle` swallows the press so a thumb resting on the handle is
  not a long press on the row. Keyed by id alone — keying the detector on the drag
  lambda would cancel the gesture at every swap, a drag recomposing the list.
- **The editor is three windows that take turns** (`EditorStage`: reward, binding
  menu, new task), one draft carried through, never two open at once.
- **Deleting discards what was earned**, and the confirmation says how much.
  Redeeming moves a full reward into the folded "Eingelöst" list with its date.
- Lowering a price below what was earned cuts the progress to the new price.

**The evening.** `Step.REWARD_FILL` in `ReevaluationScreen`, after the settlement
page and only while a reward is being filled (`RewardOutlook.head`). It is a
**preview**, like the settlement before it — "Abschließen" is what books — but
drawn as the thing happening: `RewardBar` runs from where the reward stands to
where tonight puts it each time the page comes to the front, the count running
with it, one bar per reward when tonight overflows. Recomputed with the preview,
so a tick taken back on the first page takes its points back out of the bar.

**The two sounds are placeholders**, as asked: `RewardSounds.FILL` is
`notification.mp3` and `RewardSounds.FULL` is `pom_work_start.mp3`, played through
`EtaSound` (alarm stream, silent under Do-Not-Disturb). Swapping the real ones in
is copying them into `res/raw` and naming them in that object.

#### The points system can be put out of sight

`UserSetup.pointsSystem`, a checkbox in the settings — since step 36 the
"Punktetracker" row of `AdvancedFeaturesBox`, applied at once and off for a new
setup (*Step 36*). **It stops nothing**: harvest, contracts, charges and the
weekly devaluation are booked exactly as before, which is what lets it be switched
back on with the account where it would have stood anyway.

- **`LocalPointsVisible`** (`ui/components/PointsVisibility.kt`), provided in
  `EtaApp` around `MainScaffold`. A composition local rather than a field on every
  state: the switch hides figures in a dozen places that share nothing else.
  **Anything new that shows a figure in points has to read it.**
- **What goes**: `PointsBox` and with it the correction by hand (its long press);
  the settlement page of the evening and the devaluation page of the weekly
  planning (both still booked); "Prognose" in the planner; what a contract pays, on
  the tab, in both dialogs and in the evening's question; the points sentence of
  "Erledigt?"; the billing question of the follow-up dialog (the block keeps its
  default); the `RateField`; the Inflation and Absagen cards in the settings.
- **Custom Earn and Custom Spend leave the revolver**; Social, being neutral,
  stays, and the revolver is then called "Spontan". What already stands on a day
  stays and is booked.
- **Contracts stay**, as asked: listed, signed, asked about every evening — only
  their figures go.
- **The Belohn-o-mat does not read the switch.** Tab, evening page and numbers
  stay, the one place points are still seen. Confirmed with the user.
- **Known limits:** the questionnaire runs before there is a setup and still
  mentions points; a few sentences that name points without a figure were left
  ("Punkte-Einträge", the Subtask-Builder's "keine Punkte").

#### Holding a list down makes something for it

On the Listen tab the **heading** of three boxes takes a long press, and the open
box says so at its foot (`longPressHint`):

- **Sammelliste** → `TodoCreateDialog`, a finished ToDo in the Sammelliste.
- **Wochenliste** → the same, taken straight into the week.
- **Wiederkehrende Aufgaben** → `RecurringCreateDialog`, laid down at once through
  `RecurringTaskService.createGroup`.

Neither ToDo path is a way round a rule. The **Sperrliste** can veto the name as it
can everywhere, and a card for the week goes through `takeIntoWeek` under the
weekly planning's own **gate**: while goals of an earlier cycle are still lying in
the week list the new card is kept, in the Sammelliste, and `SmartListsViewModel.notice`
says why. The tab is still read-only about *moving* cards; this makes new ones.

**`ListCard` has one gesture detector, not two.** It used to be a `clickable` with
a long-press `detectTapGestures` stacked on it, for "Liste für Morgen": the inner
detector consumes the press and the outer `clickable` then has nothing to click
on, so that heading very likely could not be folded by a tap. Tap and long press
are told apart in one `detectTapGestures` now, callbacks through
`rememberUpdatedState`.

**Verified by the compiler (`--rerun-tasks`), the suite (510, 16 new in
`RewardFillTest`), `assembleRelease`, `lint`, and the migration's SQL against
`24.json`.** Nobody has seen any of it, and nobody has heard either sound.
