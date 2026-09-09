package com.example.erik_iteration_2.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.erik_iteration_2.ErikApplication
import com.example.erik_iteration_2.ui.components.ErikButton
import com.example.erik_iteration_2.ui.components.ErikButtonStyle
import com.example.erik_iteration_2.ui.components.ErikScreen
import com.example.erik_iteration_2.ui.components.ErikText
import com.example.erik_iteration_2.ui.format.formatClock
import com.example.erik_iteration_2.ui.theme.ErikTheme
import kotlin.time.Clock
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The screen the wake alarm comes up on.
 *
 * Over the lock screen and with the display turned on, because an alarm that
 * needs unlocking first is one you answer by fumbling. Both flags have API-level
 * histories, hence the split: the window flags are the only route before
 * Android 8.1.
 *
 * The sound is **not** owned here — see [WakeAlarmRinger]. This screen is one of
 * three ways to reach the same ringing, the others being the notification's two
 * buttons, and whichever the user takes has to silence it.
 */
class WakeAlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge()

        val container = (application as ErikApplication).container
        WakeAlarmRinger.start(this)

        setContent {
            ErikTheme {
                WakeScreen(
                    onSnooze = {
                        container.wakeAlarmCoordinator.snooze()
                        finish()
                    },
                    onStop = {
                        container.wakeAlarmCoordinator.stopAndRearm()
                        finish()
                    },
                )
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
private fun WakeScreen(onSnooze: () -> Unit, onStop: () -> Unit) {
    // Back does not dismiss an alarm. One of the two buttons has to be pressed,
    // or the ringing would stop with nothing rearmed and nothing said.
    BackHandler(enabled = true) {}

    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(LocalLifecycleOwner.current) {
        while (true) {
            delay(1_000)
            now = Clock.System.now()
        }
    }

    ErikScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ErikTheme.spacing.xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ErikText(
                text = now.toLocalDateTime(TimeZone.currentSystemDefault()).time.formatClock(),
                style = ErikTheme.typography.display,
            )
            ErikText(
                text = "Aufstehen",
                style = ErikTheme.typography.title,
                color = ErikTheme.colors.accent,
                textAlign = TextAlign.Center,
            )
            ErikText(
                text = "Der Tag fängt an.",
                style = ErikTheme.typography.body,
                color = ErikTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = ErikTheme.spacing.sm),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = ErikTheme.spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.md),
            ) {
                ErikButton(
                    text = "Aus",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onStop,
                )
                ErikButton(
                    text = "Schlummern",
                    style = ErikButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onSnooze,
                )
            }
        }
    }
}
