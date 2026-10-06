package com.example.eta.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import com.example.eta.EtaApplication
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaScreen
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.format.formatClock
import com.example.eta.ui.theme.EtaTheme
import com.example.eta.ui.theme.applyDesignToWindow
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

        val container = (application as EtaApplication).container
        val design = container.designStore.design.value
        applyDesignToWindow(design)
        WakeAlarmRinger.start(this)

        setContent {
            EtaTheme(design) {
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

    EtaScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(EtaTheme.spacing.xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EtaText(
                text = now.toLocalDateTime(TimeZone.currentSystemDefault()).time.formatClock(),
                style = EtaTheme.typography.display,
            )
            EtaText(
                text = "Aufstehen",
                style = EtaTheme.typography.title,
                color = EtaTheme.colors.accent,
                textAlign = TextAlign.Center,
            )
            EtaText(
                text = "Der Tag fängt an.",
                style = EtaTheme.typography.body,
                color = EtaTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = EtaTheme.spacing.sm),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = EtaTheme.spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md),
            ) {
                EtaButton(
                    text = "Aus",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onStop,
                )
                EtaButton(
                    text = "Schlummern",
                    style = EtaButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onSnooze,
                )
            }
        }
    }
}
