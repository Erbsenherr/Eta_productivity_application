package com.example.eta.ui.calendar

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaScreen
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.theme.EtaTheme

/**
 * The evening's calendar step: what was put in the calendar for tomorrow since
 * the last time anyone looked.
 *
 * It sits between the concretizing step and the planner, for the reason the whole
 * feature exists: an appointment decided **before** the day is filled is part of
 * the picture, and one decided after it is a collision.
 *
 * **It gets out of the way when it has nothing to say.** A user who has not
 * connected an account, or whose calendar holds nothing new, walks through the
 * evening exactly as before — a step that stopped every night to report that
 * there was nothing to report would be a tax on the feature. The auto-skip fires
 * once, on the first settled sync; after the user has decided something, the
 * screen stays and waits for "Weiter".
 */
@Composable
fun CalendarStepScreen(
    viewModel: CalendarEventsViewModel,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val conflicts by viewModel.conflicts.collectAsStateWithLifecycle()
    var autoSkipped by remember { mutableStateOf(false) }

    LaunchedEffect(state.settled, conflicts) {
        if (autoSkipped || !state.settled || conflicts != null) return@LaunchedEffect
        autoSkipped = true
        onDone()
    }

    BackHandler { onDone() }

    EtaScreen(modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(EtaTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg),
            ) {
                Column {
                    EtaText(text = "Neue Termine", style = EtaTheme.typography.title)
                    EtaText(
                        text = "Für ${viewModel.to.formatLong()}",
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textSecondary,
                    )
                }

                CalendarEventsSection(viewModel)
                Spacer(Modifier.size(EtaTheme.spacing.xl))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(EtaTheme.spacing.lg),
            ) {
                Spacer(Modifier.weight(1f))
                EtaButton(text = "Weiter zur Tagesplanung", onClick = onDone)
            }
        }
    }
}
