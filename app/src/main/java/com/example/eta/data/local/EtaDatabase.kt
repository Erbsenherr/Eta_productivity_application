package com.example.eta.data.local

import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.example.eta.domain.model.CalendarEvent
import com.example.eta.domain.model.CalendarSource
import com.example.eta.domain.model.ConflictDismissal
import com.example.eta.domain.model.Contract
import com.example.eta.domain.model.DayPlan
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.JournalEntry
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.PointsTransaction
import com.example.eta.domain.model.Reminder
import com.example.eta.domain.model.Reward
import com.example.eta.domain.model.RewardTask
import com.example.eta.domain.model.Subtask
import com.example.eta.domain.model.SubtaskCheck
import com.example.eta.domain.model.Vacation
import com.example.eta.domain.model.VacationRule
import com.example.eta.domain.setup.UserSetup

@Database(
    entities = [
        Item::class,
        PlannedBlock::class,
        DayPlan::class,
        PointsTransaction::class,
        UserSetup::class,
        Contract::class,
        JournalEntry::class,
        Vacation::class,
        VacationRule::class,
        CalendarSource::class,
        CalendarEvent::class,
        Reminder::class,
        ConflictDismissal::class,
        Subtask::class,
        SubtaskCheck::class,
        Reward::class,
        RewardTask::class,
    ],
    version = 27,
    exportSchema = true,
)
@ColumnTypeConverters(Converters::class)
abstract class EtaDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun plannedBlockDao(): PlannedBlockDao
    abstract fun dayPlanDao(): DayPlanDao
    abstract fun pointsDao(): PointsDao
    abstract fun setupDao(): SetupDao
    abstract fun contractDao(): ContractDao
    abstract fun journalDao(): JournalDao
    abstract fun vacationDao(): VacationDao
    abstract fun calendarDao(): CalendarDao
    abstract fun reminderDao(): ReminderDao
    abstract fun conflictDao(): ConflictDao
    abstract fun subtaskDao(): SubtaskDao
    abstract fun rewardDao(): RewardDao
    abstract fun resetDao(): ResetDao

    companion object {
        // Still the pre-rename name: this is the file on the phone, and the
        // entry name inside every backup made so far.
        const val NAME = "erik.db"
    }
}

/**
 * Subtasks: the steps inside a task, and which of them a day has ticked off.
 *
 * Two tables and not one column, because a subtask belongs to the task while a
 * tick belongs to one occurrence of it — the same split the whole model rests on.
 * Nothing on `items` changes: a task with no rows in `subtasks` is a group with an
 * empty list, which is why every card in the database is already one.
 *
 * - **`subtasks`**: `itemId` cascades, so deleting a card takes its steps with it.
 *   `position` is the order the builder put them in, not unique per item — saving a
 *   reordered list writes several rows and a unique index would make their order
 *   matter.
 * - **`subtask_checks`**: the row's existence is the tick. Composite key over
 *   (`blockId`, `subtaskId`), both cascading: a block cleared by an edit to its
 *   standing task takes its ticks with it, and so does a deleted step.
 */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `subtasks` (
                `id` TEXT NOT NULL,
                `itemId` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `note` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`itemId`) REFERENCES `items`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """,
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_subtasks_itemId_position` " +
                "ON `subtasks` (`itemId`, `position`)",
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `subtask_checks` (
                `blockId` TEXT NOT NULL,
                `subtaskId` TEXT NOT NULL,
                `checkedAt` INTEGER NOT NULL,
                PRIMARY KEY(`blockId`, `subtaskId`),
                FOREIGN KEY(`blockId`) REFERENCES `planned_blocks`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`subtaskId`) REFERENCES `subtasks`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """,
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_subtask_checks_subtaskId` " +
                "ON `subtask_checks` (`subtaskId`)",
        )
    }
}

/**
 * The Extras box, the Growth-Tasks and the conflicts the two can cause.
 *
 * - **`items.pomodoroWork` / `pomodoroPause`**, nullable: the rhythm as a
 *   *default* on the task. The block keeps its own pair, which is what a long
 *   press on the "now" box still writes for one sitting.
 * - **`items.reminderLeadHours` / `reminderMessage`**, nullable. Null is no
 *   reminder, so nothing that predates the box starts announcing itself an hour
 *   early.
 * - **`items.growthStart` / `growthTarget` / `growthIncrement` / `growthOrder`**,
 *   nullable, and **`growthDynamic`** `NOT NULL DEFAULT 0`. A growth task's
 *   *current* length is `estimatedDuration`, which already exists — that is the
 *   whole reason expansion, placement and the yield needed no change at all.
 * - **`reminders.itemId` / `blockId`**, nullable: which task a reminder was
 *   derived from, null for one typed into the tab. No foreign key, on purpose —
 *   `planned_blocks` cascades on delete, and a reminder that already rang must
 *   not vanish because the schedule was laid down again around it.
 * - **`conflict_dismissals`**, a table of its own: the collisions the dashboard
 *   has been told to keep quiet about. Keyed by the two block ids, which is what
 *   makes a dismissal last exactly one day without storing a rule about days.
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `items` ADD COLUMN `pomodoroWork` INTEGER")
        connection.execSQL("ALTER TABLE `items` ADD COLUMN `pomodoroPause` INTEGER")
        connection.execSQL("ALTER TABLE `items` ADD COLUMN `reminderLeadHours` INTEGER")
        connection.execSQL("ALTER TABLE `items` ADD COLUMN `reminderMessage` TEXT")
        connection.execSQL("ALTER TABLE `items` ADD COLUMN `growthStart` INTEGER")
        connection.execSQL("ALTER TABLE `items` ADD COLUMN `growthTarget` INTEGER")
        connection.execSQL("ALTER TABLE `items` ADD COLUMN `growthIncrement` INTEGER")
        connection.execSQL("ALTER TABLE `items` ADD COLUMN `growthOrder` INTEGER")
        connection.execSQL(
            "ALTER TABLE `items` ADD COLUMN `growthDynamic` INTEGER NOT NULL DEFAULT 0",
        )
        connection.execSQL("ALTER TABLE `reminders` ADD COLUMN `itemId` TEXT")
        connection.execSQL("ALTER TABLE `reminders` ADD COLUMN `blockId` TEXT")
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `conflict_dismissals` (
                `id` TEXT NOT NULL,
                `date` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """,
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_conflict_dismissals_date` " +
                "ON `conflict_dismissals` (`date`)",
        )
    }
}

/**
 * Reminders, the pomodoro rhythm, and the "still on it" question.
 *
 * - **`reminders`**, a table of its own: a reminder belongs to no item and no
 *   day plan, so it has nothing to hang off in the existing ones.
 * - **`planned_blocks.pomodoroWork` / `pomodoroPause` / `pomodoroAnchor`**, all
 *   nullable. Null is no rhythm, so nothing already planned starts ticking.
 * - **`user_setup.stillActiveReminder`** `NOT NULL DEFAULT 0` and
 *   **`stillActivePerDay`** `NOT NULL DEFAULT 3`. Off for an upgraded account —
 *   a question nobody asked for is a question nobody agreed to.
 */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reminders` (
                `id` TEXT NOT NULL,
                `text` TEXT NOT NULL,
                `at` INTEGER NOT NULL,
                `firedAt` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """,
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_reminders_at` ON `reminders` (`at`)",
        )
        connection.execSQL("ALTER TABLE `planned_blocks` ADD COLUMN `pomodoroWork` INTEGER")
        connection.execSQL("ALTER TABLE `planned_blocks` ADD COLUMN `pomodoroPause` INTEGER")
        connection.execSQL("ALTER TABLE `planned_blocks` ADD COLUMN `pomodoroAnchor` INTEGER")
        connection.execSQL(
            "ALTER TABLE `user_setup` ADD COLUMN `stillActiveReminder` INTEGER NOT NULL DEFAULT 0",
        )
        connection.execSQL(
            "ALTER TABLE `user_setup` ADD COLUMN `stillActivePerDay` INTEGER NOT NULL DEFAULT 3",
        )
    }
}

/**
 * The Google Calendar tables.
 *
 * Two, and the split is the same one the rest of the model rests on. A
 * **calendar** is standing configuration — which of the account's calendars
 * describe the user's day — and its one user-written field, `enabled`, has to
 * survive every refresh. An **event** is one dated occurrence plus the decision
 * taken about it, and its primary key is the remote identity (`calendarId` and
 * `eventId` joined), which is what makes syncing the same appointment twice land
 * on one row instead of two.
 *
 * Nothing is added to `items` or `planned_blocks`: an imported appointment is an
 * ordinary card with `BlockOrigin.CALENDAR_IMPORT`, and the link back to Google
 * lives on the event row as `itemId`. Which is the right way round — the app's
 * own model stays free of a column that only means something while an account
 * happens to be connected.
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `calendar_sources` (
                `id` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `accountName` TEXT,
                `isPrimary` INTEGER NOT NULL,
                `isForeign` INTEGER NOT NULL,
                `enabled` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """,
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `calendar_events` (
                `id` TEXT NOT NULL,
                `calendarId` TEXT NOT NULL,
                `eventId` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `date` TEXT NOT NULL,
                `start` INTEGER NOT NULL,
                `duration` INTEGER NOT NULL,
                `allDay` INTEGER NOT NULL,
                `remoteUpdatedAt` INTEGER,
                `decision` TEXT NOT NULL,
                `itemId` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """,
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_calendar_events_date` ON `calendar_events` (`date`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_calendar_events_decision` ON `calendar_events` (`decision`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_calendar_events_itemId` ON `calendar_events` (`itemId`)",
        )
    }
}

/**
 * The return journey, the excuse and the price of a cancellation.
 *
 * - **`returnAfter`** on both tables, beside `travelBefore` and `breakAfter`. Null
 *   on every existing row, which is "no return journey", so nothing already
 *   planned changes shape.
 * - **`planned_blocks.forceMajeure`**, the reason a cancellation was excused.
 *   Non-null is the excuse.
 * - **`user_setup.cancellationPenaltyPerHour`**, `NOT NULL DEFAULT 1.0` — the rate
 *   the rule was agreed with, so an upgraded account behaves exactly as described
 *   rather than starting at zero and charging nothing.
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `items` ADD COLUMN `returnAfter` INTEGER")
        connection.execSQL("ALTER TABLE `planned_blocks` ADD COLUMN `returnAfter` INTEGER")
        connection.execSQL("ALTER TABLE `planned_blocks` ADD COLUMN `forceMajeure` TEXT")
        connection.execSQL(
            "ALTER TABLE `user_setup` ADD COLUMN `cancellationPenaltyPerHour` " +
                "REAL NOT NULL DEFAULT 1.0",
        )
    }
}

/**
 * Everything the first round of real use needed.
 *
 * Three unrelated features share one migration because they arrived together and
 * a version per column would only be a longer list to keep in step:
 *
 * - **`travelBefore` / `breakAfter`** on both tables — the journey there and the
 *   break afterwards. Null on every existing row, which is exactly "no margins",
 *   so nothing already planned changes shape.
 * - **`items.endSound`**, `NOT NULL DEFAULT 0`. Zero rather than one on purpose:
 *   the setup lays down the frame of the day — Morgenzeit, Pause, Freizeit — and
 *   defaulting to on would have every one of those chime at its end for a user
 *   who only upgraded. New cards get it ticked in the concretizing step instead.
 * - **`contracts.editedAt`**, the once-only flag on changing a contract's wording.
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `items` ADD COLUMN `travelBefore` INTEGER")
        connection.execSQL("ALTER TABLE `items` ADD COLUMN `breakAfter` INTEGER")
        connection.execSQL(
            "ALTER TABLE `items` ADD COLUMN `endSound` INTEGER NOT NULL DEFAULT 0",
        )
        connection.execSQL("ALTER TABLE `planned_blocks` ADD COLUMN `travelBefore` INTEGER")
        connection.execSQL("ALTER TABLE `planned_blocks` ADD COLUMN `breakAfter` INTEGER")
        connection.execSQL("ALTER TABLE `contracts` ADD COLUMN `editedAt` INTEGER")
    }
}

/** Adds the single-row setup table the questionnaire writes. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `user_setup` (
                `id` INTEGER NOT NULL,
                `bedPrepTime` INTEGER NOT NULL,
                `sleepTime` INTEGER NOT NULL,
                `wakeTime` INTEGER NOT NULL,
                `morningDuration` INTEGER NOT NULL,
                `lunchTime` INTEGER NOT NULL,
                `lunchDuration` INTEGER NOT NULL,
                `meals` TEXT NOT NULL,
                `housekeeping` TEXT,
                `sport` TEXT,
                `freeTime` TEXT NOT NULL,
                `socialTimePerWeek` INTEGER NOT NULL,
                `mindfulness` TEXT,
                `work` TEXT NOT NULL,
                `dailyPlanningTime` INTEGER NOT NULL,
                `weeklyPlanningDay` TEXT NOT NULL,
                `weeklyPlanningTime` INTEGER NOT NULL,
                `completedAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """,
        )
    }
}

/**
 * Adds the item role.
 *
 * Existing rows keep a null role, which is correct for anything the user made and
 * wrong only for the generated schedule — answering the questionnaire again
 * regenerates those by their deterministic ids and fills it in.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `items` ADD COLUMN `role` TEXT")
    }
}

/**
 * Adds the two notes.
 *
 * One per table on purpose: a note on the item follows every occurrence, a note on
 * the block belongs to one date. That is the same definition/occurrence split the
 * rest of the model rests on, so it costs a column each and nothing more.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `items` ADD COLUMN `note` TEXT")
        connection.execSQL("ALTER TABLE `planned_blocks` ADD COLUMN `note` TEXT")
    }
}

/**
 * Lets the devaluation have its own weekday.
 *
 * Null keeps it on the weekly planning day, which is where it always was — so
 * every existing row keeps behaving exactly as before.
 */
/**
 * The wake alarm's on/off switch.
 *
 * Defaults to 0 for every existing row, which is the only safe answer: nobody who
 * has been using the app agreed to be woken by it.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `user_setup` ADD COLUMN `wakeAlarm` INTEGER NOT NULL DEFAULT 0",
        )
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `user_setup` ADD COLUMN `inflationDay` TEXT")
    }
}

/**
 * Stamps a weekly goal with the cycle it was taken on in.
 *
 * The week list empties as items are planned into days, so it cannot answer "what
 * did this week commit to" on its own. Existing rows keep a null stamp, which
 * reads as "not a goal of any cycle" — right for everything the old code created.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `items` ADD COLUMN `weekStartedOn` TEXT")
    }
}

/**
 * Forbids two blocks for the same item on the same day.
 *
 * Existing databases may already hold duplicates — a unique index cannot be
 * created over them, so they are cleared first. A settled sibling wins over an
 * open one: the duplicates are born identical in the same instant, so the only
 * one that can carry information is one the user has since answered.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            DELETE FROM `planned_blocks`
            WHERE `completedAt` IS NULL AND `discardedAt` IS NULL AND EXISTS (
                SELECT 1 FROM `planned_blocks` AS other
                WHERE other.`itemId` = `planned_blocks`.`itemId`
                  AND other.`date` = `planned_blocks`.`date`
                  AND other.rowid <> `planned_blocks`.rowid
                  AND (other.`completedAt` IS NOT NULL OR other.`discardedAt` IS NOT NULL)
            )
            """,
        )
        connection.execSQL(
            """
            DELETE FROM `planned_blocks` WHERE rowid NOT IN (
                SELECT MIN(rowid) FROM `planned_blocks` GROUP BY `itemId`, `date`
            )
            """,
        )
        connection.execSQL("DROP INDEX IF EXISTS `index_planned_blocks_itemId`")
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_planned_blocks_itemId_date` ON `planned_blocks` (`itemId`, `date`)",
        )
    }
}

/**
 * Marks a day as settled, separately from a day being planned.
 *
 * Reading "tomorrow is confirmed" as "today is done" let a day be planned ahead
 * and then never settled: the alarm fell silent and the harvest was lost without
 * a word. Two promises, two columns.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `day_plans` ADD COLUMN `settledAt` INTEGER")
    }
}

/**
 * Adds the holiday tables.
 *
 * The rules live in their own table rather than as a blob on the holiday: they are
 * queried per item during expansion, and a per-item decision is exactly the shape
 * a relation is for.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `vacations` (
                `id` TEXT NOT NULL,
                `label` TEXT NOT NULL,
                `from` TEXT NOT NULL,
                `to` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """,
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `vacation_rules` (
                `id` TEXT NOT NULL,
                `vacationId` TEXT NOT NULL,
                `itemId` TEXT NOT NULL,
                `treatment` TEXT NOT NULL,
                `movedStart` INTEGER,
                PRIMARY KEY(`id`)
            )
            """,
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_vacation_rules_vacationId` ON `vacation_rules` (`vacationId`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_vacation_rules_itemId` ON `vacation_rules` (`itemId`)",
        )
    }
}

/** Adds self-contracts and the journal both reevaluations write to. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `contracts` (
                `id` TEXT NOT NULL,
                `slot` INTEGER,
                `title` TEXT NOT NULL,
                `conditions` TEXT NOT NULL,
                `breachDefinition` TEXT NOT NULL,
                `effort` TEXT NOT NULL,
                `signature` TEXT NOT NULL,
                `signedOn` TEXT NOT NULL,
                `endsOn` TEXT NOT NULL,
                `state` TEXT NOT NULL,
                `legacySince` TEXT,
                `closedAt` INTEGER,
                `lastCheckedOn` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """,
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_contracts_state` ON `contracts` (`state`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_contracts_slot` ON `contracts` (`slot`)",
        )

        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `journal_entries` (
                `id` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `date` TEXT NOT NULL,
                `question` TEXT NOT NULL,
                `answer` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """,
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_journal_entries_date` ON `journal_entries` (`date`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_journal_entries_kind` ON `journal_entries` (`kind`)",
        )
    }
}

/**
 * Drops the separate lunch break: it moved inside the work answer, and the work
 * column's encoding changed with it.
 *
 * The stored answers are discarded rather than converted. Both halves of what the
 * new shape needs — where the break sits inside the working day — are simply not
 * in the old row, so any conversion would be an invention. Clearing the table
 * sends the app back to the questionnaire, which is the honest outcome.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `user_setup`")
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `user_setup` (
                `id` INTEGER NOT NULL,
                `bedPrepTime` INTEGER NOT NULL,
                `sleepTime` INTEGER NOT NULL,
                `wakeTime` INTEGER NOT NULL,
                `morningDuration` INTEGER NOT NULL,
                `meals` TEXT NOT NULL,
                `housekeeping` TEXT,
                `sport` TEXT,
                `freeTime` TEXT NOT NULL,
                `socialTimePerWeek` INTEGER NOT NULL,
                `mindfulness` TEXT,
                `work` TEXT NOT NULL,
                `dailyPlanningTime` INTEGER NOT NULL,
                `weeklyPlanningDay` TEXT NOT NULL,
                `weeklyPlanningTime` INTEGER NOT NULL,
                `completedAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """,
        )
    }
}

/**
 * The update condition on growth tasks, and the Mengen-Inkrement (step 24).
 *
 * - **`items.growthEvery`** `NOT NULL DEFAULT 1` and **`growthProgress`**
 *   `NOT NULL DEFAULT 0`: the increment every *n* confirmed completions, and how
 *   many have been counted towards the next one. One is what every growth task
 *   did before, so an existing one carries on exactly as it was.
 * - **`items.quantity` / `quantityStart` / `quantityIncrement` / `quantityTarget`**,
 *   nullable — all null is no count — plus **`quantityEvery`** (1) and
 *   **`quantityProgress`** (0), the same pair as the growth task's.
 */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `items` ADD COLUMN `growthEvery` INTEGER NOT NULL DEFAULT 1",
        )
        connection.execSQL(
            "ALTER TABLE `items` ADD COLUMN `growthProgress` INTEGER NOT NULL DEFAULT 0",
        )
        connection.execSQL("ALTER TABLE `items` ADD COLUMN `quantity` INTEGER")
        connection.execSQL("ALTER TABLE `items` ADD COLUMN `quantityStart` INTEGER")
        connection.execSQL("ALTER TABLE `items` ADD COLUMN `quantityIncrement` INTEGER")
        connection.execSQL("ALTER TABLE `items` ADD COLUMN `quantityTarget` INTEGER")
        connection.execSQL(
            "ALTER TABLE `items` ADD COLUMN `quantityEvery` INTEGER NOT NULL DEFAULT 1",
        )
        connection.execSQL(
            "ALTER TABLE `items` ADD COLUMN `quantityProgress` INTEGER NOT NULL DEFAULT 0",
        )
    }
}

/**
 * 19 -> 20: reading task names aloud.
 *
 * Two columns on `user_setup`: **`taskAnnouncement`**, `SOUND` for every existing
 * row because that is what the app did before the choice existed, and
 * **`speakNotes`**, off — a note read out loud in a room is something to ask for.
 */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `user_setup` ADD COLUMN `taskAnnouncement` TEXT NOT NULL DEFAULT 'SOUND'",
        )
        connection.execSQL(
            "ALTER TABLE `user_setup` ADD COLUMN `speakNotes` INTEGER NOT NULL DEFAULT 0",
        )
    }
}

/**
 * The weekend's own night: one nullable column holding the three times, encoded
 * the way the other nested answers are. Null for everyone who upgrades — their
 * nights were all the same, and stay so until they say otherwise.
 */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `user_setup` ADD COLUMN `weekendNight` TEXT")
    }
}

/**
 * 21 -> 22: a weekend of the user's choosing, an end date for a standing task,
 * and the run of kept evenings that lets a broken contract back in.
 *
 * - **`user_setup.weekendDays`**: the days a weekend night ends on, as names in
 *   week order. `SATURDAY,SUNDAY` for every existing row — what the weekend was
 *   while it could not be chosen.
 * - **`items.repeatUntil`**: the last day a recurring definition lays an
 *   occurrence down on. Null repeats without end, which is every row so far.
 * - **`contracts.probationKeptSince`**: null for everyone, a contract already on
 *   probation included — its run starts counting with the next kept evening.
 */
val MIGRATION_21_22 = object : Migration(21, 22) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `user_setup` ADD COLUMN `weekendDays` TEXT NOT NULL DEFAULT 'SATURDAY,SUNDAY'",
        )
        connection.execSQL("ALTER TABLE `items` ADD COLUMN `repeatUntil` TEXT")
        connection.execSQL("ALTER TABLE `contracts` ADD COLUMN `probationKeptSince` TEXT")
    }
}

/**
 * 22 -> 23: the Routine-Modus, and the morning becoming one.
 *
 * **`items.routineMode`**, off for everything — except the questionnaire's
 * morning, which is renamed from "Morgenzeit" to "Morgenroutine" in the same
 * step. Only a row still carrying the old name is renamed; the mode is switched
 * on for every morning row, since that is what the settings would write the next
 * time they are saved anyway.
 */
val MIGRATION_22_23 = object : Migration(22, 23) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `items` ADD COLUMN `routineMode` INTEGER NOT NULL DEFAULT 0",
        )
        connection.execSQL(
            """
            UPDATE `items` SET `routineMode` = 1
            WHERE `id` = 'setup:morning' OR `id` LIKE 'setup:morning-%'
            """.trimIndent(),
        )
        connection.execSQL(
            """
            UPDATE `items` SET `name` = 'Morgenroutine', `normalizedName` = 'morgenroutine'
            WHERE (`id` = 'setup:morning' OR `id` LIKE 'setup:morning-%')
              AND `name` = 'Morgenzeit'
            """.trimIndent(),
        )
    }
}

/**
 * 23 -> 24: the Belohn-o-mat, and a points system that can be put out of sight.
 *
 * - **`rewards`**: what each costs, what it has earned and where it stands in the
 *   list. The progress is a column of its own, since points poured into a reward
 *   stay with it whatever the order does afterwards.
 * - **`reward_tasks`**: the standing tasks a reward is bound to. `rewardId`
 *   cascades; `itemId` is a plain column, because a retired definition has to go
 *   on naming the task it stood for.
 * - **`user_setup.pointsSystem`**: on for everyone who upgrades — it is what the
 *   app did before there was a switch.
 */
val MIGRATION_23_24 = object : Migration(23, 24) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `rewards` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `cost` REAL NOT NULL,
                `progress` REAL NOT NULL,
                `position` INTEGER NOT NULL,
                `redeemedAt` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """,
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `reward_tasks` (
                `rewardId` TEXT NOT NULL,
                `itemId` TEXT NOT NULL,
                PRIMARY KEY(`rewardId`, `itemId`),
                FOREIGN KEY(`rewardId`) REFERENCES `rewards`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """,
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_reward_tasks_itemId` ON `reward_tasks` (`itemId`)",
        )
        connection.execSQL(
            "ALTER TABLE `user_setup` ADD COLUMN `pointsSystem` INTEGER NOT NULL DEFAULT 1",
        )
    }
}

/**
 * 24 -> 25: three more features that can be put out of sight, and a date that
 * changed its meaning.
 *
 * - **`user_setup.growthTasks` / `contracts` / `rewards`**: `1` for every existing
 *   row. A new setup starts with all of them off, but nobody who has contracts
 *   running should find the tab gone after an update. `pointsSystem` is left as
 *   it stands for the same reason.
 * - **`items.targetDate`** used to be the day a ToDo was aimed at, unlocking a
 *   week before; it is now the unlock day itself. Every ToDo still waiting to be
 *   planned is moved a week earlier, so each unlocks on exactly the day it would
 *   have — and, the one-month clock no longer starting before that day, none is
 *   suddenly older than it was. Rows already on a day or finished are left alone:
 *   nothing reads the date off them.
 */
val MIGRATION_24_25 = object : Migration(24, 25) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `user_setup` ADD COLUMN `growthTasks` INTEGER NOT NULL DEFAULT 1",
        )
        connection.execSQL(
            "ALTER TABLE `user_setup` ADD COLUMN `contracts` INTEGER NOT NULL DEFAULT 1",
        )
        connection.execSQL(
            "ALTER TABLE `user_setup` ADD COLUMN `rewards` INTEGER NOT NULL DEFAULT 1",
        )
        connection.execSQL(
            """
            UPDATE `items` SET `targetDate` = date(`targetDate`, '-7 days')
            WHERE `targetDate` IS NOT NULL
              AND `type` = 'TODO'
              AND `stage` IN ('COLLECTION', 'WEEK')
            """.trimIndent(),
        )
    }
}

/**
 * 25 -> 26: **`planned_blocks.flexible`**, off for every block there is — a
 * standing occurrence is fixed until the user says, of that one, that it is not.
 */
val MIGRATION_25_26 = object : Migration(25, 26) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `planned_blocks` ADD COLUMN `flexible` INTEGER NOT NULL DEFAULT 0",
        )
    }
}

/**
 * Version 27: the charge for unplanned time becomes the user's to set.
 *
 * `unplannedPenalty` on and `unplannedPenaltyPerHour` at 1.5 for every existing
 * row — the rule exactly as it stood while it was a constant, so nobody's
 * evening changes by upgrading.
 */
val MIGRATION_26_27 = object : Migration(26, 27) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `user_setup` ADD COLUMN `unplannedPenalty` INTEGER NOT NULL DEFAULT 1",
        )
        connection.execSQL(
            "ALTER TABLE `user_setup` ADD COLUMN `unplannedPenaltyPerHour` REAL NOT NULL DEFAULT 1.5",
        )
    }
}
