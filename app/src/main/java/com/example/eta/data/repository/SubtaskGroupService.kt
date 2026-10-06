package com.example.eta.data.repository

import com.example.eta.data.local.ItemDao
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Priority
import com.example.eta.domain.model.Stage
import com.example.eta.domain.planning.minuteToLocalTime
import com.example.eta.domain.subtask.SubtaskDraft
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * What the user answered in the "Tasks gruppieren?" dialog, or what the shorter
 * questions ("zur Gruppe hinzufügen?", "welche Gruppe auflösen?") worked out on
 * their own.
 */
data class GroupAnswers(
    val name: String,
    val subtasks: List<SubtaskDraft>,
    val duration: Duration,
    val category: Category?,
    val priority: Priority?,
    /** The one deadline a group may carry — see [SubtaskGroupService.fold]. */
    val deadlineAt: Instant?,
    val travelBefore: Duration?,
    val returnAfter: Duration?,
    val breakAfter: Duration?,
)

/**
 * Folding one task into another, which is what every merge comes down to.
 *
 * One survivor and one dissolved task, whichever route asked: two plain ToDos
 * becoming a group, a task joining a group that already has steps, or one group
 * being poured into another. The three differ only in the answers they arrive
 * with, so they share the write — a second copy of it would be a second place for
 * the rules about what a merge destroys to drift.
 *
 * Takes **ids** and reads both rows itself. A service handed a row cannot tell how
 * old it is, and what opens this is a drag that outlives the composition it came
 * from — the rule earned twice in step 12.
 */
class SubtaskGroupService(
    private val itemDao: ItemDao,
    private val planRepository: PlanRepository,
    private val subtaskRepository: SubtaskRepository,
    private val clock: Clock = Clock.System,
) {

    /**
     * Makes [survivorBlockId]'s task the group and takes [dissolvedBlockId]'s off
     * the day, starting the group at [startMinute].
     *
     * What happens to the dissolved task, and why each part:
     *
     * - **Its block is deleted**, so the hours it held come back to the day. The
     *   group's own length grew by its duration, which is where that time went.
     * - **Its item is retired, not deleted.** `planned_blocks` cascades on delete,
     *   so deleting would take any past completion of it out of the Erfolgsliste —
     *   and what happened, happened. Retired, it simply leaves the active lists.
     * - **Its steps are cleared**, because they have been copied into the survivor:
     *   leaving them on a retired row would be one group's list in two places.
     *
     * Only ToDos ever reach here. A recurring occurrence and an imported
     * appointment are not movable, so neither can be dropped on anything or
     * dropped on — which is what keeps this from being able to destroy a standing
     * task.
     */
    suspend fun fold(
        survivorBlockId: String,
        dissolvedBlockId: String,
        answers: GroupAnswers,
        startMinute: Int,
    ): Boolean {
        val survivorBlock = planRepository.findBlock(survivorBlockId) ?: return false
        val dissolvedBlock = planRepository.findBlock(dissolvedBlockId) ?: return false
        val survivor = itemDao.findById(survivorBlock.itemId) ?: return false
        val dissolved = itemDao.findById(dissolvedBlock.itemId) ?: return false

        val now = clock.now()
        val renamed = if (answers.name.isNotBlank() && answers.name != survivor.name) {
            survivor.renamed(answers.name, now)
        } else {
            survivor
        }

        itemDao.upsert(
            renamed.copy(
                category = answers.category,
                priority = answers.priority,
                estimatedDuration = answers.duration,
                travelBefore = answers.travelBefore,
                returnAfter = answers.returnAfter,
                breakAfter = answers.breakAfter,
                deadlineAt = answers.deadlineAt,
                updatedAt = now,
            ),
        )
        subtaskRepository.save(survivor.id, answers.subtasks)

        planRepository.addBlock(
            survivorBlock.copy(
                start = minuteToLocalTime(startMinute),
                plannedDuration = answers.duration,
                travelBefore = answers.travelBefore,
                returnAfter = answers.returnAfter,
                breakAfter = answers.breakAfter,
                updatedAt = now,
            ),
        )

        planRepository.removeBlock(dissolvedBlock)
        subtaskRepository.save(dissolved.id, emptyList())
        itemDao.upsert(dissolved.copy(stage = Stage.DONE, completedAt = now, updatedAt = now))
        return true
    }
}
