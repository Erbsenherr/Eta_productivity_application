---
paths:
  - "app/src/**/alarm/EtaSound.kt"
  - "app/src/**/alarm/WakeAlarm*.kt"
  - "app/src/**/domain/sound/**"
  - "app/src/**/ui/settings/SoundChoicesBox.kt"
  - "app/src/**/ui/theme/EtaColors.kt"
---

### Step 51: every sound chosen, and a negative balance in red

Asked for directly. **No schema change** — the choices are in `SharedPreferences`.

#### A sound per moment

**`SoundEvent`** (`domain/sound/SoundChoice.kt`) is every moment Eta makes a
sound, thirteen of them: the six task events (`TaskEventKind` maps one to one,
`TIMER_DONE` included), the planning phase, a reminder, the Belohn-o-mat's two
and the streaks' two (still the placeholders of steps 35 and 48 as their
defaults), and **the wake alarm**. Each has a `default` — the file it played
before — null for the wake alarm, whose default stays the phone's alarm tone.

- **`SoundChoice`**: `Default`, `Eta(EtaSoundFile)` — one of the seven files in
  `res/raw`, any of them for any moment — or `System(uri)`, picked in Android's
  own list (`RingtoneManager.ACTION_RINGTONE_PICKER`, `TYPE_ALL`, neither
  "Standard" nor "Lautlos" offered: silence is the Audio mode's business).
- **Stored by name, not by resource id** (`eta:TASK_END`), since `R.raw` ids
  are not stable across builds; `rawOf` in `EtaSound.kt` maps the names. The
  default is stored as nothing, and **anything unreadable decodes to the
  default, never to silence**. Pinned in `SoundChoiceTest`.
- **`EtaSound.play(context, SoundEvent)`** replaced `play(context, @RawRes)`:
  every sound now goes through a URI (`uriOf` — `android.resource://` for Eta's
  files), and **a choice that will not play falls back to the default** — a
  system sound can vanish with an update, or lie on storage Eta may not read.
  The wake alarm does the same in `WakeAlarmRinger`: the phone's alarm tone
  stands in, so a broken choice never means a silent morning.
- Mode, DND and the ringer still decide *whether* a sound plays; this only
  decides *which*. The wake alarm still asks none of them.

**On the "Audio" page**, `SoundChoicesBox` below the announcement: a row per
moment with what it plays, a tap opens `SoundPickDialog`. Choosing an entry
plays it (`EtaSound.preview`, which ignores mode and quiet — it was asked for —
and stops the one before); nothing is kept until "Übernehmen". Applied at once,
outside the draft, like the mode. **Not in a backup**, as nothing in the
"sound" preferences is.

#### A negative balance in red

`EtaColors.alertRed` — `danger` by day, `AlertRedOnDark` by night — is what
"Läuft bald ab" used by hand since step 48, now a property. The `PointsBox`
balance below zero uses it, and so does its "heute erarbeitet" when negative.
**`danger` itself is unchanged**: by night it stays the rose, for the locked
contract slots and everything else that wears it.

**Verified by the compiler (`--rerun-tasks`), the suite (734), `assembleRelease`
and `lint`.** Nobody has heard any of it — whether the system picker lists all
three kinds under `TYPE_ALL` differs between phones.
