package com.example.eta.ui.tutorial

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.eta.di.AppContainer
import com.example.eta.domain.model.Stage
import com.example.eta.domain.tutorial.TutorialId
import com.example.eta.domain.tutorial.StepKind
import com.example.eta.domain.tutorial.TUTORIAL_EVENING
import com.example.eta.domain.tutorial.TUTORIAL_MORNING
import com.example.eta.domain.tutorial.TutorialClock
import com.example.eta.domain.tutorial.TutorialFacts
import com.example.eta.domain.tutorial.TutorialStage
import com.example.eta.domain.tutorial.TutorialStep
import com.example.eta.domain.tutorial.exitTarget
import com.example.eta.domain.tutorial.remembering
import com.example.eta.domain.tutorial.tutorialFacts
import com.example.eta.ui.components.TutorialGuide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus

/**
 * One run of the tutorial: a practice app of its own.
 *
 * A second [AppContainer] over a database that exists only in memory, on a
 * clock of its own, with a view model store of its own. The tutorial's screens
 * are the app's real screens, built from *this* container — which is the whole
 * of how the tutorial keeps out of the user's data: there is no code path to
 * guard, because nothing here holds a reference to the real database at all.
 *
 * The store matters as much as the database. The screens ask for their view
 * models by key, and in the activity's store a second run would be handed the
 * first run's — still wired to a database that has been thrown away.
 */
class TutorialSession(context: Context) : ViewModelStoreOwner {

    val clock = TutorialClock()

    val container = AppContainer(context, sandbox = true, clock = clock)

    override val viewModelStore = ViewModelStore()

    fun close() {
        viewModelStore.clear()
        container.close()
    }
}

/** Where the tutorial stands before and around its steps. */
enum class TutorialPhase {
    IDLE,

    /** The idea of the app, shown again when the tutorial is repeated. */
    CONCEPT,

    /** Quickstart, or — one day — the long one. */
    CHOICE,

    /** The practice database is being laid down. */
    LOADING,
    RUNNING,
}

private data class Signals(
    val latest: Map<String, String> = emptyMap(),
    val seen: Set<String> = emptySet(),
)

/** How long a "that is locked here" line stays up. */
private const val HINT_MILLIS = 3_500L

/**
 * How long a finished run's database is left open behind it. Its screens are
 * gone by then; a query of theirs still in flight would otherwise meet a closed
 * database.
 */
private const val CLOSE_DELAY_MILLIS = 3_000L

@OptIn(ExperimentalCoroutinesApi::class)
class TutorialViewModel(
    private val appContext: Context,
    private val store: TutorialStore,
) : ViewModel() {

    /** Which tutorial is running; its steps are what everything below walks. */
    var tutorial: TutorialId = TutorialId.QUICKSTART
        private set

    val steps: List<TutorialStep> get() = tutorial.steps

    private val _phase = MutableStateFlow(TutorialPhase.IDLE)
    val phase: StateFlow<TutorialPhase> = _phase.asStateFlow()

    private val _session = MutableStateFlow<TutorialSession?>(null)
    val session: StateFlow<TutorialSession?> = _session.asStateFlow()

    private val _index = MutableStateFlow(0)
    val index: StateFlow<Int> = _index.asStateFlow()

    private val _hint = MutableStateFlow<String?>(null)
    val hint: StateFlow<String?> = _hint.asStateFlow()
    private var hintJob: Job? = null

    private val signals = MutableStateFlow(Signals())

    /**
     * Whether the step in front had already been done when it came up — which
     * happens on the way **back**. A [StepKind.FOLLOW] step then gets a "Weiter"
     * of its own instead of following by itself: it would otherwise throw the
     * user forward again the instant they stepped back onto it.
     */
    private val _arrivedReady = MutableStateFlow(false)
    val arrivedReady: StateFlow<Boolean> = _arrivedReady.asStateFlow()

    /** Which half of the simulated day the clock stands in. */
    private var evening = false
    private val eveningFrom: Int get() = steps.indexOfFirst { it.evening }

    val guide = TutorialGuide { key, value ->
        signals.update {
            Signals(latest = it.latest + (key to value), seen = it.seen + "$key=$value")
        }
    }

    val facts: StateFlow<TutorialFacts> = _session
        .flatMapLatest { session -> session?.let(::databaseFacts) ?: flowOf(TutorialFacts()) }
        .combine(signals) { facts, signals ->
            facts.copy(signals = signals.latest, seen = signals.seen)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TutorialFacts())

    init {
        // A step whose way on is a button of the screen itself: the moment what
        // it waited for has happened, the tutorial follows.
        viewModelScope.launch {
            combine(_index, facts, _phase) { index, facts, phase -> Triple(index, facts, phase) }
                .collect { (index, facts, phase) ->
                    val step = steps.getOrNull(index) ?: return@collect
                    // The frame follows the step as it goes on.
                    if (phase == TutorialPhase.RUNNING) guide.spot = step.spotAt(facts)
                    if (phase == TutorialPhase.RUNNING &&
                        step.kind == StepKind.FOLLOW &&
                        !_arrivedReady.value &&
                        step.ready(facts)
                    ) {
                        advanceFrom(index)
                    }
                }
        }
    }

    private fun databaseFacts(session: TutorialSession): Flow<TutorialFacts> {
        val container = session.container
        val today = session.clock.today
        // What the feature tutorials have the user make, counted.
        val made = combine(
            container.growthService.observeGrowthTasks(),
            container.contractRepository.observeAll(),
            container.rewardRepository.observe(),
        ) { growth, contracts, rewards -> Triple(growth.size, contracts.size, rewards.size) }

        return combine(
            container.itemRepository.observeStage(Stage.COLLECTION),
            container.itemRepository.observeStage(Stage.WEEK),
            container.planRepository.observeDay(today),
            container.planRepository.observeDay(today.plus(DatePeriod(days = 1))),
            container.planRepository.observeDayPlan(today),
        ) { collection, week, todayBlocks, tomorrowBlocks, dayPlan ->
            tutorialFacts(
                collection = collection,
                week = week,
                today = todayBlocks,
                tomorrow = tomorrowBlocks,
                daySettled = dayPlan?.settledAt != null,
            )
        }.combine(made) { facts, (growth, contracts, rewards) ->
            facts.copy(growthTasks = growth, contracts = contracts, rewards = rewards)
        }.scan(TutorialFacts()) { earlier, now -> now.remembering(earlier) }
    }

    /**
     * The tutorial has come onto the screen. Does nothing when it already was —
     * this view model outlives a rotation, and the run with it.
     */
    fun enter(showConcept: Boolean, direct: TutorialId? = null) {
        if (_phase.value != TutorialPhase.IDLE) return
        if (direct != null) {
            // Asked for by name, beside the switch it explains: no page about
            // the idea of the app and no list to pick it from again.
            _phase.value = TutorialPhase.CHOICE
            start(direct)
            return
        }
        _phase.value = if (showConcept) TutorialPhase.CONCEPT else TutorialPhase.CHOICE
    }

    fun conceptRead() {
        if (_phase.value == TutorialPhase.CONCEPT) _phase.value = TutorialPhase.CHOICE
    }

    /** Builds the practice app and its example day, then shows the first step. */
    fun start(id: TutorialId) {
        if (_phase.value != TutorialPhase.CHOICE) return
        _phase.value = TutorialPhase.LOADING
        tutorial = id
        viewModelScope.launch {
            val session = TutorialSession(appContext)
            session.container.tutorialSeed?.lay(id)
            signals.value = Signals()
            evening = false
            _session.value = session
            show(0)
            _phase.value = TutorialPhase.RUNNING
        }
    }

    /** "Weiter" on the tutorial's own bar. */
    fun next() {
        val index = _index.value
        val step = steps.getOrNull(index) ?: return
        val open = when (step.kind) {
            StepKind.FOLLOW -> _arrivedReady.value
            else -> step.passable(facts.value)
        }
        if (open) advanceFrom(index)
    }

    /**
     * "Zurück" on the tutorial's bar: the step before, to read it again.
     *
     * Nothing is undone — what was ticked stays ticked, what was noted stays
     * noted — so a task come back to shows as done, and the clock goes back to
     * the morning only if the step belongs there.
     */
    fun back() {
        val index = _index.value
        if (_phase.value == TutorialPhase.RUNNING && index > 0) show(index - 1)
    }

    /**
     * "Tag abschließen" on the dashboard. The way into the evening, but only at
     * the step that asks for it; before that it is a way sideways like any other.
     */
    fun closeDayPressed() {
        val step = steps.getOrNull(_index.value) ?: return
        if (step.stage == TutorialStage.DASHBOARD && step.kind == StepKind.FOLLOW) {
            exitPressed(TutorialStage.DASHBOARD)
        } else {
            locked()
        }
    }

    /**
     * One of the screen's own ways out was pressed — "Abschließen", "Woche
     * steht", "Zurück", the system back inside a phase.
     */
    fun exitPressed(stage: TutorialStage) {
        val index = _index.value
        // A screen that is already gone finishing what it was doing.
        if (steps.getOrNull(index)?.stage != stage) return
        when (val target = exitTarget(steps, index, stage, facts.value)) {
            null -> say("Erst die Aufgabe hier unten erledigen — dann geht es weiter.")
            else -> if (target >= steps.size) finish() else show(target)
        }
    }

    /** Something the tutorial keeps shut was tapped: a way to another screen. */
    fun locked() = say("Im Tutorial gesperrt — folge einfach den Schritten hier unten.")

    /** Over, by its last step, by "Überspringen" or by "Beenden". */
    fun finish() {
        val session = _session.value
        store.finish()
        _session.value = null
        _phase.value = TutorialPhase.IDLE
        _index.value = 0
        _hint.value = null
        guide.spot = null
        guide.allowed = emptySet()
        signals.value = Signals()
        if (session != null) {
            // Not on the view model's scope: the screens are leaving and this has
            // to outlast them by a moment — see CLOSE_DELAY_MILLIS.
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                delay(CLOSE_DELAY_MILLIS)
                runCatching { session.close() }
            }
        }
    }

    private fun advanceFrom(index: Int) {
        if (_index.value != index) return
        if (index + 1 >= steps.size) finish() else show(index + 1)
    }

    private fun show(index: Int) {
        val step = steps[index]
        // Before the screen that reads the hour is built, not after. Derived
        // from where the step stands rather than set on the way past, so a
        // stage skipped into, or a step gone back to, is at the right hour.
        val late = eveningFrom in 0..index
        if (late != evening) {
            evening = late
            _session.value?.clock?.jumpTo(if (late) TUTORIAL_EVENING else TUTORIAL_MORNING)
        }
        guide.spot = step.spotAt(facts.value)
        guide.allowed = step.allow
        _hint.value = null
        _arrivedReady.value = step.kind == StepKind.FOLLOW && step.ready(facts.value)
        _index.value = index
    }

    private fun say(text: String) {
        _hint.value = text
        hintJob?.cancel()
        hintJob = viewModelScope.launch {
            delay(HINT_MILLIS)
            _hint.value = null
        }
    }

    override fun onCleared() {
        runCatching { _session.value?.close() }
    }
}

fun tutorialViewModelFactory(context: Context, store: TutorialStore): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { TutorialViewModel(context.applicationContext, store) }
    }
