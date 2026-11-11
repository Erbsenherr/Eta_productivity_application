package com.example.eta.ui.weekplanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.data.repository.InflationPreview
import com.example.eta.data.repository.ItemRepository
import com.example.eta.data.repository.PlanRepository
import com.example.eta.data.repository.ScheduleMaintenance
import com.example.eta.data.repository.SetupRepository
import com.example.eta.data.repository.WeekPlanningService
import com.example.eta.data.repository.WeekScope
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.withExtras
import com.example.eta.ui.attributes.TodoAttributes
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.Priority
import com.example.eta.domain.model.Stage
import com.example.eta.domain.planning.WeekBudget
import com.example.eta.domain.planning.weekBudget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.minus
import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.NightOverride
import com.example.eta.domain.model.SleepLog
import com.example.eta.domain.planning.sleepStretches
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.stats.WeekReview
import com.example.eta.domain.stats.weekReview

/** The one question the weekly evaluation field asks. */
const val WEEKLY_QUESTION = "Was soll in der kommenden Woche besser laufen?"

data class WeekPlannerUiState(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val collection: List<Item> = emptyList(),
    val weekList: List<Item> = emptyList(),
    val budget: WeekBudget = WeekBudget(0, 0, 0),
    /**
     * Goals of an earlier cycle still lying unplanned in the week list. While any
     * remain, the week list takes on nothing new — but a goal that has been
     * planned into a day is no longer one of them, so an emptied revolver always
     * means the week can be filled again.
     */
    val unfinished: List<Item> = emptyList(),
    /**
     * ToDos a group can swallow as steps.
     *
     * Derived from the two lists this screen already reads rather than from a query
     * of its own: the week's goals and the Sammelliste's ToDos are exactly the set
     * `observeFoldCandidates` returns.
     */
    val foldable: List<Item> = emptyList(),
) {
    /** New goals only once what was already committed to is worked off. */
    val canTakeOnMore: Boolean get() = unfinished.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
class WeekPlannerViewModel(
    private val weekPlanningService: WeekPlanningService,
    private val itemRepository: ItemRepository,
    planRepository: PlanRepository,
    private val scheduleMaintenance: ScheduleMaintenance,
    setupRepository: SetupRepository,
    private val scope: WeekScope = WeekScope.SCHEDULED,
    private val clock: Clock = Clock.System,
    /** Step 58: the Wochenrückblick reads the history; null leaves it out. */
    private val reviewSources: ReviewSources? = null,
) : ViewModel() {

    /**
     * The stretch being planned, and so the stretch the free hours are counted
     * over. The scheduled phase lays out the week starting tomorrow; a mid-week
     * top-up covers the seven days from today, since that is what the user is
     * adding to.
     */
    val weekStart: LocalDate = when (scope) {
        WeekScope.SCHEDULED -> weekPlanningService.weekStart()
        WeekScope.MIDWEEK -> weekPlanningService.today()
    }
    val weekEnd: LocalDate = weekPlanningService.weekEnd()
    val isMidWeek: Boolean = scope == WeekScope.MIDWEEK

    /**
     * The planning day this cycle began on. Only known once the setup is read, so
     * the gate's query hangs off a flow rather than a value captured too early —
     * reading it at construction would have asked about the wrong cycle.
     */
    private val cycleStart = MutableStateFlow(weekPlanningService.today())

    private val _inflation = MutableStateFlow<InflationPreview?>(null)
    val inflation: StateFlow<InflationPreview?> = _inflation.asStateFlow()

    private val _evaluation = MutableStateFlow("")
    val evaluation: StateFlow<String> = _evaluation.asStateFlow()

    private val _banned = MutableStateFlow<Int?>(null)
    val banned: StateFlow<Int?> = _banned.asStateFlow()

    /**
     * The Wochenrückblick (step 58): the seven days before the week planned,
     * against every day before them. Null until read, and for a top-up.
     */
    private val _review = MutableStateFlow<WeekReview?>(null)
    val review: StateFlow<WeekReview?> = _review.asStateFlow()

    /** Whether the phase has a Wochenrückblick at all — the scheduled one, with its history. */
    val hasReview: Boolean get() = reviewSources != null && !isMidWeek

    val uiState: StateFlow<WeekPlannerUiState> = combine(
        itemRepository.observeStage(Stage.COLLECTION),
        itemRepository.observeStage(Stage.WEEK),
        planRepository.observeRange(weekStart, weekEnd),
        setupRepository.observe(),
        cycleStart.flatMapLatest { itemRepository.observeUnfinishedWeekGoals(it) },
    ) { collection, weekList, blocks, setup, unfinished ->
        WeekPlannerUiState(
            unfinished = unfinished,
            weekStart = weekStart,
            weekEnd = weekEnd,
            // A ToDo whose week has not come round yet cannot be pulled in — and
            // the Sammelliste now also holds bare recurring notes, which are not
            // week goals at all: they belong to the standing schedule, and the
            // evening's concretizing step is what puts them there.
            collection = collection.filter {
                it.type == ItemType.TODO && it.isAvailableOn(weekEnd)
            },
            weekList = weekList,
            foldable = weekList + collection.filter { it.type == ItemType.TODO },
            budget = weekBudget(setup, blocks, weekList),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WeekPlannerUiState(weekStart, weekEnd),
    )

    init {
        viewModelScope.launch {
            cycleStart.value = weekPlanningService.cycleStart()
            // The week being planned has to exist before its free hours can be counted.
            scheduleMaintenance.topUpUntil(weekEnd)
            _inflation.value = weekPlanningService.inflationPreview()
            // The one-month ban is checked here: weekly is timely enough for a
            // monthly rule, and doing it daily would only add noise.
            _banned.value = weekPlanningService.sweepStaleCollectionItems().takeIf { it > 0 }
        }
        if (hasReview) viewModelScope.launch { _review.value = readReview() }
    }

    private suspend fun readReview(): WeekReview? {
        val sources = reviewSources ?: return null
        val setup = sources.setup() ?: return null
        val to = weekStart.minus(DatePeriod(days = 1))
        val from = weekStart.minus(DatePeriod(days = 7))
        val overrides = sources.overrides()
        return weekReview(
            blocks = sources.blocks(REVIEW_EARLIEST, to),
            logs = sources.logs(REVIEW_EARLIEST, to),
            from = from,
            to = to,
            today = weekPlanningService.today(),
            sleepMinutesOn = { date -> setup.sleepStretches(date, overrides).sumOf { it.last - it.first + 1 } },
        )
    }

    /**
     * Leaving the phase, by whichever door.
     *
     * Nothing is booked here any more: the devaluation hangs off its own weekday
     * and has already happened by the time this screen opens. The exit stays a
     * single point so the screen has one story about how it is left.
     */
    fun leave(onDone: () -> Unit) {
        onDone()
    }

    fun setEvaluation(text: String) {
        _evaluation.update { text }
    }

    fun pullIntoWeek(item: Item) {
        viewModelScope.launch { itemRepository.takeIntoWeek(item, cycleStart.value) }
    }

    /**
     * Back to the Sammelliste. [ItemRepository.moveTo] restarts the timeout clock
     * on the way in, which is what keeps a deliberately deferred ToDo safe from
     * the ban it would otherwise walk into.
     */
    fun returnToCollection(item: Item) {
        viewModelScope.launch { itemRepository.moveTo(item, Stage.COLLECTION) }
    }

    val today: LocalDate get() = weekPlanningService.today()

    /**
     * A goal thought of during the planning, straight into the Sammelliste.
     *
     * Concretized on the spot: a bare note could be pulled into the week and then
     * never planned, because the revolver only offers finished cards. The
     * Sperrliste can still veto the name — that check is the whole point of it.
     */
    fun addToCollection(name: String, attributes: TodoAttributes, onDone: () -> Unit) {
        viewModelScope.launch {
            val now = clock.now()
            itemRepository.add(
                Item.newTodo(
                    name = name,
                    category = attributes.category,
                    priority = attributes.priority,
                    targetDate = attributes.unlockFrom,
                    estimatedDuration = attributes.duration,
                    now = now,
                ).copy(
                    travelBefore = attributes.travelBefore,
                    returnAfter = attributes.returnAfter,
                    breakAfter = attributes.breakAfter,
                    endSound = attributes.endSound,
                ).withExtras(attributes.extras),
                subtasks = attributes.subtasks,
                folded = attributes.foldedItemIds,
            )
            onDone()
        }
    }

    /** Closes the phase: the retrospective, then the same exit as any other. */
    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            weekPlanningService.recordEvaluation(WEEKLY_QUESTION, _evaluation.value)
            leave(onDone)
        }
    }
}

/** Before anything this app can hold: the Wochenrückblick compares with all of it. */
private val REVIEW_EARLIEST = LocalDate(2000, 1, 1)

/**
 * What the Wochenrückblick reads (step 58), as four functions so the view
 * model needs no further repositories — and the tutorial's practice week, which
 * has none worth reading, simply passes none.
 */
class ReviewSources(
    val setup: suspend () -> UserSetup?,
    val overrides: suspend () -> Map<LocalDate, NightOverride>,
    val blocks: suspend (from: LocalDate, to: LocalDate) -> List<BlockWithItem>,
    val logs: suspend (from: LocalDate, to: LocalDate) -> List<SleepLog>,
)
