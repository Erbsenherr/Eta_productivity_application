package com.example.eta.ui.dashboard

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.planning.BlockConflict
import com.example.eta.domain.planning.containerEndMinute
import com.example.eta.domain.planning.containerStartMinute
import com.example.eta.domain.planning.minuteToLocalTime
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaDialog
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.theme.EtaTheme
import kotlinx.datetime.LocalDate

/**
 * Blocks standing on top of each other, at the very top of the day.
 *
 * It should not normally have anything to say: a growth task that cannot reach
 * its target without a clash is warned about while it is being set up, and
 * placement gives way rather than overlapping. But a plan can be rearranged
 * after the fact — an appointment imported onto a commute, a block moved by
 * hand, a growth task that grew into a day that has since filled up — and a
 * collision that nothing mentions is one the user only finds out about by living
 * through it. So it is drawn first, in the warning colour, and it disappears by
 * itself the moment one of the two blocks moves.
 *
 * Every collision on the day, whatever caused it, because "two things at the
 * same time" is one problem to the person having it.
 *
 * Long press on a row: **Ignorieren** or **Lösen**. A long press rather than two
 * buttons per row, because the box is at the top of the screen and has to stay
 * small when it has three of them.
 */
@Composable
fun ConflictBox(
    conflicts: List<BlockConflict>,
    today: LocalDate,
    onIgnore: (BlockConflict) -> Unit,
    onResolve: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (conflicts.isEmpty()) return
    var chosen by remember { mutableStateOf<BlockConflict?>(null) }

    EtaSurface(
        modifier = modifier.fillMaxWidth(),
        color = EtaTheme.colors.surface,
        borderColor = EtaTheme.colors.warning,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaText(
                text = if (conflicts.size == 1) "Überschneidung" else "Überschneidungen",
                style = EtaTheme.typography.heading,
                color = EtaTheme.colors.warning,
            )
            EtaText(
                text = "Lange drücken: ignorieren oder lösen.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textSecondary,
            )
            conflicts.forEach { conflict ->
                ConflictRow(
                    conflict = conflict,
                    today = today,
                    onLongPress = { chosen = conflict },
                )
            }
        }
    }

    chosen?.let { conflict ->
        EtaDialog(title = "Überschneidung", onDismiss = { chosen = null }) {
            EtaText(
                text = "${conflict.earlier.item.name} und ${conflict.later.item.name} " +
                    "liegen übereinander.",
                style = EtaTheme.typography.body,
            )
            EtaText(
                text = "Ignorieren gilt nur für diesen Tag — morgen wird wieder gefragt. " +
                    "Lösen öffnet die Planung dieses Tages.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                EtaButton(
                    text = "Ignorieren",
                    style = EtaButtonStyle.Secondary,
                    onClick = {
                        onIgnore(conflict)
                        chosen = null
                    },
                )
                Spacer(Modifier.weight(1f))
                EtaButton(
                    text = "Lösen",
                    onClick = {
                        val date = conflict.date
                        chosen = null
                        onResolve(date)
                    },
                )
            }
        }
    }
}

@Composable
private fun ConflictRow(
    conflict: BlockConflict,
    today: LocalDate,
    onLongPress: () -> Unit,
) {
    // The rule from step 12: a `pointerInput` lambda keeps what it closed over,
    // so the callback is read through its own state rather than captured.
    val press = rememberUpdatedState(onLongPress)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(conflict.key) {
                detectTapGestures(onLongPress = { press.value() })
            },
    ) {
        EtaText(
            text = "${conflict.earlier.item.name} ↔ ${conflict.later.item.name}",
            style = EtaTheme.typography.body,
        )
        EtaText(
            text = buildString {
                if (conflict.date != today) append("morgen · ")
                append(conflict.earlier.span())
                append(" / ")
                append(conflict.later.span())
            },
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.warning,
        )
    }
}

/** The stretch a block really holds, journeys and break included. */
private fun BlockWithItem.span(): String {
    val from = minuteToLocalTime(block.containerStartMinute()).formatClock()
    val to = minuteToLocalTime(block.containerEndMinute()).formatClock()
    return "$from–$to"
}
