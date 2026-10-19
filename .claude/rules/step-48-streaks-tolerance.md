---
paths:
  - "app/src/**/domain/streak/**"
  - "app/src/**/ui/streaks/**"
  - "app/src/**/ui/reevaluation/**"
  - "app/src/**/HabitStreak.kt"
  - "app/src/**/StreakRepository.kt"
  - "app/src/**/StreakDao.kt"
  - "app/src/**/ReevaluationService.kt"
  - "app/src/**/domain/reevaluation/DailySettlement.kt"
  - "app/src/**/domain/reward/SleepBonus.kt"
  - "app/src/**/domain/planning/SleepCheck.kt"
  - "app/src/**/ui/settings/TabOrderBox.kt"
  - "app/src/**/ui/theme/AppDesign.kt"
  - "app/src/**/ui/dashboard/ConflictBox.kt"
---

### Step 48: Streaks, what counts as on time, a breach that costs, and a menu of one's own order

Three bugs, five changes and two features from `update.txt`. Database **version
33**: the table `streaks`, `user_setup.streaks` (0) / `punctualTolerance` (5),
`sleep_logs.tolerance` (nullable). Fifteen questions went to the user first; the
answers are the rules below, and what was mine is marked.

#### Three bugs

- **Breaks leave the Erfolgsliste** — `ItemRole.BREAK` filtered in
  `observeCompletedWithItems`, so the ones already ticked off leave too.
- **"Guten Morgen!" ends with the morning routine.** `sleepPrompts(…,
  morningDone)`: the dashboard passes true once today's `ItemRole.MORNING`
  block is completed or every one of its steps ticked. A morning still
  unanswered moves **at once** into "Aufstehzeit – heute" (asked).
- **Space above "Gruppe erledigt"** in the "Gerade" box when steps stand above it.

#### Five changes

- **The sleep bonuses can be bound in the Belohn-o-mat** — as **two entries**
  (asked), `SleepBonus.WAKE` / `BED` (`domain/reward/SleepBonus.kt`): fixed ids
  `sleep:wake` / `sleep:bed` in `reward_tasks.itemId` (a plain column, so no row
  stands behind them) and a name a binding matches like a standing task's.
  **Unbound they flow like any earning** (asked) — they did not count at all
  before. `sleepEarnings` turns the settlement's two figures into `Earning`s;
  `RewardRepository.outlook` / `pour` take them as `extra`. `harvestLate` passes
  none.
- **Holding "Überschneidungen" ignores all of them**, after a `ConfirmDialog`
  (asked) — only with two or more; the caption says so.
- **"Läuft bald ab" is red by night**: `AlertRedOnDark` for that box alone. The
  dark palettes' `danger` stays a rose — changing the token would repaint every
  locked contract slot (mine).
- **What counts as on time** is `UserSetup.punctualTolerance`, minutes, **for
  getting up and going to bed alike** (asked), on the "Schlaf und Morgen" page
  (`PunctualToleranceBox`, 0–120, a warning past
  `RECOMMENDED_MAX_PUNCTUAL_TOLERANCE` = 15). A morning stores the tolerance it
  was answered under (`SleepLog.tolerance`, null meaning the old five). For the
  evening it keeps "Gehe nun schlafen" on offer that long past the bedtime, and
  holds "Schlafenszeit – Vortag" back as long; `SleepPrompts.tolerance` is what
  the boxes print.
- **Breaking a contract costs 10 points** (`CONTRACT_BREACH_PENALTY`,
  `PointsReason.CONTRACT_BREACH`): **once**, when a slot-holder (`ACTIVE`) is
  answered "gebrochen", whether it is then let go or served on probation;
  **not** for a legacy contract (asked), and not for a probation not kept again.
  `DailySettlement.breachPenalty`. The answer says the price, and so do the
  signing guarantee and the contracts tutorial.

#### "Menü umsortieren"

`DesignChoice.tabOrder` (names, in `DesignStore` as `tabOrder`, comma-separated).
**Every tab moves, the dashboard and the settings too** (asked); the app still
opens on the dashboard and back still leads there.

- `orderTabs(defaults, stored)` is the order the bar uses: a name the stored
  order does not know — a tab added by a later update — goes in **right after
  its predecessor** in the app's order, not behind the settings; a stored name
  that is gone is dropped.
- The dialog (`TabOrderBox`, on the **Design** page as asked, below the design
  card) lists only the **visible** tabs; `reorderVisible` writes them back into
  the full order with each hidden tab keeping its slot. Drag after a long press,
  the gesture of the subtask builder.

#### Streaks

An Advanced Feature, **off for everyone** (`UserSetup.streaks`, `Features.streaks`,
`EtaTab.Streaks` with `EtaIcon.FLAME`, after the Notiz-Merger). **Off, nothing is
counted, paid or broken** — unlike the other switches, which only hide (mine;
the user asked it of the series).

`HabitStreak` (`streaks`; named apart from `domain.streak.Streak`, the dashboard's
run): `kind` — `TASK` (a standing task by **normalized name**, the Belohn-o-mat's
identity, since a task is one row per weekday), `SLEEP`, `SERIES` —, `length`
(**0 = begun and waiting** for its first completion: "Schließe die Task ab …"),
`lastCountedOn` (one evening counts once), `endedAt`.

- **The evening decides, nothing else** — `ReevaluationService.previewStreaks` /
  `settle` → `StreakRepository.apply`. `streakOutcomes` in
  `domain/streak/HabitStreaks.kt` is the whole rule; `StreaksAndToleranceTest`
  pins it.
  - **A task streak is due when an occurrence stood in the plan** (`taskDoneOn`).
    Excused as höhere Gewalt or **called off before its day** counts as not due;
    called off on the day itself breaks it (asked). A holiday lays no block.
    Only standing tasks; a "Nachholen von …" ToDo has another name.
  - **The sleep streak** holds when this morning **and** last night were on time
    — the pair the settlement books. An unanswered end breaks it (asked).
  - **A waiting streak cannot break.** Not due: it stands still.
- **The series** is the Tagesabschluss-Serie as a streak, one row, never ended,
  never asked about. `seriesOutcome`: its length is `streakOf` over the settled
  days with tonight's included; **it broke** if that run no longer reaches back
  to `lastCountedOn`. A day **made up with TECHNISCHE SCHWIERIGKEITEN before**
  the next evening closes the gap (asked); afterwards it changes nothing. A row
  never counted takes the run as it stands, and **switching the feature on
  clears `lastCountedOn`** (`SettingsViewModel.setFeature` →
  `resumeSeries`), so a gap from while it was off is not charged (mine).
- **What it pays**: `STREAK_POINTS_PER_DAY` (0.001) × the new length, **per
  evening it is extended** (asked: "0,001 × Länge pro Abend"), at most
  `STREAK_MAX_PER_STREAK` (1) each and `STREAK_MAX_TOTAL` (3) together, cut in
  proportion beyond. Breaking costs `STREAK_BREAK_PENALTY` (10), the series
  included. `PointsReason.STREAK` / `STREAK_BROKEN`, one row per streak with its
  label; `DailySettlement.streakPayout` / `streakPenalty`. **Not** an earning for
  the Belohn-o-mat, like contracts (mine). `formatFinePoints` prints thousandths.
- **The evening's page** (`Step.STREAKS`, `StreaksStep.kt`) stands **before the
  contracts** while the feature is on. Each streak has its turn,
  `STREAK_TURN_MILLIS` apart, nothing to press: one longer counts up with a
  sound and a pulse; one gone out goes grey, and **then** asks "Neu beginnen" /
  "Verfallen lassen" — unanswered is let go. Restarted, it stays at 0, waiting.
  The sounds are **placeholders** (`StreakSounds`: `notification`,
  `pom_pause_start`).
- **The tab** (`ui/streaks/`): the series first, then the rest; the flame shows
  today's completion already (`shownLength`) — the evening only confirms it.
  "Streak beginnen" offers the sleep streak, the standing schedule and a new
  task (`RecurringCreateDialog`, begun at once). **Unlimited, one per task**
  (asked). **Holding a streak ends it — and a streak that has begun counting
  is broken by that and costs 10** (mine: otherwise ending one on the afternoon
  it would break is the way round every break). A waiting one goes free.
- **A tutorial** (`TutorialId.STREAKS`, `TutorialStage.STREAKS`, asked), the
  shape of the Belohn-o-mat's: one `TASK`, begin a streak (`TutorialFacts.streaks`).
- **Catch-up does not touch streaks** (`settle(evening = false)`), like contracts.

**Verified by the compiler, the suite (714; 20 in `StreaksAndToleranceTest`),
`assembleRelease`, `lint` and the migration against `33.json`.** Nobody has seen
any of it, and nobody has heard the streak sounds.
