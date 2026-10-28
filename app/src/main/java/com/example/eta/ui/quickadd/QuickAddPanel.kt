package com.example.eta.ui.quickadd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.EtaTextField
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.theme.EtaTheme

/**
 * The quick-add box: a name for the Sammelliste, and nothing else.
 *
 * One page since step 53. The second, swiped to, made a recurring note; now
 * every note is a ToDo, and the form it is finished in — the evening's card, or
 * "Bearbeiten" on the Listen tab — has the switch that makes it a standing task.
 *
 * Mounted by the dashboard and, unchanged, by the home-screen widget's activity.
 */
@Composable
fun QuickAddPanel(
    viewModel: QuickAddViewModel,
    modifier: Modifier = Modifier,
) {
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }

    fun submit() {
        if (text.isBlank()) return
        viewModel.quickAdd(text)
        text = ""
    }

    EtaSurface(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Quick-Add", style = EtaTheme.typography.heading)
            EtaText(
                text = "Nur notieren — was es braucht, kommt am Abend dazu. Ob es sich " +
                    "wiederholen soll, stellst du dann auf der Karte ein.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            EtaTextField(
                value = text,
                onValueChange = {
                    text = it
                    if (feedback != null) viewModel.dismissFeedback()
                },
                placeholder = "Was liegt an?",
                onImeAction = ::submit,
            )
            EtaButton(text = "In die Sammelliste", onClick = ::submit)

            FeedbackLine(feedback)
        }
    }
}

@Composable
private fun FeedbackLine(feedback: QuickAddFeedback?) {
    when (feedback) {
        is QuickAddFeedback.Added -> EtaText(
            text = "»${feedback.name}« liegt in der Sammelliste.",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.success,
        )

        is QuickAddFeedback.Blocked -> EtaText(
            text = "»${feedback.name}« steht auf der Sperrliste — " +
                "wieder möglich ab ${feedback.until.formatLong()}.",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.danger,
        )

        null -> Unit
    }
}
