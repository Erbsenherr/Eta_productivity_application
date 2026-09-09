package com.example.erik_iteration_2.data.local

import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.example.erik_iteration_2.domain.model.Contract
import com.example.erik_iteration_2.domain.model.DayPlan
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.model.JournalEntry
import com.example.erik_iteration_2.domain.model.PlannedBlock
import com.example.erik_iteration_2.domain.model.PointsTransaction
import com.example.erik_iteration_2.domain.model.Vacation
import com.example.erik_iteration_2.domain.model.VacationRule
import com.example.erik_iteration_2.domain.setup.UserSetup

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
    ],
    version = 13,
    exportSchema = true,
)
@ColumnTypeConverters(Converters::class)
abstract class ErikDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun plannedBlockDao(): PlannedBlockDao
    abstract fun dayPlanDao(): DayPlanDao
    abstract fun pointsDao(): PointsDao
    abstract fun setupDao(): SetupDao
    abstract fun contractDao(): ContractDao
    abstract fun journalDao(): JournalDao
    abstract fun vacationDao(): VacationDao
    abstract fun resetDao(): ResetDao

    companion object {
        const val NAME = "erik.db"
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
