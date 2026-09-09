package com.example.erik_iteration_2.ui.quickadd

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.erik_iteration_2.ui.components.ErikButton
import com.example.erik_iteration_2.ui.components.ErikSurface
import com.example.erik_iteration_2.ui.components.ErikText
import com.example.erik_iteration_2.ui.components.ErikTextField
import com.example.erik_iteration_2.ui.format.formatLong
import com.example.erik_iteration_2.ui.theme.ErikTheme
import kotlinx.coroutines.launch

private const val PAGE_TODO = 0
private const val PAGE_COUNT = 2

/** How far a sideways drag on the box's frame must travel to turn the page. */
private const val PAGE_SWIPE_THRESHOLD_PX = 60f

/**
 * The quick-add box, both halves of it.
 *
 * One surface, two pages: a note for the Sammelliste, and — one swipe to the
 * right — a note that is meant to repeat. Both ask for a name and nothing else;
 * the questions are the evening's, in the concretizing step.
 *
 * **The whole box swipes, not only the pager.** The heading and its dots sit
 * outside the pager so they do not slide about, which used to mean the top half
 * of the card swallowed the gesture. The drag detector therefore sits on the
 * surface itself: Compose hands a gesture to the innermost handler first, so the
 * pager keeps its own area and this one picks up everything around it.
 *
 * Mounted by the dashboard and, unchanged, by the home-screen widget's activity.
 */
@Composable
fun QuickAddPanel(
    viewModel: QuickAddViewModel,
    modifier: Modifier = Modifier,
) {
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = PAGE_TODO) { PAGE_COUNT }
    val scope = rememberCoroutineScope()

    fun goTo(page: Int) {
        scope.launch { pagerState.animateScrollToPage(page.coerceIn(0, PAGE_COUNT - 1)) }
    }

    ErikSurface(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                var travel = 0f
                detectHorizontalDragGestures(
                    onDragStart = { travel = 0f },
                    onDragEnd = {
                        when {
                            travel <= -PAGE_SWIPE_THRESHOLD_PX -> goTo(pagerState.currentPage + 1)
                            travel >= PAGE_SWIPE_THRESHOLD_PX -> goTo(pagerState.currentPage - 1)
                        }
                    },
                    onDragCancel = { travel = 0f },
                ) { _, amount -> travel += amount }
            },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.md)) {
            PanelHeading(
                page = pagerState.currentPage,
                onSelectPage = ::goTo,
            )

            HorizontalPager(state = pagerState) { page ->
                QuickAddForm(
                    placeholder = if (page == PAGE_TODO) {
                        "Was liegt an?"
                    } else {
                        "Was soll sich wiederholen?"
                    },
                    caption = if (page == PAGE_TODO) {
                        "Nur notieren — Kategorie, Priorität und Dauer kommen am Abend dazu. " +
                            "Nach rechts wischen für Wiederkehrendes."
                    } else {
                        "Nur notieren — Wochentage, Beginn und Dauer kommen am Abend dazu."
                    },
                    action = if (page == PAGE_TODO) {
                        "In die Sammelliste"
                    } else {
                        "Als Wiederholung notieren"
                    },
                    onAdd = if (page == PAGE_TODO) viewModel::quickAdd else viewModel::addRecurring,
                    onTyping = viewModel::dismissFeedback,
                    hasFeedback = feedback != null,
                )
            }

            FeedbackLine(feedback)
        }
    }
}

/**
 * The title and the two dots.
 *
 * The dots are tappable as well as swipeable — on a box this small the dots are
 * the clearest thing to aim at, and a tap is the cheaper gesture of the two.
 */
@Composable
private fun PanelHeading(page: Int, onSelectPage: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ErikText(
            text = if (page == PAGE_TODO) "Quick-Add" else "Quick-Add · wiederkehrend",
            style = ErikTheme.typography.heading,
            modifier = Modifier.weight(1f),
        )
        repeat(PAGE_COUNT) { dot ->
            val interactionSource = remember(dot) { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .padding(start = ErikTheme.spacing.xs)
                    .size(if (dot == page) 8.dp else 6.dp)
                    .background(
                        color = if (dot == page) {
                            ErikTheme.colors.accent
                        } else {
                            ErikTheme.colors.border
                        },
                        shape = CircleShape,
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onSelectPage(dot) },
                    ),
            )
        }
    }
}

/**
 * One page. The two differ only in their words: a name goes in, and that is the
 * entire form on both sides.
 */
@Composable
private fun QuickAddForm(
    placeholder: String,
    caption: String,
    action: String,
    onAdd: (String) -> Unit,
    onTyping: () -> Unit,
    hasFeedback: Boolean,
) {
    var text by remember(placeholder) { mutableStateOf("") }

    fun submit() {
        if (text.isBlank()) return
        onAdd(text)
        text = ""
    }

    Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.md)) {
        ErikText(
            text = caption,
            style = ErikTheme.typography.caption,
            color = ErikTheme.colors.textMuted,
        )
        ErikTextField(
            value = text,
            onValueChange = {
                text = it
                if (hasFeedback) onTyping()
            },
            placeholder = placeholder,
            onImeAction = ::submit,
        )
        ErikButton(text = action, onClick = ::submit)
    }
}

@Composable
private fun FeedbackLine(feedback: QuickAddFeedback?) {
    when (feedback) {
        is QuickAddFeedback.Added -> ErikText(
            text = "»${feedback.name}« liegt in der Sammelliste.",
            style = ErikTheme.typography.caption,
            color = ErikTheme.colors.success,
        )

        is QuickAddFeedback.AddedRecurring -> ErikText(
            text = "»${feedback.name}« liegt in der Sammelliste — " +
                "am Abend fehlen noch Wochentage, Beginn und Dauer.",
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
