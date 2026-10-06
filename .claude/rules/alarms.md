---
paths:
  - "app/src/**/alarm/**"
  - "app/src/**/domain/planning/PlanningSchedule.kt"
  - "app/src/**/domain/planning/TaskStart.kt"
  - "app/src/main/AndroidManifest.xml"
  - "app/src/main/res/raw/**"
  - "sounds/**"
---

### The planning alarm, and why one can be silent

`domain/planning/PlanningSchedule.kt` holds the arithmetic — when a phase is next
due, what a snooze and a "NOTFALL" do — and is the part worth testing; `alarm/`
holds the Android side.

- **`setAlarmClock`**, because this *is* an alarm the user set: it survives doze and
  shows in the status bar, so an alarm that will interrupt the evening is never a
  surprise. `setWindow` is the fallback where exact alarms are refused — late beats
  silent.
- **Every path reschedules**, including the ones where nothing rings. An alarm that
  decides to stay quiet and forgets to set the next one has switched the whole
  mechanism off, and the failure would be invisible for days. Alarms do not survive
  a reboot, hence `BootCompletedReceiver`.
- **"Done" is read off what the phase leaves behind**, never a flag of its own: the
  daily phase ends by confirming tomorrow's `DayPlan`, the weekly one by booking the
  devaluation. See `PlanningPhaseService`.
- `nextPlanning` is **strict** about "after": firing at the appointed minute must not
  be able to schedule the same moment again and loop.
- The emergency deferral is **clamped** to `MAX_EMERGENCY_HOURS`. Pushing a phase a
  whole day back is not deferring it, it is skipping it. It opens `DeferDialog`
  rather than guessing, since a notification button cannot ask how many hours.
- Foreground is counted in `EtaApplication` by started activities — the alarm needs
  exactly the one fact that the user already has the app open.

The scheduling logic was never the problem. **Every way Android suppresses these
alarms is silent**, and the app used to have no way to say which one had happened:

- **`POST_NOTIFICATIONS` refused** — the alarm fires and reschedules, the banner
  throws into a `runCatching`, nothing appears and nothing is logged.
  `NotificationPermission()` asks once; Android stops asking after two dismissals.
  Since step 18 this costs the banner and **not the sound**.
- **`SCHEDULE_EXACT_ALARM` denied** — the likely one: from API 33 it is *denied by
  default*, and `targetSdk` is 37, so `canScheduleExactAlarms()` was false on any
  modern device and every alarm fell back to `setWindow`, which doze can push well
  past its hour.
- **The manufacturer's battery manager** stopping the app outright.

Two answers, both needed. **`USE_EXACT_ALARM` in the manifest**, beside
`SCHEDULE_EXACT_ALARM` capped at `maxSdkVersion="32"` — the pair Android wants: the
old permission is granted at install up to 32, the new one from 33, and neither
needs anything from the user. `USE_EXACT_ALARM` is reserved by **Play Store policy**
for apps whose core function is an alarm clock or calendar; Eta sets a wake alarm
and two phase alarms, so it qualifies on the merits, but the claim would have to be
made in a listing if this were published. And an **"Alarme" card in the settings
tab**, from `alarm/AlarmReadiness.kt`: it names all three states, offers the system
screen that fixes each, and prints when each alarm is next due — without that, an
alarm armed for tomorrow cannot be told apart from one that was never set. It reads
the states on demand rather than watching them, since they change in the system
settings, which means leaving the app, and coming back is the moment to look.

### Sounds, and the alarms that announce a task

The user supplied the mp3s in `Erik_2/sounds/`. Android can only play a notification
sound from a resource or a content URI, so they are **copied** into
`app/src/main/res/raw/` — `sounds/` stays the source folder, `res/raw/` is what
ships. Re-copy after changing one; nothing watches the folder.

**Only Do-Not-Disturb silences Eta — and since step 30 not even that, if the user
says so.** "Bei „Nicht stören“ → Trotzdem spielen" in the Alarme card sets
`EtaSound.ignoresDoNotDisturb`, and `isSilenced` — what `play` and `EtaSpeech.speak`
ask — is then false. It is the app's *own* check being switched off, nothing more:
no permission is involved, and what the phone itself mutes stays muted. The alarm
stream gets through priority-only and alarms-only; **total silence mutes it too**,
and overriding that would need notification-policy access and changing the user's
filter, which was not built. The switch lives in `SharedPreferences` ("sound"),
because `EtaSound` is called from receivers with a context and nothing else — so it
applies at once and does not travel in a backup.

`alarm/EtaSound.kt` plays every file itself
with `MediaPlayer` on `USAGE_ALARM`, so the notification volume is irrelevant and
the *alarm* slider governs it — the Alarme card says so. DND is checked by hand,
because the alarm stream cuts through most DND configurations: any interruption
filter other than "all" counts as on. Music ducks for the length of a sound
(`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`). The coordinators play **one sound per kind
per ring**, so two tasks starting on the same minute are not twice as loud.
`postNotification` replaced every `runCatching { notify(...) }` with an explicit
permission check, which is also what turned `lint` green.

**A channel's sound is fixed when the channel is created** — `setSound` on an
existing `NotificationChannel` is ignored — so every change to one needs a **new
channel id**, with `deleteNotificationChannel` on the old one so no dead entry
lingers in the system settings. That is why the ids read `planning_v3`,
`task_start_v2`, `task_end_v2`: they carried sounds until step 18 and are silent
now, `ensureSilentChannel` deleting their predecessors. `NotificationCompat.setSound`
is still set on the builder, ignored from Android 8 on, for the 7.x devices
`minSdk 24` still covers.

`planning_start.mp3` plays on **every** ring of a planning phase, the five-minute
retries included: "planmäßig" reads as "at its scheduled time, as opposed to
deferred", and the retries exist to nag. Splitting them would need a second channel.

**One task alarm, not one per block.** `alarm/TaskStartAlarm.kt`,
`TaskStartCoordinator.kt` and `TaskStartNotifications.kt` hold the Android side,
`domain/planning/TaskStart.kt` the part worth testing (`nextTaskEvent`, `eventsAt`,
`blocksStartingAt`). It is always aimed at the next *event* — start, planned end,
still-active question or pomodoro boundary, whichever comes first — because a day
of ten blocks would otherwise mean ten pending intents to keep in step with every
edit. What it does differently from the planning alarm:

- **`setExactAndAllowWhileIdle`, not `setAlarmClock`**: the latter puts an entry in
  the status bar's alarm slot, and rewriting that ten times a day turns a useful
  signal into noise. The inexact fallback is a five-minute window rather than ten,
  since a nudge is worth less the later it comes.
- **It does not go quiet while the app is open.** The planning alarm's exemption
  exists because a planning phase is something you do *in* the app. A task
  beginning is about the world, and looking at the screen is no evidence at all.
- **The minute it was laid down for travels in the intent**, so an alarm delivered
  late — the inexact window, or doze — announces the right thing rather than the
  nearest thing. `blocksStartingAt` returns a **list**: two things starting on the
  same minute is ordinary, and announcing one of them would be arbitrary.
- **Only open blocks.** One already ticked off or dropped has been dealt with.
- **Today and tomorrow are both read**, or the first block after a late evening
  would be silent.
- **Every block announces itself**, the frame of the day included — that is what "a
  new activity begins according to the plan" says. `ItemRole` is the handle if it
  turns out to be too much.
- **`task_end.mp3` announces the *planned end*, not a completion**: it rings only if
  the block is still open at that minute. `Item.endSound` is the switch, offered
  wherever a card is filled in and **on by default for new cards**.

**Rescheduling on every path**: app start, `RootViewModel.rescheduleAlarms` (which
runs on leaving every flow, so it covers the planner, the reevaluation and the
holiday editor), the boot receiver, and after every ring. The one edit that happens
*outside* a flow is on the dashboard — a start time changed in "Heute anstehend",
or a block ticked off — so `DashboardViewModel` re-aims directly rather than waiting
for the next flow change.

Two limits worth knowing: **doze throttles `setExactAndAllowWhileIdle`** to about
one alarm every nine minutes per app, so two blocks closer together than that can
have the second announcement arrive late on a sleeping device. And nobody has heard
a sound through `EtaSound`, including whether a receiver's process lives long
enough for `task_start.mp3` to finish — it is 24 seconds long and `goAsync` returns
long before that. `MediaPlayer` renders in the media server, which should carry it;
test that on the phone first.

### Reading a task's name aloud (step 28)

`UserSetup.taskAnnouncement` chooses between the mp3s and the device's speech
engine for everything the **task alarm** says — start, planned end, the pomodoro
turns, the still-active question. `speakNotes` adds the notes. Both sit in the
"Ansagen" card of the settings tab and are saved with "Einrichtung sichern".

- **Only the task alarm.** The planning alarm, the Erinnerungen tab and the wake
  alarm keep their sounds: none of them has a task's name to read. A reminder's
  own text would be the natural next thing to read out, and nobody asked yet.
- **`alarm/EtaSpeech.kt` follows `EtaSound`'s rules**, because it stands in for
  it: `USAGE_ALARM`, so the alarm slider governs it, silent under Do-Not-Disturb
  and under nothing else, music ducked for the sentence. German voice where the
  engine has one; otherwise the engine's own rather than silence.
- **Every task is named**, unlike the sounds' one-per-kind: the name is the point.
  `spokenAnnouncement` in `domain/planning/TaskAnnouncement.kt` builds the
  sentence and is what `TaskAnnouncementTest` pins.
- **Notes only at the start** — the definition's, then the day's own. At the end
  or inside a pomodoro they would be the same paragraph again.
- **No engine, no silence.** `speak` returns false when the engine cannot be
  bound within three seconds or refuses the sentence, and the coordinator plays
  the sound it would have played. "Probe hören" in the card exists because
  whether a phone has a usable voice can only be heard.
- **`reschedule()` now runs before the announcement**, not after: a sentence takes
  seconds, and the booking is the half that must not be lost.
- **The manifest needs `<queries>` for `android.intent.action.TTS_SERVICE`**, or
  from Android 11 the engine reads as not installed and everything falls back to
  the sounds without a word.

**The limit, untested like every sound here:** the alarm receiver holds its
`goAsync` for at most the three seconds of binding plus five of speech. A sentence
longer than that keeps being read only while Android leaves the process alone,
which the speech engine being a *bound* service makes likely but not certain on a
sleeping phone. If long notes are cut off, a short foreground service is the fix.

### The wake alarm

`UserSetup.wakeAlarm` is the switch, `wakeTime` the hour. **No second time**,
deliberately: the hour the planner shades as the end of the night and the hour the
phone rings at cannot then drift apart. Off by default. This does not have to go
through the Android clock app; what it takes:

- **`setAlarmClock`**, unambiguously right here: it *is* an alarm clock, it is exempt
  from doze, and the next-alarm entry in the status bar is a feature rather than
  noise — being able to see that it is armed is half the reassurance. (The task
  alarm avoids it for exactly that reason inverted.)
- **A full-screen-intent notification, not `startActivity`**: a receiver may not
  start an activity from the background on Android 10 and later. The full-screen
  intent is the path the system keeps open for alarms, and it degrades honestly —
  where it is refused the notification is still posted, so the alarm becomes a loud
  heads-up instead of nothing.
- **`WakeAlarmRinger` is a process-wide object, not part of the screen.** Sound and
  screen come apart in three ordinary ways: the full-screen intent is refused, the
  activity is rotated and rebuilt, or the user presses "Aus" on the notification,
  which is a receiver. A ringer owned by an activity keeps playing after that
  activity is gone, which is the one failure a wake alarm must not have. `start` is
  idempotent so a second route to the same ringing does not layer a second player.
- **The sound loops, on the alarm stream.** A channel sound plays once and stops,
  which is a notification, not an alarm — so the wake channel is silent on purpose
  and the ringer does the playing. The tone is the **device's own alarm ringtone**:
  what the user recognises, built to be looped, and what their volume keys are
  aimed at. Swapping in a file is one line in `WakeAlarmNotifications.soundUri`.
  This is the one sound DND does not silence: an alarm clock that stays quiet under
  DND is not an alarm clock.
- **The notification is `ongoing` and back does nothing.** An alarm that can be
  swiped away half asleep without answering it is not an alarm.

**What the system clock app still has that this does not:** OEM allow-listing.
Samsung, Xiaomi and others exempt their own clock unconditionally, so the
"Akku-Optimierung" row in the Alarme card matters more here than anywhere else in
the app. If the wake alarm proves unreliable on this phone, that row is the first
thing to check, and the honest fallback is the system clock.

**The weekend can ring later** since step 32: `nextWake` asks `wakeTimeOn(weekday)`
day by day, so a `weekendNight` in the setup moves Saturday's and Sunday's alarm with
the hour the planner shades. Still no time of the alarm's own — the drift that
design avoids stays avoided. One weekend time for both days; a time per weekday
would be the next step and nobody asked.

