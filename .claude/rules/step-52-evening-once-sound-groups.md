---
paths:
  - "app/src/**/alarm/EtaSound.kt"
  - "app/src/**/alarm/EtaSpeech.kt"
  - "app/src/**/alarm/AlarmStreamVolume.kt"
  - "app/src/**/alarm/WakeAlarmRinger.kt"
  - "app/src/**/domain/sound/**"
  - "app/src/**/ui/settings/**"
  - "app/src/**/ui/rewards/**"
  - "app/src/**/*Reward*.kt"
  - "app/src/**/ui/planner/**"
  - "app/src/**/domain/planning/UnplannedTime.kt"
  - "app/src/**/domain/setup/SetupItems.kt"
  - "app/src/**/SetupRepository.kt"
  - "app/src/**/ui/dashboard/**"
  - "app/src/**/ui/contracts/**"
---

### Step 52: sounds in two groups, the Abendroutine, one-off rewards, unplanned hours, the Termin

Eight items from `update.txt`. Database **version 35**. Four questions went to
the user first; their answers are marked.

#### Alarme and Benachrichtigungen

**This overrules step 44's "with DND off and the ringer muted there is no way
round"** and the single DND switch of step 30.

- **`SoundGroup`** (`domain/sound/SoundRules.kt`): `ALARM` is the wake alarm
  and the planning phases, `NOTIFICATION` everything else — task events,
  pomodoro, timer, reminders, Belohn-o-mat, streaks, and the names read aloud
  (asked). `SoundEvent.group` maps them.
- **`GroupAudio`** per group: `throughDoNotDisturb`, `throughSilentRinger` —
  **every combination**, as asked — and `constantVolume` (percent, null = "wie
  das Handy"). In `SharedPreferences` ("sound", keys `group.<GROUP>.dnd` /
  `.silent` / `.volume`); a group not yet answered inherits the old
  `ignoreDoNotDisturb` for its DND switch. `mayPlay` keeps step 44's rule
  that **under DND the DND switch alone decides**.
- **Volume.** Every sound still rides the alarm stream, so a volume of its own
  means moving the alarm slider for the length of the sound:
  `AlarmStreamVolume` holds it (several overlapping sounds share one hold),
  puts the user's level back after the last, and writes the borrowed level
  down so `EtaApplication` restores it after a process died mid-sound.
  `playbackVolume`: constant → that; bound alarm → slider left alone; bound
  notification → **the notification slider's position**, so a notification
  volume of 0 means silence, as "gebunden" promises. Zero is never played.
  Known price: the phone's own alarm clock ringing during one of Eta's sounds
  rings at Eta's volume for those seconds.
- **The wake alarm still rings through all quiet**; only the Alarme group's
  constant volume reaches it, held for the whole ringing.
- `EtaSpeech` is a notification: its quiet and its volume.
- **UI**: `SoundGroupFields` (`ui/settings/SoundGroupBox.kt`), mounted in the
  Alarme card and on the new page **"Benachrichtigungen"**
  (`SettingsPage.NOTIFICATIONS`, after Alarme). Applied at once, with "Probe
  hören" (`EtaSound.previewGroup`, at the unsaved volume). Previews on the
  Audio page play at their group's volume too.

#### The Abendroutine

"Bettfertig machen" **is** the Abendroutine (asked): `SetupLabels.BED_PREP` is
"Abendroutine", generated with `routineMode`; hour and length still hang off
the night (`bedPrep` → sleep). `SetupRoutine` (`MORNING`, `EVENING`) names the
two routines' rows (`owns`, `of`) and replaced `isMorningRoutine` everywhere
but one wrapper; `SetupRepository.observeRoutineSteps` / `saveRoutineSteps`
replaced the morning's, and `saveSettings` carries both routines' steps across
a row swap. Steps are optional and edited in the questionnaire (sleep page),
in the settings ("Schlaf und Morgen", `RoutineBox`) and on the Listen tab
(`RecurringGroupDialog(routine = …)`); `SetupRoutineSteps` is the one builder.
The migration renames rows still called "Bettfertig machen" and switches the
mode on for every `setup:bedprep` row. **Not built**: an evening counterpart to
"Guten Morgen" being ended by the routine (`morningDone`).

#### A routine ticked off in "Heute anstehend"

With steps still open, the tick asks `RoutineTickDialog`: "Ja, alle Schritte"
ticks every open step and then the block (`DashboardViewModel.completeRoutine`)
— the evening finds the routine done; "Nur die Routine" is the old tick, the
evening asking about the steps. Any task in Routine-Modus, not only the morning.

#### One-off rewards ("Einmalig")

"Belohnung erstellen" asks first (`RewardKindDialog`): **Einmalig** or **Über die
Tagesabrechnung** (the old path, unchanged). A one-off (`Reward.once`) has a
`condition` in words and optionally `dueAt` (date + hour, `OnceRewardDialog`);
`cost` 0, no binding.

- **It collects nothing**: `isFilling` is false, so `pourInto`, `claimsOf` and
  `RewardOutlook.head` pass it over; `rolesOf` gives it `RewardRole.GOAL`.
- **Reached is `achievedAt`** — the card's "Ziel erreicht" at any time (asked),
  after which it is `isFull` and "Einlösen" works as for any reward.
- **Its date**: once `dueAt` has passed (`isDue`), the tab shows
  `RewardDueDialog` — "Ja, erreicht" (`achieve`), "Nein — Belohnung verfällt"
  (`expire`, `expiredAt`: asked) or "Später", which asks again on the next visit.
  It is a popup **in the tab**, not a system notification.
- An expired one goes into the folded box, "Verfallen am …"; `isPast` is
  redeemed or expired.

#### Unplanned hours in the day planner

`UnplannedLine` under the date: "Unverplant: 3 h 30 min", from
`unplannedMinutes` (`domain/planning/UnplannedTime.kt`: the waking day less
every block not called off, overlaps once — the evening's arithmetic). Red past
`FREE_UNPLANNED_HOURS`, green up to and at it, **grey on a weekend day**
(`weekendDays`) unless the new Flexibilität switch `UserSetup.weekendPlanning`
is on. **It colours the number and nothing else** — the evening still charges
unplanned time on a weekend as before.

#### The Termin

A fourth answer in `NewEntryKindDialog`: "Termin", **never refused by the
week-first rule** (it is for what turns up after the week stands). The same form
as "Neues ToDo" (`NewTodoDialog(appointment = true)`), the same item, laid down
as **`BlockOrigin.APPOINTMENT`**: not `isMovable`, not `isHandPlaced` — so it is
drawn square, cannot be dragged, its start cannot be changed in "Heute
anstehend", and taking it off the day calls it off rather than returning it to
the week list. Called off, it is an ordinary ToDo to `consequenceOf` (back to
the Sammelliste). The Sperrliste still applies to its name.

#### Contracts

The field "Vertragsbruch" is **"Ausnahmen, Sonderfälle"** ("Wann gilt der
Vertrag nicht?"), optional as before. The column stays `breachDefinition`;
what a breach costs is unchanged.

**Verified by the compiler, the suite (746; 12 in `Step52Test`),
`assembleRelease`, `lint` and the migration against `35.json`.** Nobody has seen
or heard any of it — whether a phone lets `setStreamVolume` move the alarm
stream under every DND setting least of all.
