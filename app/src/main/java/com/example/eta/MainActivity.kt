package com.example.eta

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eta.alarm.PlanningAlarmContract
import com.example.eta.domain.planning.PlanningPhase
import com.example.eta.ui.root.EtaApp
import com.example.eta.ui.root.LAUNCH_SCREEN_MILLIS
import com.example.eta.ui.root.LaunchScreen
import com.example.eta.ui.theme.EtaTheme
import com.example.eta.ui.theme.applyDesignToWindow
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    /**
     * Set when the user tapped "NOTFALL" on the alarm. The activity is
     * `singleTask`, so a second tap arrives through [onNewIntent] rather than a
     * fresh instance — hence the state rather than a one-shot read of the intent.
     */
    private var deferPhase by mutableStateOf<PlanningPhase?>(null)

    /** Set when the user answered the alarm and wants to start the phase. */
    private var openPhase by mutableStateOf<PlanningPhase?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        readDeferPhase(intent)
        val container = (application as EtaApplication).container
        applyDesignToWindow(container.designStore.choice.value)

        // Only on a cold start, and not when an alarm was answered: someone who
        // tapped "jetzt planen" is on their way somewhere.
        val showLaunch = savedInstanceState == null && deferPhase == null && openPhase == null

        setContent {
            val design by container.designStore.choice.collectAsStateWithLifecycle()
            LaunchedEffect(design) { applyDesignToWindow(design) }

            var launching by remember { mutableStateOf(showLaunch) }
            LaunchedEffect(Unit) {
                if (launching) {
                    delay(LAUNCH_SCREEN_MILLIS)
                    launching = false
                }
            }

            EtaTheme(design) {
                EtaApp(
                    container = container,
                    deferPhase = deferPhase,
                    onDeferHandled = { deferPhase = null },
                    openPhase = openPhase,
                    onOpenHandled = { openPhase = null },
                )
            }
            AnimatedVisibility(
                visible = launching,
                enter = EnterTransition.None,
                exit = fadeOut(),
            ) {
                LaunchScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readDeferPhase(intent)
    }

    /**
     * Reads the alarm's answer out of the intent — **once**.
     *
     * The extras are removed as they are read, because a configuration change
     * recreates the activity with the very same intent. Without this, rotating
     * the phone re-opened the planning phase or brought `DeferDialog` back up:
     * these are one-shot instructions, and leaving them in the intent made them
     * standing ones.
     */
    private fun readDeferPhase(intent: Intent?) {
        deferPhase = intent.takePhaseExtra(PlanningAlarmContract.EXTRA_DEFER_PHASE)
        openPhase = intent.takePhaseExtra(PlanningAlarmContract.EXTRA_OPEN_PHASE)
    }

    private fun Intent?.takePhaseExtra(key: String): PlanningPhase? =
        phaseExtra(key).also { if (this != null) removeExtra(key) }

    private fun Intent?.phaseExtra(key: String): PlanningPhase? =
        this?.getStringExtra(key)?.let { runCatching { PlanningPhase.valueOf(it) }.getOrNull() }
}
