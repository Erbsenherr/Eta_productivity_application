---
paths:
  - "app/src/**/ui/weekplanner/**"
  - "app/src/**/domain/stats/WeekReview.kt"
  - "app/src/**/domain/reward/Balancing.kt"
  - "app/src/**/ui/settings/SettingsScreen.kt"
  - "app/src/**/EtaApplication.kt"
---

### Step 58: the Wochenrückblick, and the Punkte-Balancing

The last step of the `update.txt` begun at step 54. Nothing asked beyond the
first round; the decisions stated there are the design. Database **version
41**: `user_setup.balancing` (`TEXT NOT NULL DEFAULT ''`).

#### The Wochenrückblick

- **The first page of the scheduled weekly phase** (`Step.REVIEW`), never of a
  top-up, and not in the tutorial's practice week (the factory hands no
  `ReviewSources`; `hasReview` is false).
- **Last week is the seven days before the week planned** — up to the planning
  day itself, of which only what is over counts (`isOver`). **Set against every
  day before it** — the user's own usual, not an ideal.
- `weekReview` (`domain/stats/WeekReview.kt`) reuses `overview` for four
  figures — Aufgaben abgeschlossen, Pünktlich aufgestanden, Pünktlich schlafen
  gegangen, Zeit verplant — and adds **"Planmäßig erledigt"** (`accuracy`: the
  tasks taken on that were finished, not changed beforehand and, for a
  Timer-Task, worked in full — `taskStats`' strict reading, over every task).
  Above them the number of tasks done.
- **`Trend`**: better or worse by `REVIEW_MARGIN_POINTS` (5) percentage points,
  otherwise "wie sonst"; unknown where either side counted nothing. A first week
  says it has nothing to compare with; an empty one says so instead of dashes.
- Read once when the phase opens (`WeekPlannerViewModel.review`), through
  `ReviewSources` — four functions, so the view model learned no repository.

#### The Punkte-Balancing

**Debug → "Balancing" → "Punkte-Balancing"**: `SettingsPage.BALANCING` and
`POINT_BALANCING`, not in the menu, reached by `SubPageRow`s; `parent` is
where "‹ …" and the system back go.

- **`BalanceKey`** (`domain/reward/Balancing.kt`) lists every tunable value
  with its default — the constant it used to be: the three category
  multipliers, a calendar hour, waking and going to bed on time (now apart:
  `PUNCTUAL_WAKE_POINTS` / `PUNCTUAL_BED_POINTS`), giving up free time, the
  free unplanned hours, the legacy cap, a contract breach, a streak breaking,
  a streak's daily rate and both caps, the weekly devaluation, and step 55's
  malus and recovery. **The two that were settings already** — what an hour
  called off costs and what an unplanned hour costs — are on the same page,
  writing their own `UserSetup` columns.
- **The constants became getters** (`val X: Double get() = Balancing[KEY]`,
  `Category.yieldMultiplier` likewise), so every call site stayed as it was.
  **`Balancing.values`** is one global, because the arithmetic is pure and
  reached from a dozen places with no setup to hand; unset, every key is its
  default — which is what the tests and a fresh install see. A test that sets
  it resets it (`Step58Test`'s `@After`).
- **Stored on the setup row** (`balancing`, `encodeBalancing`: only what
  differs from its default), so it travels with a backup and goes with the
  debug reset. Part of the draft: saved with "Sichern". **`EtaApplication`
  follows the row** for the life of the process and sets `Balancing.values`
  on every change — the tutorial's practice container included, which runs on
  the user's values.
- The three texts that said "30 %" or "10 %" read the live share
  (`percentOf`).

**Known limits.** A changed value applies from the next figure on; nothing
past is booked again. Other texts that name a figure ("zwei halbe Punkte",
"kostet 10 Punkte") still say the default.

**Verified by the compiler, the suite (783; 5 in `Step58Test`),
`assembleRelease`, `lint` and the schema against `41.json`.** Nobody has seen
any of it.
