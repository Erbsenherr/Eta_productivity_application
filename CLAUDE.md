# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Eta is a productivity app: a smart to-do list / week planner with a reward-points
system. `Eta_doc/` holds the German concept documents and is the **source of truth
for behaviour** — `Konzept.md` is the root, the rest hang off it. Read the relevant
doc before implementing a feature; the point values, timeouts and phase mechanics
are all specified there and are easy to get subtly wrong.
`Eta_doc/Paint Draft Dayplanner.png` is the layout sketch for the day planner.

**The app was called ERIK** (project `ERIK_Iteration_2`) until it was renamed to
Eta. Three things keep the old name on purpose, and must not be "finished":

- **`applicationId = "com.example.erik_iteration_2"`**, while the `namespace` and
  the Kotlin package are `com.example.eta`. Android identifies the installed app
  by the id; a new one would install as a second, empty app beside the old one,
  and the Google Calendar OAuth client is registered for that package name.
- **`EtaDatabase.NAME = "erik.db"`** — the file on the phone, and the entry name
  inside every backup zip made so far.
- The repository folder `Erik_2`, the GitHub repository `ERIK_It2`, and the old
  `erik-debug-…` APKs in `dist/`.

## Build

The Gradle wrapper needs a JDK, and `JAVA_HOME` is not set globally on this
machine. AGP also needs the SDK: opening the project in Android Studio writes a
`local.properties` with `sdk.dir`, which is untracked and easily absent on a fresh
clone — without it the build fails at *configuration* time with "SDK location not
found", which reads like a project fault and is not one. Either open the project
once, or hand both in:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "C:\Users\paul_\AppData\Local\Android\Sdk"
```

- Build: `.\gradlew.bat assembleDebug`
- Unit tests: `.\gradlew.bat testDebugUnitTest`
- Single test class: `.\gradlew.bat testDebugUnitTest --tests "*.YieldTest"`
- Lint: `.\gradlew.bat lint`

Test results land in `app/build/test-results/testDebugUnitTest/*.xml`; the Gradle
console does not print the pass count. **`lint` is green** — no errors, only
warnings and hints — since step 18, when every `notify` started going through
`postNotification` in `alarm/EtaSound.kt`, which checks the permission
explicitly. A new error is therefore yours; findings live in
`app/build/reports/lint-results-debug.xml`.

**Nothing in this app has ever run on a device.** There is no emulator or phone on
this machine, so everything is verified by compilation, the unit suite (452 tests
as of step 30), `assembleRelease` and `lint` — and the sounds not at all: channel
setup, stream choice and whether an alarm really fires can only be judged on
hardware. Assume that of every screen, colour and sound described below unless it
says otherwise, and check them on the phone before trusting them.

### An APK in `dist/` at the end of every session

**Every session that changed code ends with a fresh APK in `dist/`**, without being
asked — it is how the user gets the work onto the phone. Once the suite,
`assembleRelease` and `lint` are green, as the last step:

```powershell
$bt  = "C:\Users\paul_\AppData\Local\Android\Sdk\build-tools\36.1.0"
$out = "dist\eta-release-$(Get-Date -Format 'yyyy-MM-dd-HHmm').apk"
& "$bt\apksigner.bat" sign --ks "$env:USERPROFILE\.android\debug.keystore" `
    --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey `
    --out $out app\build\outputs\apk\release\app-release-unsigned.apk
```

- **The release build, signed with the debug keystore.** `assembleRelease` leaves
  `app-release-unsigned.apk`, which a phone refuses; there is no release signing
  config in the build. Every APK in `dist/` so far carries the debug key, and it
  has to stay that one: Android only installs an update over an app signed with
  the same key, so another key means uninstalling first — and losing the database.
  The Google Calendar grant is matched against that key's SHA-1 as well.
- The name is `eta-release-<date>-<time>.apk`; the `.idsig` beside it is
  apksigner's and can be ignored. Old APKs stay — `dist/` is the history.
- Say in the closing message which file it is. A session that only touched
  documentation builds none.

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
  `OnSetupChange` usage compiled green three times and only surfaced on
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

### Compose traps, each earned the hard way

- **Nothing may be read from the enclosing composition inside a `pointerInput`
  lambda.** `Modifier.pointerInput(keys)` does not restart its block on
  recomposition, so the lambdas keep whatever they closed over at launch time.
  State goes through its `State` object, callbacks through `rememberUpdatedState`,
  and identity through a value that cannot go stale — which is why `move` takes a
  block **id** and looks the row up itself. This cost a moved block snapping back
  (step 11) and then a drag silently writing back a pre-edit row, deleting its
  margins (step 12). Every gesture since — long-press, drag-to-reorder, the
  pomodoro long press — obeys it.
- **A list of cards with state needs `key(item.id)`.** A plain `forEach` gives
  every `remember` a *slot*, not an identity, so deleting a card moved its
  neighbour into the slot with the "Wirklich?" already armed (step 14).
- **`contentDescription` inside a `semantics { … }` lambda is the receiver's own
  extension property, whose getter throws.** A parameter of that name assigned to
  itself compiles clean and crashes at runtime; call it `label`.
- **`androidx.compose.material:material` (Material 1) is on the *debug* classpath**
  because `ui-tooling` needs it for previews, so a stray
  `import androidx.compose.material.*` compiles in debug and breaks only in
  release. Run `assembleRelease` before trusting a UI change. M3 is gone entirely,
  so those imports fail immediately.

## Architecture

### Layers

`domain/` is pure Kotlin and holds the rules worth testing without a database.
`data/local/` has the Room DAOs, `data/repository/` the repositories, and
`di/AppContainer.kt` wires them manually — no DI framework, since the graph is
small and it keeps another annotation processor out of the build. The model
classes carry Room annotations directly rather than having entity classes plus
mappers: `Konzept.md` asks for the simplest thing that works, and mappers would
cost ~250 lines for no current benefit.

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
something has to call it: it runs on every app start, at the top of both planning
phases, on leaving every flow, and after every growth increment or edit to a
standing task. Before it existed, only the setup materialized anything — a
fortnight — so two weeks after the questionnaire the planner, the free-hour maths
and the "now" box all went empty with no error to explain it. Expansion is cheap
and idempotent, so call it more often rather than less.

`domain/recurrence/RecurrenceExpansion.kt` turns definitions into blocks.
`expandRecurring` is **idempotent** — it takes the already-materialized item/date
pairs and skips them. A definition missing its start time or duration is skipped,
never guessed at, which is what makes a bare recurring note inert by construction
rather than by a guard someone has to remember.

**What that idempotence does *not* buy:** a block the user deleted is no longer
among the materialized pairs, so expanding a range that still covers its date
creates it again. Ranges run forward from today, which is why a deleted *past*
occurrence stays deleted — but deleting a **future** block is not a way to
suppress an occurrence. Anything that needs to suppress one durably has to be
consulted by expansion itself (see *Vacation mode*), or to set `discardedAt`, so
the row stays and expansion leaves it alone.

`RecurrenceRule.Daily` is the one rule without a weekday. It exists because the
setup questionnaire's daily routines would otherwise need seven definitions each.

### One occurrence per item per day, enforced

`planned_blocks` carries a **unique index on (itemId, date)**, and materialization
inserts with `IGNORE` rather than upserting. This is not belt and braces — it is
the only thing that makes the invariant true. `materializeRecurring` reads what
exists, works out what is missing, then writes; two overlapping runs both read
"nothing there" and both wrote, and with a random-UUID primary key two rows for
one item on one day were perfectly legal. Finishing the questionnaire used to
start three materializations within milliseconds, so every recurring task appeared
three times — and when the writes happened to land first it looked fine, which is
why it read as random.

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
  category, duration and margins inherited, **no target date** so it can be planned
  at once. It skips the Sperrliste check on purpose — it follows from a commitment
  the user still holds. Idempotent via `PlannedBlock.makeUpItemId`. From there it
  is an ordinary ToDo, so completing it reaches the Erfolgsliste with no special
  handling.
- **Deadline / Spend** → nothing further.

A null `targetDate` means "available immediately" — use `Item.isAvailableOn(date)`
rather than comparing against `availableFrom` directly.

### Erfolgsliste comes from blocks, not from `Stage.DONE`

A recurring item is never retired, so it can never reach `Stage.DONE` — checking
one off would otherwise delete it forever. The Erfolgsliste is therefore the view
over *completed blocks* (`observeErfolgsliste`), which is also what gives it the
"Name, Datum und Uhrzeit" the docs ask for: name from the item, date and time from
the block. **Standing tasks are excluded since step 23**, at the user's request: a
list filling up with Morgenzeit, Pause and Freizeit every day is one nobody reads. `Stage.DONE` still retires one-shot ToDos so they leave the active
lists — `ItemRepository.retireCompletedTodos` runs at settlement, the moment a day
is accounted for. Nothing used to set that stage at all.

### The six lists are five stages

`Stage` has `COLLECTION`, `LOCKED`, `WEEK`, `DAY`, `DONE`. The sixth list from
`Konzept.md`, "Liste für Morgen", is **not** a stage — it is the view of tomorrow's
blocks when that `DayPlan` is confirmed. Long-pressing clears `confirmedAt`, which
reopens editing and brings the revolver back out.

### Points

The **balance is a ledger sum**, not a stored number: `PointsTransaction` rows are
summed by `PointsDao.observeBalance()`. Weekly inflation, contract payouts and
spends therefore stay auditable instead of overwriting one another, and a manual
correction (`PointsReason.MANUAL`, from a long press on `PointsBox`, positive or
negative with a note) is a row like any other.

Checking a task off does **not** credit points. Per `Tägliche Reevaluation.md` the
harvest happens in the evening, so the dashboard shows the balance alongside
today's *pending* yield, labelled as not yet credited. **`ReevaluationService` is
the only thing that writes `HARVEST` rows**, `harvestLate` included — see *Step 22*.

`yieldOf(item, block)` in `domain/reward/Yield.kt` is the single place points are
computed. It keys off the block, bills `actualDuration` when the user corrected it
and `plannedDuration` otherwise, and never sees the margins. `yieldOf(category,
duration)` is the same arithmetic without a block, for a screen that has to show
what an answer would be worth. `plannedYield` is the day's forecast — every block
not called off, done or not — shown as "geplant" in `PointsBox` and as "Prognose"
in the planner header, where a rate is chosen. `formatSignedPoints` is the one
place a points *change* gets its sign; a negative harvest once printed as a green
"+-5".

Category multipliers: Fokus 1.0, Nebenbei 0.5, **Achtsam 1.0** (the docs have a
copy-paste error here; 1.0 is the confirmed value). Imported calendar events pay
per *full* hour and ignore the category.

### `ItemRole`: what a block is *for*

`Item.role` is a nullable, closed enum saying what part an item plays in the day —
`FREE_TIME`, `BED_PREP`, `MORNING`, `BREAK`, `WORK`, `MEAL`, `HOUSEKEEPING`,
`SPORT`, `MINDFULNESS`. The setup stamps it on everything it generates.

It exists because rules have to **recognise a kind of block**, and identity is the
wrong handle for that. Free time used to be found by the literal id
`setup:freetime`, so retiring or replacing that one row silently switched the
forfeit payout off, and a free-time block made by hand was never recognised at
all. `Belohnungssystem.md` says "des *Freizeit* (o.ä.) Punktes" — the "o.ä." was
always describing a category.

Deliberately **not** free-form tags: this is the app's own vocabulary, needed so
its rules can fire. `Category` (Fokus/Nebenbei/Achtsam) is the classification the
user makes. `SETUP_ITEM_ID_PREFIX` does only what it was for — idempotent
regeneration.

**Known limit:** nothing lets the user *set* a role. A block they create by hand
carries none, so it counts as neither free time nor anything else. The block edit
dialog is where that belongs when it matters.

## Where the rest of the documentation lives

This file holds only what every task needs. Everything about one subject sits in
`.claude/rules/`, each file carrying a `paths:` header so it loads by itself when
a matching source file is touched. **If a task concerns a subject below and its
file has not loaded, read it before changing anything** — the globs are a
convenience, not a guarantee. A cross-reference like "see *The Heute tab*" or
"see *Step 23*" names a section heading in one of these files.

| file in `.claude/rules/` | sections it holds |
| --- | --- |
| `setup.md` | *The setup questionnaire* |
| `database.md` | *The database and its migrations* — the version and the migration table |
| `contracts.md` | *Contracts and the evening settlement*, *Breaking a contract without giving it up (step 17)*, *Contracts can be changed once (step 11)* |
| `lists.md` | *The "Smart toDos" (Listen) tab* |
| `streak-reevaluation.md` | *The streak, and what keeps a day honest*, *Reevaluation: every block answered before moving on* |
| `alarms.md` | *The planning alarm, and why one can be silent*, *Sounds, and the alarms that announce a task*, *Reading a task's name aloud (step 28)*, *The wake alarm* |
| `week-planning.md` | *The weekly planning phase*, *The week list, and taking on new goals* |
| `day-planner.md` | *The day planner*, *A block that brings its own margins*, *The third revolver: Pause (step 29)* |
| `ui-foundation.md` | *UI: Foundation only*, *Navigation: tabs and flows* |
| `quickadd-attributes.md` | *Quick-Add, and what a bare note becomes*, *One attribute form, used everywhere* |
| `dashboard.md` | *Notes, priorities and the "now" box*, *The Heute tab* |
| `settings.md` | *The settings tab* — backup and debug reset included |
| `google-calendar.md` | *Google Calendar, read-only (step 15)* — step 16's conflict answers included |
| `vacation.md` | *Vacation mode* |
| `steps-10-14.md` | *Step 10* (replanning today, cancelling is discarding), *Step 11*, *Step 12*, *Step 13* (the Rückweg, what a cancellation costs, Höhere Gewalt), *Step 14* |
| `step-18-standing-schedule.md` | *Step 18*: the standing schedule on the Listen tab, overlap warnings, Erinnerungen, the still-active question, Pomodoro |
| `step-19-24-growth.md` | *Step 19*: Growth-Tasks, dynamic time setting, the Growth-Tasks tab, the reminder extra · *Step 24*: growth to the second, update condition, Mengen-Inkrement |
| `step-20-22.md` | *Step 20* (a finished task gives its time back), *Step 21* (finishing a card early, the wastebasket), *Step 22* (pricing an early completion, deadlines, the block underneath) |
| `step-23-subtasks.md` | *Step 23*: subtasks, the builder, merging in the planner, the follow-up question, the drawn signature |
| `step-25-wochenschema.md` | *Step 25*: the Wochenschema |
| `step-26-small-ones.md` | *Step 26*: the hidden "Gruppieren" button, tap to unfold a name, free pomodoro phases, the red mark in the Wochenschema, "Nächster freier Slot" |
| `step-30-designs.md` | *Step 30*: designs (`AppDesign`: Eta, Eta Dunkel, Legacy), `applyDesignToWindow`, the widget's colours · *The launch screen* |
| `step-27-early-billing.md` | *Step 27*: what a task finished early is billed at — the question in the follow-up dialog, capped at twice the time used |

**When writing up a new round**, put each item into the file its subject belongs
to, or a new `step-N-….md` with its own `paths:` — and add it to this table.
Only something every task needs goes in this file.

## Confirmed design decisions

These came out of discussion with the user and are not all derivable from `Eta_doc`:

- **The Eta design is the default.** Eta and Eta Dunkel are two designs chosen by
  hand and do not follow the device; Legacy is one tap away in the settings and
  still does. See *Step 30*.
- **Eta may play through "Nicht stören"**, off by default — see *Sounds, and the
  alarms that announce a task*.
- **UI: Compose Foundation only, no Material3.** The revolver, drag-and-drop timeline
  and custom card shapes fight Material components.
- **Persistence:** local Room only, but sync-friendly — UUID string keys and
  `updatedAt` on every row, so a sync layer can be added without a schema rewrite.
- **Google Calendar:** read-only, a step inside each planning phase rather than a
  screen. Overlapping imported events count **once** toward free time (interval
  merge) — `plannedMinutes` and `occupiedMinutesOfWeek` merge per day, and always did.
- **Sperrliste identity:** normalized name (trimmed, lowercased).
- **Spend covers both** Custom Spend and Custom Earn via a signed `pointsPerHour`. The
  second revolver's third entry, "Social", is assumed neutral at 0
  (`SOCIAL_POINTS_PER_HOUR`): the setup already budgets social time out of the free
  hours, so paying for it again would count it twice. `Belohnungssystem.md` gives no
  rate for it.
- **The questionnaire's framework blocks carry no category** and therefore no yield;
  "Arbeit / Uni" is the exception and counts as Fokus **for now** — the Listen tab is
  where that provisional choice gets corrected.
- The questionnaire asks for a **start time** wherever `Setup-Questionaire.md` only
  names a duration (Hausputz, Sport, Kochen). Without one, `expandRecurring` would skip
  the definition rather than guess.
- **The Mittagspause is not its own question**, contrary to `Setup-Questionaire.md`:
  asked separately it collided with the working day every time. Someone who answers
  "keine festen Zeiten" for work therefore gets no scheduled break.

## Build order

1. Data model · 2. Room persistence + list logic · 2b. Foundations design system ·
3. Dashboard · 4. Setup questionnaire · 5. Revolver day planner · 6. Contracts +
rewards · 6b. Weekly planning phase · 6c. Planning alarms + daily flow closed ·
6d. "Smart toDos" tab · 8. Settings tab · 9. Vacation mode · 10. Same-day replanning,
quick-add swipe + widget · 11–14. Four rounds of real use · 7/15. Google Calendar,
read-only (parked last at the user's request) · 16. Calendar's own first round ·
17. Breaking a contract without giving it up · 18–22. Five more rounds ·
23. Subtasks, groups and a drawn signature · 24. Growth to the second, update
condition, Mengen-Inkrement · 25. The Wochenschema · 26. Five conveniences and a
hidden button · 27. Billing a task finished early · 28. Task names read aloud ·
29. A Pause revolver in the day planner · 30. Designs to choose from, and a launch
screen.

**The list is finished**; what follows are rounds of real use, each written up from an
`update.txt` in the repo root. Every screen the concept names exists. Steps 11–14 and
16–17 are documented where their rules belong rather than under their own heading —
step 16's two items in *Google Calendar*, step 17's in *Contracts*; the `step…` files in
`.claude/rules/` carry what is left, and are also where a cross-reference to "step N"
lands.

## Open questions

- **The planning times** are stored and the alarm rings; what remains open from the setup
  step is nothing more than that.
- **`MIGRATION_1_2` is verified only by diffing against the exported schema.** A real
  migration test needs `MigrationTestHelper` with a JVM SQLite driver; `room3-testing` is
  already on the test classpath for it.
- **One wake time for every day** — see *The wake alarm*.
- **Nothing lets the user set an `ItemRole`** — see *`ItemRole`*.
- **Notes at creation time**, and a deadline on a card already planned into a day, have
  nowhere to be asked for yet.
- **A second Google account** needs a second grant — see *Google Calendar*.

Both parked reward questions were decided with the user at step 6: the legacy cap is
**hard**, with the surplus forfeited and the crown shown when the gross exceeds it; and
the weekly inflation applies to the balance **including** the closing week's yields —
what stands on the account on the planning day loses 30%.
