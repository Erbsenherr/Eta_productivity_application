package com.example.erik_iteration_2.ui.weekplanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.window.Dialog
import com.example.erik_iteration_2.domain.model.Category
import com.example.erik_iteration_2.domain.model.Priority
import com.example.erik_iteration_2.ui.components.ErikButton
import com.example.erik_iteration_2.ui.components.ErikButtonStyle
import com.example.erik_iteration_2.ui.components.ErikChoice
import com.example.erik_iteration_2.ui.components.ErikDurationPicker
import com.example.erik_iteration_2.ui.components.ErikField
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

private val CATEGORY_OPTIONS = listOf(
    Category.FOKUS to "Fokus",
    Category.NEBENBEI to "Nebenbei",
    Category.ACHTSAM to "Achtsam",
)

private val PRIORITY_OPTIONS = listOf(
    Priority.URGENT_MUST to "Muss zeitnah geschehen",
    Priority.MUST to "Muss geschehen",
    Priority.URGENT_WANT to "Soll zeitnah geschehen",
    Priority.WANT to "Soll geschehen",
)

/**
 * Adding something to the Sammelliste without leaving the weekly planning.
 *
 * Created **already concretized**, unlike the dashboard's Quick-Add. A bare note
 * pulled into the week would sit there unplannable — the revolver only offers
 * finished cards — so a goal invented here answers the same questions the
 * concretizing step would have asked anyway.
 */
@Composable
fun AddGoalDialog(
    today: LocalDate,
    onDismiss: () -> Unit,
    onCreate: (
        name: String,
        category: Category,
        priority: Priority,
        inDays: Int,
        duration: Duration,
    ) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Category.FOKUS) }
    var priority by remember { mutableStateOf(Priority.MUST) }
    var inDays by remember { mutableIntStateOf(7) }
    var duration by remember { mutableStateOf(1.hours) }

    Dialog(onDismissRequest = onDismiss) {
        ErikSurface(
            modifier = Modifier.widthIn(max = 400.dp),
            color = ErikTheme.colors.surfaceRaised,
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 600.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg),
            ) {
                ErikText(text = "Neues ToDo", style = ErikTheme.typography.title)
                ErikText(
                    text = "Landet in der Sammelliste und kann gleich in die Woche.",
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.textMuted,
                )

                ErikField(label = "Name") {
                    ErikTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "Was steht an?",
                    )
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
                    hint = "Am ${today.plus(DatePeriod(days = inDays)).formatLong()}.",
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
                        text = "Abbrechen",
                        style = ErikButtonStyle.Secondary,
                        onClick = onDismiss,
                    )
                    Spacer(Modifier.weight(1f))
                    ErikButton(
                        text = "Anlegen",
                        enabled = name.isNotBlank(),
                        onClick = { onCreate(name.trim(), category, priority, inDays, duration) },
                    )
                }
            }
        }
    }
}
