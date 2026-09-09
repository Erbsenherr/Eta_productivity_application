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

The Gradle wrapper needs a JDK, and `JAVA_HOME` is not set globally on this
machine. Android Studio's bundled JDK works:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

AGP also needs the SDK. Opening the project in Android Studio writes a
`local.properties` with `sdk.dir`, which is untracked and easily absent on a
fresh clone — without it the build fails at *configuration* time with "SDK
location not found", which reads like a project fault and is not one. Either open
the project once, or hand it in:

```powershell
$env:ANDROID_HOME = "C:\Users\paul_\AppData\Local\Android\Sdk"
```

- Build: `.\gradlew.bat assembleDebug`
- Unit tests: `.\gradlew.bat testDebugUnitTest`
- Single test class: `.\gradlew.bat testDebugUnitTest --tests "*.YieldTest"`
- Lint: `.\gradlew.bat lint`

Test results land in `app/build/test-results/testDebugUnitTest/*.xml`; the Gradle
console output does not print the pass count.

**`lint` is red before you start**, on one pre-existing finding:
`PlanningNotifications.kt:106` calls `notify` without the `checkSelfPermission`
guard lint wants (`MissingPermission`). Refusing the permission is already a
handled case — the alarm keeps firing and rescheduling, it simply has nothing to
show — so this is a missing guard, not a missing behaviour. Do not read a red
`lint` as something you just broke. `lintVitalRelease`, which `assembleRelease`
runs, is green.

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

The database is at **version 12**. `MIGRATION_1_2` adds the `user_setup` table,
`MIGRATION_2_3` recreates it empty when the lunch answer moved into the work one —
the old row could not be converted, since where the break sits inside the working
day simply is not in it — `MIGRATION_3_4` adds `contracts` and `journal_entries`,
`MIGRATION_4_5` adds `items.role`, `MIGRATION_5_6` the two `note` columns, and
`MIGRATION_6_7` the holiday tables, `MIGRATION_7_8` `day_plans.settledAt`,
`MIGRATION_8_9` the unique index on `planned_blocks(itemId, date)` — after
clearing the duplicates it would otherwise choke on — `MIGRATION_9_10`
`items.weekStartedOn`, `MIGRATION_10_11` `user_setup.inflationDay` and
`MIGRATION_11_12` `user_setup.wakeAlarm`. When changing an entity, diff the hand-written SQL against
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
- **A planned task that was not done costs its hours as unplanned time.**
  `heldItsTime()` is what decides whether a block covered the stretch it sat on,
  and only a completed one does. Without this, the day charged for hours that
  were never claimed while letting a broken plan pass free — so planning
  something and abandoning it was strictly cheaper than not planning it, which is
  the opposite of what the charge is for. Filling the freed slot with something
  that did happen makes the charge go away by itself, because planned time is a
  union of what stood. A block left unanswered counts the same as a dropped one;
  the reevaluation gate normally prevents that, and `CatchUpService` does not.
  `DailySettlement.droppedHours` reports that share separately so the evening can
  say which half of the empty time was the user's own doing.
- Free time is the mirror image, and the one exception to the rule above: a
  **`ItemRole.FREE_TIME`** block that was
  *discarded* pays 4 points an hour, up to the daily free time the setup provides.
  Giving it up is a deliberate forfeit, not a failure — which is why it keys off
  `discardedAt`, not off being incomplete, and why `heldItsTime()` still counts
  it as covering its hours: that decision is already priced, and the unplanned
  charge would put a second price on it. The cap exists because 4 points an hour
  against the single point an hour of focused work would otherwise make *planning
  free time in order to skip it* the most lucrative thing in the app. Confirmed
  with the user.

Contract verdicts default to **kept**. The question is "did you hold it", and a
breach is the exception; defaulting the other way would punish a skipped screen.

### The "Smart toDos" tab

`ui/lists/` shows the six lists of `Konzept.md` stacked on one screen, and is
**read-only about placement**. Every one of these lists is what a phase left
behind — the Sperrliste of the weekly sweep, the Wochenliste of the weekly
planning, the Tagesliste of the day planner — so *moving* a card here would be a
second route around the rules those phases enforce. The way to move a card is to
run the phase that moves it. Keep it that way when adding to this screen.

The **attributes** of a definition are the exception, added in step 11 and
deliberately: a wrong duration or priority is a typo, and running a whole
planning phase to fix a typo is the tail wagging the dog. Tapping a row opens
`ItemDetailDialog`, which offers Bearbeiten and Löschen on the **Sammelliste** and
the **Wochenliste** only — see *Step 11*.

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
still fires and still reschedules — which also means it fails *invisibly*, and is
why the settings tab now names that state outright. See *Why an alarm can be
silent*. Alarms do not survive a reboot, hence `BootCompletedReceiver`.

### Sounds, and the alarm that announces a task

The user supplied two mp3s in `Erik_2/sounds/`. Android can only play a
notification sound from a resource or a content URI, so they are **copied** into
`app/src/main/res/raw/` — `sounds/` stays the source folder, `res/raw/` is what
ships. Re-copy after changing one; nothing watches the folder.

**A channel's sound is fixed when the channel is created.** `setSound` on an
existing `NotificationChannel` is ignored, so giving the planning alarm its own
sound meant a **new channel id**: `planning_v2`, with `deleteNotificationChannel`
on the old `planning` so no dead entry lingers in the system settings. Any future
change to that sound needs the same move again. `NotificationCompat.setSound` is
still set on the builder as well — it is ignored from Android 8 on, and it is
what makes 7.x, which `minSdk 24` still covers, hear anything at all.

**The two sounds ride different streams, and that is the whole difference between
them.** The planning alarm is an alarm: `USAGE_ALARM`, `CATEGORY_ALARM`,
`setAlarmClock` on the other side, and it is *meant* to cut through an evening.
The task-start sound is a nudge about the world: `USAGE_NOTIFICATION_EVENT`,
`CATEGORY_REMINDER`, so a phone that has been silenced stays silent for it. That
would be the wrong answer for the alarm and is the right one here.

`planning_start.mp3` plays on **every** ring of a planning phase, the five-minute
retries included. Reading "planmäßig" as "at its scheduled time, as opposed to
deferred" rather than "only the first one": the retries exist to nag, and a silent
nag is not one. Splitting them would need a second channel; say so if that is
wanted.

**The task-start alarm is a mechanism that did not exist**, so `alarm/` gained
`TaskStartAlarm.kt` (contract, scheduler, receiver), `TaskStartCoordinator.kt`
and `TaskStartNotifications.kt`, with the part worth testing in
`domain/planning/TaskStart.kt`. What it does differently from the planning alarm,
and why:

- **One alarm at a time**, always aimed at the next block still to come, rather
  than one per block. A day of ten blocks would otherwise mean ten pending
  intents to keep in step with every edit.
- **`setExactAndAllowWhileIdle`, not `setAlarmClock`.** The latter puts an entry
  in the status bar's alarm slot; rewriting that ten times a day turns a useful
  signal into noise. The same inexact `setWindow` fallback applies when the user
  has refused the exact-alarm permission — five minutes here rather than ten,
  since a nudge is worth less the later it comes.
- **It does not go quiet while the app is open.** The planning alarm's exemption
  exists because a planning phase is something you do *in* the app, so having it
  open is evidence you are doing it. A task beginning is about the world, and
  looking at the screen is no evidence at all.
- **The minute it was laid down for travels in the intent.** What rings is
  matched against *that*, not against the clock, so an alarm delivered late — the
  inexact window, or doze — still announces the right thing rather than the
  nearest thing. `blocksStartingAt` returns a **list**: two things starting on the
  same minute is ordinary, and announcing one of them would be arbitrary.
- **Only open blocks.** One already ticked off or dropped has been dealt with, and
  announcing it would tell the user something they told the app.
- **Today and tomorrow are both read.** The next start after a late evening is a
  morning; looking only at today would leave the alarm cancelled overnight and the
  first block of the day silent.
- **Every block announces itself**, the frame of the day included — Morgenzeit,
  Pause, Freizeit. That is what "a new activity begins according to the plan"
  says. `ItemRole` is the handle if it turns out to be too much.

**Rescheduling, again, on every path**: app start, `RootViewModel.rescheduleAlarms`
(which runs on leaving every flow, and so covers the planner, the reevaluation and
the holiday editor), the boot receiver, and after every ring. The one edit that
happens *outside* a flow is on the dashboard — a start time changed in "Heute
anstehend", or a block ticked off — so `DashboardViewModel` re-aims it directly
rather than waiting for the next flow change.

Two limits worth knowing. **Refusing the notification permission costs the sound**,
not just the banner: the sound rides the notification, so on API 33+ a declined
permission means silence — unlike the planning alarm, where the alarm itself still
keeps the loop alive. And **doze throttles `setExactAndAllowWhileIdle`** to about
one alarm every nine minutes per app, so two blocks closer together than that can
have the second announcement arrive late on a sleeping device.

### Why an alarm can be silent, and the card that says which

The scheduling logic was never the problem — `nextPlanning` is right, every path
reschedules, and the receiver books the next attempt before it does anything
else. **Every way Android suppresses these alarms is silent**, and the app used
to have no way to say which one had happened:

- **`POST_NOTIFICATIONS` refused.** The alarm fires, reschedules, and
  `PlanningNotifications.show` throws into a `runCatching`. Nothing appears and
  nothing is logged. `NotificationPermission()` asks once; Android stops asking
  after two dismissals, and the app never mentioned it again.
- **`SCHEDULE_EXACT_ALARM` denied.** This is the likely one: from API 33 the
  permission is **denied by default**, and `targetSdk` is 37 — so
  `canScheduleExactAlarms()` was false on any modern device and every alarm fell
  back to `setWindow`, which doze can push well past its hour.
- **The manufacturer's battery manager** stopping the app outright.

Two answers, and both were needed:

**`USE_EXACT_ALARM` in the manifest**, beside `SCHEDULE_EXACT_ALARM` capped at
`maxSdkVersion="32"`. That is the pair Android wants: the old permission is
granted at install up to 32, the new one is granted at install from 33 and needs
nothing from the user. `USE_EXACT_ALARM` is reserved by **Play Store policy** for
apps whose core function is an alarm clock or calendar — a policy question, not a
technical one. ERIK sets a wake alarm and two phase alarms, so it qualifies on the
merits, but the claim would have to be made in a listing if this were published.

**An "Alarme" card in the settings tab**, from `alarm/AlarmReadiness.kt`. It
names all three states, offers the system screen that fixes each, and prints when
each alarm is next due — because without that, an alarm armed for tomorrow cannot
be told apart from one that was never set. It reads the states on demand rather
than watching them: they change in the system settings, which means leaving the
app, and coming back is the moment to look.

### The wake alarm

`UserSetup.wakeAlarm` is the switch, `wakeTime` is the hour. **No second time**,
deliberately: the hour the planner shades as the end of the night and the hour the
phone rings at cannot then drift apart. Off by default, and the migration defaults
existing rows to 0 — nobody who has been using the app agreed to be woken by it.
Database **version 12**, `MIGRATION_11_12`.

No, this does not have to go through the Android clock app. What it takes, and
what each piece is for:

- **`setAlarmClock`**, which here is unambiguously right: it *is* an alarm clock,
  it is exempt from doze, and the next-alarm entry it puts in the status bar is a
  feature rather than noise — being able to see that it is armed is half the
  reassurance. (The task-start alarm deliberately does *not* use it, for exactly
  that reason inverted: ten entries a day would be noise.)
- **A full-screen-intent notification, not `startActivity`.** A receiver may not
  start an activity from the background on Android 10 and later. The full-screen
  intent is the path the system keeps open for alarms, and it degrades honestly:
  where it is refused, the notification is still posted, so the alarm becomes a
  loud heads-up instead of nothing.
- **`WakeAlarmRinger` is a process-wide object, not part of the screen.** The
  sound and the screen come apart in three ordinary ways — the full-screen intent
  is refused, the activity is rotated and rebuilt, or the user presses "Aus" on
  the notification, which is a receiver. A ringer owned by an activity keeps
  playing after that activity is gone, which is the one failure a wake alarm must
  not have. `start` is idempotent so a second route to the same ringing does not
  layer a second player on it.
- **The sound loops, on the alarm stream.** A channel sound plays once and stops,
  which is a notification, not an alarm — so the wake channel is silent on
  purpose and the ringer does the playing. The tone is the **device's own alarm
  ringtone**: it is what the user already recognises, it is built to be looped,
  and it is what their volume keys are aimed at. Swapping in a file is one line in
  `WakeAlarmNotifications.soundUri`, with the mp3 copied into `res/raw` the way
  `task_start` and `planning_start` are — `sounds/placeholder.mp3` is sitting
  there unused if that was its purpose.
- **The notification is `ongoing` and back does nothing.** An alarm that can be
  swiped away half asleep without answering it is not an alarm; one of the two
  buttons has to be pressed.

**What the system clock app still has that this does not:** OEM allow-listing.
Samsung, Xiaomi and others exempt their own clock unconditionally and treat third
-party apps by their battery rules, so the "Akku-Optimierung" row in the new
Alarme card matters more for this than for anything else in the app. If the wake
alarm proves unreliable on this phone, that row is the first thing to check, and
the honest fallback is the system clock.

**Open:** one time for every day. Someone who gets up later at the weekend needs
either a per-weekday `wakeTime` — which would touch the sleep answer and the free
-hour maths with it — or a wake time of the alarm's own, which reintroduces the
drift this design avoids. Neither is obviously right; ask before choosing.

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

`PlannerDay` decides which day is on the screen. The planning phase is
`TOMORROW`; `TODAY` is the same screen turned on the day already running, reached
from the dashboard's "Heute umplanen" and opening at the hour before now rather
than at seven. Only `TOMORROW` is locked by its confirmation — see *Step 10*.

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
10. ~~Same-day replanning, quick-add swipe + widget~~ ·
11. ~~First round of real use: bugfixes, tap-to-read, contract editing, manual
points, `task_end.mp3`, Anfahrt/Pause~~ ·
12. ~~Second round: one shared attribute form, drag auto-scroll, the stale-capture
class of bug~~ ·
7. Calendar UI *(parked last, at the user's request)*

The three smaller confirmed features — notes on cards, revolver priority gating and
the dashboard's "now" box — are done; see *Notes, priorities and the "now" box*.

**Next up:** step 7, the calendar. Everything else on the list is done — see *One
occurrence per item per day*, *The week list, and taking on new goals*,
*Reevaluation: every block answered before moving on*, *Step 10: replanning the
day you are in*, *Step 11: the first round of use on a real phone* and *Step 12:
one form, one gesture, one stale capture*.
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

**No UI has ever been rendered, and no sound has ever been heard.** There is no
emulator or connected device on this machine, so every screen so far is verified
only by compilation and unit tests, and the notification sounds not at all —
channel setup, stream choice and whether an alarm actually fires can only be
judged on hardware. Check layout, colours and both sounds on a real device before
trusting them.

### Step 10: replanning the day you are in, and the quick-add widget

Six things the user asked for together. They are one subject: everything here is
about reaching the plan *while the day is running*, rather than only the evening
before.

1. **The day planner opens on today as well as on tomorrow.** Same screen, same
   revolver, same gestures — the point is to cancel, move or add something to the
   day already under way.
2. **The start time is editable in "Heute anstehend"**, not only the duration.
3. **The whole Quick-Add box swipes sideways** to a second quick-add for
   explicitly recurring tasks, which also asks for a name and nothing else.
4. **Moving a block that is already on the day has to be as precise as a drop out
   of the revolver** — the drop indicator with its time is right, the free-hand
   drag is not.
5. **Long-pressing a card in the day planner also offers a copy into the week
   list**, for a task that did not finish inside the time it was given.
6. **The Quick-Add box exists as a home-screen widget**, including the swipe to
   the recurring one.
7. **Not doing a planned task has to cost**, by counting its hours as unplanned
   — see *Contracts and the evening settlement*.

Notes on each, in the order they were built:

**The way in is on the dashboard, under "Heute anstehend".** Small corrections —
a start time, a duration — happen in that list without leaving the screen; the
button below it is for the ones that need the planner: calling something off,
moving it by more than a nudge, adding what was not foreseen. `AppFlow` gained
`PlannerToday` beside `Planner`, so it is a **flow**, not a tab: something you are
in the middle of, which leaves by being done with.

**Today and tomorrow are one screen with a `PlannerDay`.** `DayPlannerViewModel`
used to derive its date from the clock; it now takes `PlannerDay.TODAY` or
`.TOMORROW`, the same shape `WeekScope` already uses for the weekly planner.
The confirmation only locks the screen for **tomorrow**: today's `DayPlan` was
confirmed last night, and honouring that would make the whole feature take a
long press first. Today is therefore always editable and its footer says
"Fertig" rather than "Bestätigen" — re-confirming a day being lived is not a
thing, and writing `confirmedAt` again would be a lie about when it was planned.

**Cancelling is discarding, not deleting.** "Vom Tag nehmen" removes the block,
which is right for a ToDo the user dragged in: it goes back to the week list and
the recurrence that never produced it will not produce it again. For a
**recurring** occurrence it is exactly wrong — `expandRecurring` skips only the
pairs *currently* materialized, so a deleted occurrence of today comes back on
the next top-up, which runs from today. So the dialog offers **"Absagen"** for
anything not movable, and that sets `discardedAt`: the row stays, so expansion
leaves it alone, and the evening never asks about it again. It deliberately does
not offer "Nachholen von …" — same reasoning as a holiday, where *deciding in
advance not to do something is not the same as dropping it*.

**The start time on the dashboard slides like a drop does.** `setStart` runs the
same `canPlace` / `firstFreeStart` pair the planner uses, so the dashboard cannot
become a back door around the one rule the planner enforces. The row's
"07:00 · 1 h" caption is the control: the clock half opens `ErikTimePickerDialog`,
which was extracted out of `ErikTimePicker` for this — a full picker field in
every list row would have swamped the list.

**The recurring quick-add asks for a name, exactly like the other one.** That is
what a quick-add is: the thought is caught now and the questions are answered in
the evening. `Item.newQuickRecurring` puts a RECURRING card in the Sammelliste
with no rule, no start time and no duration, where it waits next to the bare
ToDos — same one-month clock, same Sperrliste at the end of it, because an
unanswered note is being hoarded whichever kind it is. It occupies no day in the
meantime: `expandRecurring` skips a definition missing any of those three rather
than guessing, so a bare recurring note is inert by construction rather than by a
guard someone has to remember.

That made **`Item.isConcretized` a question per type**, which it should always
have been. A ToDo needs category, priority and duration — what the revolver sorts
and bills by. A recurring definition needs rule, start time and duration — what
the expansion refuses to guess. It used to read `type != TODO || …`, i.e. every
recurring item was concretized by definition, which was true only while the sole
way to make one was the questionnaire.

**The evening's concretizing step got a second card.** `ConcretizeScreen` picks
it by `item.type`: no priority and no target date for a recurring note — a
standing task has neither — and weekdays, start and duration instead.
`rulesForWeekdays` in `domain/recurrence/` is the rule: seven days collapse into
`RecurrenceRule.Daily`, fewer become **one definition per weekday**, following
the questionnaire's precedent so that later moving the Tuesday one does not drag
the Thursday one along. `ItemRepository.concretizeRecurring` updates the row that
already exists and adds copies for the rest, then `ScheduleMaintenance.topUp`
runs — or the task would exist without a single occurrence while the user is
looking at the screen that just said it was done.

One list needed narrowing for this: the weekly planner's Sammelliste column now
filters to `ItemType.TODO`. A bare recurring note is not a week goal; it belongs
to the standing schedule, and the evening is what puts it there.

**`ui/quickadd/` owns the panel, and both callers mount it.** The pager, the two
forms and their view model live in one place because the widget needs the same
thing the dashboard shows; leaving it inside `DashboardScreen` would have meant
writing it twice and letting the two drift. Now that both pages are one text
field, they are literally the same composable with different words.

**The whole box swipes, not only the pager.** The heading and its dots sit
*outside* the pager, so that they do not slide about during a swipe — which meant
the top half of the card swallowed the gesture and only the lower half turned the
page. The fix is a `detectHorizontalDragGestures` on the `ErikSurface` itself:
Compose offers a gesture to the innermost handler first, so the pager keeps its
own area and the outer detector picks up everything around it. The dots are
tappable too, since on a box this size they are the clearest thing to aim at.

**Dragging a block now snaps and says where it will land.** The block reports its
candidate minute upward while the finger is down, `DayTimeline` draws the same
`DropIndicator` a revolver drop draws, and the block's own offset follows the
*snapped* minute rather than the finger. That is what the imprecision was: the
finger covers the target and nothing said what time it had reached.

**A copy into the week list is a new ToDo, not a moved one.** The occurrence that
ran out of time keeps its history — it stays on its day, and the Erfolgsliste
still shows it. The copy carries the name, category, note and the block's
duration, is stamped with the current cycle via `takeIntoWeek`, and **skips the
Sperrliste check** for the same reason a make-up does: it follows from work the
user is already doing, not from an idea being hoarded and retyped.

**The widget is a launcher, not a text field.** `RemoteViews` has no editable
field on a home screen, so `QuickAddWidgetProvider` draws a field-shaped tap
target and opens `QuickAddActivity` — a translucent, `singleTop`,
excluded-from-recents activity holding the very same `QuickAddPanel`, so the
swipe to the recurring form comes for free. It stays open after an entry, since
the point of a quick-add is that several fit in one sitting, and it shares the
one `AppContainer`, so a note is in the Sammelliste before the sheet is closed.
The widget has **no `updatePeriodMillis`**: it shows nothing that changes, and
waking it on a timer would spend battery redrawing the same picture.

Two things the widget cannot share and therefore duplicates, both marked as such:
the **palette** (`erik_widget_*` in `values/colors.xml` and its night twin — a
launcher inflates `RemoteViews` in its own process, where no Compose tree and no
`ErikTheme` exist) and the **sheet window theme** (`Theme.ERIK_QuickAdd`).

**What this step added to `domain/` is tested; the rest is not.** `heldItsTime`,
`rulesForWeekdays` and the per-type `isConcretized` all carry tests, and the
settlement suite had to be corrected rather than extended: several of its cases
described "a full day" with blocks that were never completed, which under the new
rule is the *opposite* of a full day. Everything else here is wiring and screens,
leaning on arithmetic that was already covered. Verified by `assembleDebug`,
`assembleRelease` and the suite, and by nothing else: still no device on this
machine.

### Step 11: the first round of use on a real phone

The first list of complaints written after actually living with the app
(`update.txt`, in the repo root). Worked in the order the user gave: the three
bugs first, then the two readability gaps, then the features. What each one
turned out to be, and what was decided where the note left room:

#### The three bugs

**Rotating the phone threw the screen away.** `MainScaffold` held `tab` and `flow`
in a plain `remember`, so a configuration change put the user back on the
dashboard — mid-planning-phase included. They are `rememberSaveable` now, stored
by enum name so the saver is the trivially bundle-able one. The other half of it
was `MainActivity`: it reads the alarm's `EXTRA_OPEN_PHASE` / `EXTRA_DEFER_PHASE`
out of the intent in `onCreate`, and a recreated activity re-reads the *same*
intent — so a rotation re-opened the phase or re-showed `DeferDialog`. The extras
are removed from the intent once read, which is what makes them the one-shot they
were always meant to be.

**"Absagen" looked like it did nothing.** It did exactly what it says —
`discardedAt` is set, the row stays, expansion leaves it alone — but the day
planner drew a discarded block identically to an open one, so the only feedback
was the dialog closing. The timeline now draws a called-off block struck through,
dimmed and captioned "abgesagt", and the edit dialog offers **"Doch einplanen"**
in place of "Absagen" once it is. The model was right; nothing said so.

**A moved block snapped back.** The reason is a Compose trap worth remembering:
`Modifier.pointerInput(keys)` does not restart its block on recomposition, so the
`onDragEnd` lambda handed to `detectDragGestures` keeps the values it **closed
over at launch time**. It closed over `draggedMinute`, a plain `val` recomputed
each composition — so the drag preview (which recomputes inside its own lambda)
was right while the committed minute was always the block's original start. The
committed minute is now derived inside `onDragEnd` from the `MutableFloatState`
itself, which is the one thing in that scope that does read through. **Rule:** in
a `pointerInput` lambda, read state through its `State` object, never through a
`val` captured from the composition.

#### The two readability gaps

Both are the same complaint — the name does not fit — and both are answered by a
tap, which did nothing anywhere before.

- **In the day planner**, tapping a block shows its full name, time and note in a
  bubble above the day, for as long as it stays selected. A quarter-hour block is
  16dp tall and will never hold its own label; long-press already means "edit", so
  the short tap was the free gesture.
- **In the Smart-toDos tab**, tapping a row opens `ItemDetailDialog` with the full
  name, the note and everything the card carries. Long-press does the same thing:
  the note asked for both, and having them differ would only be a thing to
  remember.

**The Listen tab is no longer strictly read-only, and that is a deliberate
reversal.** It was read-only because each list is what a phase left behind, so
editing there would be a second route around the rules the phases enforce. That
still holds for *placement* — nothing here plans, unplans or moves anything — but
it does not hold for the attributes of a definition: a wrong duration or priority
is a typo, and sending the user through a whole planning phase to fix a typo is
the tail wagging the dog. So `ItemDetailDialog` offers **Bearbeiten** and
**Löschen** on the **Sammelliste** and the **Wochenliste** only, over the same
attributes the concretizing step asks for plus the three new switches. Tages- and
Morgenliste show the name and nothing else — those are blocks, and the day planner
owns them; the Sperrliste is a rule with a date on it. The Erfolgsliste has no
card behind its rows at all, so a long name there simply gets three lines instead
of a popup that could add nothing to it.

#### Contracts can be changed once

Long-pressing a contract opens `EditContractDialog`; the arithmetic is
`editedContract` in `domain/contract/ContractRules.kt`, so the rule a test asserts
and the one the screen applies are the same code. Four rules, all from the user:

- **Only the text.** Title, conditions and breach definition. `effort` — and with
  it the daily payout — deliberately cannot be changed: an edit that could raise
  the rate would make "bearbeiten" a way to pay yourself more for a promise
  already half-served.
- **Once, ever.** `Contract.editedAt` is the flag and `Contract.isEditable` the
  question. The long press stops offering after it is set and says why —
  `editRefusal` picks the sentence, because a gesture that silently does nothing
  is indistinguishable from one that is not there.
- **Legacy contracts cannot be edited at all.** They already got their reduction
  by running a month; re-writing what they ask would make that month meaningless.
- **The term restarts.** `signedOn` becomes today and `endsOn` moves with it,
  keeping the term the same *length* — so a contract two weeks into its run is
  back at zero and needs a full month again before it can become legacy. That is
  the point of the rule rather than a side effect: changing what you promised is
  making a new promise.

#### Points by hand

Long-pressing the `PointsBox` opens a dialog that books a `PointsReason.MANUAL`
row, positive or negative, with a note. It is a ledger, so a correction is a row
like any other and the history still adds up — nothing overwrites the balance.

#### `task_end.mp3`

The counterpart to the start alarm, and the third sound in `sounds/`, copied into
`app/src/main/res/raw/` the way the other two are. What is different about it:

- **It announces the *planned end*, not a completion.** It rings only if the block
  is still **open** at that minute — a task ticked off early has been dealt with,
  and the same reasoning already governs the start alarm.
- **`Item.endSound` is the switch**, a boolean on the definition, offered as a
  checkbox in both concretize cards and **on by default for new cards**. The
  migration sets it to **0** for every existing row: the setup's frame — Morgenzeit,
  Pause, Freizeit — would otherwise start chiming all day, and nobody agreed to
  that by upgrading.
- **It shares the task-start alarm**, not a second one. `TaskStartCoordinator`
  now aims at the next *event*, start or end, whichever comes first — one pending
  intent for the whole day either way, which is the property that made the start
  alarm cheap. `domain/planning/TaskStart.kt` carries the arithmetic:
  `nextTaskEvent` and `eventsAt`.
- Its own channel (`task_end`), because a channel's sound is fixed at creation —
  the same trap `planning_v2` documents.

#### Anfahrt and Pause: a block that brings its own margins

The largest of the six, and the one with a real modelling choice behind it.
Confirmed with the user: **two duration columns on the block**, not two extra
blocks.

`PlannedBlock.travelBefore` and `PlannedBlock.breakAfter` are nullable durations.
The block still starts when it says it starts; the **container** it occupies runs
from `containerStartMinute()` to `containerEndMinute()`, and that container is
what everything about *time* is asked: `overlaps`, `canPlace`, `firstFreeStart`,
`plannedMinutes`, and the weekly free-hour maths. What everything about *points*
is asked stays `effectiveDuration` — travel and break pay nothing, and `yieldOf`
never sees them.

Why not two blocks with a parent id: each would need an `Item` to point at, which
means an extra definition for every task that has them, two more entries in every
list, two more questions in the evening reevaluation, and a moved task that has to
drag two other rows along atomically. Every one of those is a place for them to
come apart. As columns they cannot: there is nothing to keep in step.

- **Drawn as the note asks**: one enclosing box around two or three, with the
  travel above the task and the break below it, both in a dimmed fill. The task
  keeps its own box inside.
- **Dragging moves the container.** It falls out of the model rather than being
  arranged: the finger grabs the task, the task's start moves, and the margins are
  offsets from it.
- **`Item.travelBefore` / `Item.breakAfter` are the defaults**, stamped onto every
  block the definition produces — so a recurring task with a commute has one on
  every occurrence. Both concretize cards offer the two checkboxes, each defaulting
  to **15 minutes** when ticked and editable in the same panel. `BlockEditDialog`
  edits them **per occurrence** afterwards — adding a journey to today does not add
  one to every Tuesday — while the list tab's `ItemEditDialog` edits the
  definition's defaults. They travel with the two copies the app makes of a task
  as well, `Item.newMakeUpTodo` and the planner's "in die Woche kopieren": both are
  the same work on another day.
- **The break is the part that yields when the day is full.** Placement tries the
  whole container first. If that does not fit anywhere but the task *with its
  travel* does, the user gets `BreakDroppedDialog` — "Es ist kein Zeitslot frei,
  der die vorgesehene Pause zulässt. Trotzdem einplanen?" — and answering yes
  places it with `breakAfter = null`. The travel is never dropped: it is the time
  the task actually takes to reach, not a courtesy after it, and a task placed
  without it would simply be planned wrong. If even task-plus-travel finds no room,
  it is the ordinary `NoRoom` refusal.

**Database version 13**, `MIGRATION_12_13`: `items.travelBefore`,
`items.breakAfter`, `items.endSound`, `planned_blocks.travelBefore`,
`planned_blocks.breakAfter`, `contracts.editedAt`. All nullable except
`items.endSound`, which is `NOT NULL DEFAULT 0` — see above for why 0.

**Still verified by nothing but the compiler and the suite.** The new arithmetic —
the container span, `nextTaskEvent`, the contract edit — carries tests. The sounds,
the bubble, the container's looks and whether the drag now really commits can only
be judged on the phone this list came from.

### Step 12: one form, one gesture, one stale capture

The second list from living with the app. Two bugs, five conveniences, and one
theme running through most of them: the same question was being asked by five
different screens, each with its own idea of which fields exist.

#### The stale capture, again — and this time it deleted data

**Moving a block wiped its Anfahrt and Pause.** The persistence was never at
fault: the generated `EntityUpsertAdapter` writes every column, and `move` copies
the block. The fault was the same Compose trap step 11 documents, one level up.
`Modifier.pointerInput` does not restart its block on recomposition, so the
lambdas inside it keep **the whole `BlockWithItem` they closed over at launch
time**. Its keys are `(id, movable, minutePx)`, none of which an edit changes — so
after adding margins through `BlockEditDialog`, the next drag handed `move` the
*pre-edit* block and wrote it straight back. Margins were only the visible half:
a note, a corrected duration or a completion would have gone the same way, and
long-press opened the edit dialog on the same stale row.

Two fixes, and both are worth keeping:

- **Every callback in `TimelineBlock` goes through `rememberUpdatedState`.** That
  is the general answer to the whole class, long-press included.
- **`move` takes a block *id*, not a block**, and looks the row up in its own
  state. A view model that is handed a row cannot tell how old it is; one that is
  handed an identity cannot be lied to.

**Rule, now twice earned:** inside a `pointerInput` lambda, nothing may be read
from the enclosing composition directly. State goes through its `State` object,
callbacks through `rememberUpdatedState`, and identity through a value that
cannot go stale.

#### One attribute form, used everywhere

`ui/attributes/ItemAttributes.kt` holds `TodoAttributes` and
`RecurringAttributes` — the answers themselves — and the two composables that ask
for them. Every screen that creates, concretizes or edits a card now mounts one
of those two:

| where | what it was |
| --- | --- |
| `ConcretizeScreen`, both cards | the original; now the shared fields |
| `ItemDetailDialog` → `ItemEditDialog` (Listen-Tab) | ToDo fields only, no margins |
| `AddGoalDialog` (Wochenplanung → "ToDo hinzufügen") | no margins, no end sound |
| `NewTodoDialog` (spontanes ToDo im Planer) | name, category, duration |
| `BlockEditDialog` | block-level, and now the end sound too |

Two places are deliberately left alone. **Quick-Add** — dashboard box and widget
both — asks for a name because that is what a quick-add *is*: the thought is
caught now and the questions are answered in the evening, and `RemoteViews` could
not hold the form anyway. **`SpendDialog`**, the second revolver's Custom Earn /
Custom Spend / Social, is not a ToDo at all: it is a fixed-length points entry
whose margins are already reachable by long-pressing the block it produces. A
sound at the end of an hour of reward time is the one extra with an obvious use
there — say so and it is a small addition, with a third `PendingPlacement` for
the break prompt.

**The extras ride along with the shared fields**, so "Anfahrtszeit", "Pause
danach" and "Ton am Ende" now exist wherever a card is filled in rather than in
the two screens that happened to get them first. `NewTodoDialog` is asked for no
target date — dropping it on a slot has already answered that — which is what
`showTargetDate` on the shared composable is for.

Two consequences that fall out of the sharing rather than being arranged:

- **A spontaneous ToDo goes through the same placement as a revolver drop**, break
  prompt included. `BreakPrompt` therefore no longer carries an `Item`; it carries
  a `PendingPlacement`, which is either the card from the revolver or the one
  about to be created. Answering it re-runs exactly the placement that was
  refused, with the break gone.
- **Saving an edit finishes a card.** The forms cannot express "no category" any
  more — the null option is gone from all of them, as it always was in the
  concretizing step — so a Sammelliste entry that was `unvollständig` before the
  edit is `isConcretized` after it. That was its own complaint, and it needed no
  code of its own once the forms agreed.

#### A recurring note is concretized where it is edited

A bare **recurring** quick-add in the Sammelliste used to open the ToDo form,
which cannot make it complete: `isConcretized` wants a rule, a start time and a
duration, and the ToDo form has none of them. It now opens
`RecurringAttributeFields`, and **"Sichern" runs `concretizeRecurring`** — the
same call the evening step makes, so several weekdays become several definitions
and `ScheduleMaintenance.topUp` lays the occurrences down at once.

Confirmed with the user, because it has a visible consequence: the entry
**leaves the Sammelliste**. That is not a side effect but the point — a recurring
task belongs to the standing schedule, and `concretizeRecurring` moves it to
`Stage.DAY` and clears `enteredCollectionAt`, the one-month clock having nothing
left to measure. The dialog says so before it happens.

Nothing has to be cleared up afterwards: a definition without a rule lays down no
block at all, so there are no stale occurrences from before the edit. An
already-concretized recurring definition never appears in this tab — it is in
`Stage.DAY` — so the case does not arise, and if it ever does it would need
`PlanRepository.clearUpcoming` first, the way `SetupRepository.complete` does.

#### Dragging past the edge of the screen

A block dragged to the top or bottom of the day now **scrolls the day under it**,
so the hours off-screen can be reached. `domain/planning/DragScroll.kt` holds the
arithmetic — how fast, and from how close to the edge — and is the part worth
testing; the speed ramps with proximity so the last quarter-hour is still
placeable.

The awkward part is not the scrolling but keeping the drag honest while the
ground moves. A block drag is **relative**: it accumulates the finger's own
travel, which is what preserves the grab point on a two-hour block. Auto-scrolling
moves the content without moving the finger, so the screen publishes how far it
has scrolled and the block adds that to its own travel. Both are read as `State`
in composition, so the block, the drop indicator and the container all follow a
scroll that happens while the finger is perfectly still.

A revolver drag needs none of that: it is **absolute** — a position on screen
turned into a minute through the timeline's own reported geometry — so it follows
a scroll for free, exactly as *The day planner* says.

**The container drags with its block.** It always moved in the database; on screen
it used to sit still while the task slid out of it, which is also what made the
lost margins look like a deletion. `DayTimeline` now knows which block is being
dragged and to what minute, and offsets its frame by the same amount.

One thing had to be fixed before any of this was safe. The revolver announced
`onPullStart` on **any** touch of the centre chamber but `onPullEnd` only when the
gesture turned out to be a pull — so turning the revolver left the screen holding
a drag that never ended. That was a stuck ghost before; with an auto-scroll
watching the finger's last known position it would have been a day scrolling by
itself. `onPullStart` now fires at the moment the direction is decided, which is
the same moment `pulling` becomes true, so the two are a pair.

#### Two smaller ones

- **"Offene Tage" keeps its list folded** until `TECHNISCHE SCHWIERIGKEITEN` is
  typed out. The count, the warning and the phrase field stay visible — the box
  still has to *say* that days are outstanding — but thirty un-actionable rows no
  longer push the rest of the settings tab off the screen. The phrase already
  gated the buttons; now it gates the list they sit in, which costs nothing
  because nothing above it could be acted on either.
- **The list popup lost its "Schließen" button.** Tapping outside already closes
  it, and the row it sat in reads better with only the two buttons that do
  something.

**Verified by the compiler and the suite, as ever.** `DragScroll` and the shared
attribute defaults carry tests; the drag fix does not, because what broke was a
Compose capture rather than arithmetic — the honest guard against it is the rule
above, not a test.

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
