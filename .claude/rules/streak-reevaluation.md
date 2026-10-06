---
paths:
  - "app/src/**/domain/streak/**"
  - "app/src/**/ui/reevaluation/**"
  - "app/src/**/CatchUpService.kt"
  - "app/src/**/PlanningPhaseService.kt"
---

### The streak, and what keeps a day honest

Three layers hold the daily phase together: **the alarm** rings every five minutes
until the phase is done, **`PhaseBox`** on the dashboard says what is still owed so
a dismissed notification does not leave the day untracked, and **the streak**
(`domain/streak/Streak.kt`) counts consecutive settled days.

`streakOf` measures from **yesterday** while today is still unsettled: a day being
lived has not been skipped, and a streak that read zero every morning would say
nothing. Miss an evening and the run is gone; there is no partial credit, and that
consequence is the mechanic rather than a side effect of one.

**"Done" means both halves.** `PlanningPhaseService.isCompleted(DAILY)` requires
today to be *settled* and tomorrow *confirmed* — `DayPlan` carries `settledAt`
beside `confirmedAt` for exactly this. Reading one as the other let a day be planned
ahead at lunchtime and then never settled: the alarm went quiet and the harvest
vanished without a word.

**Catch-up is not a convenience.** `CatchUpService` resolves a past day only after
the phrase `TECHNISCHE SCHWIERIGKEITEN` is typed out in full, only within 30 days,
and it pays the day's harvest but **never contract points** — a contract is
attested to on the day, and saying a week later that it was kept is a different act
this service cannot witness. The journal keeps a note, so a made-up day is never
indistinguishable from a lived one. In the settings tab the list of open days stays
folded until the phrase is typed: the count and the warning have to be visible, but
thirty un-actionable rows should not push the rest of the screen away.

### Reevaluation: every block answered before moving on

The first step of the Tagesabschluss gates both the "Weiter" button and the pager
swipe until no block is `isOpen`. An unanswered block is neither harvest nor
forfeit and costs the points either way, and the streak rests on that settlement. A
day with nothing planned leaves the gate open; going back is never blocked. A group
whose steps are not all ticked offers to carry the rest over — see *Step 23*.

