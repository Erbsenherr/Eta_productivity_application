---
paths:
  - "app/src/**/domain/growth/**"
  - "app/src/**/data/repository/GrowthService.kt"
  - "app/src/**/ui/dashboard/TimerControls.kt"
  - "app/src/**/ui/dashboard/DashboardScreen.kt"
  - "app/src/**/ui/dashboard/DashboardViewModel.kt"
  - "app/src/**/ui/reevaluation/**"
  - "app/src/**/*Timer*.kt"
---

### Step 55: "Vorzeitig beenden", and the time it costs

The second step of the `update.txt` that began at step 54. Two questions were
put and are the design. Database **version 38**:
`planned_blocks.endedEarly` (`INTEGER NOT NULL DEFAULT 0`) and
`items.timerMalusBase` (seconds, nullable).

#### The answers that are rules now

- **−10 % of the time as it stands, +2 % of the time before the malus** per
  sitting worked in full, up to that time: 60 → 54 → 48:36; 54 → 55:12 → 56:24 …
  → 60. (The note's example said 55:20; 2 % of 60 min is 1:12, and the user
  chose that.)
- **A Timer-Task ticked without its timer** asks one window with three answers
  — "Volle Zeit gemacht" (counts in full, the increment included: the workout
  tracked elsewhere), "Vorzeitig beendet" (a time put in, and the malus),
  "Abbrechen".

#### The arithmetic

`domain/growth/TimerTask.kt`, pinned in `Step55Test`:

- **`withEarlyEndMalus`** — the time on the timer (`timerDuration`, the
  buffer stays behind it) goes down by `EARLY_END_MALUS` of itself, to the
  second, never under `MIN_GROWTH_DURATION`; `timerMalusBase` keeps the time
  before the **first** malus.
- **`recoveredFromMalus`** — `MALUS_RECOVERY` of the base back, capped at it;
  reaching it clears the base. A time already at or past the base (the form
  changed it since) ends the malus without moving.
- **`advancedBySitting(endedEarly, workedInFull, lenient)`** is what
  `GrowthService.advanceFor` now calls instead of `advancedByCompletion`:
  ended early → no growth, the malus; under a malus → no growth, recovery if
  worked in full (**strictly** — Flexibilität's `allowPartialTimerGrowth`
  does not make a short sitting recover); otherwise as ever. **The increment
  waits for a sitting that begins without a malus** — the sitting that
  recovers the last of it does not also grow. A Mengen-Inkrement counts
  either way.
- **Booked with the evening**, like growing: `advanceFor` takes every
  Timer-Task now, not only growing or counted ones, writes `timerMalusBase`
  across the family, and relays the open occurrences ahead. Today's and a
  confirmed tomorrow's blocks keep their length — the "next instance" is the
  next one laid down. **Only a confirmed "Vorzeitig beenden" costs**: a sitting
  short for any other reason ("Am Blockende aufhören", a step's timer) is not
  worked in full and nothing more, as before.
- `endedEarly` is the block's, set where the early end is confirmed;
  **`PlanRepository.reopen` clears it**, so a tick taken back takes it back.

#### On the screen

- **`TimerControls`**: with `TimerActions.onEndEarly` the second button is
  **"Vorzeitig beenden"** until the time is counted down, then "Ende". This
  overrules step 42's "Ende behind a long press" for a task; **a routine's step
  keeps it** (its `TimerActions` has no `onEndEarly`) and carries no malus.
- **`EarlyEndDialog`** — counted against the goal, and `MalusNote`: the task's
  own minutes (54 → …, +1:12 per full sitting, "Bis dahin wächst die Zielzeit
  nicht" for a growing one). From the button **and** from a tick in "Heute
  anstehend" on a timer started and short of its goal; `endTimer(id, early =
  true)` — not early if the time ran out while the question stood.
- **`TimerTickDialog`** (three answers) on the dashboard for a timer never
  started, and **in the evening for one never started or started and left
  short** (`counted`); it used to bill the latter silently.
- The "Ziele" tab says "Zeit-Malus: zurück auf … mit vollen Sitzungen" under
  a task that carries one. The Growth tutorial's text says it too.

**Known limits.** The form's "Dauer" shows the shortened time with nothing to
say why; changing it there to the old length or more ends the malus at the
next sitting. The malus is not adjustable (Balancing, step 58, is where it
would be).

**Verified by the compiler, the suite (770; 7 in `Step55Test`),
`assembleRelease`, `lint` and the schema against `38.json`.** Nobody has seen
any of it.
