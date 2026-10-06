---
paths:
  - "app/src/**/ui/dashboard/FollowUpDialog.kt"
  - "app/src/**/ui/dashboard/DashboardViewModel.kt"
  - "app/src/**/*FollowUp*.kt"
---

### Step 27: what a task finished early is billed at

Ticking a task off **while it is still running** used to bill its whole planned
length without a word. The dialog that opens after the tick — the follow-up
question from *Step 23* — now asks first: **Voll abrechnen** or **Nur die gebrauchte
Zeit**.

**"Voll" is capped at twice the time used**, the user's own rule: finishing early is
not to be punished, but an hour's task done in twenty minutes is billed **forty**,
and the last twenty lapse. Done in forty, it is billed the whole hour. Without the
cap, ticking everything off in its first minute would pay the same as doing it.
`EarlyBilling` and `earlyBilling(block, minuteOfDay)` in `domain/planning/FollowUp.kt`
hold the arithmetic, `FollowUpTest` pins it.

- **Early means: begun, and not yet at its planned end.** Before the block's start
  there is no time used to measure, and from its end on there is nothing to give
  away — both answer null and the tick bills as it always did. Only on **today**;
  a tick on another day is being caught up.
- **Measured against `effectiveDuration`**, so a length the user corrected by hand
  beforehand is what "the whole task" means.
- **No new column.** The answer is written to `actualDuration`, which `yieldOf`
  already bills by, so the evening's harvest, "geplant" and the settlement all
  follow without knowing the question exists.
- **The generous answer is written with the tick**, before the dialog appears.
  Dismissing it — tapping outside, or any of the three follow-up buttons — must not
  be able to leave either the uncapped plan or nothing at all. Choosing the other
  option writes at once, for the same reason.
- **`FollowUpQuestion` has two optional halves** now, `next` and `billing`. The last
  task of the day can be finished early with nothing to pull forward, so the dialog
  shows whichever half exists and a plain "Fertig" when there is no next task.
- **Taking the tick back clears `actualDuration`** (dashboard only). The billed
  length belongs to the tick; one left behind would bill a task that then runs its
  full course at the shortened figure. The price: a duration corrected by hand in
  "Heute anstehend" goes with it and has to be set again.

**Not covered:** "Erledigt" on the Listen tab (`completeEarly`) for a card that
already stands on today ticks the block off where it stands and does **not** ask —
it bills the planned length. The evening reevaluation does not ask either: by then
no tick is early.
