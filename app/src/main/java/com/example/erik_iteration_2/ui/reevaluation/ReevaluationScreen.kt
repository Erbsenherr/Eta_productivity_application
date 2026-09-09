package com.example.erik_iteration_2.ui.reevaluation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.erik_iteration_2.data.local.BlockWithItem
import com.example.erik_iteration_2.domain.model.Contract
import com.example.erik_iteration_2.domain.model.ContractState
import com.example.erik_iteration_2.domain.reevaluation.DailySettlement
import com.example.erik_iteration_2.domain.reevaluation.FREE_UNPLANNED_HOURS
import com.example.erik_iteration_2.ui.components.ErikButton
import com.example.erik_iteration_2.ui.components.ErikButtonStyle
import com.example.erik_iteration_2.ui.components.ErikCheckbox
import com.example.erik_iteration_2.ui.components.ErikChoice
import com.example.erik_iteration_2.ui.components.ErikField
import com.example.erik_iteration_2.ui.components.ErikScreen
import com.example.erik_iteration_2.ui.components.ErikSurface
import com.example.erik_iteration_2.ui.components.ErikText
import com.example.erik_iteration_2.ui.components.ErikTextField
import com.example.erik_iteration_2.ui.format.formatClock
import com.example.erik_iteration_2.ui.format.formatLong
import com.example.erik_iteration_2.ui.format.formatPoints
import com.example.erik_iteration_2.ui.format.formatShort
import com.example.erik_iteration_2.ui.theme.ErikTheme
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

    ErikScreen(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(
                    start = ErikTheme.spacing.lg,
                    end = ErikTheme.spacing.lg,
                    top = ErikTheme.spacing.lg,
                ),
            ) {
                ErikText(text = "Tagesabschluss", style = ErikTheme.typography.title)
                ErikText(
                    text = state.date.formatLong(),
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.textSecondary,
                )
                Spacer(Modifier.size(ErikTheme.spacing.sm))
                ProgressBar((pagerState.currentPage + 1f) / steps.size)
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
                        .padding(ErikTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg),
                ) {
                    when (steps[page]) {
                        Step.TASKS -> TasksStep(state, viewModel)
                        Step.CONTRACTS -> ContractsStep(state, viewModel)
                        Step.REWARD -> RewardStep(state.preview)
                        Step.JOURNAL -> JournalStep(journal, viewModel::answer)
                        Step.RECURRING -> RecurringStep(state, viewModel)
                    }
                    Spacer(Modifier.size(ErikTheme.spacing.xl))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ErikTheme.spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm),
            ) {
                if (pagerState.currentPage > 0) {
                    ErikButton(
                        text = "Zurück",
                        style = ErikButtonStyle.Secondary,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        },
                    )
                }
                if (tasksPending) {
                    ErikText(
                        text = if (openBlocks == 1) {
                            "Eine Aufgabe ist noch offen."
                        } else {
                            "$openBlocks Aufgaben sind noch offen."
                        },
                        style = ErikTheme.typography.caption,
                        color = ErikTheme.colors.warning,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }

                if (pagerState.currentPage == steps.lastIndex) {
                    ErikButton(
                        text = if (state.settled != null) "Gebucht" else "Abschließen",
                        enabled = state.settled == null,
                        onClick = { viewModel.settle(onClose) },
                    )
                } else {
                    ErikButton(
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

@Composable
private fun ProgressBar(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(ErikTheme.colors.border, ErikTheme.shapes.pill),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(4.dp)
                .background(ErikTheme.colors.accent, ErikTheme.shapes.pill),
        )
    }
}

/** Phase 1: what happened, and what deliberately did not. */
@Composable
private fun TasksStep(state: ReevaluationUiState, viewModel: ReevaluationViewModel) {
    ErikSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.md)) {
            ErikText(text = "Wurde alles abgearbeitet?", style = ErikTheme.typography.heading)
            ErikText(
                text = "Abgehakte Einträge wandern in die Erfolgsliste. Was bewusst " +
                    "ausgefallen ist, kannst du hier fallen lassen.",
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.textMuted,
            )

            if (state.blocks.isEmpty()) {
                ErikText(
                    text = "Für heute war nichts geplant.",
                    style = ErikTheme.typography.body,
                    color = ErikTheme.colors.textMuted,
                )
            }

            state.blocks.forEach { entry ->
                BlockRow(
                    entry = entry,
                    onToggle = { viewModel.toggleCompleted(entry) },
                    onDiscard = { viewModel.discard(entry) },
                )
            }
        }
    }

    state.makeUpOffers.forEach { offer ->
        ErikSurface(
            modifier = Modifier.fillMaxWidth(),
            borderColor = ErikTheme.colors.warning,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
                ErikText(
                    text = "»${offer.entry.item.name}« ist ausgefallen",
                    style = ErikTheme.typography.bodyStrong,
                )
                if (offer.createdItem != null) {
                    ErikText(
                        text = "»${offer.createdItem.name}« liegt in der Sammelliste.",
                        style = ErikTheme.typography.caption,
                        color = ErikTheme.colors.success,
                    )
                } else {
                    ErikText(
                        text = "Nachholen?",
                        style = ErikTheme.typography.caption,
                        color = ErikTheme.colors.textSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
                        ErikButton(text = "Nachholen", onClick = { viewModel.makeUp(offer) })
                        ErikButton(
                            text = "Lassen",
                            style = ErikButtonStyle.Secondary,
                            onClick = { viewModel.dismissMakeUp(offer) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockRow(
    entry: BlockWithItem,
    onToggle: () -> Unit,
    onDiscard: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ErikCheckbox(
            checked = entry.block.isCompleted,
            enabled = !entry.block.isDiscarded,
            onCheckedChange = { onToggle() },
        )
        Spacer(Modifier.size(ErikTheme.spacing.md))
        Column(Modifier.weight(1f)) {
            ErikText(
                text = entry.item.name,
                style = ErikTheme.typography.body,
                color = if (entry.block.isOpen) {
                    ErikTheme.colors.textPrimary
                } else {
                    ErikTheme.colors.textMuted
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ErikText(
                text = buildString {
                    append(entry.block.start.formatClock())
                    append(" · ")
                    append(entry.block.effectiveDuration.formatShort())
                    if (entry.block.isDiscarded) append(" · fallen gelassen")
                },
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.textMuted,
            )
        }
        if (entry.block.isOpen) {
            ErikButton(
                text = "Fällt aus",
                style = ErikButtonStyle.Secondary,
                onClick = onDiscard,
            )
        }
    }
}

/** Phase 2a: the contract questions. */
@Composable
private fun ContractsStep(state: ReevaluationUiState, viewModel: ReevaluationViewModel) {
    ErikSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg)) {
            ErikText(text = "Deine Verträge", style = ErikTheme.typography.heading)
            if (state.contracts.isEmpty()) {
                ErikText(
                    text = "Keine laufenden Verträge.",
                    style = ErikTheme.typography.body,
                    color = ErikTheme.colors.textMuted,
                )
            }
            state.contracts.forEach { contract ->
                ContractQuestion(
                    contract = contract,
                    kept = state.verdicts[contract.id] ?: true,
                    onAnswer = { viewModel.setVerdict(contract, it) },
                )
            }
        }
    }
}

@Composable
private fun ContractQuestion(contract: Contract, kept: Boolean, onAnswer: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
        ErikText(
            text = contract.title + if (contract.state == ContractState.LEGACY) " (Legacy)" else "",
            style = ErikTheme.typography.bodyStrong,
        )
        ErikText(
            text = contract.conditions,
            style = ErikTheme.typography.caption,
            color = ErikTheme.colors.textSecondary,
        )
        ErikChoice(
            options = listOf(
                true to "Gehalten — ${formatPoints(contract.dailyPayout)} Punkte",
                false to "Gebrochen — Vertrag endet, Slot gesperrt",
            ),
            selected = kept,
            onSelect = onAnswer,
        )
    }
}

/** Phase 2b and 3: the reward, and what is deducted from it. */
@Composable
private fun RewardStep(settlement: DailySettlement?) {
    ErikSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.md)) {
            ErikText(text = "Belohnung", style = ErikTheme.typography.heading)

            if (settlement == null) {
                ErikText(
                    text = "Wird berechnet …",
                    style = ErikTheme.typography.body,
                    color = ErikTheme.colors.textMuted,
                )
                return@Column
            }

            LedgerLine("Erledigte Aufgaben", settlement.harvest)
            LedgerLine("Verträge", settlement.contractPayout)
            if (settlement.legacyGross != 0.0) {
                LedgerLine("Legacy-Verträge", settlement.legacyCredited)
                if (settlement.crowned) {
                    ErikText(
                        text = "👑 ${formatPoints(settlement.legacyGross)} erarbeitet, " +
                            "gutgeschrieben werden höchstens 2.",
                        style = ErikTheme.typography.caption,
                        color = ErikTheme.colors.warning,
                    )
                }
            }
            if (settlement.freeTimeForgone != 0.0) {
                LedgerLine("Freizeit verzichtet", settlement.freeTimeForgone)
            }
            if (settlement.unplannedPenalty != 0.0) {
                LedgerLine("Unverplante Zeit", -settlement.unplannedPenalty)
                ErikText(
                    text = "${formatPoints(settlement.unplannedHours)} h unverplant; " +
                        "die ersten ${formatPoints(FREE_UNPLANNED_HOURS)} sind frei.",
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.textMuted,
                )
                // Says which half of those hours was the user's own doing. Empty
                // hours were never claimed; these were promised and given back.
                if (settlement.droppedHours > 0.0) {
                    ErikText(
                        text = "Davon ${formatPoints(settlement.droppedHours)} h, " +
                            "die verplant und dann fallen gelassen wurden.",
                        style = ErikTheme.typography.caption,
                        color = ErikTheme.colors.warning,
                    )
                }
            }

            Spacer(Modifier.size(ErikTheme.spacing.xs))
            Row {
                ErikText(
                    text = "Summe",
                    style = ErikTheme.typography.bodyStrong,
                    modifier = Modifier.weight(1f),
                )
                ErikText(
                    text = formatPoints(settlement.total),
                    style = ErikTheme.typography.title,
                    color = if (settlement.total < 0) {
                        ErikTheme.colors.danger
                    } else {
                        ErikTheme.colors.success
                    },
                )
            }
            ErikText(
                text = "Gebucht wird erst am Ende.",
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.textMuted,
            )
        }
    }
}

@Composable
private fun LedgerLine(label: String, amount: Double) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ErikText(
            text = label,
            style = ErikTheme.typography.body,
            color = ErikTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        ErikText(
            text = (if (amount > 0) "+" else "") + formatPoints(amount),
            style = ErikTheme.typography.bodyStrong,
            color = when {
                amount > 0 -> ErikTheme.colors.success
                amount < 0 -> ErikTheme.colors.danger
                else -> ErikTheme.colors.textMuted
            },
        )
    }
}

/** Phase 4: the little diary. */
@Composable
private fun JournalStep(answers: Map<String, String>, onAnswer: (String, String) -> Unit) {
    ErikSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg)) {
            ErikText(text = "Kurz nachdenken", style = ErikTheme.typography.heading)
            ErikText(
                text = "Antworten werden mit Datum abgelegt.",
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.textMuted,
            )

            ErikField(label = JournalQuestions.WENT_WELL) {
                ErikTextField(
                    value = answers[JournalQuestions.WENT_WELL].orEmpty(),
                    onValueChange = { onAnswer(JournalQuestions.WENT_WELL, it) },
                    singleLine = false,
                )
            }
            ErikField(label = JournalQuestions.MISSING_RECURRING) {
                ErikTextField(
                    value = answers[JournalQuestions.MISSING_RECURRING].orEmpty(),
                    onValueChange = { onAnswer(JournalQuestions.MISSING_RECURRING, it) },
                    singleLine = false,
                )
            }
            ErikField(label = JournalQuestions.STRESS) {
                ErikChoice(
                    options = StressLevel.entries.map { it.name to it.label },
                    selected = answers[JournalQuestions.STRESS].orEmpty(),
                    onSelect = { onAnswer(JournalQuestions.STRESS, it) },
                )
            }

            // The follow-ups only appear when the previous answer calls for them.
            if (answers[JournalQuestions.STRESS] == StressLevel.SEHR.name) {
                ErikField(label = JournalQuestions.STRESS_FROM_PLAN) {
                    ErikChoice(
                        options = listOf("Ja" to "Ja", "Nein" to "Nein"),
                        selected = answers[JournalQuestions.STRESS_FROM_PLAN].orEmpty(),
                        onSelect = { onAnswer(JournalQuestions.STRESS_FROM_PLAN, it) },
                    )
                }
            }
            if (answers[JournalQuestions.STRESS_FROM_PLAN] == "Ja") {
                ErikField(label = JournalQuestions.PLAN_CHANGE) {
                    ErikTextField(
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
    ErikSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.md)) {
            ErikText(text = "Wiederkehrende Aufgaben", style = ErikTheme.typography.heading)
            ErikText(
                text = "Was heute lief — passt das noch? Ausrangierte Aufgaben tauchen " +
                    "künftig nicht mehr auf, Vergangenes bleibt in der Erfolgsliste.",
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.textMuted,
            )
            if (state.recurringDefinitions.isEmpty()) {
                ErikText(
                    text = "Heute lief keine wiederkehrende Aufgabe.",
                    style = ErikTheme.typography.body,
                    color = ErikTheme.colors.textMuted,
                )
            }
            state.recurringDefinitions.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ErikText(
                        text = item.name,
                        style = ErikTheme.typography.body,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ErikButton(
                        text = "Ausrangieren",
                        style = ErikButtonStyle.Secondary,
                        onClick = { viewModel.retireRecurring(item) },
                    )
                }
            }
        }
    }
}
