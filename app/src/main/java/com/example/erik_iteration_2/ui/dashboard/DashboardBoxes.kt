package com.example.erik_iteration_2.ui.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.example.erik_iteration_2.domain.planning.endMinute
import com.example.erik_iteration_2.domain.planning.minuteToLocalTime
import com.example.erik_iteration_2.domain.planning.notesInOrder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.erik_iteration_2.data.local.BlockWithItem
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.planning.DailyPhaseStatus
import com.example.erik_iteration_2.domain.streak.Streak
import com.example.erik_iteration_2.ui.components.ErikButton
import com.example.erik_iteration_2.ui.components.ErikButtonStyle
import com.example.erik_iteration_2.ui.components.ErikCheckbox
import com.example.erik_iteration_2.ui.components.ErikStepperButton
import com.example.erik_iteration_2.ui.components.ErikSurface
import com.example.erik_iteration_2.ui.components.ErikText
import com.example.erik_iteration_2.ui.format.formatClock
import com.example.erik_iteration_2.ui.format.formatCountdown
import com.example.erik_iteration_2.ui.format.formatPoints
import com.example.erik_iteration_2.ui.format.formatShort
import com.example.erik_iteration_2.ui.theme.ErikTheme
import com.example.erik_iteration_2.ui.theme.colorOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** How much one tap on the duration stepper moves the needle. */
private val DURATION_STEP = 15.minutes

@Composable
fun BoxHeading(text: String, modifier: Modifier = Modifier) {
    ErikText(
        text = text,
        style = ErikTheme.typography.heading,
        modifier = modifier,
    )
}

/** Box 0 — only rendered when something is actually about to expire. */
@Composable
fun CriticalBox(
    critical: List<CriticalTodo>,
    modifier: Modifier = Modifier,
) {
    if (critical.isEmpty()) return

    ErikSurface(
        modifier = modifier.fillMaxWidth(),
        color = ErikTheme.colors.surface,
        borderColor = ErikTheme.colors.danger,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
            ErikText(
                text = "Läuft bald ab",
                style = ErikTheme.typography.heading,
                color = ErikTheme.colors.danger,
            )
            ErikText(
                text = "Wandert sonst für ein halbes Jahr auf die Sperrliste.",
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.textSecondary,
            )
            critical.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ErikText(
                        text = entry.item.name,
                        style = ErikTheme.typography.body,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ErikText(
                        text = when {
                            entry.daysLeft < 0 -> "überfällig"
                            entry.daysLeft == 0 -> "heute"
                            entry.daysLeft == 1 -> "morgen"
                            else -> "noch ${entry.daysLeft} Tage"
                        },
                        style = ErikTheme.typography.label,
                        color = ErikTheme.colors.danger,
                    )
                }
            }
        }
    }
}

/**
 * The "now" box: what is running, and what comes after it.
 *
 * Two pages rather than one line, swipeable, because the two answers are wanted at
 * different moments — during something, and between things. It answers "what now",
 * which is the first thing the screen is asked, so it sits above the account.
 */
@Composable
fun NowBox(
    current: BlockWithItem?,
    next: BlockWithItem?,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(initialPage = if (current == null) 1 else 0) { 2 }

    ErikSurface(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ErikText(
                    text = if (pagerState.currentPage == 0) "Gerade" else "Als Nächstes",
                    style = ErikTheme.typography.label,
                    color = ErikTheme.colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                repeat(2) { page ->
                    Box(
                        modifier = Modifier
                            .padding(start = ErikTheme.spacing.xs)
                            .size(if (page == pagerState.currentPage) 8.dp else 6.dp)
                            .background(
                                color = if (page == pagerState.currentPage) {
                                    ErikTheme.colors.accent
                                } else {
                                    ErikTheme.colors.border
                                },
                                shape = CircleShape,
                            ),
                    )
                }
            }

            HorizontalPager(state = pagerState) { page ->
                val entry = if (page == 0) current else next
                if (entry == null) {
                    ErikText(
                        text = if (page == 0) {
                            "Gerade steht nichts an."
                        } else {
                            "Für heute ist nichts mehr geplant."
                        },
                        style = ErikTheme.typography.body,
                        color = ErikTheme.colors.textMuted,
                    )
                } else {
                    NowEntry(entry)
                }
            }
        }
    }
}

@Composable
private fun NowEntry(entry: BlockWithItem) {
    Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(colorOf(entry.item.category), CircleShape),
            )
            Spacer(Modifier.width(ErikTheme.spacing.sm))
            ErikText(
                text = entry.item.name,
                style = ErikTheme.typography.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ErikText(
            text = "${entry.block.start.formatClock()} – " +
                minuteToLocalTime(entry.block.endMinute()).formatClock(),
            style = ErikTheme.typography.bodyStrong,
            color = ErikTheme.colors.textSecondary,
        )
        // Specific note first; that ordering is the point of having two.
        entry.notesInOrder().forEach { note ->
            ErikText(
                text = note,
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.textMuted,
            )
        }
    }
}

/**
 * What the daily planning phase still owes, and when it is next due.
 *
 * The alarm used to be the only thing keeping the day honest, which meant a
 * dismissed notification left no trace at all. This is the standing reminder the
 * screen itself carries.
 */
@Composable
fun PhaseBox(
    phase: DailyPhaseStatus,
    streak: Streak,
    onCloseDay: () -> Unit,
    onPlanTomorrow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ErikSurface(
        modifier = modifier.fillMaxWidth(),
        borderColor = if (phase.isComplete) ErikTheme.colors.success else ErikTheme.colors.warning,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ErikText(
                    text = phase.headline,
                    style = ErikTheme.typography.heading,
                    color = if (phase.isComplete) {
                        ErikTheme.colors.success
                    } else {
                        ErikTheme.colors.warning
                    },
                    modifier = Modifier.weight(1f),
                )
                StreakBadge(streak)
            }
            phase.dueAt?.let { due ->
                ErikText(
                    text = "Nächste Tagesplanung: ${due.time.formatClock()}" +
                        if (phase.isComplete) " — dann für morgen." else " Uhr.",
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.textMuted,
                )
            }

            if (!phase.isComplete) {
                Row(horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
                    if (!phase.todaySettled) {
                        ErikButton(text = "Tag abschließen", onClick = onCloseDay)
                    }
                    if (!phase.tomorrowConfirmed) {
                        ErikButton(
                            // Already planned ahead: the phase only has to wave it through.
                            text = if (phase.tomorrowReadyToConfirm) {
                                "Morgen bestätigen"
                            } else {
                                "Morgen planen"
                            },
                            style = if (phase.todaySettled) {
                                ErikButtonStyle.Primary
                            } else {
                                ErikButtonStyle.Secondary
                            },
                            onClick = onPlanTomorrow,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The run of settled days.
 *
 * Shown next to the phase rather than as a box of its own: the streak and the
 * thing that keeps it alive are one subject, and the number means most where the
 * button that extends it is.
 *
 * A run that is alive but not yet extended today reads greyer than one already
 * earned — the difference between "still going" and "done for today".
 */
@Composable
private fun StreakBadge(streak: Streak) {
    val colour = when {
        streak.isBroken -> ErikTheme.colors.textMuted
        streak.todayOpen -> ErikTheme.colors.textSecondary
        else -> ErikTheme.colors.success
    }

    Column(horizontalAlignment = Alignment.End) {
        ErikText(
            text = if (streak.isBroken) "—" else "🔥 ${streak.length}",
            style = ErikTheme.typography.title,
            color = colour,
        )
        ErikText(
            text = when {
                streak.isBroken -> "keine Serie"
                streak.length == 1 -> "1 Tag"
                else -> "${streak.length} Tage"
            },
            style = ErikTheme.typography.caption,
            color = ErikTheme.colors.textMuted,
        )
    }
}

/** Box 1 — the account, plus what today would add once harvested. */
@Composable
fun PointsBox(
    balance: Double,
    pendingHarvest: Double,
    modifier: Modifier = Modifier,
    crowned: Boolean = false,
) {
    ErikSurface(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                ErikText(
                    text = if (crowned) "Punktestand 👑" else "Punktestand",
                    style = ErikTheme.typography.label,
                    color = ErikTheme.colors.textSecondary,
                )
                Spacer(Modifier.size(ErikTheme.spacing.xs))
                ErikText(
                    text = formatPoints(balance),
                    style = ErikTheme.typography.display,
                    color = if (balance < 0) ErikTheme.colors.danger else ErikTheme.colors.textPrimary,
                )
            }
            if (pendingHarvest != 0.0) {
                Column(horizontalAlignment = Alignment.End) {
                    ErikText(
                        text = "heute erarbeitet",
                        style = ErikTheme.typography.caption,
                        color = ErikTheme.colors.textMuted,
                    )
                    ErikText(
                        text = "+${formatPoints(pendingHarvest)}",
                        style = ErikTheme.typography.title,
                        color = ErikTheme.colors.success,
                    )
                    ErikText(
                        text = "wird abends gutgeschrieben",
                        style = ErikTheme.typography.caption,
                        color = ErikTheme.colors.textMuted,
                    )
                }
            }
        }
    }
}

/** Box 2 — the day's blocks, checkable, with the duration correctable once done. */
@Composable
fun TasksBox(
    title: String,
    blocks: List<BlockWithItem>,
    modifier: Modifier = Modifier,
    onToggle: ((BlockWithItem) -> Unit)? = null,
    onDurationChange: ((BlockWithItem, Duration) -> Unit)? = null,
    emptyHint: String = "Nichts geplant.",
) {
    ErikSurface(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.md)) {
            BoxHeading(title)
            if (blocks.isEmpty()) {
                ErikText(
                    text = emptyHint,
                    style = ErikTheme.typography.body,
                    color = ErikTheme.colors.textMuted,
                )
            } else {
                blocks.forEach { entry ->
                    TaskRow(
                        entry = entry,
                        onToggle = onToggle,
                        onDurationChange = onDurationChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    entry: BlockWithItem,
    onToggle: ((BlockWithItem) -> Unit)?,
    onDurationChange: ((BlockWithItem, Duration) -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onToggle != null) {
                ErikCheckbox(
                    checked = entry.block.isCompleted,
                    onCheckedChange = { onToggle(entry) },
                )
                Spacer(Modifier.width(ErikTheme.spacing.md))
            }

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(colorOf(entry.item.category), CircleShape),
            )
            Spacer(Modifier.width(ErikTheme.spacing.sm))

            Column(modifier = Modifier.weight(1f)) {
                ErikText(
                    text = entry.item.name,
                    style = ErikTheme.typography.body,
                    color = if (entry.block.isCompleted) {
                        ErikTheme.colors.textMuted
                    } else {
                        ErikTheme.colors.textPrimary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ErikText(
                    text = "${entry.block.start.formatClock()} · ${entry.block.effectiveDuration.formatShort()}",
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.textMuted,
                )
            }
        }

        // The doc asks for the real duration to be recordable right here.
        if (entry.block.isCompleted && onDurationChange != null) {
            DurationStepper(
                duration = entry.block.effectiveDuration,
                onChange = { onDurationChange(entry, it) },
                modifier = Modifier.padding(start = 46.dp),
            )
        }
    }
}

@Composable
private fun DurationStepper(
    duration: Duration,
    onChange: (Duration) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm),
    ) {
        ErikText(
            text = "tatsächlich",
            style = ErikTheme.typography.caption,
            color = ErikTheme.colors.textMuted,
        )
        ErikStepperButton(
            label = "−",
            size = 26.dp,
            onClick = { onChange((duration - DURATION_STEP).coerceAtLeast(DURATION_STEP)) },
        )
        ErikText(
            text = duration.formatShort(),
            style = ErikTheme.typography.label,
        )
        ErikStepperButton(
            label = "+",
            size = 26.dp,
            onClick = { onChange(duration + DURATION_STEP) },
        )
    }
}

/** Box 3 — open deadlines counting down. */
@Composable
fun DeadlinesBox(
    deadlines: List<Item>,
    now: Instant,
    modifier: Modifier = Modifier,
) {
    ErikSurface(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.md)) {
            BoxHeading("Deadlines")
            if (deadlines.isEmpty()) {
                ErikText(
                    text = "Keine offenen Deadlines.",
                    style = ErikTheme.typography.body,
                    color = ErikTheme.colors.textMuted,
                )
            } else {
                deadlines.forEach { item ->
                    val target = item.deadlineAt ?: return@forEach
                    val overdue = target < now
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(colorOf(item.category), CircleShape),
                        )
                        Spacer(Modifier.width(ErikTheme.spacing.sm))
                        ErikText(
                            text = item.name,
                            style = ErikTheme.typography.body,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        ErikText(
                            text = formatCountdown(target, now),
                            style = ErikTheme.typography.label,
                            color = if (overdue) ErikTheme.colors.danger else ErikTheme.colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}
