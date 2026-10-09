---
paths:
  - "app/src/**/ui/weekplanner/**"
  - "app/src/**/WeekPlanning*.kt"
---

### The weekly planning phase

`ui/weekplanner/` runs the three parts `Planungsphase.md` names, with the maths in
`domain/planning/WeekPlanning.kt` and `WeekPlanningService` doing the writing.

- **The week is the seven days after the planning day**, not a calendar week. The
  user chooses which weekday they plan on, so the plan covers the seven days that
  follow whenever that happens.
- **The devaluation reads "already applied" off the ledger**, not off a stored flag:
  the ledger is the record of what happened, and a second source of truth could
  disagree with it. Reopening the screen therefore cannot charge twice.
- **Free hours come from the blocks, not from the setup answers.** The two agree for
  a fresh setup, but a recurring task created afterwards only shows up in the blocks
  — a week computing its budget from `weeklySpans()` would be lying. Sleep comes out first. A weekly
  "social" lump used to come out as well; the question was dropped in step 38 and
  `weekBudget` no longer reads the stored figure.
- **`costOf` is what a ToDo really costs the week**: its duration plus the break it
  earns. `breakFor` reads the rule as two tiers — an hour earns 15 minutes, an hour
  and a half or more earns 25. **Assumption:** "pro 1.5 Stunden" could also mean 25
  minutes for *every* 1.5 hours, which would make long tasks far more expensive. The
  tier reading is the conservative one; say so if it is wrong.
- Overbooking is shown as "überbucht" rather than as negative time, and `freeMinutes`
  never goes below zero.
- **A week with no free time in it says so**, and so does a day in the planner before
  it is confirmed. Neither blocks: having none is allowed and the forfeit is even
  paid for, but it should be a decision rather than an oversight. Both read
  `ItemRole.FREE_TIME`, so they notice whatever the user did to their schedule.

This phase also owns the **Sperrliste sweep**. `sweepStaleCollectionItems` runs when
the screen opens: the ban is a one-month rule, so weekly is timely enough and a
daily pass would be noise. It had no caller before this.

**Mid-week top-up.** `WeekScope` distinguishes the scheduled phase (the week
starting tomorrow) from a top-up (the seven days from today). They differ only in
the range, but the range is what the free hours are counted over, so mixing them
would show hours belonging to a different week. **A top-up runs only the planning
step** — the retrospective belongs to the scheduled phase alone, since adding a ToDo
on a Wednesday is not the week turning over. `stepsFor` enforces that.

**Both the phase and the top-up can be walked out of**, and the Sammelliste can be
added to from inside them: a fresh setup has an empty Sammelliste, so the phase
would otherwise open on two empty columns with nothing to do but accept the
devaluation. Goals added here are created **already concretized**, since the
revolver only offers finished cards. The entry points sit where the absence is felt
— an **empty revolver** in the day planner, and the Wochenliste card on the Listen
tab. The old route was a secondary button behind a swipe on the dashboard, which is
why it looked as though the week could only be planned on the planning day. The
Sammelliste column here filters to `ItemType.TODO`: a bare recurring note is not a
week goal, it belongs to the standing schedule.

**The devaluation hangs on a weekday, not on a visit.** `isInflationDue` compares
the last `INFLATION` row against the most recent occurrence of the chosen weekday,
and `WeekPlanningService.applyInflation()` runs on **app start**, next to
`ScheduleMaintenance.topUp()`. Hanging it on finishing the weekly planning made it
optional: never opening the phase meant never paying. The ledger is the only record
of whether it ran, so it cannot run twice for one week whatever route reaches it —
and a due week with nothing saved writes a **zero row**, or the account would stay
"never applied" and be charged the moment it went positive. The day is
`UserSetup.inflationDay`, `null` meaning "follow the weekly planning day", since
planning on Sunday evening and devaluing on Monday morning is reasonable to want.
Because it has almost always been booked by the time anyone looks,
`inflationPreview()` reports the *last booked* figure backwards (pre-loss balance
and what came off it) rather than a forecast.

### The week list, and taking on new goals

**New goals only once the last cycle's are worked off.** `Item.weekStartedOn` stamps
a ToDo with the planning cycle it was taken on in, because the week list itself
cannot answer the question — an item leaves `Stage.WEEK` the moment it is planned
into a day, long before it is done. Goals stamped for *this* cycle never block, so a
week can still be filled in one sitting; leftovers from an earlier one do. Handing an
item back to the Sammelliste clears the stamp: giving up a commitment is allowed,
quietly carrying it forever is not.

**The gate counts `Stage.WEEK` only, not `Stage.DAY`.** What is left to work off is
what is still *lying there*: a goal planned into a day has been dealt with as far as
this question goes, and the day it sits on is what holds it now. Counting Stage.DAY
too meant a week could be planned out completely — revolver empty, nothing left to
place — and still refuse anything new, the opposite of the rule's intent. An emptied
revolver now always means the week can be filled again.

