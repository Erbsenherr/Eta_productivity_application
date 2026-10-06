package com.example.eta.ui.vacation

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
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.VacationTreatment
import com.example.eta.domain.vacation.VacationPlan
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaChoice
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaScreen
import com.example.eta.ui.components.EtaStepper
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.EtaTextField
import com.example.eta.ui.components.EtaTimePicker
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.theme.EtaTheme

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

    EtaScreen(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(EtaTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    EtaText(text = "Urlaubsmodus", style = EtaTheme.typography.display)
                    EtaText(
                        text = "Danach gilt wieder der gewohnte Plan — von selbst.",
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textSecondary,
                    )
                }
                EtaButton(text = "Zurück", style = EtaButtonStyle.Secondary, onClick = onClose)
            }

            state.existing.forEach { plan ->
                ExistingVacationBox(plan = plan, onDelete = { viewModel.delete(plan) })
            }

            EtaSurface(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg)) {
                    EtaText(text = "Neuer Zeitraum", style = EtaTheme.typography.heading)

                    EtaField(label = "Name") {
                        EtaTextField(value = label, onValueChange = viewModel::setLabel)
                    }
                    EtaField(
                        label = "Beginn",
                        hint = "Am ${viewModel.from.formatLong()}.",
                    ) {
                        EtaStepper(
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
                    EtaField(
                        label = "Dauer",
                        hint = "Bis einschließlich ${viewModel.to.formatLong()}.",
                    ) {
                        EtaStepper(
                            value = if (lengthInDays == 1) "1 Tag" else "$lengthInDays Tage",
                            valueWidth = 104.dp,
                            onDecrement = { viewModel.setLengthInDays(lengthInDays - 1) },
                            onIncrement = { viewModel.setLengthInDays(lengthInDays + 1) },
                        )
                    }
                }
            }

            EtaText(text = "Wiederkehrende Aufgaben", style = EtaTheme.typography.title)
            EtaText(
                text = "Was nicht angetippt wird, läuft im Urlaub weiter wie sonst.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )

            if (state.recurring.isEmpty()) {
                EtaSurface(modifier = Modifier.fillMaxWidth()) {
                    EtaText(
                        text = "Es gibt noch keine wiederkehrenden Aufgaben.",
                        style = EtaTheme.typography.body,
                        color = EtaTheme.colors.textMuted,
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

            EtaButton(
                text = "Urlaub eintragen",
                enabled = decisions.isNotEmpty(),
                onClick = { viewModel.save(onClose) },
            )

            Spacer(Modifier.size(EtaTheme.spacing.xl))
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
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            Column {
                EtaText(text = item.name, style = EtaTheme.typography.bodyStrong)
                EtaText(
                    text = item.startTime?.let { "sonst um ${it.formatClock()}" } ?: "ohne Uhrzeit",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
            }

            EtaChoice(
                options = TREATMENT_OPTIONS,
                selected = draft?.treatment,
                onSelect = onChoose,
            )

            if (draft?.treatment == VacationTreatment.MOVE) {
                EtaField(
                    label = "Stattdessen um",
                    hint = "Nur für diesen Zeitraum; die Aufgabe selbst bleibt unverändert.",
                ) {
                    EtaTimePicker(
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
    EtaSurface(
        modifier = Modifier.fillMaxWidth(),
        borderColor = EtaTheme.colors.accent,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaText(text = plan.vacation.label, style = EtaTheme.typography.heading)
            EtaText(
                text = "${plan.vacation.from.formatLong()} bis ${plan.vacation.to.formatLong()}",
                style = EtaTheme.typography.body,
                color = EtaTheme.colors.textSecondary,
            )
            val suspended = plan.rules.count { it.treatment == VacationTreatment.SUSPEND }
            val moved = plan.rules.count { it.treatment == VacationTreatment.MOVE }
            EtaText(
                text = "$suspended ausgesetzt, $moved verschoben.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            EtaButton(
                text = "Urlaub aufheben",
                style = EtaButtonStyle.Secondary,
                onClick = onDelete,
            )
        }
    }
}
