---
paths:
  - "app/src/**/ui/components/PointsVisibility.kt"
  - "app/src/**/ui/components/EtaDatePicker.kt"
  - "app/src/**/ui/settings/**"
  - "app/src/**/ui/attributes/**"
  - "app/src/**/domain/staging/**"
  - "app/src/**/domain/recurrence/RecurringSchedule.kt"
  - "app/src/**/data/repository/RecurringTaskService.kt"
---

### Step 36: Advanced Features, "Freigeschaltet ab", and hours of their own

Seven items from `update.txt`. Database **version 25**. No questions were put this
time; the decisions that were mine are marked.

#### Advanced Features

One box in the settings (`AdvancedFeaturesBox`, where "Punktesystem" was) with four
switches: **Punktetracker** (`UserSetup.pointsSystem`, unchanged in what it does —
see *Step 35*), **Growth-Tasks** (`growthTasks`), **Verträge** (`contracts`) and
**Belohn-o-mat** (`rewards`).

- **All four are off for a new setup**, the points included, so a newcomer finds
  four tabs. **Every existing row is switched on by the migration** — mine to
  decide: the user asked for "off by default", but nobody with contracts running
  should find the tab gone after an update. Switching them off is one tap each.
- **A switch hides and stops nothing**, like the points: a growth task goes on
  growing, a reward goes on filling, and a running contract counts as **kept**
  every evening — the default an unanswered question always had — and is paid.
- **`LocalFeatures`** (a `Features` value, beside `LocalPointsVisible`) carries the
  other three. What reads it: `EtaTab.isShown` for the three tabs, the evening's
  `Step.CONTRACTS` and `Step.REWARD_FILL`, and `RecurringAttributeFields`, which
  drops the Growth-Task extra — **except on a task that already grows**, which has
  to stay reachable so growing can be switched off.
- **Applied at once**, not with "Einrichtung sichern": `SettingsViewModel.setFeature`
  writes through `SetupRepository.update`, which changes the *stored* row and
  regenerates nothing, and updates the draft too so the next save does not write
  the old answer back. This is also the fix for the reported bug — the account
  still showing with the tracker switched off: the switch sat in the draft and did
  nothing until the save button further down was pressed.
- `EtaApp` waits for the setup row before drawing the scaffold, or the tabs that
  are switched off would flash for a frame. A saved tab whose feature is off falls
  back to Heute (`savedTab` / `tab`).
- The questionnaire's free-time page drops its sentence about points while the
  tracker is off, which it is for everyone going through it the first time.

#### "Freigeschaltet ab" replaces the Zieldatum

`items.targetDate` keeps its column name and **changes its meaning**: it was the
day a ToDo was aimed at, unlocking a week before; it is now the unlock day itself.
`Item.availableFrom` is simply `targetDate`.

- **Null is "Heute"**, and the default. `TodoAttributes.unlockFrom` replaced
  `inDays`; the form stores no date at all for today, so finishing a note that has
  waited three weeks does not move its clock.
- **The one-month clock to the Sperrliste starts at the unlock day** where that is
  later than `enteredCollectionAt` — `Item.collectionClockStart` in
  `CollectionSweep.kt`, which `isStaleInCollection` and `banDate` both read. The
  two SQL queries still filter on `enteredCollectionAt`; that is a superset, and
  the callers narrow it. A card that says "not before March" is not banned in
  February for not having been planned.
- **`TodoAttributes.of` keeps a past unlock day** rather than reading it as today:
  the clock counts from it, and an edit that dropped it would wind the clock back.
- **Yes, this makes the ban evadable** by setting a far unlock day. It is what was
  asked for, and it is at least a visible act with a date on it.
- **The migration moves every waiting ToDo's date a week earlier** (`COLLECTION`
  and `WEEK` only), so each unlocks on exactly the day it would have.
- **`EtaDatePickerDialog`** (`ui/components/EtaDatePicker.kt`) is the app's first
  date picker: a month grid, ‹ › for the month, « » for the year, tap a day to
  choose it. `monthGrid` is the arithmetic and `MonthGridTest` pins it. The day
  steppers elsewhere (reminders, deadline, "Wiederholen bis") were left as they
  are.

#### Abweichende Uhrzeiten

A checkbox under the weekdays of every recurring form, shown once more than one
day is picked. Ticked, each weekday gets its own "Beginn am …" picker.

- **`RecurringAttributes.startTimes`** is null while unticked; `ownStartTimes` is
  what is really asked for (none for a single weekday), and travels as
  `RecurringEdit.startTimes` / the `startTimes` parameter of `concretizeRecurring`.
- **`rulesWithTimes`** (`RecurringSchedule.kt`, pinned in `RecurringScheduleTest`)
  is the one place that turns weekdays and hours into rules: the ordinary rules
  when the hours agree, **one rule per weekday when they differ — even for all
  seven**, since a daily definition has one start time.
- **Days with an hour of their own become separate entries** in the Wiederkehrend
  list afterwards: `groupRecurring` groups by start time, and that is the model
  doing what it was built for. The form says so before it is saved.
- "Nächster freier Slot" stands down while the hours differ; one free hour for
  every weekday is not an answer to a form that asked for several.
- The three hand-built `RecurringEdit`s became `attributes.toEdit(name, note)`, so
  a new field cannot be forgotten at one of them.

#### Also

- **The Sperrliste is the last list** on the Listen tab, after Termine.
- **"ⓘ Was zählt als Freizeit?"** on the questionnaire's free-time page opens
  `FreeTimeInfoDialog`: free time is unplanned time, not a liked task that should
  happen regularly.
- **`patchnotes/`** — see `CLAUDE.md`.

**Verified by the compiler (`--rerun-tasks`), the suite (525), `assembleRelease`,
`lint`, and the migration's columns against `25.json`.** Nobody has seen any of it.
