---
paths:
  - "app/src/**/ui/planner/**"
  - "app/src/**/ui/reevaluation/**"
  - "app/src/**/domain/reevaluation/**"
  - "app/src/**/ReevaluationService.kt"
---

## Rounds of real use

### Step 10: replanning the day you are in

Seven items, one subject: reaching the plan *while the day is running* rather than only
the evening before. The planner on today, an editable start time in "Heute anstehend",
the swipe to a recurring quick-add, a precise drag, the copy into the week list, the
home-screen widget, and the charge for a planned task not done — each is documented
where it belongs (*The day planner*, *The Heute tab*, *Quick-Add*, *Contracts*). Two
things that live here:

**Today is always editable.** The confirmation locks the screen for **tomorrow** only:
today's `DayPlan` was confirmed last night, and honouring that would make the whole
feature take a long press first. Today's footer therefore says "Fertig" rather than
"Bestätigen" — re-confirming a day being lived is not a thing, and writing
`confirmedAt` again would be a lie about when it was planned.

**Cancelling is discarding, not deleting.** "Vom Tag nehmen" removes the block, which
is right for a ToDo the user dragged in: it goes back to the week list and the
recurrence that never produced it will not produce it again. For a **recurring**
occurrence it is exactly wrong, since expansion would recreate it on the next top-up.
So the dialog offers **"Absagen"**, which sets `discardedAt`: the row stays, expansion
leaves it alone, and the evening never asks about it again. It deliberately does not
offer "Nachholen von …" — deciding in advance not to do something is not the same as
dropping it. Step 13 changed two things here: "Absagen" is offered for **every** block,
since a ToDo can be called off as well as handed back and the two mean different
things, and a cancelled block no longer holds its slot.

### Step 11: the first round of use on a real phone

The three bugs, the two readability gaps and the features are all documented with
their subjects: the stale capture under *Compose traps*, tap-to-read under *The day
planner* and *The Listen tab*, contract editing under *Contracts*, manual points under
*Points*, `task_end.mp3` under *Sounds*, and the margins under *A block that brings its
own margins*. What is only here:

- **Rotation threw the screen away.** `MainScaffold` held `tab` and `flow` in a plain
  `remember`, mid-planning-phase included. The other half was `MainActivity`: it reads
  the alarm's `EXTRA_OPEN_PHASE` / `EXTRA_DEFER_PHASE` out of the intent in `onCreate`,
  and a recreated activity re-reads the *same* intent, so a rotation re-opened the
  phase or re-showed `DeferDialog`. The extras are removed from the intent once read,
  which is what makes them the one-shot they were always meant to be.
- **"Absagen" looked like it did nothing.** It did exactly what it says, but the
  planner drew a discarded block identically to an open one, so the only feedback was
  the dialog closing. The model was right; nothing said so. Since step 13 a cancelled
  block is not drawn in the planner at all and the strike-through lives in the two
  lists that account for the day.
- **The Listen tab stopped being strictly read-only**, deliberately — see *The Listen
  tab*: read-only about *placement* still holds, but not about the attributes of a
  definition.

### Step 12: one form, one gesture, one stale capture

Two bugs and five conveniences, with one theme: the same question was being asked by
five screens, each with its own idea of which fields exist. The shared form is under
*One attribute form*, the auto-scroll under *The day planner*, and the stale capture —
which this time *deleted data*, wiping a block's margins on the next drag — under
*Compose traps*. Also here:

- **A recurring note is concretized where it is edited.** A bare recurring quick-add in
  the Sammelliste used to open the ToDo form, which cannot make it complete. It opens
  `RecurringAttributeFields` now, and "Sichern" runs `concretizeRecurring` — the same
  call the evening step makes. Nothing has to be cleared up afterwards: a definition
  without a rule lays down no block, so there are no stale occurrences from before the
  edit. An already-concretized recurring definition never appears in this tab, being in
  `Stage.DAY`, and if it ever does it would need `clearUpcoming` first.
- **"Offene Tage" keeps its list folded** until the phrase is typed — see *The streak*.
- **The list popup lost its "Schließen" button**: tapping outside already closes it.

### Step 13: the return journey, and what a cancellation costs

The Rückweg and "Als Nächstes" are under *A block that brings its own margins*, the
drop onto the revolver under *The day planner*. The pricing, confirmed with the user in
four parts:

**A cancellation leaves the plan.** A called-off block used to keep its slot, on the
grounds that the time was spent either way. That is wrong for a cancellation — the
whole point of calling something off is that its hours come back — so a discarded block
is taken out of the day entirely: not drawn in the planner, blocking nothing, its hours
free to be filled. It is still *listed* in the evening reevaluation and in "Heute
anstehend", marked as cancelled, because those are where the day is read.

**It costs its hours.** `CANCELLATION` is a `PointsReason`: one point per hour called
off, times `UserSetup.cancellationPenaltyPerHour`, which the settings tab offers and
which defaults to **1.0**. A quarter of an hour costs 0.25, two hours cost 2.

- **Only on the day itself** — see *Contracts*. That is what makes the calendar's
  conflict dialog free to use, and why `BlockEditDialog` says which of the two it is:
  "Absagen" reads the same either way, and only one of them is a consequence.
- **Booked in the evening, with the day**, as its own line in `DailySettlement`.
  Cancelling on Tuesday for Friday therefore costs on Friday, the day that came up
  short.
- **Both routes count**: "Absagen" in the planner, days ahead, and "Fällt aus" in the
  evening. They reach the same state and would otherwise differ only in price, which
  would make waiting until the evening the cheaper way to give up.
- **Any kind of card.** Calling a **ToDo** off also sends it back to the Sammelliste
  through `returnedToCollection` — the same consequence the evening's "Fällt aus" has,
  and necessary here for a duller reason: without it the item would sit in `Stage.DAY`
  with no block to show it and appear in no list at all. `returnedToCollection` rather
  than `moveTo`, so the one-month clock does not restart.
- **Free time is exempt**, and that is not an oversight: giving up a `FREE_TIME` block
  is already priced at four points an hour in the user's favour, and charging it again
  would quietly turn a payout of 4 into a payout of 3. It therefore keeps holding its
  slot as well, exactly as `heldItsTime` always had it.

**The freed time is ordinary time.** The charge for unplanned hours is untouched: fill
the freed slot with something that happens and it never bites, leave it empty and it
does. Both charges are settled at the end of the day, by which point "was the time
used" has an answer. One consequence a reader will look for: `droppedHours` no longer
counts a cancellation — those hours left the plan, they are the cancellation's own
line, and whatever of them stayed empty shows up under unplanned time. The two numbers
no longer overlap.

**"Höhere Gewalt" gives them back.** In the reevaluation's first step a cancelled row
is **held down** rather than tapped: a bar fills the row, and when it has covered it
the dialog asks for a reason. A hold because this is the one thing on that screen that
undoes a consequence, and the consequence is the mechanic — the same reasoning as the
typed-out phrase in `CatchUpService`, one scale down. `PlannedBlock.forceMajeure` holds
the reason, and being non-null is what excuses the block. Because it is stored rather
than held in the screen, the settlement recomputes without that block however often
the reevaluation is left and re-entered, and the reason survives as a record of why a
day reads the way it does. Nothing is refunded, because nothing was charged yet: the
excuse happens before the booking, and the line simply is not there.

### Step 14: four small ones

No schema change. `PhaseBox`'s `owedFrom` is under *The Heute tab*, `key(item.id)`
under *Compose traps*, the note's position under *Notes*, and the Custom Earn / Spend
forecast under *Points* — the data there was always right and `yieldOf` always billed
it; what was missing was any figure that moved when a rate was chosen, since "heute
erarbeitet" counts ticked-off blocks only.

