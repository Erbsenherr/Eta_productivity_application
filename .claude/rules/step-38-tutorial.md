---
paths:
  - "app/src/**/tutorial/**"
  - "app/src/**/ui/components/TutorialGuide.kt"
  - "app/src/**/ui/components/Fold.kt"
  - "app/src/**/di/AppContainer.kt"
  - "app/src/**/ui/root/**"
  - "app/src/**/TutorialTest.kt"
---

### Step 38: the tutorial

One item from `update.txt`, with three additions from the user: streamline text and
UI, make it repeatable from the settings (at the very bottom), and **it must never
touch the user's data**. No schema change — the database stays at **version 26**.

#### A practice app, not a set of practice screens

**The tutorial shows the app's own screens over a second `AppContainer`.**
`TutorialSession` (`ui/tutorial/TutorialViewModel.kt`) builds
`AppContainer(context, sandbox = true, clock = …)`: the same wiring over
`Room.inMemoryDatabaseBuilder`. That is the whole of how the rule is kept — not a
guard on some code path, but the absence of one: nothing in a session holds a
reference to `erik.db`. Do not "optimise" this into the real container with
flagged rows.

What `sandbox` changes, and it is everything it has to:

- **The database is in memory**, thrown away when the run ends.
- **All four alarm coordinators are inert** (`enabled = !sandbox`):
  `DashboardViewModel` calls `taskStartCoordinator.reschedule()` on every tick,
  and from a practice container that would aim the phone's one task alarm at
  "Katze füttern" — in place of the user's own next task.
- **`tutorialSeed` is non-null only there.** There is no call that could write the
  example tasks into the real database.
- **`close()`** does something only there.

The session is also a **`ViewModelStoreOwner`**, provided as
`LocalViewModelStoreOwner` around the screens. That matters as much as the
database: the screens ask for their view models by key, and in the activity's
store a second run would be handed the first run's, still wired to a database
that is gone. The database is closed three seconds *after* a run ends
(`CLOSE_DELAY_MILLIS`), on a scope of its own, so a query still in flight does not
meet a closed one.

**Every view model factory in `EtaApp` passes `clock = container.clock`** for the
same reason the container takes one. A new factory has to as well, or its screen
runs on the phone's hour inside the tutorial.

#### The simulated day

`TutorialClock` (`domain/tutorial/`) runs — a second is a second — but from an hour
it chooses: **10:30** on the real date, and **20:00** from the step marked
`TutorialStep.evening` on. The date is fixed at the start, so a tutorial begun at
23:58 does not roll into the next day. Which half of the day it is, is derived
from where the current step stands (`eveningFrom`), not set on the way past — a
step gone back to is at its own hour again.

**The evening is reached on the dashboard, by the user** (second round): the
step "Planungszeit" says it is 20:00 — the practice setup's own
`dailyPlanningTime`, so `PhaseBox` turns owed and moves to the top by the app's
ordinary rule — frames the box, and waits for "Tag abschließen". Before that
step the button is locked like every other way sideways
(`TutorialViewModel.closeDayPressed`).

The alternative, laying the examples around the real hour, was worked through and
dropped: it needs a different day for every hour and has none for the last one
before midnight, which is exactly when a new app gets tried.

- **`LocalEtaClock`** carries it to the two composables that read the time
  themselves: the dashboard's ticking "now", and `RepeatUntilRow`. Anything new
  that calls `Clock.System` in a composable should read the local instead.
- **The example day** (`tutorialItems`): Frühstück 07:30, Arbeiten 09:00–12:00,
  Katze füttern 12:00, Mail versenden 14:00, ids prefixed `tutorial:`.
  **All four are standing tasks**, and the mail has to be: the evening offers
  "Nachholen" only for a dropped *recurring* occurrence — a dropped ToDo goes back
  to the Sammelliste and is asked nothing. So it is a weekly mail on whatever
  weekday the tutorial runs, and the other three are daily, which also puts them
  on tomorrow for the planner to plan around.
- **The setup row is written directly** (`TutorialSeed`), not through
  `SetupRepository.complete`, which would add bed preparation and the morning to a
  day the texts say has four tasks. `tutorialSetup` is the questionnaire's draft
  with every page skipped, the points on, and the other Advanced Features on only
  in a tutorial about one (`TutorialId.advanced`).

#### Five tutorials (third round)

`TutorialId` (`domain/tutorial/TutorialCatalogue.kt`): **Quickstart**, **Extras**,
**Growth-Tasks**, **Verträge**, **Belohn-o-mat**. Each is a list of steps over the
same machinery; `TutorialViewModel.tutorial` says which runs, and `TutorialSeed.lay`
is told so it can add what that one needs.

- **The points are in the Quickstart, not a tutorial of their own.** The tracker
  is on for a new setup now, and on in the practice app, so the Quickstart frames
  the account ("Dein Punktekonto", which also says where to switch it off), says
  what a category pays, stops on the evening's settlement page ("Die
  Abrechnung"), and has the user turn to the second revolver before explaining
  Custom Earn and Custom Spend. Its last words point at the other tutorials.
- **The settlement of the example day is negative**, and the step says why: four
  short tasks leave most of the day unplanned, and unplanned time is charged.
  Deliberately not papered over with a seeded free-time block — the day the
  texts name has four tasks, and the charge is the mechanic.
- **A feature tutorial stays on its feature's tab** (`TutorialStage.GROWTH` /
  `CONTRACTS` / `REWARDS`) and has one `TASK`: make one. Done is a count read off
  the practice database. **Creating happens in a dialog, and a dialog is a window
  of its own that covers the coach** — so the step says everything the form will
  ask *before* it is opened, and says that the bar comes back afterwards. Nothing
  can be framed or gated inside a dialog; do not write a step that needs it.
- **The Extras tutorial runs on the evening's card** (`TutorialStage.EXTRAS`,
  the same `ConcretizeScreen`), for that reason: it is the one place the Extras
  box is not inside a dialog. A seeded note, "Zahnarzt", is what it is shown on.
  `ExtrasBox` wraps each extra in a box with a `tutorialSpot`, and reports
  `EXTRAS_OPEN` from inside the fold — composed only while it is open. The card's
  own buttons stay grey throughout, no step opening them.
- **Reached three ways**: the choice screen lists all of them under the
  Quickstart; the tutorial page of the settings does; and so does the Advanced
  Features page. From the settings a named one starts **at once**
  (`TutorialStore.request(id)`, `enter(direct = …)`) — no idea page, no list.
- The contracts tutorial does **not** walk into the evening to show the question
  being asked: whether a contract signed today is asked about today was not
  verified, and a step that waits for a page that may not come would hold the
  tutorial shut.

#### The script

`domain/tutorial/TutorialScript.kt` holds `QUICKSTART_STEPS`, pure Kotlin and pinned by
`TutorialTest`. A step names its stage (which screen is up), a title, a text, and
how it is left:

- **`TEXT`** — read, "Weiter".
- **`TASK`** — something to do on the screen above; "Weiter" is grey until
  `ready`. Its `checks` are listed with a ring or a tick.
- **`FOLLOW`** — **no button on the coach.** What it asks for is a button of the
  screen itself (its "Weiter", "Abschließen", "Woche steht"), and the tutorial
  follows by itself when `ready` turns true. Two buttons called "Weiter" a thumb
  apart, only one of them meant, was the alternative.

**"Done" is read off the practice database** wherever the database knows
(`tutorialFacts`: a tick, a card in the week, a block on tomorrow, `settledAt`), so
it means the thing happened and not that a button was pressed. Only what no table
holds comes in as a **signal** — which page of a pager is in front — through
`TutorialGuide.report`. `remembering` keeps "a note was made" and its name once
the note has left the Sammelliste by being filled in.

- **"Zurück" on the coach** goes to the step before, across screens too. Nothing
  is undone: what was ticked stays ticked, so a task come back to shows as done.
  A `FOLLOW` step that is already done when it comes up (`arrivedReady`) gets a
  "Weiter" of its own and does **not** follow by itself — it would throw the
  user forward again the instant they stepped back onto it.
- **A step must not be able to hold the tutorial shut.** The mail step is the
  example: should the "Nachholen?" question be gone without an answer, it
  cannot be put again, so `ready` lets go (`OPEN_MAKE_UP_OFFERS`). A tick on
  the mail is the opposite case and holds the step shut on purpose — it can be
  taken back, and the coach asks for exactly that. A new `TASK` needs the same thought — and "Beenden" is
  on every step regardless.
- **The screens' own ways out are kept and counted** (`exitTarget`): "Woche
  steht", "Zurück", "Rest später", the system back inside a phase. Pressed with
  nothing left undone in the stage, it moves to the next stage — skipping text
  that was not read — and in the last stage to the closing words first. Otherwise
  the coach says the task comes first. A screen that is already gone finishing
  its work (the Tagesabschluss calling `onClose` after the tutorial moved on) is
  ignored rather than answered with a hint.
- **Everything else that would leave is locked** (`TutorialViewModel.locked`):
  "Heute umplanen", `PhaseBox`'s buttons, "Wochenliste füllen", the system back.
  No tab bar is drawn at all — `TutorialHost` stands in place of `MainScaffold`.

**Where the script differs from `update.txt`, because the app does:** the catch-up
lands in the **Sammelliste**, not the week list; filling in a Quick-Add asks for
**no note** (still an open question in `CLAUDE.md`); the weekly planning opens on
the **Wertverfall** and its **Rückblick** page; the Tagesabschluss's steps are not locked one by one. The
texts say what the screens do.

#### Gates: the screens' own buttons wait for their step

Asked for after the first round on a phone: a screen's "Weiter" or "Übernehmen"
pressed early left the screen a page ahead of the coach. `TutorialStep.allow`
names the buttons a step opens (`TutorialGate`), `TutorialGuide.allowed` carries
them, and a button asks `tutorialAllows(gate)` — **true outside the tutorial**.
Everything not named is grey:

- Tagesabschluss: "Weiter" and the pager swipe (`REEVALUATION_NEXT`, only once
  every block is answered and excused), "Abschließen", "Fällt aus" **per row**
  (the gate carries the item id, and only the mail's is ever opened), and
  "Lassen" under "Nachholen?", which no step opens.
- Quick-Adds: "Übernehmen" per kind of card, "Löschen" never, and the button
  under the cards.
- "Woche steht", and "Bestätigen" in the planner — confirming puts the revolver
  away, which would leave the drag the step asks for impossible.

`TutorialTest` pins which step opens which gate. **A new button that moves a
tutorial screen on needs a gate**, or it is the old desynchronisation again.

The step after the mail is **höhere Gewalt**: hold the cancelled row, give a
reason; done is `forceMajeure` on the block.

#### The hooks in the real screens

Kept to four kinds (the gates above being the fourth), all no-ops outside the tutorial (`LocalTutorialGuide` is
null):

- **`Modifier.tutorialSpot(id)`** frames the part a step points at and scrolls it
  into view. The frame is drawn **outside** the bounds, so nothing shifts when it
  comes and goes. On: the "now" box, Quick-Add, "Heute anstehend", four ToDo
  fields, the weekdays, "Wiederholen bis", and the Wochenschema.
- **`ReportToTutorial(key, value)`** in `NowBox`, `ReevaluationScreen` and
  `WeekPlannerScreen`.
- **`WeekPlannerScreen` takes a nullable calendar view model**; null drops the
  calendar step. The practice week has no calendar, and that page would otherwise
  offer to connect the user's real Google account.

`rememberFold` neither reads nor writes the user's folds inside the tutorial: a
list they folded away would be missing from the walk-through.

**One change to the app itself fell out of it:** the evening's recurring card now
shows the **Wochenschema** (`SchemePreview`, made `internal`), as the Listen tab's
editor always did — the script describes it as part of filling a recurring note
in, and it belongs there. `ConcretizeViewModel` reads the setup for it, so
"Nächster freier Slot" on that card now treats the night as taken, too.

#### When it is shown

`TutorialStore`, in `SharedPreferences` like the design: it has to be known before
there is a setup row, and it is a fact about this install. Not in a backup.

- **First open**: `TutorialIntroScreen` *before* the questionnaire (`introSeen`),
  which sets `owed`. Once the setup exists, `EtaApp` shows `TutorialHost` in place
  of the scaffold: Quickstart, "Ausführliches Tutorial" greyed out, or
  "Überspringen".
- **Someone updating an app they already use is not marched through it** — they
  have a setup and no flag — and finds it in the settings.
- **"Tutorial wiederholen"** is the last row of the settings menu, under Debug.
  `requested` is not persisted; it shows the idea page again first.
- **The debug reset re-arms all of it**, welcome page included.
- **A process death mid-run starts over** at the choice (first run) or returns to
  the app (repeat): the practice database lived in memory.

**Verified by the compiler (`--rerun-tasks`), the suite (564, 33 in
`TutorialTest`), `assembleRelease` and `lint`.** Nobody has seen it, and more than
usual rests on that here: whether an in-memory Room database opens on a phone,
whether the frame and the scroll-into-view land where they should, how much of a
small screen the coach takes, and whether the keyboard leaves the Quick-Add box
visible above it.
