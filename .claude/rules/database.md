---
paths:
  - "app/src/**/data/local/**"
  - "app/schemas/**"
  - "app/src/**/domain/model/**"
---

### The database and its migrations

**Version 25.** When changing an entity, diff the hand-written SQL against the
matching `app/schemas/…/N.json`: Room validates at open time and a mismatch is a
runtime crash, not a compile error. A new *enum value* needs no migration at all —
Room stores enums by name in a TEXT column.

| version (each `MIGRATION_<from>_<to>`) | adds |
| --- | --- |
| 1→2 | `user_setup` |
| 2→3 | recreates it **empty**: the lunch answer moved into the work one and the old row could not be converted, since where the break sits inside the working day simply is not in it |
| 3→4 | `contracts`, `journal_entries` |
| 4→5 | `items.role` |
| 5→6 | the two `note` columns |
| 6→7 | the holiday tables |
| 7→8 | `day_plans.settledAt` |
| 8→9 | the unique index on `planned_blocks(itemId, date)`, after clearing the duplicates it would otherwise choke on |
| 9→10 | `items.weekStartedOn` |
| 10→11 | `user_setup.inflationDay` |
| 11→12 | `user_setup.wakeAlarm`, defaulted to 0 — nobody who has been using the app agreed to be woken by it |
| 12→13 | `travelBefore` / `breakAfter` on both tables, `items.endSound` (`NOT NULL DEFAULT 0`), `contracts.editedAt` |
| 13→14 | `returnAfter` on both tables, `planned_blocks.forceMajeure`, `user_setup.cancellationPenaltyPerHour` (`REAL NOT NULL DEFAULT 1.0`, the rate just agreed) |
| 14→15 | the two Google-Calendar tables |
| 15→16 | `reminders`, the three pomodoro columns on blocks, `user_setup.stillActiveReminder` (0) / `stillActivePerDay` (3) |
| 16→17 | the Extras columns on `items` (pomodoro default, `reminderLeadHours` / `reminderMessage`, the five growth fields, `growthDynamic NOT NULL DEFAULT 0`), `reminders.itemId` / `blockId`, the `conflict_dismissals` table |
| 17→18 | `subtasks` and `subtask_checks` — see *Step 23*. Nothing on `items`: a card with no steps is a group with an empty list |
| 18→19 | `items.growthEvery` (1) / `growthProgress` (0), and the Mengen-Inkrement: `quantity`, `quantityStart`, `quantityIncrement`, `quantityTarget` nullable, `quantityEvery` (1) / `quantityProgress` (0) — see *Step 24* |
| 20→21 | `user_setup.weekendNight` (`TEXT`, nullable): the weekend's own night as one encoded `NightTimes`, null for everyone who upgrades — see *Step 32* |
| 21→22 | `user_setup.weekendDays` (`TEXT NOT NULL DEFAULT 'SATURDAY,SUNDAY'`, weekday names in week order), `items.repeatUntil` and `contracts.probationKeptSince` (both `TEXT`, nullable, ISO dates) — see *Step 33* |
| 22→23 | `items.routineMode` (`INTEGER NOT NULL DEFAULT 0`); the questionnaire's morning rows get it switched on, and those still named "Morgenzeit" are renamed "Morgenroutine" — see *Step 34* |
| 23→24 | `rewards` and `reward_tasks` (`rewardId` cascades, `itemId` is a plain column on purpose), `user_setup.pointsSystem` (`INTEGER NOT NULL DEFAULT 1`) — see *Step 35* |
| 24→25 | `user_setup.growthTasks` / `contracts` / `rewards` (`INTEGER NOT NULL DEFAULT 1` — on for whoever upgrades, off for a new setup), and every waiting ToDo's `targetDate` moved a week earlier, the column having become the unlock day — see *Step 36* |
| 19→20 | `user_setup.taskAnnouncement` (`TEXT NOT NULL DEFAULT 'SOUND'`, an enum by name) and `speakNotes` (0) — see *Reading a task's name aloud* |

`items.endSound` defaults to 0 for existing rows on purpose: the setup's frame —
Morgenzeit, Pause, Freizeit — would otherwise start chiming all day, and nobody
agreed to that by upgrading.

