package com.example.erik_iteration_2.widget

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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.erik_iteration_2.ErikApplication
import com.example.erik_iteration_2.di.AppContainer
import com.example.erik_iteration_2.ui.components.ErikButton
import com.example.erik_iteration_2.ui.components.ErikButtonStyle
import com.example.erik_iteration_2.ui.quickadd.QuickAddPanel
import com.example.erik_iteration_2.ui.quickadd.QuickAddViewModel
import com.example.erik_iteration_2.ui.theme.ErikTheme

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

        val container = (application as ErikApplication).container

        setContent {
            ErikTheme {
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
                .padding(ErikTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.md),
        ) {
            QuickAddPanel(
                viewModel = viewModel(factory = quickAddViewModelFactory(container)),
            )
            ErikButton(
                text = "Fertig",
                style = ErikButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
                onClick = onDismiss,
            )
        }
    }
}

private fun quickAddViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            QuickAddViewModel(itemRepository = container.itemRepository)
        }
    }
