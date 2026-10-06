package com.example.eta.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.data.local.BlockWithItem
import com.example.eta.data.repository.ItemRepository
import com.example.eta.data.repository.PlanRepository
import com.example.eta.data.repository.RecurringEdit
import com.example.eta.data.repository.RecurringTaskService
import com.example.eta.data.repository.ReevaluationService
import com.example.eta.data.repository.ScheduleMaintenance
import com.example.eta.data.repository.SetupRepository
import com.example.eta.data.repository.SubtaskRepository
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.Stage
import com.example.eta.domain.model.Subtask
import com.example.eta.domain.planning.MINUTES_PER_DAY
import com.example.eta.domain.planning.minuteToLocalTime
import com.example.eta.domain.recurrence.RecurringGroup
import com.example.eta.domain.recurrence.groupRecurring
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.staging.daysUntilBan
import com.example.eta.ui.attributes.RecurringAttributes
import com.example.eta.ui.attributes.TodoAttributes
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

/** A Sammelliste entry with how long it has left before the ban. */
data class CollectionEntry(
    val item: Item,
    val daysLeft: Int?,
)

/** A banned ToDo and the day it may be written down again. */
data class LockedEntry(
    val item: Item,
    val until: LocalDate?,
)

/** One line of the Erfolgsliste: name, date and time, as `Konzept.md` asks. */
data class DoneEntry(
    val name: String,
    val at: LocalDateTime,
)

data class SmartListsUiState(
    val today: LocalDate,
    val tomorrow: LocalDate,
    val locked: List<LockedEntry> = emptyList(),
    /** The Sammelliste without its recurring notes — those are [incompleteRecurring]. */
    val collection: List<CollectionEntry> = emptyList(),
    /**
     * Recurring notes still in the Sammelliste. Shown at the top of the
     * Wiederkehrend box rather than in the Sammelliste, above the Wochenschema, so
     * that finishing one happens with the free hours of the week in view.
     */
    val incompleteRecurring: List<CollectionEntry> = emptyList(),
    val week: List<Item> = emptyList(),
    val todayBlocks: List<BlockWithItem> = emptyList(),
    val tomorrowBlocks: List<BlockWithItem> = emptyList(),
    /** The "Liste für Morgen" only exists once tomorrow's plan is confirmed. */
    val tomorrowConfirmed: Boolean = false,
    val done: List<DoneEntry> = emptyList(),
    /** Calendar appointments on days that have not come yet. */
    val upcoming: List<BlockWithItem> = emptyList(),
    /** The standing schedule, one entry per task rather than per weekday. */
    val recurring: List<RecurringGroup> = emptyList(),
    /** Every live recurring definition, which is what an overlap is checked against. */
    val definitions: List<Item> = emptyList(),
    /** The steps inside each card that has any, by item id. */
    val subtasks: Map<String, List<Subtask>> = emptyMap(),
    /** ToDos a group can swallow as steps: the week's goals and the backlog. */
    val foldable: List<Item> = emptyList(),
)

/**
 * The "Smart toDos" tab: the six lists, side by side.
 *
 * Read-only about **placement**, which is the part that matters: every one of
 * these lists is the result of a phase — the Sperrliste of the weekly sweep, the
 * Wochenliste of the weekly planning, the Tagesliste of the day planner — so
 * moving a card from here would be a second route around the rules those phases
 * enforce, and there is still no way to do it.
 *
 * The *attributes* of a definition are a different matter. A wrong duration or
 * priority is a typo, and making the user run a whole planning phase to correct
 * one is the tail wagging the dog — so [saveTodo], [saveRecurring] and [delete]
 * exist, and the screen offers them on the Sammelliste and the Wochenliste alone.
 *
 * [completeEarly] is the one exception to the placement rule, and a deliberate
 * one: it does lay a block down. Saying a card is done is not moving it between
 * lists to be dealt with later — it is the end of it, and the Erfolgsliste can
 * only be reached through a completed occurrence.
 */
class SmartListsViewModel(
    private val itemRepository: ItemRepository,
    private val planRepository: PlanRepository,
    private val scheduleMaintenance: ScheduleMaintenance,
    private val recurringTaskService: RecurringTaskService,
    private val reevaluationService: ReevaluationService,
    private val subtaskRepository: SubtaskRepository,
    setupRepository: SetupRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    val today: LocalDate = clock.todayIn(timeZone)
    private val tomorrow: LocalDate = today.plus(DatePeriod(days = 1))

    private val stages = combine(
        itemRepository.observeStage(Stage.LOCKED),
        itemRepository.observeStage(Stage.COLLECTION),
        itemRepository.observeStage(Stage.WEEK),
    ) { locked, collection, week ->
        Triple(
            locked.map { LockedEntry(it, it.lockedUntil?.toLocalDateTime(timeZone)?.date) },
            collection.map { CollectionEntry(it, it.daysUntilBan(clock.now(), timeZone)) },
            week,
        )
    }

    private val days = combine(
        planRepository.observeDay(today),
        planRepository.observeDay(tomorrow),
        planRepository.observeDayPlan(tomorrow),
        planRepository.observeErfolgsliste(),
        // Strictly after today: an appointment today is in the Tagesliste, and
        // one tomorrow in the Liste für Morgen once that is confirmed. This
        // list is for the ones no other list on this screen can reach.
        planRepository.observeUpcomingAppointments(today),
    ) { todayBlocks, tomorrowBlocks, dayPlan, done, upcoming ->
        DayLists(
            todayBlocks = todayBlocks,
            tomorrowBlocks = tomorrowBlocks,
            confirmed = dayPlan?.isConfirmed == true,
            done = done.mapNotNull { entry ->
                entry.block.completedAt?.let {
                    DoneEntry(entry.item.name, it.toLocalDateTime(timeZone))
                }
            },
            upcoming = upcoming,
        )
    }

    /** The setup's answers, for the night the Wochenschema books. */
    val setup: StateFlow<UserSetup?> = setupRepository.observe().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    val uiState: StateFlow<SmartListsUiState> = combine(
        stages,
        days,
        recurringTaskService.observeDefinitions(),
        subtaskRepository.observeByItem(),
        itemRepository.observeFoldCandidates(),
    ) { staged, dayLists, definitions, subtasks, foldable ->
        SmartListsUiState(
            subtasks = subtasks,
            foldable = foldable,
            recurring = groupRecurring(definitions),
            definitions = definitions,
            today = today,
            tomorrow = tomorrow,
            locked = staged.first,
            collection = staged.second.filter { it.item.type != ItemType.RECURRING },
            incompleteRecurring = staged.second.filter { it.item.type == ItemType.RECURRING },
            week = staged.third,
            todayBlocks = dayLists.todayBlocks,
            tomorrowBlocks = dayLists.tomorrowBlocks,
            tomorrowConfirmed = dayLists.confirmed,
            done = dayLists.done,
            upcoming = dayLists.upcoming,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SmartListsUiState(today = today, tomorrow = tomorrow),
    )

    /**
     * Saves a ToDo's attributes.
     *
     * Nothing here touches the stage or any block — but it does finish the card:
     * the shared form cannot express "no category", so an entry that was
     * `unvollständig` before is `isConcretized` after.
     */
    fun saveTodo(item: Item, name: String, note: String?, attributes: TodoAttributes) {
        viewModelScope.launch {
            val now = clock.now()
            val renamed = if (name != item.name) item.renamed(name, now) else item
            // The evening step's own call, so a card finished here is finished by
            // the same rules — and comes out `isConcretized` either way.
            itemRepository.concretize(
                item = renamed.copy(note = note),
                category = attributes.category,
                priority = attributes.priority,
                targetDate = today.plus(DatePeriod(days = attributes.inDays.coerceAtLeast(0))),
                estimatedDuration = attributes.duration,
                travelBefore = attributes.travelBefore,
                returnAfter = attributes.returnAfter,
                breakAfter = attributes.breakAfter,
                endSound = attributes.endSound,
                extras = attributes.extras,
                subtasks = attributes.subtasks,
                folded = attributes.foldedItemIds,
            )
        }
    }

    /**
     * Finishes a recurring note, through the evening step's own call.
     *
     * The visible consequence is that the entry leaves the Sammelliste: several
     * weekdays become several definitions in `Stage.DAY`, `enteredCollectionAt` is
     * cleared — the one-month clock has nothing left to measure — and the
     * occurrences are laid down at once, or the task would exist without a single
     * one while the user is looking at the screen that just said it was done.
     *
     * Nothing needs clearing up first: a definition without a rule lays down no
     * block, so a card reaching this point has no occurrences to go stale.
     */
    fun saveRecurring(item: Item, name: String, note: String?, attributes: RecurringAttributes) {
        if (attributes.weekdays.isEmpty()) return
        viewModelScope.launch {
            val now = clock.now()
            val renamed = if (name != item.name) item.renamed(name, now) else item
            itemRepository.concretizeRecurring(
                item = renamed.copy(note = note),
                category = attributes.category,
                weekdays = attributes.weekdays,
                startTime = attributes.startTime,
                duration = attributes.duration,
                travelBefore = attributes.travelBefore,
                returnAfter = attributes.returnAfter,
                breakAfter = attributes.breakAfter,
                endSound = attributes.endSound,
                extras = attributes.extras,
                growth = attributes.growth,
                subtasks = attributes.subtasks,
                folded = attributes.foldedItemIds,
            )
            scheduleMaintenance.topUp()
        }
    }

    /**
     * Saves an edited standing task.
     *
     * Occurrences on the confirmed days — today, and tomorrow once its plan is
     * confirmed — stay as they are; everything after is laid down again from the
     * new answers. See [RecurringTaskService].
     */
    fun saveRecurringGroup(
        group: RecurringGroup,
        name: String,
        note: String?,
        attributes: RecurringAttributes,
    ) {
        if (attributes.weekdays.isEmpty()) return
        viewModelScope.launch {
            recurringTaskService.saveGroup(
                ids = group.ids,
                edit = RecurringEdit(
                    name = name,
                    note = note,
                    category = attributes.category,
                    weekdays = attributes.weekdays,
                    startTime = attributes.startTime,
                    duration = attributes.duration,
                    travelBefore = attributes.travelBefore,
                    returnAfter = attributes.returnAfter,
                    breakAfter = attributes.breakAfter,
                    endSound = attributes.endSound,
                    extras = attributes.extras,
                    growth = attributes.growth,
                    subtasks = attributes.subtasks,
                    folded = attributes.foldedItemIds,
                ),
            )
        }
    }

    /** Ends a standing task; past occurrences stay in the Erfolgsliste. */
    fun endRecurringGroup(group: RecurringGroup) {
        viewModelScope.launch { recurringTaskService.endGroup(group.ids) }
    }

    /**
     * Finishing a card ahead of its plan.
     *
     * The Erfolgsliste is the view over **completed blocks**, never over items in
     * `Stage.DONE` — so ticking a card off from a list it has not been planned
     * into means giving it an occurrence to be completed. It gets one on today,
     * starting now, as long as the item says it is; a card that has never been
     * concretized says nothing about its length and so gets a block of no length,
     * which is the honest figure to earn points on.
     *
     * A card that *does* already stand on today is checked off where it stands,
     * margins and hour intact, rather than being given a second occurrence —
     * which the unique index on (itemId, date) would refuse anyway. One that was
     * called off is un-called-off in the same breath: saying it is done is the
     * later statement, and the two cannot both hold.
     *
     * Points are deliberately not credited here. `ReevaluationService` is the only
     * thing that writes `HARVEST` rows, and this block is harvested with the rest
     * of the day in the evening — the dialog says so before it happens.
     */
    fun completeEarly(item: Item, category: Category, duration: Duration) {
        viewModelScope.launch {
            val now = clock.now()
            // Written back before anything else: for a card that was never filled
            // in these are the two answers that decide the yield, and `yieldOf`
            // reads the category off the item rather than off the block.
            val answered = item.copy(
                category = category,
                estimatedDuration = duration,
                updatedAt = now,
            )
            if (answered != item) itemRepository.update(answered)

            val existing = planRepository.findDay(today)
                .firstOrNull { it.block.itemId == item.id }
                ?.block
            val block = existing?.copy(
                completedAt = now,
                // The cancellation is withdrawn with it; a block cannot be both.
                discardedAt = null,
                forceMajeure = null,
                updatedAt = now,
            ) ?: PlannedBlock(
                itemId = item.id,
                date = today,
                start = minuteToLocalTime(startOfEarlyBlock(now, duration)),
                plannedDuration = duration,
                // Placed by hand, by the user, on the day — the same origin a card
                // dropped onto the planner gets.
                origin = BlockOrigin.DRAGGED,
                completedAt = now,
                createdAt = now,
                updatedAt = now,
            )
            planRepository.addBlock(block)
            // What the evening would have done anyway: a one-shot ToDo whose
            // occurrence is done leaves the active lists.
            itemRepository.retireCompletedTodos(today)
            // And if the evening has already been and gone, the harvest has to be
            // carried to the ledger here — it is not coming round again.
            reevaluationService.harvestLate(BlockWithItem(block = block, item = answered))
        }
    }

    /** Whether tonight's settlement will still pick up what is ticked off now. */
    val todaySettled: StateFlow<Boolean> = planRepository.observeDayPlan(today)
        .map { it?.isSettled == true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    /**
     * Where a block made on the spot begins: now, pulled back far enough that it
     * still ends inside the day.
     *
     * A block running past midnight is not a thing the timeline can draw, and a
     * long task ticked off late in the evening would ask for one.
     */
    private fun startOfEarlyBlock(now: Instant, duration: Duration): Int {
        val time = now.toLocalDateTime(timeZone).time
        val minutes = duration.inWholeMinutes.toInt()
        val latest = (MINUTES_PER_DAY - minutes).coerceAtLeast(0)
        return (time.hour * 60 + time.minute).coerceAtMost(latest)
    }

    /**
     * Throws a definition away for good.
     *
     * `planned_blocks` cascades on delete, so this takes its occurrences with it —
     * including completed ones, which leave the Erfolgsliste. That is what
     * "löschen" has to mean for a definition, and the dialog asks twice before
     * calling it.
     */
    fun delete(item: Item) {
        viewModelScope.launch { itemRepository.delete(item) }
    }

    private data class DayLists(
        val todayBlocks: List<BlockWithItem>,
        val tomorrowBlocks: List<BlockWithItem>,
        val confirmed: Boolean,
        val done: List<DoneEntry>,
        val upcoming: List<BlockWithItem>,
    )
}
