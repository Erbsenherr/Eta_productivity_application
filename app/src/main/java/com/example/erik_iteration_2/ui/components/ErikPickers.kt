package com.example.erik_iteration_2.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.erik_iteration_2.ui.format.formatShort
import com.example.erik_iteration_2.ui.format.formatVeryShort
import com.example.erik_iteration_2.ui.theme.ErikTheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.DayOfWeek

/** A labelled row that puts its control on the right and wraps under on narrow screens. */
@Composable
fun ErikField(
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    control: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.xs),
    ) {
        ErikText(text = label, style = ErikTheme.typography.bodyStrong)
        if (hint != null) {
            ErikText(
                text = hint,
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.textMuted,
            )
        }
        Spacer(Modifier.size(ErikTheme.spacing.xs))
        control()
    }
}

/** A duration in 15-minute steps, never dropping below [minimum]. */
@Composable
fun ErikDurationPicker(
    value: Duration,
    onValueChange: (Duration) -> Unit,
    modifier: Modifier = Modifier,
    step: Duration = 15.minutes,
    minimum: Duration = Duration.ZERO,
) {
    ErikStepper(
        modifier = modifier,
        value = if (value == Duration.ZERO) "keine" else value.formatShort(),
        valueWidth = 78.dp,
        onDecrement = { onValueChange((value - step).coerceAtLeast(minimum)) },
        onIncrement = { onValueChange(value + step) },
    )
}

/** One choice among a handful, stacked so German labels have room to breathe. */
@Composable
fun <T> ErikChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm),
    ) {
        options.forEach { (option, label) ->
            ErikChoiceRow(
                label = label,
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
private fun ErikChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = ErikTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (selected) ErikTheme.colors.accentSoft else ErikTheme.colors.surface,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = if (selected) ErikTheme.colors.accent else ErikTheme.colors.border,
                shape = shape,
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = ErikTheme.spacing.lg, vertical = ErikTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .border(
                    width = if (selected) 4.dp else 1.dp,
                    color = if (selected) ErikTheme.colors.accent else ErikTheme.colors.border,
                    shape = CircleShape,
                ),
        )
        Spacer(Modifier.width(ErikTheme.spacing.md))
        ErikText(
            text = label,
            style = ErikTheme.typography.body,
            color = if (selected) ErikTheme.colors.textPrimary else ErikTheme.colors.textSecondary,
        )
    }
}

/** Mo–So as seven equally wide pills, one of them picked. */
@Composable
fun ErikWeekdayPicker(
    selected: DayOfWeek,
    onSelect: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
    days: List<DayOfWeek>,
) = ErikWeekdayPicker(
    selected = setOf(selected),
    onToggle = onSelect,
    modifier = modifier,
    days = days,
)

/**
 * The same pills, any number of them picked.
 *
 * Toggling the last remaining day off is ignored: every answer that uses this
 * has to happen *somewhere*, and an empty set would silently delete the task
 * rather than reading as an answer.
 */
@Composable
fun ErikWeekdayPicker(
    selected: Set<DayOfWeek>,
    onToggle: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
    days: List<DayOfWeek>,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.xs),
    ) {
        days.forEach { day ->
            val isSelected = day in selected
            val interactionSource = remember(day) { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = if (isSelected) {
                            ErikTheme.colors.accent
                        } else {
                            ErikTheme.colors.surface
                        },
                        shape = ErikTheme.shapes.small,
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) ErikTheme.colors.accent else ErikTheme.colors.border,
                        shape = ErikTheme.shapes.small,
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { if (!isSelected || selected.size > 1) onToggle(day) },
                    )
                    .padding(vertical = ErikTheme.spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                ErikText(
                    text = day.formatVeryShort(),
                    style = ErikTheme.typography.label,
                    color = if (isSelected) {
                        ErikTheme.colors.onAccent
                    } else {
                        ErikTheme.colors.textSecondary
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
