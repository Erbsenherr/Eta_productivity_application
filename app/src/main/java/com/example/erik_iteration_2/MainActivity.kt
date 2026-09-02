package com.example.erik_iteration_2

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.erik_iteration_2.alarm.PlanningAlarmContract
import com.example.erik_iteration_2.domain.planning.PlanningPhase
import com.example.erik_iteration_2.ui.root.ErikApp
import com.example.erik_iteration_2.ui.theme.ErikTheme

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
        enableEdgeToEdge()

        readDeferPhase(intent)
        val container = (application as ErikApplication).container

        setContent {
            ErikTheme {
                ErikApp(
                    container = container,
                    deferPhase = deferPhase,
                    onDeferHandled = { deferPhase = null },
                    openPhase = openPhase,
                    onOpenHandled = { openPhase = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readDeferPhase(intent)
    }

    private fun readDeferPhase(intent: Intent?) {
        deferPhase = intent.phaseExtra(PlanningAlarmContract.EXTRA_DEFER_PHASE)
        openPhase = intent.phaseExtra(PlanningAlarmContract.EXTRA_OPEN_PHASE)
    }

    private fun Intent?.phaseExtra(key: String): PlanningPhase? =
        this?.getStringExtra(key)?.let { runCatching { PlanningPhase.valueOf(it) }.getOrNull() }
}
