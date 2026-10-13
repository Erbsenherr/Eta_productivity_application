package com.example.eta.ui.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.data.local.BlockWithItem
import com.example.eta.alarm.TaskStartCoordinator
import com.example.eta.alarm.WakeAlarmCoordinator
import com.example.eta.data.repository.ItemRepository
import com.example.eta.data.repository.NightService
import com.example.eta.data.repository.PlanRepository
import com.example.eta.data.repository.RecurringTaskService
import com.example.eta.data.repository.SetupRepository
import com.example.eta.data.repository.SubtaskRepository
import com.example.eta.domain.growth.isGrowthTask
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.NightOverride
import com.example.eta.domain.model.Subtask
import com.example.eta.domain.planning.minuteOfDay
import com.example.eta.domain.planning.minuteToLocalTime
import com.example.eta.domain.planning.occupiesTime
import com.example.eta.domain.planning.sleepStretches
import com.example.eta.domain.recurrence.RecurringGroup
import com.example.eta.domain.recurrence.groupRecurring
import com.example.eta.domain.recurrence.mondayOf
import com.example.eta.domain.recurrence.rescheduleClashes
import com.example.eta.domain.recurrence.standingBlocksOn
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.setup.WEEK
import com.example.eta.domain.setup.isOwnedBySettings
import com.example.eta.ui.attributes.RecurringAttributes
import com.example.eta.ui.attributes.toEdit
import com.example.eta.ui.format.formatVeryShort
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/**
 * Which week the timetable shows.
 *
 * [CURRENT] is this calendar week as it stands — every block on its days,
 * whatever put it there. [GENERAL] is the standing week: the recurring tasks
 * and nothing that only happens once.
 */
enum class WeekView(val label: String) {
    CURRENT("Aktuell"),
    GENERAL("Generell"),
}

data class WeekTabUiState(
    val today: LocalDate,
    /** The Monday of the week [today] lies in. */
    val weekStart: LocalDate,
    /** Every live standing definition — what "Generell" draws and what an edit is compared against. */
    val definitions: List<Item> = emptyList(),
    val setup: UserSetup? = null,
    /** This week's blocks by date, cancellations left out as the planner leaves them out. */
    val blocks: Map<LocalDate, List<BlockWithItem>> = emptyMap(),
    val subtasks: Map<String, List<Subtask>> = emptyMap(),
    val foldable: List<Item> = emptyList(),
    /** Nights changed for one date — shown in "Aktuell", which is this week as it is. */
    val nightOverrides: Map<LocalDate, NightOverride> = emptyMap(),
) {
    fun dateOf(day: DayOfWeek): LocalDate = weekStart.plus(DatePeriod(days = WEEK.indexOf(day)))

    /**
     * The night as it falls on [day], for the shading: the standing one in
     * "Generell", and in "Aktuell" whatever that date really has.
     */
    fun sleepOn(day: DayOfWeek, view: WeekView): List<IntRange> = when (view) {
        WeekView.GENERAL -> setup?.sleepStretches(day)
        WeekView.CURRENT -> setup?.sleepStretches(dateOf(day), nightOverrides)
    }.orEmpty()

    /**
     * What [day] shows in [view].
     *
     * [unlocked] rounds the corners of what may then be dragged — a standing task
     * that [canReschedule] — and of nothing else: in "Aktuell" a ToDo keeps its
     * own round corners, which there say what it is rather than what can be done
     * with it.
     */
    fun blocksOn(day: DayOfWeek, view: WeekView, unlocked: Boolean): List<BlockWithItem> = when (view) {
        WeekView.GENERAL -> standingBlocksOn(day, dateOf(day), definitions, unlocked = false).map { entry ->
            entry.copy(block = entry.block.copy(flexible = unlocked && canReschedule(entry)))
        }

        WeekView.CURRENT -> blocks[dateOf(day)].orEmpty().map { entry ->
            if (isStanding(entry)) {
                entry.copy(block = entry.block.copy(flexible = unlocked && canReschedule(entry)))
            } else {
                entry
            }
        }
    }

    /** Whether a live standing task is behind this block — the only kind the timetable edits. */
    fun isStanding(entry: BlockWithItem): Boolean =
        entry.block.origin == BlockOrigin.RECURRING && definitions.any { it.id == entry.item.id }

    /**
     * Whether a drag or a pinch may change the task behind this block.
     *
     * Not the two that hang off the night — the settings regenerate them from
     * the sleep and wake times, and an hour dragged here would be overwritten by
     * the next save there. And only an occurrence that is still open: what is
     * done or called off is a record of that day, not a handle on the task.
     */
    fun canReschedule(entry: BlockWithItem): Boolean =
        isStanding(entry) && entry.block.isOpen && !isOwnedBySettings(entry.item.id)

    /** The task as the user thinks of it — every weekday that shares its answers. */
    fun groupOf(itemId: String): RecurringGroup? =
        groupRecurring(definitions).firstOrNull { itemId in it.ids }
}

/**
 * A move or a pinch that needs an answer before it is written.
 *
 * Two questions at most, in this order: **which days** — the one it was made
 * on, or every day of the task — and, if all of them and some would then
 * collide with something, **what about those**.
 */
data class PendingReschedule(
    val itemId: String,
    val name: String,
    /** The weekday the gesture was made on. It moves whatever is answered. */
    val day: DayOfWeek,
    /** Every weekday of the task, [day] included. */
    val groupDays: Set<DayOfWeek>,
    val startMinute: Int,
    val durationMinutes: Int,
    /**
     * Null while the first question stands. Then: the *other* days on which the
     * new slot runs into something, with the names of what it runs into.
     */
    val clashes: Map<DayOfWeek, List<String>>? = null,
)

/**
 * The "Woche" page of the dashboard: the week as a timetable, and the standing
 * tasks changed by hand on it.
 *
 * Everything written here is written to a **definition** — in "Aktuell" as much
 * as in "Generell": dragging this Thursday's Sport changes Sport, not this
 * Thursday. A single day is changed where it always was, in the day planner.
 */
class WeekTabViewModel(
    private val nightService: NightService,
    private val wakeAlarmCoordinator: WakeAlarmCoordinator,
    private val taskStartCoordinator: TaskStartCoordinator,
    private val recurringTaskService: RecurringTaskService,
    planRepository: PlanRepository,
    setupRepository: SetupRepository,
    subtaskRepository: SubtaskRepository,
    itemRepository: ItemRepository,
    clock: Clock = Clock.System,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    val today: LocalDate = clock.todayIn(timeZone)
    private val weekStart: LocalDate = mondayOf(today)

    private val _pending = MutableStateFlow<PendingReschedule?>(null)
    val pending: StateFlow<PendingReschedule?> = _pending.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    val uiState: StateFlow<WeekTabUiState> = combine(
        recurringTaskService.observeDefinitions(),
        setupRepository.observe(),
        planRepository.observeRange(weekStart, weekStart.plus(DatePeriod(days = WEEK.size - 1))),
        combine(subtaskRepository.observeByItem(), itemRepository.observeFoldCandidates(), ::Pair),
        nightService.observeOverrides(),
    ) { definitions, setup, blocks, (subtasks, foldable), overrides ->
        WeekTabUiState(
            today = today,
            weekStart = weekStart,
            definitions = definitions,
            setup = setup,
            blocks = blocks.filter { it.occupiesTime() }.groupBy { it.block.date },
            subtasks = subtasks,
            foldable = foldable,
            nightOverrides = overrides,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WeekTabUiState(today = today, weekStart = weekStart),
    )

    /**
     * A standing task dragged or pinched on [day].
     *
     * Takes the definition's **id** and reads everything else from the current
     * state — the rule every gesture in this app follows. A task that stands on
     * that one weekday only is simply written; one that stands on several asks
     * first.
     */
    fun reschedule(itemId: String, day: DayOfWeek, startMinute: Int, durationMinutes: Int? = null) {
        val state = uiState.value
        val item = state.definitions.firstOrNull { it.id == itemId } ?: return
        val group = state.groupOf(itemId) ?: return
        val start = item.startTime?.minuteOfDay() ?: return
        val length = item.estimatedDuration?.inWholeMinutes?.toInt() ?: return
        // A drag names an hour and nothing else; the task keeps its length.
        val wanted = durationMinutes ?: length
        if (start == startMinute && length == wanted) return

        // Growing owns the length of a growth task, and where a dynamic one
        // stands is worked out for it day by day. Both would take the gesture
        // back at the next evening, so it is refused here and said why.
        if (item.isGrowthTask && length != wanted) {
            _notice.value = "»${item.name}« arbeitet auf eine Zielzeit hin — ihre Dauer wächst von " +
                "selbst und lässt sich hier nicht ziehen."
            return
        }
        if (item.isGrowthTask && item.growthDynamic) {
            _notice.value = "»${item.name}« wird dynamisch eingeplant und hat keine feste Uhrzeit."
            return
        }

        val request = PendingReschedule(
            itemId = itemId,
            name = item.name,
            day = day,
            groupDays = group.weekdays,
            startMinute = startMinute,
            durationMinutes = wanted,
        )
        if (group.weekdays.size <= 1) apply(request, setOf(day)) else _pending.value = request
    }

    /** The first answer: only the day it was made on, or every day of the task. */
    fun answerScope(allDays: Boolean) {
        val request = _pending.value ?: return
        if (!allDays) {
            apply(request, setOf(request.day))
            return
        }
        val state = uiState.value
        val group = state.groupOf(request.itemId)
        if (group == null) {
            _pending.value = null
            return
        }
        // The day the gesture was made on is the one the user was looking at;
        // what is asked about are the others.
        val clashes = rescheduleClashes(
            group = group.definitions,
            days = request.groupDays - request.day,
            startTime = minuteToLocalTime(request.startMinute),
            duration = request.durationMinutes.minutes,
            definitions = state.definitions,
        )
        if (clashes.isEmpty()) {
            apply(request, request.groupDays)
        } else {
            _pending.value = request.copy(
                clashes = clashes.mapValues { (_, others) -> others.map { it.name }.distinct() },
            )
        }
    }

    /** The second answer: move the colliding days as well, or leave those where they are. */
    fun answerClashes(moveAnyway: Boolean) {
        val request = _pending.value ?: return
        val clashing = request.clashes?.keys.orEmpty()
        apply(request, if (moveAnyway) request.groupDays else request.groupDays - clashing)
    }

    fun dismissPending() {
        _pending.value = null
    }

    private fun apply(request: PendingReschedule, days: Set<DayOfWeek>) {
        _pending.value = null
        val state = uiState.value
        val group = state.groupOf(request.itemId) ?: return
        val start = minuteToLocalTime(request.startMinute)
        val duration = request.durationMinutes.minutes
        // Read before the write: afterwards the task has moved and would be
        // compared against itself in its new place.
        val clashes = rescheduleClashes(group.definitions, days, start, duration, state.definitions)

        viewModelScope.launch {
            recurringTaskService.reschedule(group.ids, days, start, duration)
            // A warning, not a refusal — two things at once can be meant, and
            // the same collision is said in the Listen tab's form.
            _notice.value = if (clashes.isEmpty()) {
                null
            } else {
                clashes.entries.joinToString(
                    prefix = "»${request.name}« überschneidet sich jetzt: ",
                    separator = "; ",
                ) { (day, others) ->
                    "${day.formatVeryShort()} mit " + others.map { it.name }.distinct().joinToString(", ") { "»$it«" }
                }
            }
        }
    }

    /** The edit window's "Sichern": the whole task, as on the Listen tab. */
    fun saveGroup(group: RecurringGroup, name: String, note: String?, attributes: RecurringAttributes) {
        if (attributes.weekdays.isEmpty()) return
        viewModelScope.launch {
            recurringTaskService.saveGroup(ids = group.ids, edit = attributes.toEdit(name, note))
        }
    }

    /** A standing task made from a box shaped on the timetable. */
    fun create(name: String, note: String?, attributes: RecurringAttributes) {
        if (attributes.weekdays.isEmpty()) return
        viewModelScope.launch {
            recurringTaskService.createGroup(attributes.toEdit(name, note))
        }
    }

    /**
     * The standing night that ends on [day], changed — for that weekday alone,
     * which is what the user asked the timetable to do. Both alarms that hang
     * off the night are aimed again, as the settings' own save does.
     */
    fun saveNight(day: DayOfWeek, sleep: LocalTime, wake: LocalTime) {
        viewModelScope.launch {
            nightService.saveWeekdayNight(day, sleep, wake)
            wakeAlarmCoordinator.reschedule()
            taskStartCoordinator.reschedule()
        }
    }

    /** A long press on something that only happens once — a ToDo, an appointment. */
    fun refuseOneOff(name: String) {
        _notice.value = "»$name« ist keine wiederkehrende Task. Einen einzelnen Tag änderst " +
            "du im Tagesplaner — über »Heute umplanen« oder »Morgen planen«."
    }

    /** A long press on one of the two tasks the settings own. */
    fun refuseSettingsOwned(name: String) {
        _notice.value = "»$name« hängt an deinen Schlaf- und Aufstehzeiten und wird in den " +
            "Einstellungen unter »Schlaf und Morgen« geändert."
    }

    fun dismissNotice() {
        _notice.value = null
    }
}
