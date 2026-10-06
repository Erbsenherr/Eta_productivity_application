package com.example.eta.data.repository

import com.example.eta.data.local.ReminderDao
import com.example.eta.domain.model.Reminder
import com.example.eta.domain.reminder.taskReminderPlans
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Keeps the reminders that tasks owe in step with the plan.
 *
 * The "Erinnerung" extra could have been a fourth thing riding the task alarm,
 * next to the start sound, the end sound and the pomodoro boundaries. It is a
 * row in `reminders` instead, because the user asked for these to be saved in
 * the Erinnerungen tab like any other — and a reminder that is only an alarm is
 * one they can neither see coming nor call off.
 *
 * Which means something has to reconcile: the plan is the truth and the rows are
 * derived from it, so every time the plan can have changed, the rows the plan no
 * longer asks for go and the ones it now asks for are written. **One row per
 * task**, aimed at its next occurrence — see `taskReminderPlans`.
 *
 * A reminder that already rang is never touched. It is the record of something
 * that happened, and the schedule being laid down again around it does not make
 * it untrue.
 */
class TaskReminderService(
    private val dao: ReminderDao,
    private val planRepository: PlanRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

    /**
     * Writes what the plan asks for and clears what it does not.
     *
     * Returns true when anything moved, so the caller can skip re-aiming the
     * alarm — which is almost every call, since this runs on every path that
     * touches the reminder alarm at all.
     */
    suspend fun sync(): Boolean {
        val now = clock.now()
        val today = now.toLocalDateTime(timeZone).date
        // As far as the schedule is laid down: the next occurrence of a monthly
        // task can be three weeks out, and a reminder for it is still owed.
        val blocks = planRepository.findRange(
            from = today,
            to = today.plus(DatePeriod(days = SCHEDULE_HORIZON_DAYS)),
        )
        val wanted = taskReminderPlans(blocks, now, timeZone).associateBy { it.itemId }
        val existing = dao.findPendingAutomatic().groupBy { it.itemId }

        var changed = false

        // Gone: the extra was switched off, the occurrence was ticked off or
        // called off, or the task itself has ended.
        existing.forEach { (itemId, rows) ->
            val plan = wanted[itemId]
            val keep = if (plan == null) null else rows.firstOrNull()
            rows.filter { it.id != keep?.id }.forEach {
                dao.delete(it.id)
                changed = true
            }
            if (plan != null && keep != null) {
                // The one row this task keeps, brought in line with the plan. A
                // moved block, a renamed task or a changed lead time all land here.
                if (keep.at != plan.at || keep.text != plan.text || keep.blockId != plan.blockId) {
                    dao.upsert(
                        keep.copy(
                            text = plan.text,
                            at = plan.at,
                            blockId = plan.blockId,
                            updatedAt = now,
                        ),
                    )
                    changed = true
                }
            }
        }

        // New: an extra just switched on, or the previous reminder has rung and
        // the next occurrence is now the one to announce.
        wanted.forEach { (itemId, plan) ->
            if (existing[itemId]?.isNotEmpty() == true) return@forEach
            dao.upsert(
                Reminder(
                    text = plan.text,
                    at = plan.at,
                    itemId = itemId,
                    blockId = plan.blockId,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            changed = true
        }

        return changed
    }
}
