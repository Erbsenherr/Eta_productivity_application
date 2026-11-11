---
paths:
  - "app/src/**/domain/stats/Stopwatch.kt"
  - "app/src/**/domain/model/StopwatchTime.kt"
  - "app/src/**/data/local/StopwatchDao.kt"
  - "app/src/**/data/repository/StopwatchRepository.kt"
  - "app/src/**/ui/dashboard/StopwatchBox.kt"
  - "app/src/**/ui/dashboard/DashboardBoxes.kt"
  - "app/src/**/ui/stats/**"
---

### Step 56: the stopwatch, and the times it took

The third step of the `update.txt` begun at step 54. Nothing asked beyond the
first round; the decisions stated there are the design: **it measures and does
nothing else** — no points, no billing, no growth — and it is offered on any
task in "Gerade", not only Timer-Tasks. Database **version 39**: the table
`stopwatch_times`.

#### The model

`StopwatchTime` (`stopwatch_times`): `itemId`, `blockId` (plain columns, no
foreign keys — a time outlives its block), `name` (as it was), `date`,
`worked`, `runningSince`, `endedAt`. **The timer's shape**: `worked` is what was
counted at the last pause, `runningSince` when it was last set going, so
`elapsed(now)` is right however long the app was closed. `endedAt` null is a
stopwatch still being taken; `ended` keeps whole seconds.
`domain/stats/Stopwatch.kt`, pinned in `Step56Test`. `StopwatchRepository`;
cleared by the debug reset (`clearStopwatch`).

#### In the "Gerade" box

- **"⏱"** in the box's header, on the "Gerade" page, for the task running now
  — unless that block already has a stopwatch going. `NowBox(stopwatch =
  StopwatchActions)`.
- **The first tap explains** (`StopwatchExplainDialog`), once: remembered as
  the `Fold` "hint.stopwatch" in `SharedPreferences` — how this phone has been
  shown things, not the user's data. Then `StopwatchStartDialog`: **"Ab
  jetzt"** or **"Ab Beginn laut Plan · HH:MM"** (the block's `start`, the task
  rather than its journey; grey while it is still ahead).
- **`StopwatchLines` under the pages** shows every stopwatch still being taken,
  whatever is running now — one started on a task that has since ended is not
  lost from sight. Pause / Weiter, Beenden. Ticking the task off does not end
  it.
- **Only where the times can be read**: off with the Statistiken feature, and
  not in the tutorial's sandbox (the factory hands no repository).

#### In Statistiken

A fourth card, **"Gestoppte Zeiten"** (`StatSection.STOPWATCH`, so the
settings can hide it; tint `accent`). `stopwatchSummaries`: finished times
only, grouped by normalized name, **alphabetical**, called by the name last
measured under. A tile per task — "n-mal gestoppt" — unfolds to Durchschnitt,
Kürzeste, Längste and "Alle Zeiten" (newest first); a tap on one opens
`StopwatchEditDialog`: the time to the second, "Sichern", "Löschen" (asked).
**The times are observed**, not read once per visit like the rest of the tab,
because they are edited there — `StatisticsViewModel` now writes this one
thing.

**Verified by the compiler, the suite (774; 4 in `Step56Test`),
`assembleRelease`, `lint` and the migration against `39.json`.** Nobody has
seen any of it.
