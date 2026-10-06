package com.example.eta.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.alarm.ReminderCoordinator
import com.example.eta.data.repository.ReminderRepository
import com.example.eta.domain.model.Reminder
import com.example.eta.domain.reminder.reminderMoment
import kotlin.time.Clock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The Erinnerungen tab: set a reminder, see what is still to come, change or
 * delete it.
 *
 * Every write re-aims the one reminder alarm straight away. The tab is not a
 * flow, so nothing else would do it until the next app start — and a reminder
 * set for ten minutes from now cannot wait for that.
 */
class RemindersViewModel(
    private val repository: ReminderRepository,
    private val coordinator: ReminderCoordinator,
    private val clock: Clock = Clock.System,
    val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    val pending: StateFlow<List<Reminder>> = repository.observePending()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun now(): LocalDateTime = clock.now().toLocalDateTime(timeZone)

    fun add(text: String, date: LocalDate, time: LocalTime) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.add(text.trim(), reminderMoment(date, time, timeZone))
            coordinator.reschedule()
        }
    }

    fun update(id: String, text: String, date: LocalDate, time: LocalTime) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.update(id, text.trim(), reminderMoment(date, time, timeZone))
            coordinator.reschedule()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            coordinator.reschedule()
        }
    }
}
