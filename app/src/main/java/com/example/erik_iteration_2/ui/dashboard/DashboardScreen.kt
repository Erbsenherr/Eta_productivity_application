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
import com.example.erik_iteration_2.ui.components.ErikTextField
import com.example.erik_iteration_2.ui.format.formatLong
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
    modifier: Modifier = Modifier,
    onPlanTomorrow: () -> Unit = {},
    onPlanWeek: () -> Unit = {},
    onCloseDay: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val feedback by viewModel.quickAddFeedback.collectAsStateWithLifecycle()
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
                        feedback = feedback,
                        now = now,
                        viewModel = viewModel,
                        onCloseDay = onCloseDay,
                        onPlanTomorrow = onPlanTomorrow,
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
    feedback: QuickAddFeedback?,
    now: Instant,
    viewModel: DashboardViewModel,
    onCloseDay: () -> Unit,
    onPlanTomorrow: () -> Unit,
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

        PointsBox(
            balance = state.balance,
            pendingHarvest = state.pendingHarvest,
            crowned = state.crowned,
        )

        state.phase?.let { phase ->
            PhaseBox(
                phase = phase,
                streak = state.streak,
                onCloseDay = onCloseDay,
                onPlanTomorrow = onPlanTomorrow,
            )
        }

        QuickAddBox(
            feedback = feedback,
            onAdd = viewModel::quickAdd,
            onDismissFeedback = viewModel::dismissQuickAddFeedback,
        )

        TasksBox(
            title = "Heute anstehend",
            blocks = state.todayBlocks,
            onToggle = viewModel::toggleCompleted,
            onDurationChange = { entry, duration ->
                viewModel.setActualDuration(entry.block, duration)
            },
        )

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

@Composable
private fun QuickAddBox(
    feedback: QuickAddFeedback?,
    onAdd: (String) -> Unit,
    onDismissFeedback: () -> Unit,
) {
    var text by remember { mutableStateOf("") }

    fun submit() {
        if (text.isBlank()) return
        onAdd(text)
        text = ""
    }

    ErikSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.md)) {
            BoxHeading("Quick-Add")
            ErikText(
                text = "Nur notieren — Attribute kommen in der Tagesplanung dazu.",
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.textMuted,
            )
            ErikTextField(
                value = text,
                onValueChange = {
                    text = it
                    if (feedback != null) onDismissFeedback()
                },
                placeholder = "Was liegt an?",
                onImeAction = ::submit,
            )
            ErikButton(text = "In die Sammelliste", onClick = ::submit)

            when (feedback) {
                is QuickAddFeedback.Added -> ErikText(
                    text = "»${feedback.name}« liegt in der Sammelliste.",
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.success,
                )

                is QuickAddFeedback.Blocked -> ErikText(
                    text = "»${feedback.name}« steht auf der Sperrliste — " +
                        "wieder möglich ab ${feedback.until.formatLong()}.",
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.danger,
                )

                null -> Unit
            }
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
