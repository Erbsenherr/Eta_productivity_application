---
paths:
  - "app/src/**/ui/settings/**"
  - "app/src/**/data/backup/**"
  - "app/src/**/ResetDao.kt"
---

### The settings tab

`ui/settings/` holds the standing configuration, away from planning a day. **The
questionnaire's own step composables are reused**, not reimplemented: they are
already `(draft, onChange)` pairs, so every answer is asked for in exactly one place
and cannot drift between the two screens. The draft stays null until the stored
answers arrive, so there is never a set of defaults on screen that the user might
save by accident.

**Since step 18 only the answers that are configuration are here**: sleep and
morning, the wake alarm, social time, the still-active question, the planning times,
the four "Advanced Features" switches (`AdvancedFeaturesBox` — see *Step 36*; they
apply at once, and the points one folds the next two away), inflation and the cancellation rate. Meals, housekeeping, sport, free time,
mindfulness and work are standing *tasks* and are edited on the Listen tab. Saving
goes through `SetupRepository.saveSettings`, which regenerates only bed preparation
and the morning (`SETTINGS_OWNED_ITEM_IDS`: `setup:bedprep`, `setup:morning`), never `complete` — calling `complete`
here would overwrite every edit made on the Listen tab. Social time is not a task,
so it has a `SocialTimeBox` of its own, and `SettingsMessage.Saved` lost its conflict
count, `UserSetup.conflicts()` having described answers that no longer describe the
schedule.

The **Design** card at the top chooses the look and applies at once, outside the
draft — see *Step 30*. The tab also holds the Alarme card (*The planning alarm*), the ignored calendar
events, vacation mode, the open-days catch-up and the debug reset.

**Where the data lives.** `BackupService` writes the whole database to a file the
user picks and reads it back. The live database does **not** move, and the screen
says so: scoped storage hands out document URIs and SQLite needs a path it can lock,
so an app cannot keep a working database in a folder of the user's choosing, and a
setting that looked like it moved the database and did not would be worse than none.
An automatic backup to a remembered folder is the natural next step and would need a
persisted tree URI. Two things the backup gets right that are easy to get wrong: all
three SQLite files travel together in one zip, because copying only `erik.db` while
write-ahead logging is on hands back a database missing the newest writes — precisely
the ones the user just made; and a restore only overwrites the files this app owns, a
zip entry naming a path elsewhere being ignored. Afterwards the app must be
**restarted**, because Room still holds the old files open, and the screen says so
rather than pretending the swap took effect.

**Debug reset** wipes every table via `ResetDao`, which thereby sends the app back to
the questionnaire. `ResetDao` lists its tables by hand on purpose, so a new table is
a visible omission rather than something that silently survives a reset. Note that
view models outlive the screen swap: anything the setup view model latches (its
`saving` flag) has to be cleared, or the reset comes back to a stuck questionnaire.

