package com.example.eta.ui.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.data.update.UpdateService
import com.example.eta.domain.update.Release
import com.example.eta.domain.update.isNewer
import java.io.File
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
    data class Available(val release: Release) : UpdateState
    /** Waiting for the user to allow Eta to install apps, in the system settings. */
    data class NeedsPermission(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class Ready(val release: Release, val apk: File) : UpdateState
    data class Failed(val reason: String, val release: Release? = null) : UpdateState
}

/**
 * The "App-Update" page of the settings tab.
 *
 * Its own view model, like the calendar's: it shares nothing with the draft of
 * the stored answers. Held by the tab, so a download goes on while another page
 * is open, and the intents it asks for are handed out one at a time through
 * [launch] for the screen to start — a view model has no activity to start them from.
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

    /**
     * Downloads [release] and opens the installer — or, while Eta may not
     * install apps, the system page that allows it first. Pressed again after
     * coming back from there, it carries on.
     */
    fun install(release: Release) {
        if (job?.isActive == true) return
        if (!service.mayInstall()) {
            _state.value = UpdateState.NeedsPermission(release)
            _launch.value = service.installPermissionIntent()
            return
        }
        (_state.value as? UpdateState.Ready)?.takeIf { it.release == release && it.apk.exists() }?.let {
            _launch.value = service.installIntent(it.apk)
            return
        }
        job = viewModelScope.launch {
            _state.value = UpdateState.Downloading(release, 0f)
            _state.value = try {
                val apk = service.download(release) { progress ->
                    _state.value = UpdateState.Downloading(release, progress)
                }
                _launch.value = service.installIntent(apk)
                UpdateState.Ready(release, apk)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                UpdateState.Failed(reasonOf(failure, "Der Download ist abgebrochen."), release)
            }
        }
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
