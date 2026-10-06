package com.example.eta.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.example.eta.domain.planning.BlockPhase
import com.example.eta.domain.planning.NowEntry
import com.example.eta.domain.planning.breakStartMinute
import com.example.eta.domain.planning.containerEndMinute
import com.example.eta.domain.planning.containerStartMinute
import com.example.eta.domain.planning.endMinute
import com.example.eta.domain.planning.returnStartMinute
import com.example.eta.domain.planning.minuteToLocalTime
import com.example.eta.domain.planning.notesInOrder
import com.example.eta.domain.planning.PomodoroPhaseKind
import com.example.eta.domain.planning.hasPomodoro
import com.example.eta.domain.planning.pomodoroPhaseAt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.Subtask
import com.example.eta.domain.planning.DailyPhaseStatus
import com.example.eta.domain.streak.Streak
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaCheckbox
import com.example.eta.ui.components.EtaStepperButton
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.QuantityText
import com.example.eta.ui.components.EtaTimePickerDialog
import com.example.eta.ui.components.ConfirmDialog
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.format.formatCountdown
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.format.formatPoints
import com.example.eta.ui.format.formatSignedPoints
import com.example.eta.ui.format.formatShort
import com.example.eta.ui.theme.EtaTheme
import com.example.eta.ui.theme.colorOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** How much one tap on the duration stepper moves the needle. */
private val DURATION_STEP = 15.minutes

@Composable
fun BoxHeading(text: String, modifier: Modifier = Modifier) {
    EtaText(
        text = text,
        style = EtaTheme.typography.heading,
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

    EtaSurface(
        modifier = modifier.fillMaxWidth(),
        color = EtaTheme.colors.surface,
        borderColor = EtaTheme.colors.danger,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaText(
                text = "Läuft bald ab",
                style = EtaTheme.typography.heading,
                color = EtaTheme.colors.danger,
            )
            EtaText(
                text = "Wandert sonst für ein halbes Jahr auf die Sperrliste.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textSecondary,
            )
            critical.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EtaText(
                        text = entry.item.name,
                        style = EtaTheme.typography.body,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    EtaText(
                        text = when {
                            entry.daysLeft < 0 -> "überfällig"
                            entry.daysLeft == 0 -> "heute"
                            entry.daysLeft == 1 -> "morgen"
                            else -> "noch ${entry.daysLeft} Tage"
                        },
                        style = EtaTheme.typography.label,
                        color = EtaTheme.colors.danger,
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
 *
 * A long press on either page is the pomodoro switch for that block. `running`
 * says which page it came from, since a rhythm set up during a task counts from
 * now and one set up ahead counts from the task's start. The swipe survives it:
 * a tap detector does not consume a drag, so the pager still turns.
 */
@Composable
fun NowBox(
    current: NowEntry?,
    next: NowEntry?,
    minuteOfDay: Int,
    modifier: Modifier = Modifier,
    onLongPress: (entry: BlockWithItem, running: Boolean) -> Unit = { _, _ -> },
    /** The steps inside each card, by item id, and this day's ticks by block id. */
    subtasks: Map<String, List<Subtask>> = emptyMap(),
    checked: Map<String, Set<String>> = emptyMap(),
    onCheckSubtask: (blockId: String, subtaskId: String, checked: Boolean) -> Unit = { _, _, _ -> },
    /**
     * "Erledigt" for what is running — one button for the whole group, whatever it
     * consists of. The screen asks again before it counts.
     */
    onConfirm: (BlockWithItem) -> Unit = {},
) {
    val pagerState = rememberPagerState(initialPage = if (current == null) 1 else 0) { 2 }

    EtaSurface(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EtaText(
                    text = if (pagerState.currentPage == 0) "Gerade" else "Als Nächstes",
                    style = EtaTheme.typography.label,
                    color = EtaTheme.colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                repeat(2) { page ->
                    Box(
                        modifier = Modifier
                            .padding(start = EtaTheme.spacing.xs)
                            .size(if (page == pagerState.currentPage) 8.dp else 6.dp)
                            .background(
                                color = if (page == pagerState.currentPage) {
                                    EtaTheme.colors.accent
                                } else {
                                    EtaTheme.colors.border
                                },
                                shape = CircleShape,
                            ),
                    )
                }
            }

            HorizontalPager(state = pagerState) { page ->
                val entry = if (page == 0) current else next
                if (entry == null) {
                    EtaText(
                        text = if (page == 0) {
                            "Gerade steht nichts an."
                        } else {
                            "Für heute ist nichts mehr geplant."
                        },
                        style = EtaTheme.typography.body,
                        color = EtaTheme.colors.textMuted,
                    )
                } else {
                    val running = page == 0
                    // Through State, not captured: `pointerInput` keeps the lambda
                    // it launched with, and the entry it closed over goes stale.
                    val latestEntry by rememberUpdatedState(entry.entry)
                    val latestOnLongPress by rememberUpdatedState(onLongPress)
                    // A tap unfolds a name too long for its two lines, and folds
                    // it again; the long press already meant the pomodoro.
                    var showFullName by remember(entry.entry.block.id) { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(entry.entry.block.id, running) {
                                detectTapGestures(
                                    onTap = { showFullName = !showFullName },
                                    onLongPress = { latestOnLongPress(latestEntry, running) },
                                )
                            },
                    ) {
                        NowLine(
                            now = entry,
                            minuteOfDay = minuteOfDay,
                            running = running,
                            showFullName = showFullName,
                            steps = subtasks[entry.entry.item.id].orEmpty(),
                            checkedIds = checked[entry.entry.block.id].orEmpty(),
                            onCheckSubtask = { subtaskId, on ->
                                onCheckSubtask(entry.entry.block.id, subtaskId, on)
                            },
                            onConfirm = { onConfirm(entry.entry) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
/**
 * One line of the "now" box, with the part of the block that is meant.
 *
 * The phase is what makes an appointment with a journey readable: at 17:00 the
 * next thing is not the dentist, it is the way there, and saying so is the whole
 * point of counting the container rather than the task.
 */
private fun phaseLabel(phase: BlockPhase): String? = when (phase) {
    BlockPhase.TASK -> null
    BlockPhase.TRAVEL -> "Anfahrt"
    BlockPhase.RETURN -> "Rückweg"
    BlockPhase.BREAK -> "Pause"
}

@Composable
private fun NowLine(
    now: NowEntry,
    minuteOfDay: Int,
    running: Boolean,
    showFullName: Boolean = false,
    steps: List<Subtask> = emptyList(),
    checkedIds: Set<String> = emptySet(),
    onCheckSubtask: (subtaskId: String, checked: Boolean) -> Unit = { _, _ -> },
    onConfirm: () -> Unit = {},
) {
    val entry = now.entry
    val block = entry.block

    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(colorOf(entry.item.category), CircleShape),
            )
            Spacer(Modifier.width(EtaTheme.spacing.sm))
            EtaText(
                text = entry.item.name + (phaseLabel(now.phase)?.let { " · $it" } ?: ""),
                style = EtaTheme.typography.title,
                maxLines = if (showFullName) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        EtaText(
            // The stretch that is actually meant: the journey's own quarter of an
            // hour, not the appointment it leads to.
            text = when (now.phase) {
                BlockPhase.TRAVEL ->
                    "${minuteToLocalTime(block.containerStartMinute()).formatClock()} – " +
                        block.start.formatClock()

                BlockPhase.TASK ->
                    "${block.start.formatClock()} – " +
                        minuteToLocalTime(block.endMinute()).formatClock()

                BlockPhase.RETURN ->
                    "${minuteToLocalTime(block.returnStartMinute()).formatClock()} – " +
                        minuteToLocalTime(block.breakStartMinute()).formatClock()

                BlockPhase.BREAK ->
                    "${minuteToLocalTime(block.breakStartMinute()).formatClock()} – " +
                        minuteToLocalTime(block.containerEndMinute()).formatClock()
            },
            style = EtaTheme.typography.bodyStrong,
            color = EtaTheme.colors.textSecondary,
        )
        PomodoroLine(block, minuteOfDay, running)

        // The steps, tickable while the group is being worked through. On the "Als
        // Nächstes" page they are read-only: nothing has begun yet, and a checkbox
        // there would invite ticking off what has not happened.
        steps.sortedBy { it.position }.forEach { step ->
            val done = step.id in checkedIds
            if (running) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EtaCheckbox(
                        checked = done,
                        onCheckedChange = { onCheckSubtask(step.id, it) },
                    )
                    Spacer(Modifier.width(EtaTheme.spacing.sm))
                    EtaText(
                        text = step.name,
                        style = EtaTheme.typography.body,
                        color = if (done) {
                            EtaTheme.colors.textMuted
                        } else {
                            EtaTheme.colors.textPrimary
                        },
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                EtaText(
                    text = "· ${step.name}",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
                )
            }
        }

        // The count first and louder than the notes: it is what the task is done with.
        QuantityText(entry.item)

        // Specific note first; that ordering is the point of having two.
        entry.notesInOrder().forEach { note ->
            EtaText(
                text = note,
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
        }

        // Only for what is running, and only for the task itself: confirming a
        // journey or a break would be confirming the frame rather than the thing.
        if (running && now.phase == BlockPhase.TASK && block.isOpen) {
            EtaButton(
                text = if (steps.isEmpty()) "Erledigt" else "Gruppe erledigt",
                style = EtaButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
                onClick = onConfirm,
            )
        }
    }
}

/**
 * The rhythm, if the block has one: which phase it is in and until when, or on
 * the "Als Nächstes" page simply that one is set up.
 */
@Composable
private fun PomodoroLine(block: PlannedBlock, minuteOfDay: Int, running: Boolean) {
    if (!block.hasPomodoro) return
    val rhythm = "${block.pomodoroWork?.formatShort()} / ${block.pomodoroPause?.formatShort()}"
    val phase = if (running) block.pomodoroPhaseAt(minuteOfDay) else null

    EtaText(
        text = when (phase?.kind) {
            PomodoroPhaseKind.WORK ->
                "Pomodoro · Arbeit bis ${minuteToLocalTime(phase.untilMinute).formatClock()}"

            PomodoroPhaseKind.PAUSE ->
                "Pomodoro · Pause bis ${minuteToLocalTime(phase.untilMinute).formatClock()}"

            null -> "Pomodoro eingerichtet · $rhythm"
        },
        style = EtaTheme.typography.label,
        color = if (phase?.kind == PomodoroPhaseKind.PAUSE) {
            EtaTheme.colors.success
        } else {
            EtaTheme.colors.accent
        },
    )
}

/**
 * What the daily planning phase still owes, and when it is next due.
 *
 * The alarm used to be the only thing keeping the day honest, which meant a
 * dismissed notification left no trace at all. This is the standing reminder the
 * screen itself carries.
 *
 * It only *insists* once the phase is [owed] — from the planning time on. Before
 * that the buttons are all still there, since closing the day early is allowed,
 * but the box is in the plain colours of every other box: a warning that is lit
 * all day long is one the eye learns to skip by the evening it matters.
 */
@Composable
fun PhaseBox(
    phase: DailyPhaseStatus,
    streak: Streak,
    owed: Boolean,
    onCloseDay: () -> Unit,
    onPlanTomorrow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val insistent = owed && !phase.isComplete

    EtaSurface(
        modifier = modifier.fillMaxWidth(),
        borderColor = when {
            phase.isComplete -> EtaTheme.colors.success
            insistent -> EtaTheme.colors.warning
            else -> EtaTheme.colors.border
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EtaText(
                    text = phase.headline,
                    style = EtaTheme.typography.heading,
                    color = when {
                        phase.isComplete -> EtaTheme.colors.success
                        insistent -> EtaTheme.colors.warning
                        else -> EtaTheme.colors.textSecondary
                    },
                    modifier = Modifier.weight(1f),
                )
                StreakBadge(streak)
            }
            phase.dueAt?.let { due ->
                EtaText(
                    text = "Nächste Tagesplanung: ${due.time.formatClock()}" + when {
                        phase.isComplete -> " — dann für morgen."
                        !owed -> " Uhr. Früher abschließen geht auch."
                        else -> " Uhr."
                    },
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
            }

            if (!phase.isComplete) {
                Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                    if (!phase.todaySettled) {
                        EtaButton(
                            text = "Tag abschließen",
                            style = if (insistent) EtaButtonStyle.Primary else EtaButtonStyle.Secondary,
                            onClick = onCloseDay,
                        )
                    }
                    if (!phase.tomorrowConfirmed) {
                        EtaButton(
                            // Already planned ahead: the phase only has to wave it through.
                            text = if (phase.tomorrowReadyToConfirm) {
                                "Morgen bestätigen"
                            } else {
                                "Morgen planen"
                            },
                            style = if (insistent && phase.todaySettled) {
                                EtaButtonStyle.Primary
                            } else {
                                EtaButtonStyle.Secondary
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
        streak.isBroken -> EtaTheme.colors.textMuted
        streak.todayOpen -> EtaTheme.colors.textSecondary
        else -> EtaTheme.colors.success
    }

    Column(horizontalAlignment = Alignment.End) {
        EtaText(
            text = if (streak.isBroken) "—" else "🔥 ${streak.length}",
            style = EtaTheme.typography.title,
            color = colour,
        )
        EtaText(
            text = when {
                streak.isBroken -> "keine Serie"
                streak.length == 1 -> "1 Tag"
                else -> "${streak.length} Tage"
            },
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
        )
    }
}

/**
 * Box 1 — the account, plus what today would add once harvested.
 *
 * Long-pressing it books points by hand. The gesture sits here rather than on a
 * settings row because this is where the number the correction is about is being
 * looked at.
 *
 * Two figures for today: what has been ticked off, which is what the evening
 * will credit, and [plannedYield], what the whole plan is worth once it is done.
 * The second is the forecast — it is the one that moves the moment a Custom Earn
 * or Custom Spend is placed or its rate is changed, rather than only once it has
 * been checked.
 */
@Composable
fun PointsBox(
    balance: Double,
    pendingHarvest: Double,
    plannedYield: Double,
    modifier: Modifier = Modifier,
    crowned: Boolean = false,
    onLongPress: () -> Unit = {},
) {
    EtaSurface(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    EtaText(
                        text = if (crowned) "Punktestand 👑" else "Punktestand",
                        style = EtaTheme.typography.label,
                        color = EtaTheme.colors.textSecondary,
                    )
                    Spacer(Modifier.size(EtaTheme.spacing.xs))
                    EtaText(
                        text = formatPoints(balance),
                        style = EtaTheme.typography.display,
                        color = if (balance < 0) {
                            EtaTheme.colors.danger
                        } else {
                            EtaTheme.colors.textPrimary
                        },
                    )
                }
                if (pendingHarvest != 0.0 || plannedYield != 0.0) {
                    Column(horizontalAlignment = Alignment.End) {
                        EtaText(
                            text = "heute erarbeitet",
                            style = EtaTheme.typography.caption,
                            color = EtaTheme.colors.textMuted,
                        )
                        // Signed and coloured by sign: a ticked-off Custom Spend
                        // makes this negative, and it used to read "+-5" in green.
                        EtaText(
                            text = formatSignedPoints(pendingHarvest),
                            style = EtaTheme.typography.title,
                            color = signColour(pendingHarvest),
                        )
                        EtaText(
                            text = "geplant: ${formatSignedPoints(plannedYield)}",
                            style = EtaTheme.typography.caption,
                            color = EtaTheme.colors.textSecondary,
                        )
                        EtaText(
                            text = "wird abends gutgeschrieben",
                            style = EtaTheme.typography.caption,
                            color = EtaTheme.colors.textMuted,
                        )
                    }
                }
            }
            EtaText(
                text = "Lange drücken, um von Hand zu buchen.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
        }
    }
}

@Composable
private fun signColour(value: Double) = when {
    value > 0 -> EtaTheme.colors.success
    value < 0 -> EtaTheme.colors.danger
    else -> EtaTheme.colors.textMuted
}

/**
 * Box 2 — the day's blocks, checkable, and correctable where the day has already
 * departed from the plan.
 *
 * Both halves of "07:00 · 1 h" are editable, and neither waits for the block to
 * be ticked off: something starting later than planned is the correction a
 * running day needs most often, and the duration is what the evening bills.
 */
@Composable
fun TasksBox(
    title: String,
    blocks: List<BlockWithItem>,
    modifier: Modifier = Modifier,
    onToggle: ((BlockWithItem) -> Unit)? = null,
    onDurationChange: ((BlockWithItem, Duration) -> Unit)? = null,
    onStartChange: ((BlockWithItem, LocalTime) -> Unit)? = null,
    onUncancel: ((BlockWithItem) -> Unit)? = null,
    emptyHint: String = "Nichts geplant.",
) {
    EtaSurface(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            BoxHeading(title)
            if (blocks.isEmpty()) {
                EtaText(
                    text = emptyHint,
                    style = EtaTheme.typography.body,
                    color = EtaTheme.colors.textMuted,
                )
            } else {
                blocks.forEach { entry ->
                    TaskRow(
                        entry = entry,
                        onToggle = onToggle,
                        onDurationChange = onDurationChange,
                        onStartChange = onStartChange,
                        onUncancel = onUncancel,
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
    onStartChange: ((BlockWithItem, LocalTime) -> Unit)? = null,
    onUncancel: ((BlockWithItem) -> Unit)? = null,
) {
    var pickingStart by remember(entry.block.id) { mutableStateOf(false) }
    var offeringUncancel by remember(entry.block.id) { mutableStateOf(false) }
    // A tap on the row unfolds a name cut off after its one line, and folds it again.
    var showFullName by remember(entry.block.id) { mutableStateOf(false) }

    // A cancellation leaves the day planner entirely, so this list is where it is
    // still visible — and therefore the only place it can be taken back.
    val cancelled = entry.block.isDiscarded
    val canUncancel = cancelled && onUncancel != null

    Column(
        // Keyed by what the lambdas depend on, so nothing in them can go stale.
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(entry.block.id, canUncancel) {
                detectTapGestures(
                    onTap = { showFullName = !showFullName },
                    onLongPress = if (canUncancel) {
                        { offeringUncancel = true }
                    } else {
                        null
                    },
                )
            },
        verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onToggle != null) {
                EtaCheckbox(
                    checked = entry.block.isCompleted,
                    enabled = !cancelled,
                    onCheckedChange = { onToggle(entry) },
                )
                Spacer(Modifier.width(EtaTheme.spacing.md))
            }

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(colorOf(entry.item.category), CircleShape),
            )
            Spacer(Modifier.width(EtaTheme.spacing.sm))

            Column(modifier = Modifier.weight(1f)) {
                EtaText(
                    text = entry.item.name,
                    style = EtaTheme.typography.body,
                    color = if (entry.block.isCompleted || cancelled) {
                        EtaTheme.colors.textMuted
                    } else {
                        EtaTheme.colors.textPrimary
                    },
                    textDecoration = if (cancelled) TextDecoration.LineThrough else null,
                    maxLines = if (showFullName) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!cancelled) QuantityText(entry.item)
                if (cancelled) {
                    EtaText(
                        text = "${entry.block.start.formatClock()} · " +
                            "${entry.block.effectiveDuration.formatShort()} · abgesagt",
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.warning,
                    )
                } else {
                    // The clock half is the control: a full picker field in every
                    // row would swamp the list it belongs to.
                    StartAndDuration(
                        start = entry.block.start,
                        duration = entry.block.effectiveDuration,
                        editable = onStartChange != null,
                        onEditStart = { pickingStart = true },
                    )
                }
            }
        }

        if (cancelled && onUncancel != null) {
            EtaText(
                text = "Lange drücken, um sie doch einzuplanen.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
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

    if (offeringUncancel && onUncancel != null) {
        ConfirmDialog(
            title = "Doch einplanen",
            message = "»${entry.item.name}« kehrt auf seinen alten Platz um " +
                "${entry.block.start.formatClock()} zurück. Ist die Zeit inzwischen " +
                "anderweitig verplant, geht das nicht mehr.",
            confirm = "Einplanen",
            onDismiss = { offeringUncancel = false },
            onConfirm = {
                onUncancel(entry)
                offeringUncancel = false
            },
        )
    }

    if (pickingStart && onStartChange != null) {
        EtaTimePickerDialog(
            initial = entry.block.start,
            onDismiss = { pickingStart = false },
            onConfirm = {
                onStartChange(entry, it)
                pickingStart = false
            },
        )
    }
}

/** "07:00 · 1 h", with the time tappable where the caller can act on it. */
@Composable
private fun StartAndDuration(
    start: LocalTime,
    duration: Duration,
    editable: Boolean,
    onEditStart: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        EtaText(
            text = start.formatClock(),
            style = EtaTheme.typography.caption,
            color = if (editable) EtaTheme.colors.accent else EtaTheme.colors.textMuted,
            modifier = if (editable) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onEditStart,
                )
            } else {
                Modifier
            },
        )
        EtaText(
            text = " · ${duration.formatShort()}",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
        )
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
        horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm),
    ) {
        EtaText(
            text = "tatsächlich",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
        )
        EtaStepperButton(
            label = "−",
            size = 26.dp,
            onClick = { onChange((duration - DURATION_STEP).coerceAtLeast(DURATION_STEP)) },
        )
        EtaText(
            text = duration.formatShort(),
            style = EtaTheme.typography.label,
        )
        EtaStepperButton(
            label = "+",
            size = 26.dp,
            onClick = { onChange(duration + DURATION_STEP) },
        )
    }
}

/**
 * Box 3 — open deadlines counting down.
 *
 * Drawn only while there is one, and then between the account and the Quick-Add
 * box rather than at the foot of the screen: a deadline is the one thing here
 * with a clock of its own, and it is worth seeing before the day is planned
 * around it. The caller decides whether to mount it at all, so there is no empty
 * state to draw.
 */
@Composable
fun DeadlinesBox(
    deadlines: List<Item>,
    now: Instant,
    modifier: Modifier = Modifier,
) {
    if (deadlines.isEmpty()) return

    EtaSurface(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            BoxHeading("Deadlines")
            deadlines.forEach { item ->
                val target = item.deadlineAt ?: return@forEach
                val overdue = target < now
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(colorOf(item.category), CircleShape),
                    )
                    Spacer(Modifier.width(EtaTheme.spacing.sm))
                    Column(Modifier.weight(1f)) {
                        EtaText(
                            text = item.name,
                            style = EtaTheme.typography.body,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        EtaText(
                            text = target.toLocalDateTime(TimeZone.currentSystemDefault())
                                .let { "${it.date.formatLong()} · ${it.time.formatClock()}" },
                            style = EtaTheme.typography.caption,
                            color = EtaTheme.colors.textMuted,
                        )
                    }
                    Spacer(Modifier.width(EtaTheme.spacing.sm))
                    EtaText(
                        text = formatCountdown(target, now),
                        style = EtaTheme.typography.label,
                        color = if (overdue) EtaTheme.colors.danger else EtaTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}
