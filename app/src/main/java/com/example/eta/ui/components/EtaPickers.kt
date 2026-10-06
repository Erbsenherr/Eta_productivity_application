package com.example.eta.ui.components

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.eta.ui.format.formatShort
import com.example.eta.ui.format.formatVeryShort
import com.example.eta.ui.theme.EtaTheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.DayOfWeek

/** A labelled row that puts its control on the right and wraps under on narrow screens. */
@Composable
fun EtaField(
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    control: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs),
    ) {
        EtaText(text = label, style = EtaTheme.typography.bodyStrong)
        if (hint != null) {
            EtaText(
                text = hint,
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
        }
        Spacer(Modifier.size(EtaTheme.spacing.xs))
        control()
    }
}

/**
 * A duration in 15-minute steps, never dropping below [minimum].
 *
 * [precise] makes the value itself a target that opens [DurationEntryDialog],
 * where hours, minutes and seconds are typed — for the growth task, whose
 * lengths the user wants to set to the second. Stepping stays for the ordinary
 * nudge.
 */
@Composable
fun EtaDurationPicker(
    value: Duration,
    onValueChange: (Duration) -> Unit,
    modifier: Modifier = Modifier,
    step: Duration = 15.minutes,
    minimum: Duration = Duration.ZERO,
    precise: Boolean = false,
) {
    var typing by remember { mutableStateOf(false) }
    EtaStepper(
        modifier = modifier,
        value = if (value == Duration.ZERO) "keine" else value.formatShort(),
        valueWidth = if (precise) 96.dp else 78.dp,
        onDecrement = { onValueChange((value - step).coerceAtLeast(minimum)) },
        onIncrement = { onValueChange(value + step) },
        onValueClick = if (precise) {
            { typing = true }
        } else {
            null
        },
    )
    if (typing) {
        DurationEntryDialog(
            value = value,
            minimum = minimum,
            onDismiss = { typing = false },
            onConfirm = {
                onValueChange(it)
                typing = false
            },
        )
    }
}

/**
 * Hours, minutes and seconds typed rather than stepped to.
 *
 * Three fields rather than one parsed string: "1:05" could be an hour and five
 * minutes or a minute and five seconds, and a field per unit leaves nothing to
 * guess. Empty counts as zero. A result below [minimum] is refused with the
 * floor named, rather than silently raised.
 */
@Composable
fun DurationEntryDialog(
    value: Duration,
    minimum: Duration,
    onDismiss: () -> Unit,
    onConfirm: (Duration) -> Unit,
) {
    var hoursText by remember { mutableStateOf(value.inWholeHours.toString()) }
    var minutesText by remember { mutableStateOf((value.inWholeMinutes % 60).toString()) }
    var secondsText by remember { mutableStateOf((value.inWholeSeconds % 60).toString()) }

    val h = durationPart(hoursText)
    val m = durationPart(minutesText)
    val s = durationPart(secondsText)
    val total = if (h != null && m != null && s != null) h.hours + m.minutes + s.seconds else null
    val valid = total != null && total >= minimum

    EtaDialog(title = "Dauer eingeben", onDismiss = onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            UnitField("Std.", hoursText, { hoursText = it }, Modifier.weight(1f))
            UnitField("Min.", minutesText, { minutesText = it }, Modifier.weight(1f))
            UnitField("Sek.", secondsText, { secondsText = it }, Modifier.weight(1f))
        }
        EtaText(
            text = when {
                total == null -> "Nur ganze Zahlen."
                !valid -> "Mindestens ${minimum.formatShort()}."
                else -> "= ${total.formatShort()}"
            },
            style = EtaTheme.typography.caption,
            color = if (valid) EtaTheme.colors.textSecondary else EtaTheme.colors.warning,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            EtaButton(
                text = "Übernehmen",
                enabled = valid,
                onClick = { if (valid && total != null) onConfirm(total) },
            )
        }
    }
}

/** Empty is zero; anything but a non-negative whole number is no answer. */
private fun durationPart(text: String): Long? =
    if (text.isBlank()) 0L else text.trim().toLongOrNull()?.takeIf { it >= 0 }

@Composable
private fun UnitField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
        EtaText(text = label, style = EtaTheme.typography.caption, color = EtaTheme.colors.textMuted)
        EtaTextField(
            value = value,
            onValueChange = { text -> onValueChange(text.filter(Char::isDigit).take(4)) },
            keyboardType = KeyboardType.Number,
        )
    }
}

/**
 * A whole number within [range], stepped or — tapping the value — typed. For
 * counts, where twelve taps to reach twelve is not an answer.
 */
@Composable
fun EtaCountPicker(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    format: (Int) -> String = { it.toString() },
    title: String = "Anzahl eingeben",
) {
    var typing by remember { mutableStateOf(false) }
    EtaStepper(
        modifier = modifier,
        value = format(value),
        valueWidth = 96.dp,
        onDecrement = { onValueChange((value - 1).coerceIn(range)) },
        onIncrement = { onValueChange((value + 1).coerceIn(range)) },
        onValueClick = { typing = true },
    )
    if (typing) {
        NumberEntryDialog(
            title = title,
            value = value,
            range = range,
            onDismiss = { typing = false },
            onConfirm = {
                onValueChange(it)
                typing = false
            },
        )
    }
}

/** One whole number typed, clamped into [range]. */
@Composable
fun NumberEntryDialog(
    title: String,
    value: Int,
    range: IntRange,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    label: String = "Zahl",
) {
    var text by remember { mutableStateOf(value.toString()) }
    val parsed = text.trim().toIntOrNull()

    EtaDialog(title = title, onDismiss = onDismiss) {
        EtaField(label = label, hint = "${range.first} bis ${range.last}.") {
            EtaTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(6) },
                keyboardType = KeyboardType.Number,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            EtaButton(
                text = "Übernehmen",
                enabled = parsed != null,
                onClick = { parsed?.let { onConfirm(it.coerceIn(range)) } },
            )
        }
    }
}

/** One choice among a handful, stacked so German labels have room to breathe. */
@Composable
fun <T> EtaChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm),
    ) {
        options.forEach { (option, label) ->
            EtaChoiceRow(
                label = label,
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
private fun EtaChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = EtaTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (selected) EtaTheme.colors.accentSoft else EtaTheme.colors.surface,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = if (selected) EtaTheme.colors.accent else EtaTheme.colors.border,
                shape = shape,
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = EtaTheme.spacing.lg, vertical = EtaTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .border(
                    width = if (selected) 4.dp else 1.dp,
                    color = if (selected) EtaTheme.colors.accent else EtaTheme.colors.border,
                    shape = CircleShape,
                ),
        )
        Spacer(Modifier.width(EtaTheme.spacing.md))
        EtaText(
            text = label,
            style = EtaTheme.typography.body,
            color = if (selected) EtaTheme.colors.textPrimary else EtaTheme.colors.textSecondary,
        )
    }
}

/** Mo–So as seven equally wide pills, one of them picked. */
@Composable
fun EtaWeekdayPicker(
    selected: DayOfWeek,
    onSelect: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
    days: List<DayOfWeek>,
) = EtaWeekdayPicker(
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
fun EtaWeekdayPicker(
    selected: Set<DayOfWeek>,
    onToggle: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
    days: List<DayOfWeek>,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs),
    ) {
        days.forEach { day ->
            val isSelected = day in selected
            val interactionSource = remember(day) { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = if (isSelected) {
                            EtaTheme.colors.accent
                        } else {
                            EtaTheme.colors.surface
                        },
                        shape = EtaTheme.shapes.small,
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) EtaTheme.colors.accent else EtaTheme.colors.border,
                        shape = EtaTheme.shapes.small,
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { if (!isSelected || selected.size > 1) onToggle(day) },
                    )
                    .padding(vertical = EtaTheme.spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                EtaText(
                    text = day.formatVeryShort(),
                    style = EtaTheme.typography.label,
                    color = if (isSelected) {
                        EtaTheme.colors.onAccent
                    } else {
                        EtaTheme.colors.textSecondary
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
