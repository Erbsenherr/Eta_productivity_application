# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

ERIK is a productivity app: a smart to-do list / week planner with a reward-points
system. `ERIK_doc/` holds the German concept documents and is the **source of truth
for behaviour** — `Konzept.md` is the root, the rest hang off it. Read the relevant
doc before implementing a feature; the point values, timeouts and phase mechanics
are all specified there and are easy to get subtly wrong.

`ERIK_doc/Paint Draft Dayplanner.png` is the layout sketch for the day planner.

## Build

The Gradle wrapper needs a JDK. `JAVA_HOME` is not set globally on this machine —
Android Studio's bundled JDK works:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

- Build: `.\gradlew.bat assembleDebug`
- Unit tests: `.\gradlew.bat testDebugUnitTest`
- Single test class: `.\gradlew.bat testDebugUnitTest --tests "*.YieldTest"`
- Lint: `.\gradlew.bat lint`

Test results land in `app/build/test-results/testDebugUnitTest/*.xml`; the Gradle
console output does not print the pass count.

## Toolchain notes that will bite you

- **AGP 9 uses built-in Kotlin.** There is no `kotlin-android` plugin in the build.
  It rejects the `kotlin.sourceSets` DSL, which KSP still uses, so
  `android.disallowKotlinSourceSets=false` is set in `gradle.properties`. Remove it
  once KSP moves to `android.sourceSets`.
- **Room 3 (`androidx.room3`), not Room 2.** The API differs from every Room tutorial:
  - `@ColumnTypeConverter` / `@ColumnTypeConverters` — *not* `@TypeConverter`.
  - `@Relation(parentColumns = ["x"], entityColumns = ["y"])` takes **arrays**.
  - A wrong Room 3 import surfaces as the useless
    `[MissingType]: Element '...' references a type that is not present`.
    Temporarily removing `@ColumnTypeConverters` makes Room emit the real error.
  - Inspect the real API rather than guessing:
    `javap -cp <room3-common jar> androidx.room3.Relation`.
- **Computed properties on entities need `@get:Ignore`**, or Room treats them as
  columns and fails with "must have a usable public constructor".
- `kotlin.time.Instant` is still experimental in Kotlin 2.2.10; the project opts in
  globally via `freeCompilerArgs` in `app/build.gradle.kts`.
- **Incremental compilation hides errors in files it does not revisit.** A broken
  `OnSetupChange` usage compiled green three times in a row and only surfaced on
  `assembleRelease`, which compiles from scratch. When a change touches a shared
  type, check it with `compileDebugKotlin --rerun-tasks` or a release build before
  believing the green.
- **Do not declare a callback as `(T.() -> R) -> Unit`** where the rest of the
  code uses the plain `(T) -> R` shape. One such parameter in `SettingsScreen`
  made every `onChange { it.copy(…) }` in `SetupSteps.kt` fail with the
  unhelpful "Unresolved reference 'it'", pointing at the innocent file.
- **Core library desugaring is required**, not optional: `kotlinx-datetime` resolves
  to `java.time`, which `minSdk 24` does not have. Without it the app compiles fine
  and crashes on Android 7/8 at runtime.

## Architecture

### Model: definition vs. occurrence

The central split, and the thing to understand first:

- **`Item`** is the *definition* of a card — ToDo, Deadline, Recurring or Spend.
  It says what a thing is, never when it happens.
- **`PlannedBlock`** is one *occurrence* of an item on one day, and is what the
  user actually checks off. A weekly recurring item produces one block per week,
  each with its own completion and its own yield. Without this split, recurring
  tasks cannot work, since one row cannot hold many completions.
- **`DayPlan`** holds only the confirmation state of a day.

`BlockOrigin` on a block (`RECURRING` / `DRAGGED` / `CALENDAR_IMPORT`) drives the
day-planner visuals the draft calls for: square corners and immovable for recurring
and imported blocks, rounded and draggable for ToDos.

**`ScheduleMaintenance.topUp` keeps the schedule laid down ahead of today**, and
something has to call it. It runs on every app start and at the top of both
planning phases. Before it existed, only the setup materialized anything — a
fortnight — so two weeks after answering the questionnaire the planner, the
free-hour maths and the "now" box all went empty with no error to explain it.
That is the failure mode to watch for here: expansion is cheap and idempotent, so
call it more often rather than less.

`domain/recurrence/RecurrenceExpansion.kt` turns definitions into blocks.
`expandRecurring` is **idempotent** — it takes the already-materialized item/date
pairs and skips them, so running it on every planning phase never duplicates a
block. A definition missing its start time or duration is skipped, never guessed at.

Note what that idempotence does *not* buy: a block the user deleted is no longer
among the materialized pairs, so expanding a range that still covers its date
creates it again. In practice ranges run forward from today, which is why a
deleted past occurrence stays deleted — but deleting a *future* block is not a way
to suppress an occurrence. Anything that needs to suppress one durably has to be
consulted by expansion itself; see *Vacation mode* below.

`RecurrenceRule.Daily` is the one rule without a weekday. It exists because the
setup questionnaire's daily routines would otherwise need seven definitions each.

### Closing a day

A block has three states, not two: completed (`completedAt`), consciously dropped
(`discardedAt`), and still open. The third field matters — without it the evening
reevaluation would keep re-asking about occurrences the user already dismissed.

`DayClosingService` settles a day. Dropping a block applies `consequenceOf(item)`:

- **ToDo** → back to `Stage.COLLECTION`, and `enteredCollectionAt` is *kept*, not
  reset. A ToDo repeatedly planned and dropped is precisely what the Sperrliste is
  for; resetting the clock would make the ban evadable by planning something once a
  month and abandoning it.
- **Recurring** → the caller asks whether to catch up. `makeUp()` then creates
  `Item.newMakeUpTodo`: named "Nachholen von …", priority `MUST` ("Muss geschehen"),
  category and duration inherited, **no target date** so it can be planned at once.
  It skips the Sperrliste check on purpose — it follows from a commitment the user
  still holds. Idempotent via `PlannedBlock.makeUpItemId`.
- **Deadline / Spend** → nothing further.

A make-up ToDo is an ordinary ToDo from there on, so completing it shows up in the
Erfolgsliste with no special handling.

A null `targetDate` means "available immediately" — use `Item.isAvailableOn(date)`
rather than comparing against `availableFrom` directly.

### Erfolgsliste comes from blocks, not from `Stage.DONE`

A recurring item is never retired, so it can never reach `Stage.DONE` — checking
one off would otherwise delete it forever. The Erfolgsliste is therefore the view
over *completed blocks* (`observeErfolgsliste`), which is also what gives it the
"Name, Datum und Uhrzeit" the docs ask for: name from the item, date and time from
the block. `Stage.DONE` still has a job — retiring one-shot ToDos so they leave the
active lists.

### The six lists are five stages

`Stage` has `COLLECTION`, `LOCKED`, `WEEK`, `DAY`, `DONE`. The sixth list from
`Konzept.md`, "Liste für Morgen", is **not** a stage — it is the view of tomorrow's
blocks when that `DayPlan` is confirmed. Long-pressing clears `confirmedAt`, which
is what reopens editing and brings the revolver back out.

### Points

The **balance is a ledger sum**, not a stored number: `PointsTransaction` rows are
summed by `PointsDao.observeBalance()`. Weekly inflation, contract payouts and
spends therefore stay auditable instead of overwriting one another.

Checking a task off on the dashboard does **not** credit points. Per
`Tägliche Reevaluation.md` the harvest happens in the evening phase, so the
dashboard shows the balance alongside today's *pending* yield, labelled as not yet
credited. `ReevaluationService` is the only thing that writes `HARVEST` rows.

`yieldOf(item, block)` in `domain/reward/Yield.kt` is the single place points are
computed. It keys off the block, not the item, and bills `actualDuration` when the
user corrected it, otherwise `plannedDuration`. Imported calendar events pay per
*full* hour and ignore the item's category.

Category multipliers: Fokus 1.0, Nebenbei 0.5, **Achtsam 1.0** (the docs have a
copy-paste error here; 1.0 is the confirmed value).

### `ItemRole`: what a block is *for*

`Item.role` is a nullable, closed enum saying what part an item plays in the day —
`FREE_TIME`, `BED_PREP`, `MORNING`, `BREAK`, `WORK`, `MEAL`, `HOUSEKEEPING`,
`SPORT`, `MINDFULNESS`. The setup stamps it on everything it generates.

It exists because rules have to **recognise a kind of block**, and identity is the
wrong handle for that. Free time used to be found by the literal id
`setup:freetime`, which meant that retiring or replacing that one row silently
switched the forfeit payout off, and a free-time block the user made by hand was
never recognised at all. `Belohnungssystem.md` says "des *Freizeit* (o.ä.)
Punktes" — the "o.ä." was always describing a category.

Deliberately **not** free-form tags: this is the app's own vocabulary, needed so
its rules can fire. `Category` (Fokus/Nebenbei/Achtsam) is the classification the
user makes. `SETUP_ITEM_ID_PREFIX` is back to doing only what it was for —
idempotent regeneration.

**Known limit:** nothing lets the user *set* a role yet. A block they create by
hand carries none, so it counts as neither free time nor anything else. The block
edit dialog is where that belongs when it matters.

### The setup questionnaire

`domain/setup/` holds the whole of it as pure Kotlin, with the UI in `ui/setup/`:

- **`UserSetup`** is a single-row Room entity (`SETUP_ID = 0`) and doubles as the
  questionnaire's draft — `UserSetup.draft(now)` seeds it with plausible answers.
  Its nested answers (`MealPlan`, `HousekeepingPlan`, `MindfulnessPlan`,
  `WorkSchedule`, `WeeklySlot`, `DailySlot`) each `encode()` to one string column,
  the same trick `RecurrenceRule` uses. `SetupEncodingTest` guards the round trip:
  a broken one would silently corrupt the entire configuration on next launch.
- **`SetupSchedule.kt`** flattens the answers into `SetupSpan`s (minutes from
  midnight, per weekday) and derives `conflicts()` and `freeMinutesPerWeek()` from
  them. Anything crossing midnight is *split across two weekdays*, not clipped,
  or the small hours would vanish from the maths. Two spans of the same answer
  are not a conflict — a wrapped night is one appointment, not two.
- **`SetupItems.kt`** turns the answers into recurring definitions with
  **deterministic ids** (`setup:morning`, `setup:work-0-monday`, …). That is what
  makes re-running the questionnaire an update rather than a second schedule.
  `SetupRepository.complete` retires definitions that no longer follow from the
  answers via `completedAt` instead of deleting them: `planned_blocks` cascades on
  delete, so deleting would take past completions out of the Erfolgsliste. It
  does clear their **still-open future occurrences** (`PlanRepository.clearUpcoming`),
  or moving Sport from Monday to Tuesday would leave the old Monday blocks
  standing next to the new ones for as long as they were already laid down.
  Completed and dismissed blocks are left alone — they are what happened.

**A `WeeklySlot` holds a *set* of weekdays.** Sport twice a week is ordinary, and
a one-day answer made the user enter the same task twice. Each chosen day becomes
its own definition, with the day in the id (`setup:sport-tuesday`), so the
questionnaire keeps updating exactly its own rows.

**The break lives inside the work answer**, not in a question of its own. A
`WorkBlock` is a span plus an optional `pause`, and `segments()` cuts it into
work / break / work. That is what stops the lunch break from permanently
colliding with the working day — and it is the only shape that survives the
per-weekday case, where someone with a university timetable has several
stretches a day, each with its own gap. A break that does not lie inside its
block is **dropped, not clamped**, and the questionnaire says so while the user
is still looking at it. With `WorkSchedule.None` there is no break at all.

Whether a generated block pays points is decided by whether it carries a
category, and that is assigned by meaning: the frame of the day — Bettfertig
machen, Morgenzeit, Pause, Freizeit — has `category = null` and so yields
nothing, while Hausputz, Sport, Kochen and Achtsamkeit carry theirs. Sleep
produces no item at all; it is configuration the planner shades (`colors.sleep`)
and the free-hour maths subtracts.

The database is at **version 11**. `MIGRATION_1_2` adds the `user_setup` table,
`MIGRATION_2_3` recreates it empty when the lunch answer moved into the work one —
the old row could not be converted, since where the break sits inside the working
day simply is not in it — `MIGRATION_3_4` adds `contracts` and `journal_entries`,
`MIGRATION_4_5` adds `items.role`, `MIGRATION_5_6` the two `note` columns, and
`MIGRATION_6_7` the holiday tables, `MIGRATION_7_8` `day_plans.settledAt`,
`MIGRATION_8_9` the unique index on `planned_blocks(itemId, date)` — after
clearing the duplicates it would otherwise choke on — `MIGRATION_9_10`
`items.weekStartedOn` and `MIGRATION_10_11` `user_setup.inflationDay`. When changing an entity, diff the hand-written SQL against
the matching `app/schemas/…/N.json`: Room validates it at open time and a
mismatch is a runtime crash, not a compile error.

### Contracts and the evening settlement

`domain/contract/ContractRules.kt` and `domain/reevaluation/DailySettlement.kt`
hold the rules; `ReevaluationService` is the only thing that writes to the ledger.

- **Three slots, and a breach costs one for a month.** `slotStatuses` is built from
  *all* contracts, broken ones included — a breach keeps holding its slot, which is
  what stops the user signing a replacement the same evening. The lock runs a month
  from **signing**, not from breaking, exactly as `Selbstverträge.md` words it: a
  contract broken late in its term frees its slot sooner than one broken early.
- **Legacy contracts hold no slot, pay a fifth, and are capped at 2 points a day
  together.** The cap is hard and the surplus is forfeited; the crown appears when
  the *gross* would have exceeded it. That is the only reading under which both of
  the document's sentences are true, and the user confirmed it.
- **The settlement is previewed, then booked.** `settleDay` returns a value; the
  reevaluation shows the arithmetic and only the last step writes. Each component
  becomes its own `PointsTransaction` row rather than one net figure — the balance
  is a ledger, and a month later it still has to be readable.
- Unplanned time is charged only against **waking** hours: available minutes are
  the day minus the setup's night, and overlapping blocks count once.
- Free time is the mirror image: a **`ItemRole.FREE_TIME`** block that was
  *discarded* pays 4 points an hour, up to the daily free time the setup provides.
  Giving it up is a deliberate forfeit, not a failure — which is why it keys off
  `discardedAt`, not off being incomplete. The cap exists because 4 points an hour
  against the single point an hour of focused work would otherwise make *planning
  free time in order to skip it* the most lucrative thing in the app. Confirmed
  with the user.

Contract verdicts default to **kept**. The question is "did you hold it", and a
breach is the exception; defaulting the other way would punish a skipped screen.

### The "Smart toDos" tab

`ui/lists/` shows the six lists of `Konzept.md` stacked on one screen, and is
**deliberately read-only**. Every one of these lists is what a phase left behind —
the Sperrliste of the weekly sweep, the Wochenliste of the weekly planning, the
Tagesliste of the day planner — so an edit here would be a second route around the
rules those phases enforce. The way to move a card is to run the phase that moves
it. Keep it that way when adding to this screen.

It needs no new data: `observeStage`, `observeDay`, `observeDayPlan` and
`observeErfolgsliste` already carry everything. Sections collapse; the Sperrliste
and the Erfolgsliste start closed, being backlog and history rather than today.

The one gesture it does have is the concept's own: **long-pressing "Liste für
Morgen" opens the planner**, which is how that list is reopened for editing.

### The streak, and what keeps a day honest

Three things now hold the daily phase together, and they are deliberately layered:

1. **The alarm** rings every five minutes until the phase is done.
2. **`PhaseBox`** on the dashboard says what is still owed, so a dismissed
   notification does not leave the day untracked.
3. **The streak** — `domain/streak/Streak.kt` — counts consecutive settled days.

`streakOf` measures from **yesterday** while today is still unsettled: a day being
lived has not been skipped, and a streak that read zero every morning would say
nothing. Miss an evening and the run is gone; there is no partial credit, and that
consequence is the mechanic rather than a side effect of one.

**"Done" means both halves.** `PlanningPhaseService.isCompleted(DAILY)` requires
today to be *settled* and tomorrow *confirmed* — `DayPlan` carries `settledAt`
beside `confirmedAt` for exactly this. Reading one as the other let a day be
planned ahead at lunchtime and then never settled: the alarm went quiet and the
harvest vanished without a word. Planning ahead is encouraged, so this had to be
watertight.

**Catch-up is not a convenience.** `CatchUpService` resolves a past day only after
the phrase `TECHNISCHE SCHWIERIGKEITEN` is typed out in full, only within 30 days,
and it pays the day's harvest but **never contract points** — a contract is
attested to on the day, and saying a week later that it was kept is a different
act this service cannot witness. The journal keeps a note, so a made-up day is
never indistinguishable from a lived one.

### The planning alarm

`domain/planning/PlanningSchedule.kt` holds the arithmetic — when a phase is next
due, what a snooze and a "NOTFALL" do — and is the part worth testing. `alarm/`
holds the Android side.

- **`setAlarmClock`**, because this *is* an alarm the user set: it survives doze
  and shows in the status bar, so an alarm that will interrupt the evening is
  never a surprise. Android 12 gates exact alarms behind a permission the user can
  refuse, so `setWindow` is the fallback — late beats silent.
- **Every path reschedules**, including the ones where nothing rings. An alarm
  that decides to stay quiet and then forgets to set the next one has switched the
  whole mechanism off, and the failure would be invisible for days.
- **"Done" is read off what the phase leaves behind**, never a flag of its own:
  the daily phase ends by confirming tomorrow's `DayPlan`, the weekly one by
  booking the devaluation. See `PlanningPhaseService`.
- `nextPlanning` is **strict** about "after": firing at the appointed minute must
  not be able to schedule the same moment again and loop.
- The emergency deferral is **clamped** to `MAX_EMERGENCY_HOURS`. Pushing a phase
  a whole day back is not deferring it, it is skipping it. It opens
  `DeferDialog` rather than guessing, since the concept calls it a manual entry
  and a notification button cannot ask how many hours.
- Foreground is counted in `ErikApplication` by started activities — the alarm
  needs exactly the one fact that the user already has the app open.

Refusing the notification permission costs the reminder, not the loop: the alarm
still fires and still reschedules. Alarms do not survive a reboot, hence
`BootCompletedReceiver`.

### The weekly planning phase

`ui/weekplanner/` runs the three parts `Planungsphase.md` names, with the maths in
`domain/planning/WeekPlanning.kt` and `WeekPlanningService` doing the writing.

- **The week is the seven days after the planning day**, not a calendar week. The
  user chooses which weekday they plan on, so the plan should cover the seven days
  that follow whenever that happens to be.
- **The devaluation reads "already applied" off the ledger**, not off a stored
  flag: the ledger is the record of what happened, and a second source of truth
  could disagree with it. Reopening the screen therefore cannot charge twice.
- **Free hours come from the blocks, not from the setup answers.** The two agree
  for a fresh setup, but a recurring task created afterwards only shows up in the
  blocks — a week that computed its budget from `weeklySpans()` would be lying.
  Sleep and the social lump come out first; social time has no fixed hour, so it
  can only be reserved as a lump.
- **`costOf` is what a ToDo really costs the week**: its duration plus the break
  it earns. `breakFor` reads the rule as two tiers — an hour earns 15 minutes,
  an hour and a half or more earns 25. **Assumption:** "pro 1.5 Stunden" could
  also mean 25 minutes for *every* 1.5 hours, which would make long tasks far more
  expensive. The tier reading is the conservative one; say so if it is wrong.
- Overbooking is shown as "überbucht" rather than as negative time, and
  `freeMinutes` never goes below zero.
- **A week with no free time in it says so**, and so does a day in the planner
  before it is confirmed. Neither blocks: having none is allowed and the forfeit is
  even paid for — but it should be a decision, not an oversight. Both read
  `ItemRole.FREE_TIME`, so they notice whatever the user did to their schedule.

This phase also owns the **Sperrliste sweep**. `sweepStaleCollectionItems` runs
when the screen opens: the ban is a one-month rule, so weekly is timely enough and
a daily pass would only be noise. It had no caller before this.

### The day planner

`ui/planner/` builds the screen from `Paint Draft Dayplanner.png`: the revolver on
top, the day below, one gesture connecting them. `domain/planning/DayTimeline.kt`
holds everything worth testing without a screen — minute arithmetic, overlap,
snapping, and where a block can actually go.

- **A drop becomes a time.** `DayTimeline` reports its own position and how tall a
  minute is via `onGeometry`; the screen turns the finger's position into a minute
  from that. The report comes out of layout, so it stays right while the day
  scrolls under the finger — no scroll bookkeeping of our own.
- **A drop onto occupied time slides.** `firstFreeStart` finds the next place the
  block fits and the user is told where it went. Refusing would make a dense day
  nearly unplannable; overwriting would quietly destroy something already planned.
- **Turn versus pull.** Both gestures live on the centre chamber, so the *first*
  direction the finger takes decides which one it is, and that decision is held
  until the finger lifts. Anything else and a hesitant drag does both.
- Corners already carry meaning (`shapes.draggedBlock` vs `shapes.fixedBlock`);
  the planner is where that finally shows. Only `BlockOrigin.DRAGGED` blocks move.
- Long-press opens `BlockEditDialog`. Name and category are edited on the **item**
  and so change every future occurrence; start and duration are edited on the
  **block**. The dialog says which is which rather than hiding the split.
- Confirming writes the `DayPlan`; the screen then reads as "Liste für Morgen" and
  a long press anywhere reopens it.

The revolver holds `Stage.WEEK` items, which is what the weekly planning phase
fills. There is no shortcut into it from here any more — the stopgap picker that
used to live in the footer moved to where it belongs.

The daily flow now runs end to end in the order `Planungsphase.md` gives it:
`ui/reevaluation/` (phase 1) → `ui/concretize/` (phase 2) → the planner (3 and 4),
each handing on to the next. Phase 2 is where a Quick-Add note gets its category,
priority, target date and duration — without it a jotted line can never reach the
revolver, which only offers `isConcretized` cards.

### UI: Foundation only

There is no Material dependency. `ErikTheme` provides four token sets through
static CompositionLocals — `colors`, `typography`, `shapes`, `spacing` — read as
`ErikTheme.colors.accent` and so on, the same shape as `MaterialTheme`.

Base components in `ui/components/` stand in for the Material ones:
`ErikText` (on `BasicText`), `ErikTextField` (on `BasicTextField`), `ErikSurface`,
`ErikButton`, and `ErikScreen` in place of `Scaffold`. Input without Material
pickers is covered by `ErikStepper`, `ErikTimePicker`, `ErikDurationPicker`,
`ErikChoice`, `ErikWeekdayPicker` and the `ErikField` label wrapper; `ErikTabBar`
carries the four tabs. Build new UI from these rather than reaching for raw
`Box`/`BasicText` per screen.

`ErikTimePicker` is a hand-built clock dial in `androidx.compose.ui.window.Dialog`
(which is compose-ui, not Material): tap the field, pick the hour, then the minute,
the way Android's own picker works. It is one `Canvas` — numbers, hand and knob are
all drawn — so the hit testing and the drawing share one coordinate system. In hour
mode it carries two rings, 1–12 outside and 13–24 inside, with the ring chosen by
distance from the centre; minutes snap to five. Durations keep the `ErikStepper`,
since a length is not a point on a clock face.

Two tokens carry meaning, not taste: `shapes.draggedBlock` (rounded) versus
`shapes.fixedBlock` (square) is how the day planner shows whether a block was
placed by hand or came from a recurrence or the calendar. `colorOf(category)` maps
Fokus/Nebenbei/Achtsam so no screen hand-rolls that mapping.

Press feedback is a scale-and-fade driven by an `interactionSource` with
`indication = null`; ripple lives in the Material artifacts this project omits.

**Trap:** `androidx.compose.material:material` (Material 1, not M3) is still on the
*debug* classpath because `ui-tooling` needs it for the preview panel. An accidental
`import androidx.compose.material.*` therefore compiles in debug and only breaks in
release. Run `assembleRelease` before trusting a UI change. M3 is gone entirely, so
those imports fail immediately.

### Layers

`domain/` is pure Kotlin and holds the rules worth testing without a database.
`data/local/` has the Room DAOs, `data/repository/` the repositories, and
`di/AppContainer.kt` wires them manually — no DI framework, since the graph is
small and it keeps another annotation processor out of the build.

The model classes carry Room annotations directly rather than having separate
entity classes plus mappers. `Konzept.md` asks for the simplest thing that works,
and mappers would cost ~250 lines for no current benefit.

## Confirmed design decisions

These came out of discussion with the user and are not all derivable from `ERIK_doc`:

- **UI: Compose Foundations only, no Material3.** The revolver, drag-and-drop
  timeline and custom card shapes fight Material components. Done — see *UI:
  Foundation only* above.
- **Persistence:** local Room only, but sync-friendly — UUID string keys and
  `updatedAt` on every row so a sync layer can be added without a schema rewrite.
- **Google Calendar:** the API integration comes early (planning must account for
  existing appointments); the calendar *screen* comes last. Overlapping imported
  events count **once** toward free-time calculation (interval merge).
- **Sperrliste identity:** normalized name (trimmed, lowercased).
- **"Kritisch" (dashboard box 0) means about to be banned onto the Sperrliste**, not
  about to miss a deadline. `Konzept.md` attaches that sentence to the one-month
  rule with an arrow, and box 3 already covers deadlines — reading it as deadlines
  would make the two boxes redundant. See `isCriticalInCollection`.
- **Spend covers both** Custom Spend and Custom Earn via a signed `pointsPerHour`.
  The second revolver's third entry, "Social", is assumed neutral at 0 points per
  hour (`SOCIAL_POINTS_PER_HOUR`): the setup already budgets social time out of the
  free hours, so paying for it again would count it twice. `Belohnungssystem.md`
  gives no rate for it.
- **Setup questionnaire:** the framework blocks it lays down carry no category and
  therefore no yield; "Arbeit / Uni" is the exception and counts as Fokus **for
  now**. The user asked for recurring tasks — at minimum their category — to become
  editable from the planner, which is where that provisional choice gets corrected.
- The questionnaire asks for a **start time** wherever `Setup-Questionaire.md` only
  names a duration (Hausputz, Sport, Kochen). Without one, `expandRecurring` would
  skip the definition rather than guess.
- **The Mittagspause is not its own question** any more, contrary to
  `Setup-Questionaire.md`: it is part of each work block. Asked separately it
  collided with the working day every time, and the per-weekday timetable case
  needs a break per stretch anyway. Someone who answers "keine festen Zeiten" for
  work therefore gets no scheduled break.

## Build order

1. ~~Data model~~ · 2. ~~Room persistence + list logic~~ · 2b. ~~Foundations design
system~~ · 3. ~~Dashboard~~ · 4. ~~Setup questionnaire~~ · 5. ~~Revolver day
planner~~ · 6. ~~Contracts + rewards~~ · 6b. ~~Weekly planning phase~~ ·
6c. ~~Planning alarms + daily flow closed~~ · 6d. ~~"Smart toDos" tab~~ ·
8. ~~Settings tab~~ · 9. ~~Vacation mode~~ ·
7. Calendar UI *(parked last, at the user's request)*

The three smaller confirmed features — notes on cards, revolver priority gating and
the dashboard's "now" box — are done; see *Notes, priorities and the "now" box*.

**Next up:** step 7, the calendar. Everything else on the list is done — see *One
occurrence per item per day*, *The week list, and taking on new goals* and
*Reevaluation: every block answered before moving on*.
Step 7 is **parked at the user's request** and is the last thing after that. It
must land smoothly when it comes — several accounts, sub-calendars, foreign
calendars — so read *Google Calendar: what it will need* before starting it.

Every screen the concept names now exists except the calendar, and the tab bar is
in place. The calendar is the last item.

`ui/root/ErikApp.kt` decides which of these the app opens on: the questionnaire
until `user_setup` has a row, the dashboard afterwards. `RootDestination.Loading`
is a state of its own so the questionnaire does not flash on every cold start.
Past the setup, `MainScaffold` splits everything into **tabs** and **flows**, and
that split is the point:

- A **tab** is a place you can always get back to — Heute, Listen, Verträge,
  Einstellungen — so `ErikTabBar` stays put beneath it.
- A **flow** is something you are in the middle of: a planning phase, the
  concretizing step, the holiday editor. It covers the bar and leaves by finishing
  or by going back.

Mixing them is what made the earlier flat `AppScreen` enum awkward — it made the
evening reevaluation a peer of the dashboard, which it is not. Back leaves a flow
first, then returns to the first tab.

Tab screens pass `bottomInset = false` to `ErikScreen`: the bar already sits over
the navigation bar, and insetting twice would leave a gap above it. The bar carries
**labels, not icons** — a Sperrliste or a self-contract has no icon that would not
have to be invented and then explained.

Still no navigation library: two pieces of state, `tab` and `flow`, do the whole
job. Reach for one when deep links or a real back stack arrive, not before.

### The settings tab

`ui/settings/` holds the standing configuration, away from planning a day.

**The questionnaire's own step composables are reused**, not reimplemented: they
are already `(draft, onChange)` pairs, so every answer is asked for in exactly one
place and cannot drift between the two screens. Saving goes through
`SetupRepository.complete`, the same path the questionnaire uses — which
regenerates the standing schedule and retires what no longer follows. That is what
changing an answer *should* do, and it is why editing here needs no machinery of
its own. The draft stays null until the stored answers arrive, so there is never a
set of defaults on screen that the user might save by accident.

**Where the data lives.** `BackupService` writes the whole database to a file the
user picks and reads it back. The live database does **not** move, and the screen
says so: scoped storage hands out document URIs, and SQLite needs a path it can
lock, so an app cannot keep a working database in a folder of the user's choosing.
A setting that looked like it moved the database and did not would be worse than
none. An automatic backup to a remembered folder is the natural next step and
would need a persisted tree URI.

Two things the backup gets right that are easy to get wrong:

- All three SQLite files travel together in one zip. Copying only `erik.db` while
  write-ahead logging is on hands back a database missing the newest writes —
  precisely the ones the user just made.
- A restore only overwrites the files this app owns; a zip entry naming a path
  elsewhere is ignored. Afterwards the app must be **restarted**, because Room
  still holds the old files open — the screen says so rather than pretending the
  swap took effect.

### Notes, priorities and the "now" box

Three small confirmed features, built together:

**Notes** are two nullable columns, one per table, and the definition/occurrence
split carries them for free: `Item.note` follows every occurrence, `PlannedBlock.note`
belongs to one date. `notesInOrder()` returns the **occurrence note first** — it is
the one that says something about today. The block edit dialog offers both fields
for a recurring task and only the occurrence one for a one-off, since a one-off has
no other occurrences to speak for.

Still open: notes at *creation* time. Quick-Add is name-only by design, and the
attribute-filling step (phase 2 of `Planungsphase.md`) does not exist yet — that is
where the field belongs when it is built.

**Priority gating** lives in `domain/planning/PriorityGate.kt`: the revolver offers
one tier at a time, highest first, and `Priority`'s declaration order is the
ranking. The trap this had to answer: a tier that *cannot* be placed — nothing left
in the day fits it — would hold the revolver shut and hide everything below. Hence
`skipped`, a manual wave-past, offered as a button rather than applied
automatically; the whole point of the rule is that the important things come first,
so getting past one should take a deliberate tap. Items without a priority sort
last, which is what a Quick-Add is until someone says how much it matters.

**The "now" box** reads `nowAndNext`, which only considers **open** blocks: a task
whose hour has not run out but which is already ticked off is done, and saying "you
are doing this now" would be wrong. It sits directly under box 0 — it answers
"what now", the first thing the screen is asked — and its two pages swipe.

### One occurrence per item per day, enforced

`planned_blocks` carries a **unique index on (itemId, date)**, and materialization
inserts with `IGNORE` rather than upserting. This is not belt and braces — it is
the only thing that makes the invariant true.

The bug it fixes: `materializeRecurring` reads what exists, works out what is
missing, then writes. Two runs overlapping both read "nothing there" and both
wrote, and because the primary key is a random UUID, two rows for the same item on
the same day were perfectly legal. Finishing the questionnaire used to start three
materializations within milliseconds — the setup's own, plus two `LaunchedEffect`s
that both fired on landing on the dashboard — so every recurring task appeared
three times. When the writes happened to land first, it looked fine, which is why
it read as random.

The redundant effect is gone too, but that was tidying: on its own it would only
have made the race rarer, which is worse than leaving it visible.

### The week list, and taking on new goals

**New goals only once the last cycle's are worked off.** `Item.weekStartedOn`
stamps a ToDo with the planning cycle it was taken on in, because the week list
itself cannot answer the question — an item leaves `Stage.WEEK` the moment it is
planned into a day, long before it is done. Goals stamped for *this* cycle never
block, so a week can still be filled in one sitting; leftovers from an earlier one
do. Handing an item back to the Sammelliste clears the stamp: giving up a
commitment is allowed, quietly carrying it forever is not.

This forced a gap closed: **nothing ever set `Stage.DONE`**. It was documented as
the stage that retires one-shot ToDos, but completion lived only on the block.
`ItemRepository.retireCompletedTodos` now runs at settlement, which is the moment
a day is accounted for and therefore the right place to decide a ToDo is finished.

**Mid-week top-up.** `WeekScope` distinguishes the scheduled phase (the week
starting tomorrow) from a top-up (the seven days from today). They differ only in
the range, but the range is what the free hours are counted over, so mixing them
would quietly show hours belonging to a different week.

**A top-up runs only the planning step.** The retrospective belongs to the
scheduled phase and to it alone — adding a ToDo on a Wednesday is not the week
turning over. `stepsFor` in the screen is what enforces that.

**The devaluation hangs on a weekday, not on a visit.** `isInflationDue` compares
the last `INFLATION` row against the most recent occurrence of the chosen weekday,
and `WeekPlanningService.applyInflation()` runs on **app start**, next to
`ScheduleMaintenance.topUp()`. Hanging it on finishing the weekly planning made it
optional: never opening the phase meant never paying. The ledger is the only
record of whether it ran, so it cannot run twice for one week whatever route
reaches it — and a due week with nothing saved writes a **zero row**, or the
account would stay "never applied" and be charged the moment it went positive.
The day is `UserSetup.inflationDay`, `null` meaning "follow the weekly planning
day"; the settings tab offers it, since planning on Sunday evening and devaluing
on Monday morning is a reasonable thing to want. Because it has almost always
been booked by the time anyone looks, `inflationPreview()` reports the *last
booked* figure backwards (pre-loss balance and what came off it) rather than a
forecast.

**Both the phase and the top-up can be walked out of**, and the Sammelliste can be
added to from inside them. A fresh setup has an empty Sammelliste, so the phase
would otherwise open on two empty columns with nothing to do but accept the
devaluation. Goals added here are created **already concretized**: a bare note
pulled into the week would sit there unplannable, since the revolver only offers
finished cards. The entry points sit where
the absence is felt: an **empty revolver** in the day planner offers it, and so
does the Wochenliste card in the Smart-toDos tab. The old route — a secondary
button on the dashboard's tomorrow page, behind a swipe — was the reason it looked
as though the week could only be planned on the planning day.

### Reevaluation: every block answered before moving on

The first step of the Tagesabschluss gates both the "Weiter" button and the pager
swipe until no block is `isOpen`. An unanswered block is neither harvest nor
forfeit and costs the points either way, and the streak now rests on that
settlement. A day with nothing planned leaves the gate open; going back is never
blocked.
### Google Calendar: what it will need (planned)

Parked last at the user's request, but it must land **smoothly** when it does —
including **several accounts**, the **sub-calendars** within one account, and
**foreign calendars** (subscribed or shared by someone else), with the user
choosing which of them count. What the model already has, and what it does not:

Already there, and deliberately so:

- `BlockOrigin.CALENDAR_IMPORT` on `PlannedBlock`, with the day planner drawing
  those blocks square-cornered and immovable and `yieldOf` paying them per **full**
  hour, ignoring the item's category.
- Overlapping imported events count **once** toward free time: `plannedMinutes`
  and `occupiedMinutesOfWeek` merge per day already.

Missing, and worth adding *when the integration is written* rather than
speculatively now — the shape should follow the API that is actually used, and an
unused column that turns out to be the wrong shape is worse than none:

- **An external identity on the block**: calendar id plus event id, and the
  event's own `updatedAt`. Without it a re-sync cannot tell "this event again"
  from "a new event" and will duplicate — the same trap `existing` solves for
  recurrence expansion, and the same fix. It also has to survive an event being
  moved or deleted remotely, which needs the imported block to be *findable* by
  its remote key, not just by date.
- **A table of known calendars**: account name, calendar id, display name, colour,
  and whether it is switched on. Several accounts and foreign calendars are why
  this cannot be a single "sync on/off" flag — the user picks per calendar. It
  belongs in the settings tab next to the other standing configuration.
- **All-day events.** `Planungsphase.md` wants the planner to ask whether an
  all-day event really occupies the whole day, or whether start and end should be
  supplied, so that a morning routine is not silently swallowed. `PlannedBlock`
  has no way to say "all day" — it has a start and a duration — so this needs
  either a flag or the convention that all-day means the full 24 hours.

Two rules already settled that the integration must not quietly break: an imported
block is **never movable**, and the free-hour maths counts overlaps once.

Reading calendars needs either the Calendar Provider (`READ_CALENDAR`, covers
every calendar the phone already syncs, foreign ones included, and needs no OAuth)
or the Google Calendar REST API (OAuth client, Cloud project, only Google
accounts). **The provider is the better first step** for exactly the requirement
above: it already sees every sub-calendar and every subscribed foreign calendar
the device knows about.

### Vacation mode

`ui/vacation/` enters a date range; `domain/vacation/` decides what it means.
Per recurring task: **Aussetzen**, **Verschieben** (with a time), or nothing —
and nothing is the default. A holiday only changes what it was told to change;
defaulting the other way would empty the schedule the moment a range was entered.

- The decision belongs to the **definition**, and lives in `vacation_rules`.
  Suppressing a holiday by deleting its blocks does not hold: `expandRecurring`
  skips only the item/date pairs *currently* materialized, so a deleted future
  block comes back the next time a phase expands a range covering it. Only a
  stored decision that expansion consults stays put.
- **"Verschieben" is an override, not an edit.** `recurrenceRule` and `startTime`
  are never touched, so the ordinary schedule is simply what returns when the
  range ends — nothing has to be restored, and there is no cleanup to forget.
- The open question about catch-ups **answered itself**: a suspended task produces
  no block at all, so the evening never sees an occurrence to drop and never
  offers "Nachholen von …". Deciding in advance not to do something is not the
  same as dropping it, and the model says so without a special case.
- `expandRecurring` takes a **list** of plans, because a fortnight being
  materialized can easily reach past the end of one holiday into the next.
- `VacationRepository.save` also brings **already-materialized** days into line —
  expansion only decides what to *create*, and blocks laid down before the holiday
  was entered would otherwise sit there. It never touches days already past: what
  happened, happened, and the Erfolgsliste says so.

Reachable from the settings tab, where the standing configuration lives.

**Debug reset:** lives in the settings tab and wipes every table via `ResetDao`,
which thereby sends the app back to the questionnaire. `ResetDao` lists its tables
by hand on purpose, so a new table is a visible omission rather than something
that silently survives a reset. Note that view models outlive the screen swap:
anything the setup view model latches (its `saving` flag) has to be cleared, or
the reset comes back to a stuck questionnaire.

**No UI has ever been rendered.** There is no emulator or connected device on this
machine, so every screen so far is verified only by compilation and unit tests.
Check layout and colours on a real device before trusting them.

## Open questions

Both of the parked reward questions were decided with the user at step 6:

- Legacy cap: **hard**, surplus forfeited, crown when the gross exceeds it.
- Weekly inflation: applied to the balance **including** the closing week's
  yields — what stands on the account on the planning day loses 30%.

From the setup step:

- The planning times are stored but nothing rings yet — the alarm, snooze and the
  "NOTFALL" deferral from `Planungsphase.md` belong to the planner step.
- `MIGRATION_1_2` is verified only by diffing against the exported schema. A real
  migration test needs `MigrationTestHelper` with a JVM SQLite driver; `room3-testing`
  is already on the test classpath for it.
