package com.example.eta.ui.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.data.update.UpdateService
import com.example.eta.domain.update.Release
import com.example.eta.domain.update.isNewer
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the update page stands. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val latest: String) : UpdateState
    /** [opened]: the download has been handed to the browser at least once. */
    data class Available(val release: Release, val opened: Boolean = false) : UpdateState
    data class Failed(val reason: String) : UpdateState
}

/**
 * The "App-Update" page of the settings tab.
 *
 * Its own view model, like the calendar's: it shares nothing with the draft of
 * the stored answers. The browser intent it asks for is handed out through
 * [launch] for the screen to start — a view model has no activity to start it from.
 */
class UpdateViewModel(private val service: UpdateService) : ViewModel() {

    val running: String = service.runningVersion

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _launch = MutableStateFlow<Intent?>(null)
    /** An activity the screen should start now, or null. */
    val launch: StateFlow<Intent?> = _launch.asStateFlow()

    private var job: Job? = null

    fun check() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.value = UpdateState.Checking
            _state.value = try {
                val release = service.latest()
                if (isNewer(release.tag, running)) {
                    UpdateState.Available(release)
                } else {
                    UpdateState.UpToDate(release.tag)
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                // A JSON GitHub changed, as well as no network: said, not crashed on.
                UpdateState.Failed(reasonOf(failure, "Keine Verbindung zu GitHub."))
            }
        }
    }

    /** Hands [release]'s APK to the browser. */
    fun download(release: Release) {
        _launch.value = service.downloadIntent(release)
        _state.value = UpdateState.Available(release, opened = true)
    }

    /** The screen has started what [launch] asked for. */
    fun launched() {
        _launch.value = null
    }
}

/**
 * The sentence for a failure: [UpdateService]'s own, which are written for the
 * page, or [fallback] for what the network threw — those name hosts and sockets.
 */
private fun reasonOf(failure: Exception, fallback: String): String =
    failure.message.takeIf { failure::class == IOException::class } ?: fallback
