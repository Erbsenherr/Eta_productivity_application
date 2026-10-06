package com.example.eta.ui.reevaluation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.Contract
import com.example.eta.domain.planning.cancellationCharged
import com.example.eta.domain.model.ContractState
import com.example.eta.domain.reevaluation.DailySettlement
import com.example.eta.domain.reevaluation.FREE_UNPLANNED_HOURS
import kotlinx.datetime.TimeZone
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaDialog
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaCheckbox
import com.example.eta.ui.components.EtaChoice
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaProgressBar
import com.example.eta.ui.components.EtaScreen
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.EtaTextField
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.format.formatPoints
import com.example.eta.ui.format.formatShort
import com.example.eta.ui.theme.EtaTheme
import kotlinx.coroutines.launch

private enum class Step {
    TASKS,
    CONTRACTS,
    REWARD,
    JOURNAL,
    RECURRING,
}

/**
 * The five phases of `Tägliche Reevaluation.md`, in order.
 *
 * The order matters and the screen enforces it: what was done is settled before
 * the reward is shown, and the reward is shown before anything is booked.
 */
@Composable
fun ReevaluationScreen(
    viewModel: ReevaluationViewModel,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val journal by viewModel.journal.collectAsStateWithLifecycle()
    val steps = Step.entries
    val pagerState = rememberPagerState { steps.size }
    val scope = rememberCoroutineScope()

    // The settlement rests on every block having been answered: one left open is
    // neither harvest nor forfeit, and costs the points either way. A day with
    // nothing planned has nothing to answer, so it does not trap anyone.
    val openBlocks = state.blocks.count { it.block.isOpen }
    val tasksPending = steps[pagerState.currentPage] == Step.TASKS && openBlocks > 0

    EtaScreen(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(
                    start = EtaTheme.spacing.lg,
                    end = EtaTheme.spacing.lg,
                    top = EtaTheme.spacing.lg,
                ),
            ) {
                EtaText(text = "Tagesabschluss", style = EtaTheme.typography.title)
                EtaText(
                    text = state.date.formatLong(),
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
                )
                Spacer(Modifier.size(EtaTheme.spacing.sm))
                EtaProgressBar((pagerState.currentPage + 1f) / steps.size)
            }

            HorizontalPager(
                state = pagerState,
                // Swiping past would make the gate decorative; going back stays free.
                userScrollEnabled = !tasksPending,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(EtaTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg),
                ) {
                    when (steps[page]) {
                        Step.TASKS -> TasksStep(state, viewModel)
                        Step.CONTRACTS -> ContractsStep(state, viewModel)
                        Step.REWARD -> RewardStep(state.preview)
                        Step.JOURNAL -> JournalStep(journal, viewModel::answer)
                        Step.RECURRING -> RecurringStep(state, viewModel)
                    }
                    Spacer(Modifier.size(EtaTheme.spacing.xl))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(EtaTheme.spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm),
            ) {
                if (pagerState.currentPage > 0) {
                    EtaButton(
                        text = "Zurück",
                        style = EtaButtonStyle.Secondary,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        },
                    )
                }
                if (tasksPending) {
                    EtaText(
                        text = if (openBlocks == 1) {
                            "Eine Aufgabe ist noch offen."
                        } else {
                            "$openBlocks Aufgaben sind noch offen."
                        },
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.warning,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }

                if (pagerState.currentPage == steps.lastIndex) {
                    EtaButton(
                        text = if (state.settled != null) "Gebucht" else "Abschließen",
                        enabled = state.settled == null,
                        onClick = { viewModel.settle(onClose) },
                    )
                } else {
                    EtaButton(
                        text = "Weiter",
                        enabled = !tasksPending,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        },
                    )
                }
            }
        }
    }
}

/** Phase 1: what happened, and what deliberately did not. */
@Composable
private fun TasksStep(state: ReevaluationUiState, viewModel: ReevaluationViewModel) {
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Wurde alles abgearbeitet?", style = EtaTheme.typography.heading)
            EtaText(
                text = "Abgehakte Einträge wandern in die Erfolgsliste. Was bewusst " +
                    "ausgefallen ist, kannst du hier fallen lassen.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )

            if (state.blocks.isEmpty()) {
                EtaText(
                    text = "Für heute war nichts geplant.",
                    style = EtaTheme.typography.body,
                    color = EtaTheme.colors.textMuted,
                )
            }

            state.blocks.forEach { entry ->
                BlockRow(
                    entry = entry,
                    onToggle = { viewModel.toggleCompleted(entry) },
                    onDiscard = { viewModel.discard(entry) },
                    onExcuse = { reason -> viewModel.excuse(entry, reason) },
                )
            }
        }
    }

    // Before the catch-up card, because it is the more specific answer to the same
    // evening: the steps that are left rather than the whole task again.
    state.remainders.forEach { offer ->
        EtaSurface(
            modifier = Modifier.fillMaxWidth(),
            borderColor = EtaTheme.colors.warning,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                EtaText(
                    text = "»${offer.entry.item.name}«: " +
                        if (offer.open.size == 1) {
                            "ein Schritt ist offen geblieben"
                        } else {
                            "${offer.open.size} Schritte sind offen geblieben"
                        },
                    style = EtaTheme.typography.bodyStrong,
                )
                offer.open.forEach { step ->
                    EtaText(
                        text = "· ${step.name}",
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textSecondary,
                    )
                }
                if (offer.createdItem != null) {
                    EtaText(
                        text = "»${offer.createdItem.name}« liegt in der Wochenliste, " +
                            "${offer.createdItem.estimatedDuration?.formatShort() ?: ""} lang.",
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.success,
                    )
                } else {
                    EtaText(
                        text = "Als neue Gruppe in die Wochenliste übernehmen? Sie erbt " +
                            "Kategorie und Priorität; die Dauer ist der Anteil, der offen ist.",
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                        EtaButton(
                            text = "Reste übernehmen",
                            onClick = { viewModel.carryOver(offer) },
                        )
                        EtaButton(
                            text = "Lassen",
                            style = EtaButtonStyle.Secondary,
                            onClick = { viewModel.dismissRemainder(offer) },
                        )
                    }
                }
            }
        }
    }

    state.makeUpOffers.forEach { offer ->
        EtaSurface(
            modifier = Modifier.fillMaxWidth(),
            borderColor = EtaTheme.colors.warning,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                EtaText(
                    text = "»${offer.entry.item.name}« ist ausgefallen",
                    style = EtaTheme.typography.bodyStrong,
                )
                if (offer.createdItem != null) {
                    EtaText(
                        text = "»${offer.createdItem.name}« liegt in der Sammelliste.",
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.success,
                    )
                } else {
                    EtaText(
                        text = "Nachholen?",
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                        EtaButton(text = "Nachholen", onClick = { viewModel.makeUp(offer) })
                        EtaButton(
                            text = "Lassen",
                            style = EtaButtonStyle.Secondary,
                            onClick = { viewModel.dismissMakeUp(offer) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One block of the day, and — for a cancelled one — the way to excuse it.
 *
 * The excuse is **held**, not tapped: a bar fills the row, and only once it has
 * covered it does the dialog open. This is the one control on the screen that
 * undoes a consequence, and the consequence is the mechanic — the same reasoning
 * that makes a catch-up demand a typed-out phrase, at a smaller scale.
 */
@Composable
private fun BlockRow(
    entry: BlockWithItem,
    onToggle: () -> Unit,
    onDiscard: () -> Unit,
    onExcuse: (String) -> Unit = {},
) {
    val cancelled = entry.block.isDiscarded
    var asking by remember(entry.block.id) { mutableStateOf(false) }
    var holding by remember(entry.block.id) { mutableStateOf(false) }
    val fill = remember(entry.block.id) { Animatable(0f) }

    LaunchedEffect(holding) {
        if (!holding) {
            fill.animateTo(0f, tween(EXCUSE_RELEASE_MILLIS))
            return@LaunchedEffect
        }
        fill.animateTo(1f, tween(EXCUSE_HOLD_MILLIS, easing = LinearEasing))
        asking = true
        holding = false
    }

    // Only a cancellation that is actually going to cost something can be
    // excused. One decided in advance, or a forfeited free-time block, costs
    // nothing already — offering to undo a consequence that is not there would
    // only suggest there is one.
    val excusable = entry.cancellationCharged(TimeZone.currentSystemDefault())
    val wash = EtaTheme.colors.warning

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (fill.value > 0f) {
                    drawRect(
                        color = wash.copy(alpha = 0.18f),
                        size = size.copy(width = size.width * fill.value),
                    )
                }
            }
            .then(
                if (excusable) {
                    Modifier.pointerInput(entry.block.id) {
                        detectTapGestures(
                            onPress = {
                                holding = true
                                tryAwaitRelease()
                                holding = false
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EtaCheckbox(
            checked = entry.block.isCompleted,
            enabled = !entry.block.isDiscarded,
            onCheckedChange = { onToggle() },
        )
        Spacer(Modifier.size(EtaTheme.spacing.md))
        Column(Modifier.weight(1f)) {
            EtaText(
                text = entry.item.name,
                style = EtaTheme.typography.body,
                color = if (entry.block.isOpen) {
                    EtaTheme.colors.textPrimary
                } else {
                    EtaTheme.colors.textMuted
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            EtaText(
                text = buildString {
                    append(entry.block.start.formatClock())
                    append(" · ")
                    append(entry.block.effectiveDuration.formatShort())
                    if (cancelled) append(" · abgesagt")
                },
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            if (entry.block.isExcused) {
                EtaText(
                    text = "Höhere Gewalt: ${entry.block.forceMajeure}",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.success,
                )
            } else if (excusable) {
                EtaText(
                    text = "Kostet Punkte. Halten für »höhere Gewalt«.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.warning,
                )
            }
        }
        if (entry.block.isOpen) {
            EtaButton(
                text = "Fällt aus",
                style = EtaButtonStyle.Secondary,
                onClick = onDiscard,
            )
        }
    }

    if (asking) {
        ForceMajeureDialog(
            name = entry.item.name,
            onDismiss = { asking = false },
            onConfirm = {
                onExcuse(it)
                asking = false
            },
        )
    }
}

/** How long the bar takes to fill, and how quickly it falls back when let go. */
private const val EXCUSE_HOLD_MILLIS = 900
private const val EXCUSE_RELEASE_MILLIS = 180

/**
 * The reason a cancellation is not charged for.
 *
 * A reason is required rather than optional: the whole point of the friction is
 * that excusing something should take saying what happened, and an empty field
 * would make it a second button.
 */
@Composable
private fun ForceMajeureDialog(
    name: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var reason by remember { mutableStateOf("") }

    EtaDialog(title = "Höhere Gewalt", onDismiss = onDismiss) {
        EtaText(
            text = "»$name« ist ausgefallen, ohne dass du es in der Hand hattest. " +
                "Die Strafpunkte für diese Absage entfallen dann.",
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )
        EtaField(label = "Was ist passiert?") {
            EtaTextField(
                value = reason,
                onValueChange = { reason = it },
                placeholder = "Der Termin wurde von der Praxis abgesagt.",
                singleLine = false,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            EtaButton(
                text = "Gilt",
                enabled = reason.isNotBlank(),
                onClick = { onConfirm(reason.trim()) },
            )
        }
    }
}

/** Phase 2a: the contract questions. */
@Composable
private fun ContractsStep(state: ReevaluationUiState, viewModel: ReevaluationViewModel) {
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg)) {
            EtaText(text = "Deine Verträge", style = EtaTheme.typography.heading)
            if (state.contracts.isEmpty()) {
                EtaText(
                    text = "Keine laufenden Verträge.",
                    style = EtaTheme.typography.body,
                    color = EtaTheme.colors.textMuted,
                )
            }
            state.contracts.forEach { contract ->
                ContractQuestion(
                    contract = contract,
                    kept = state.verdicts[contract.id] ?: true,
                    keepServing = state.continuations[contract.id] ?: false,
                    onAnswer = { viewModel.setVerdict(contract, it) },
                    onContinuation = { viewModel.setContinuation(contract, it) },
                )
            }
        }
    }
}

/**
 * One contract's evening question — and, after a breach, the second one.
 *
 * The follow-up only appears once the first has been answered with "gebrochen",
 * because until then there is nothing to decide. It is a question rather than a
 * third option on the first choice: "was it kept" and "what now" are not the same
 * question, and rolling them into one list of three would make the breach itself
 * look like two different kinds of breach.
 *
 * A contract **already on probation** is asked the first question and no more.
 * It is broken; the consequence is running; the answer is the record of a habit
 * rather than a verdict with anything left to decide.
 */
@Composable
private fun ContractQuestion(
    contract: Contract,
    kept: Boolean,
    keepServing: Boolean,
    onAnswer: (Boolean) -> Unit,
    onContinuation: (Boolean) -> Unit,
) {
    val onProbation = contract.isOnProbation

    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
        EtaText(
            text = contract.title + when {
                onProbation -> " (gebrochen)"
                contract.state == ContractState.LEGACY -> " (Legacy)"
                else -> ""
            },
            style = EtaTheme.typography.bodyStrong,
        )
        EtaText(
            text = contract.conditions,
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textSecondary,
        )

        if (onProbation) {
            EtaText(
                text = "Weitergeführt nach dem Bruch — die Frage bleibt, die Punkte nicht.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.danger,
            )
            EtaChoice(
                options = listOf(
                    true to "Gehalten — ohne Punkte",
                    false to "Nicht gehalten",
                ),
                selected = kept,
                onSelect = onAnswer,
            )
            return@Column
        }

        EtaChoice(
            options = listOf(
                true to "Gehalten — ${formatPoints(contract.dailyPayout)} Punkte",
                false to "Gebrochen",
            ),
            selected = kept,
            onSelect = onAnswer,
        )

        if (kept) return@Column

        EtaText(
            text = "Der Slot bleibt einen Monat ab Vertragsschluss gesperrt — so oder so. " +
                "Was soll mit dem Vertrag geschehen?",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
        )
        EtaChoice(
            options = listOf(
                false to "Aufgeben — der Vertrag endet hier",
                true to "Weiterführen — ohne Punkte, bleibt im Slot",
            ),
            selected = keepServing,
            onSelect = onContinuation,
        )
    }
}

/** Phase 2b and 3: the reward, and what is deducted from it. */
@Composable
private fun RewardStep(settlement: DailySettlement?) {
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Belohnung", style = EtaTheme.typography.heading)

            if (settlement == null) {
                EtaText(
                    text = "Wird berechnet …",
                    style = EtaTheme.typography.body,
                    color = EtaTheme.colors.textMuted,
                )
                return@Column
            }

            LedgerLine("Erledigte Aufgaben", settlement.harvest)
            LedgerLine("Verträge", settlement.contractPayout)
            if (settlement.legacyGross != 0.0) {
                LedgerLine("Legacy-Verträge", settlement.legacyCredited)
                if (settlement.crowned) {
                    EtaText(
                        text = "👑 ${formatPoints(settlement.legacyGross)} erarbeitet, " +
                            "gutgeschrieben werden höchstens 2.",
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.warning,
                    )
                }
            }
            if (settlement.freeTimeForgone != 0.0) {
                LedgerLine("Freizeit verzichtet", settlement.freeTimeForgone)
            }
            if (settlement.cancellationPenalty != 0.0) {
                LedgerLine("Abgesagt", -settlement.cancellationPenalty)
                EtaText(
                    text = "${formatPoints(settlement.cancelledHours)} h abgesagt. " +
                        "Höhere Gewalt lässt sich oben am jeweiligen Eintrag angeben.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
            }
            if (settlement.unplannedPenalty != 0.0) {
                LedgerLine("Unverplante Zeit", -settlement.unplannedPenalty)
                EtaText(
                    text = "${formatPoints(settlement.unplannedHours)} h unverplant; " +
                        "die ersten ${formatPoints(FREE_UNPLANNED_HOURS)} sind frei.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
                // Says which half of those hours was the user's own doing. Empty
                // hours were never claimed; these were promised and given back.
                if (settlement.droppedHours > 0.0) {
                    EtaText(
                        text = "Davon ${formatPoints(settlement.droppedHours)} h, " +
                            "die verplant und dann fallen gelassen wurden.",
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.warning,
                    )
                }
            }

            Spacer(Modifier.size(EtaTheme.spacing.xs))
            Row {
                EtaText(
                    text = "Summe",
                    style = EtaTheme.typography.bodyStrong,
                    modifier = Modifier.weight(1f),
                )
                EtaText(
                    text = formatPoints(settlement.total),
                    style = EtaTheme.typography.title,
                    color = if (settlement.total < 0) {
                        EtaTheme.colors.danger
                    } else {
                        EtaTheme.colors.success
                    },
                )
            }
            EtaText(
                text = "Gebucht wird erst am Ende.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
        }
    }
}

@Composable
private fun LedgerLine(label: String, amount: Double) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        EtaText(
            text = label,
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        EtaText(
            text = (if (amount > 0) "+" else "") + formatPoints(amount),
            style = EtaTheme.typography.bodyStrong,
            color = when {
                amount > 0 -> EtaTheme.colors.success
                amount < 0 -> EtaTheme.colors.danger
                else -> EtaTheme.colors.textMuted
            },
        )
    }
}

/** Phase 4: the little diary. */
@Composable
private fun JournalStep(answers: Map<String, String>, onAnswer: (String, String) -> Unit) {
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg)) {
            EtaText(text = "Kurz nachdenken", style = EtaTheme.typography.heading)
            EtaText(
                text = "Antworten werden mit Datum abgelegt.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )

            EtaField(label = JournalQuestions.WENT_WELL) {
                EtaTextField(
                    value = answers[JournalQuestions.WENT_WELL].orEmpty(),
                    onValueChange = { onAnswer(JournalQuestions.WENT_WELL, it) },
                    singleLine = false,
                )
            }
            EtaField(label = JournalQuestions.MISSING_RECURRING) {
                EtaTextField(
                    value = answers[JournalQuestions.MISSING_RECURRING].orEmpty(),
                    onValueChange = { onAnswer(JournalQuestions.MISSING_RECURRING, it) },
                    singleLine = false,
                )
            }
            EtaField(label = JournalQuestions.STRESS) {
                EtaChoice(
                    options = StressLevel.entries.map { it.name to it.label },
                    selected = answers[JournalQuestions.STRESS].orEmpty(),
                    onSelect = { onAnswer(JournalQuestions.STRESS, it) },
                )
            }

            // The follow-ups only appear when the previous answer calls for them.
            if (answers[JournalQuestions.STRESS] == StressLevel.SEHR.name) {
                EtaField(label = JournalQuestions.STRESS_FROM_PLAN) {
                    EtaChoice(
                        options = listOf("Ja" to "Ja", "Nein" to "Nein"),
                        selected = answers[JournalQuestions.STRESS_FROM_PLAN].orEmpty(),
                        onSelect = { onAnswer(JournalQuestions.STRESS_FROM_PLAN, it) },
                    )
                }
            }
            if (answers[JournalQuestions.STRESS_FROM_PLAN] == "Ja") {
                EtaField(label = JournalQuestions.PLAN_CHANGE) {
                    EtaTextField(
                        value = answers[JournalQuestions.PLAN_CHANGE].orEmpty(),
                        onValueChange = { onAnswer(JournalQuestions.PLAN_CHANGE, it) },
                        singleLine = false,
                    )
                }
            }
        }
    }
}

/** Phase 5: are the recurring tasks still right? */
@Composable
private fun RecurringStep(state: ReevaluationUiState, viewModel: ReevaluationViewModel) {
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Wiederkehrende Aufgaben", style = EtaTheme.typography.heading)
            EtaText(
                text = "Was heute lief — passt das noch? Ausrangierte Aufgaben tauchen " +
                    "künftig nicht mehr auf, Vergangenes bleibt in der Erfolgsliste.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            if (state.recurringDefinitions.isEmpty()) {
                EtaText(
                    text = "Heute lief keine wiederkehrende Aufgabe.",
                    style = EtaTheme.typography.body,
                    color = EtaTheme.colors.textMuted,
                )
            }
            state.recurringDefinitions.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EtaText(
                        text = item.name,
                        style = EtaTheme.typography.body,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    EtaButton(
                        text = "Ausrangieren",
                        style = EtaButtonStyle.Secondary,
                        onClick = { viewModel.retireRecurring(item) },
                    )
                }
            }
        }
    }
}
