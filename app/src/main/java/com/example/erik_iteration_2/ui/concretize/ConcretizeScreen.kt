package com.example.erik_iteration_2.ui.concretize

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.erik_iteration_2.domain.model.Category
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.model.Priority
import com.example.erik_iteration_2.ui.components.ErikButton
import com.example.erik_iteration_2.ui.components.ErikButtonStyle
import com.example.erik_iteration_2.ui.components.ErikChoice
import com.example.erik_iteration_2.ui.components.ErikDurationPicker
import com.example.erik_iteration_2.ui.components.ErikField
import com.example.erik_iteration_2.ui.components.ErikScreen
import com.example.erik_iteration_2.ui.components.ErikStepper
import com.example.erik_iteration_2.ui.components.ErikSurface
import com.example.erik_iteration_2.ui.components.ErikText
import com.example.erik_iteration_2.ui.components.ErikTextField
import com.example.erik_iteration_2.ui.format.formatLong
import com.example.erik_iteration_2.ui.theme.ErikTheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus

private val CATEGORY_OPTIONS = listOf(
    Category.FOKUS to "Fokus — voller Einsatz",
    Category.NEBENBEI to "Nebenbei — geht auch beiläufig",
    Category.ACHTSAM to "Achtsam — Zeit für dich",
)

private val PRIORITY_OPTIONS = listOf(
    Priority.URGENT_MUST to "Muss zeitnah geschehen",
    Priority.MUST to "Muss geschehen",
    Priority.URGENT_WANT to "Soll zeitnah geschehen",
    Priority.WANT to "Soll geschehen",
)

/**
 * Phase 2 of the daily planning: the Quick-Add notes get their attributes.
 *
 * One card at a time rather than a long form: each of these is a decision, and a
 * screenful of half-filled rows invites tapping through them without deciding.
 */
@Composable
fun ConcretizeScreen(
    viewModel: ConcretizeViewModel,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {},
) {
    val pending by viewModel.pending.collectAsStateWithLifecycle()

    ErikScreen(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ErikTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg),
        ) {
            Column {
                ErikText(text = "Quick-Adds ausfüllen", style = ErikTheme.typography.title)
                ErikText(
                    text = "Ohne Kategorie, Priorität und Dauer lässt sich ein ToDo nicht " +
                        "verplanen — der Revolver bietet nur fertige Karten an.",
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.textSecondary,
                )
            }

            if (pending.isEmpty()) {
                ErikSurface(modifier = Modifier.fillMaxWidth()) {
                    ErikText(
                        text = "Nichts offen — alle Notizen sind ausgefüllt.",
                        style = ErikTheme.typography.body,
                        color = ErikTheme.colors.textMuted,
                    )
                }
            }

            pending.forEach { item ->
                ConcretizeCard(
                    item = item,
                    today = viewModel.today,
                    onSave = { name, category, priority, inDays, duration ->
                        viewModel.concretize(item, name, category, priority, inDays, duration)
                    },
                    onDiscard = { viewModel.discard(item) },
                )
            }

            ErikButton(
                text = if (pending.isEmpty()) "Weiter zur Tagesplanung" else "Rest später",
                onClick = onDone,
            )

            Spacer(Modifier.size(ErikTheme.spacing.xl))
        }
    }
}

@Composable
private fun ConcretizeCard(
    item: Item,
    today: kotlinx.datetime.LocalDate,
    onSave: (String, Category, Priority, Int, Duration) -> Unit,
    onDiscard: () -> Unit,
) {
    var name by remember(item.id) { mutableStateOf(item.name) }
    var category by remember(item.id) { mutableStateOf(Category.FOKUS) }
    var priority by remember(item.id) { mutableStateOf(Priority.MUST) }
    var inDays by remember(item.id) { mutableIntStateOf(7) }
    var duration by remember(item.id) { mutableStateOf(1.hours) }

    ErikSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg)) {
            ErikField(label = "Name") {
                ErikTextField(value = name, onValueChange = { name = it })
            }
            ErikField(label = "Kategorie") {
                ErikChoice(
                    options = CATEGORY_OPTIONS,
                    selected = category,
                    onSelect = { category = it },
                )
            }
            ErikField(label = "Priorität") {
                ErikChoice(
                    options = PRIORITY_OPTIONS,
                    selected = priority,
                    onSelect = { priority = it },
                )
            }
            ErikField(
                label = "Zieldatum",
                // The card unlocks a week early, which is worth saying out loud.
                hint = "Am ${today.plus(DatePeriod(days = inDays)).formatLong()}. " +
                    "Planbar wird es eine Woche vorher.",
            ) {
                ErikStepper(
                    value = when (inDays) {
                        0 -> "heute"
                        1 -> "morgen"
                        else -> "in $inDays Tagen"
                    },
                    valueWidth = 104.dp,
                    onDecrement = { inDays = (inDays - 1).coerceAtLeast(0) },
                    onIncrement = { inDays += 1 },
                )
            }
            ErikField(label = "Geschätzte Dauer") {
                ErikDurationPicker(
                    value = duration,
                    onValueChange = { duration = it },
                    minimum = 15.minutes,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
                ErikButton(
                    text = "Verwerfen",
                    style = ErikButtonStyle.Secondary,
                    onClick = onDiscard,
                )
                Spacer(Modifier.weight(1f))
                ErikButton(
                    text = "Übernehmen",
                    onClick = { onSave(name, category, priority, inDays, duration) },
                )
            }
        }
    }
}
