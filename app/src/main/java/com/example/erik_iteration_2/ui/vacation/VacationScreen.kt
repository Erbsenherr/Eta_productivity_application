package com.example.erik_iteration_2.ui.vacation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.model.VacationTreatment
import com.example.erik_iteration_2.domain.vacation.VacationPlan
import com.example.erik_iteration_2.ui.components.ErikButton
import com.example.erik_iteration_2.ui.components.ErikButtonStyle
import com.example.erik_iteration_2.ui.components.ErikChoice
import com.example.erik_iteration_2.ui.components.ErikField
import com.example.erik_iteration_2.ui.components.ErikScreen
import com.example.erik_iteration_2.ui.components.ErikStepper
import com.example.erik_iteration_2.ui.components.ErikSurface
import com.example.erik_iteration_2.ui.components.ErikText
import com.example.erik_iteration_2.ui.components.ErikTextField
import com.example.erik_iteration_2.ui.components.ErikTimePicker
import com.example.erik_iteration_2.ui.format.formatClock
import com.example.erik_iteration_2.ui.format.formatLong
import com.example.erik_iteration_2.ui.theme.ErikTheme

private val TREATMENT_OPTIONS: List<Pair<VacationTreatment?, String>> = listOf(
    null to "Läuft normal weiter",
    VacationTreatment.SUSPEND to "Aussetzen",
    VacationTreatment.MOVE to "Verschieben",
)

/**
 * The Urlaubsmodus: a stretch of days, and what each recurring task does in it.
 *
 * The decision is asked per task and defaults to "carries on" — a holiday only
 * changes what it was told to change. Defaulting the other way would empty the
 * whole schedule the moment a date range was entered.
 */
@Composable
fun VacationScreen(
    viewModel: VacationViewModel,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val label by viewModel.label.collectAsStateWithLifecycle()
    val startsInDays by viewModel.startsInDays.collectAsStateWithLifecycle()
    val lengthInDays by viewModel.lengthInDays.collectAsStateWithLifecycle()
    val decisions by viewModel.decisions.collectAsStateWithLifecycle()

    ErikScreen(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ErikTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    ErikText(text = "Urlaubsmodus", style = ErikTheme.typography.display)
                    ErikText(
                        text = "Danach gilt wieder der gewohnte Plan — von selbst.",
                        style = ErikTheme.typography.caption,
                        color = ErikTheme.colors.textSecondary,
                    )
                }
                ErikButton(text = "Zurück", style = ErikButtonStyle.Secondary, onClick = onClose)
            }

            state.existing.forEach { plan ->
                ExistingVacationBox(plan = plan, onDelete = { viewModel.delete(plan) })
            }

            ErikSurface(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg)) {
                    ErikText(text = "Neuer Zeitraum", style = ErikTheme.typography.heading)

                    ErikField(label = "Name") {
                        ErikTextField(value = label, onValueChange = viewModel::setLabel)
                    }
                    ErikField(
                        label = "Beginn",
                        hint = "Am ${viewModel.from.formatLong()}.",
                    ) {
                        ErikStepper(
                            value = when (startsInDays) {
                                0 -> "heute"
                                1 -> "morgen"
                                else -> "in $startsInDays Tagen"
                            },
                            valueWidth = 104.dp,
                            onDecrement = { viewModel.setStartsInDays(startsInDays - 1) },
                            onIncrement = { viewModel.setStartsInDays(startsInDays + 1) },
                        )
                    }
                    ErikField(
                        label = "Dauer",
                        hint = "Bis einschließlich ${viewModel.to.formatLong()}.",
                    ) {
                        ErikStepper(
                            value = if (lengthInDays == 1) "1 Tag" else "$lengthInDays Tage",
                            valueWidth = 104.dp,
                            onDecrement = { viewModel.setLengthInDays(lengthInDays - 1) },
                            onIncrement = { viewModel.setLengthInDays(lengthInDays + 1) },
                        )
                    }
                }
            }

            ErikText(text = "Wiederkehrende Aufgaben", style = ErikTheme.typography.title)
            ErikText(
                text = "Was nicht angetippt wird, läuft im Urlaub weiter wie sonst.",
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.textMuted,
            )

            if (state.recurring.isEmpty()) {
                ErikSurface(modifier = Modifier.fillMaxWidth()) {
                    ErikText(
                        text = "Es gibt noch keine wiederkehrenden Aufgaben.",
                        style = ErikTheme.typography.body,
                        color = ErikTheme.colors.textMuted,
                    )
                }
            }

            state.recurring.forEach { item ->
                TaskDecision(
                    item = item,
                    draft = decisions[item.id],
                    onChoose = { viewModel.choose(item, it) },
                    onMovedStart = { viewModel.setMovedStart(item, it) },
                )
            }

            ErikButton(
                text = "Urlaub eintragen",
                enabled = decisions.isNotEmpty(),
                onClick = { viewModel.save(onClose) },
            )

            Spacer(Modifier.size(ErikTheme.spacing.xl))
        }
    }
}

@Composable
private fun TaskDecision(
    item: Item,
    draft: DraftRule?,
    onChoose: (VacationTreatment?) -> Unit,
    onMovedStart: (kotlinx.datetime.LocalTime) -> Unit,
) {
    ErikSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.md)) {
            Column {
                ErikText(text = item.name, style = ErikTheme.typography.bodyStrong)
                ErikText(
                    text = item.startTime?.let { "sonst um ${it.formatClock()}" } ?: "ohne Uhrzeit",
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.textMuted,
                )
            }

            ErikChoice(
                options = TREATMENT_OPTIONS,
                selected = draft?.treatment,
                onSelect = onChoose,
            )

            if (draft?.treatment == VacationTreatment.MOVE) {
                ErikField(
                    label = "Stattdessen um",
                    hint = "Nur für diesen Zeitraum; die Aufgabe selbst bleibt unverändert.",
                ) {
                    ErikTimePicker(
                        value = draft.movedStart ?: item.startTime ?: kotlinx.datetime.LocalTime(9, 0),
                        onValueChange = onMovedStart,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExistingVacationBox(plan: VacationPlan, onDelete: () -> Unit) {
    ErikSurface(
        modifier = Modifier.fillMaxWidth(),
        borderColor = ErikTheme.colors.accent,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
            ErikText(text = plan.vacation.label, style = ErikTheme.typography.heading)
            ErikText(
                text = "${plan.vacation.from.formatLong()} bis ${plan.vacation.to.formatLong()}",
                style = ErikTheme.typography.body,
                color = ErikTheme.colors.textSecondary,
            )
            val suspended = plan.rules.count { it.treatment == VacationTreatment.SUSPEND }
            val moved = plan.rules.count { it.treatment == VacationTreatment.MOVE }
            ErikText(
                text = "$suspended ausgesetzt, $moved verschoben.",
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.textMuted,
            )
            ErikButton(
                text = "Urlaub aufheben",
                style = ErikButtonStyle.Secondary,
                onClick = onDelete,
            )
        }
    }
}
