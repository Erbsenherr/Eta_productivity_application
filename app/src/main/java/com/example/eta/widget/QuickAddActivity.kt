package com.example.eta.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.eta.EtaApplication
import com.example.eta.di.AppContainer
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.quickadd.QuickAddPanel
import com.example.eta.ui.quickadd.quickAddViewModelFactory
import com.example.eta.ui.theme.EtaTheme

/**
 * What the home-screen widget opens: the quick-add panel and nothing else.
 *
 * Translucent and dismissed by tapping beside it, because it is a note taken on
 * the way past rather than a visit to the app. It deliberately does **not** close
 * itself after an entry — the point of a quick-add is that several fit into one
 * sitting, and the feedback line under the field is what says each one landed.
 *
 * It shares [AppContainer] with the app, so a note taken here is in the
 * Sammelliste before the panel is even closed.
 */
class QuickAddActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as EtaApplication).container

        val design = container.designStore.design.value

        setContent {
            EtaTheme(design) {
                QuickAddSheet(
                    container = container,
                    onDismiss = { finish() },
                )
            }
        }
    }
}

@Composable
private fun QuickAddSheet(
    container: AppContainer,
    onDismiss: () -> Unit,
) {
    val scrimInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            // Tapping beside the panel leaves, the way a sheet does. The panel
            // itself sits on an opaque surface, so nothing falls through to here.
            .clickable(
                interactionSource = scrimInteraction,
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(EtaTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md),
        ) {
            QuickAddPanel(
                viewModel = viewModel(factory = quickAddViewModelFactory(container)),
            )
            EtaButton(
                text = "Fertig",
                style = EtaButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
                onClick = onDismiss,
            )
        }
    }
}
