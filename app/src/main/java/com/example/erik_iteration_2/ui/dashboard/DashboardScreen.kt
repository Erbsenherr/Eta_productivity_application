package com.example.erik_iteration_2.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.erik_iteration_2.domain.planning.minuteOfDay
import com.example.erik_iteration_2.domain.planning.nowAndNext
import com.example.erik_iteration_2.ui.components.ErikButton
import com.example.erik_iteration_2.ui.components.ErikButtonStyle
import com.example.erik_iteration_2.ui.components.ErikScreen
import com.example.erik_iteration_2.ui.components.ErikSurface
import com.example.erik_iteration_2.ui.components.ErikText
import com.example.erik_iteration_2.ui.format.formatClock
import com.example.erik_iteration_2.ui.format.formatLong
import com.example.erik_iteration_2.ui.quickadd.QuickAddPanel
import com.example.erik_iteration_2.ui.quickadd.QuickAddViewModel
import com.example.erik_iteration_2.ui.theme.ErikTheme
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val PAGE_TODAY = 0
private const val PAGE_TOMORROW = 1
private const val PAGE_COUNT = 2

/**
 * The app's home screen. Swiping right reveals tomorrow, as the concept asks —
 * a pager rather than navigation, so the two days feel like one surface.
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    quickAddViewModel: QuickAddViewModel,
    modifier: Modifier = Modifier,
    onPlanTomorrow: () -> Unit = {},
    onReplanToday: () -> Unit = {},
    onPlanWeek: () -> Unit = {},
    onCloseDay: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editFeedback by viewModel.blockEditFeedback.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = PAGE_TODAY) { PAGE_COUNT }
    val now = rememberTickingNow()

    ErikScreen(modifier = modifier, bottomInset = false) {
        Column(Modifier.fillMaxSize()) {
            PageIndicator(
                currentPage = pagerState.currentPage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = ErikTheme.spacing.lg,
                        end = ErikTheme.spacing.lg,
                        top = ErikTheme.spacing.lg,
                    ),
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    PAGE_TODAY -> TodayPage(
                        state = state,
                        editFeedback = editFeedback,
                        now = now,
                        viewModel = viewModel,
                        quickAddViewModel = quickAddViewModel,
                        onCloseDay = onCloseDay,
                        onPlanTomorrow = onPlanTomorrow,
                        onReplanToday = onReplanToday,
                    )

                    else -> TomorrowPage(state, onPlanTomorrow, onPlanWeek)
                }
            }
        }
    }
}

@Composable
private fun TodayPage(
    state: DashboardUiState,
    editFeedback: BlockEditFeedback?,
    now: Instant,
    viewModel: DashboardViewModel,
    quickAddViewModel: QuickAddViewModel,
    onCloseDay: () -> Unit,
    onPlanTomorrow: () -> Unit,
    onReplanToday: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ErikTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg),
    ) {
        PageHeader(title = "Heute", subtitle = state.today.formatLong())

        CriticalBox(critical = state.critical)

        // Recomputed as the clock ticks, so "gerade" stays true without a refresh.
        val nowAndNext = remember(now, state.todayBlocks) {
            nowAndNext(state.todayBlocks, now.toLocalDateTime(TimeZone.currentSystemDefault()).time.minuteOfDay())
        }
        NowBox(current = nowAndNext.current, next = nowAndNext.next)

        // Long press on the account: booking a correction by hand.
        var bookingPoints by remember { mutableStateOf(false) }

        PointsBox(
            balance = state.balance,
            pendingHarvest = state.pendingHarvest,
            crowned = state.crowned,
            onLongPress = { bookingPoints = true },
        )

        if (bookingPoints) {
            ManualPointsDialog(
                balance = state.balance,
                onDismiss = { bookingPoints = false },
                onBook = { amount, note ->
                    viewModel.adjustPoints(amount, note)
                    bookingPoints = false
                },
            )
        }

        state.phase?.let { phase ->
            PhaseBox(
                phase = phase,
                streak = state.streak,
                onCloseDay = onCloseDay,
                onPlanTomorrow = onPlanTomorrow,
            )
        }

        QuickAddPanel(viewModel = quickAddViewModel)

        TasksBox(
            title = "Heute anstehend",
            blocks = state.todayBlocks,
            onToggle = viewModel::toggleCompleted,
            onDurationChange = { entry, duration ->
                viewModel.setActualDuration(entry.block, duration)
            },
            onStartChange = viewModel::setStart,
        )

        // Small corrections happen in the list above. This is the way to the
        // planner itself, for the day already under way — cancelling something,
        // moving it by more than a nudge, or adding what was not foreseen.
        ErikButton(
            text = "Heute umplanen",
            style = ErikButtonStyle.Secondary,
            onClick = onReplanToday,
        )

        BlockEditFeedbackLine(editFeedback, viewModel::dismissBlockEditFeedback)

        DeadlinesBox(deadlines = state.deadlines, now = now)

        Spacer(Modifier.size(ErikTheme.spacing.xl))
    }
}

@Composable
private fun TomorrowPage(
    state: DashboardUiState,
    onPlanTomorrow: () -> Unit,
    onPlanWeek: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ErikTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg),
    ) {
        PageHeader(title = "Morgen", subtitle = state.tomorrow.formatLong())

        TasksBox(
            title = "Morgen anstehend",
            blocks = state.tomorrowBlocks,
            emptyHint = "Für morgen ist noch nichts geplant.",
        )

        // Until the planning alarms exist, these are the ways into the two phases.
        Row(horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
            ErikButton(text = "Morgen planen", onClick = onPlanTomorrow)
            ErikButton(
                text = "Woche planen",
                style = ErikButtonStyle.Secondary,
                onClick = onPlanWeek,
            )
        }

        Spacer(Modifier.size(ErikTheme.spacing.xl))
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String) {
    Column {
        ErikText(text = title, style = ErikTheme.typography.display)
        ErikText(
            text = subtitle,
            style = ErikTheme.typography.body,
            color = ErikTheme.colors.textSecondary,
        )
    }
}

/** What a start-time correction did, when it could not do exactly as asked. */
@Composable
private fun BlockEditFeedbackLine(
    feedback: BlockEditFeedback?,
    onDismiss: () -> Unit,
) {
    if (feedback == null) return

    ErikSurface(
        modifier = Modifier.fillMaxWidth(),
        borderColor = ErikTheme.colors.warning,
        contentPadding = ErikTheme.spacing.md,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ErikText(
                text = when (feedback) {
                    is BlockEditFeedback.Moved ->
                        "»${feedback.name}« lag dort nicht frei — jetzt um ${feedback.to.formatClock()}."

                    is BlockEditFeedback.NoRoom ->
                        "»${feedback.name}« passt heute nirgends mehr hin."
                },
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            ErikButton(text = "OK", style = ErikButtonStyle.Secondary, onClick = onDismiss)
        }
    }
}

@Composable
private fun PageIndicator(currentPage: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(PAGE_COUNT) { page ->
            Box(
                modifier = Modifier
                    .size(if (page == currentPage) 8.dp else 6.dp)
                    .background(
                        color = if (page == currentPage) {
                            ErikTheme.colors.accent
                        } else {
                            ErikTheme.colors.border
                        },
                        shape = CircleShape,
                    ),
            )
        }
        Spacer(Modifier.width(ErikTheme.spacing.sm))
        ErikText(
            text = if (currentPage == PAGE_TODAY) "wischen für morgen" else "wischen für heute",
            style = ErikTheme.typography.caption,
            color = ErikTheme.colors.textMuted,
        )
    }
}

/**
 * A clock that advances once a second, so deadline countdowns tick.
 * Only recomposes what reads it.
 */
@Composable
private fun rememberTickingNow(): Instant {
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = Clock.System.now()
        }
    }
    return now
}
