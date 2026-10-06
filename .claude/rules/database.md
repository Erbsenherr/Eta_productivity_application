---
paths:
  - "app/src/**/data/local/**"
  - "app/schemas/**"
  - "app/src/**/domain/model/**"
---

### The database and its migrations

**Version 20.** When changing an entity, diff the hand-written SQL against the
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
| 19→20 | `user_setup.taskAnnouncement` (`TEXT NOT NULL DEFAULT 'SOUND'`, an enum by name) and `speakNotes` (0) — see *Reading a task's name aloud* |

`items.endSound` defaults to 0 for existing rows on purpose: the setup's frame —
Morgenzeit, Pause, Freizeit — would otherwise start chiming all day, and nobody
agreed to that by upgrading.

