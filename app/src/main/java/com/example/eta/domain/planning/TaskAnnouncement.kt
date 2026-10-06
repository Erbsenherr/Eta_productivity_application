package com.example.eta.domain.planning

/** How a task event makes itself heard. */
enum class TaskAnnouncement {
    /** The mp3 for its kind — what the app always did. */
    SOUND,

    /** The task's name, read out by the device's speech engine. */
    SPEECH,
}

/**
 * What is read out for the events of one ring.
 *
 * A sound says only *that* something begins; the point of speaking is to say
 * *what*. So unlike the sounds, which play once per kind however many tasks share
 * the minute, every task is named — two things starting together are two names.
 *
 * Notes are read only at a task's **start**, and only when asked for: that is the
 * moment they are instructions. At its end, or in the middle of a pomodoro, they
 * would be the same paragraph again. The definition's note comes first, then the
 * day's own, the order the dashboard shows them in.
 */
fun spokenAnnouncement(events: List<TaskEvent>, withNotes: Boolean): String =
    events.joinToString(" ") { event ->
        val name = event.entry.item.name.trim()
        when (event.kind) {
            TaskEventKind.START -> {
                val notes = if (withNotes) {
                    listOfNotNull(event.entry.item.note, event.entry.block.note)
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                } else {
                    emptyList()
                }
                (listOf("Jetzt: $name.") + notes).joinToString(" ")
            }

            TaskEventKind.END -> "$name: die Zeit ist um."
            TaskEventKind.STILL_ACTIVE -> "Bist du noch bei $name?"
            TaskEventKind.POMODORO_PAUSE -> "Pause bei $name."
            TaskEventKind.POMODORO_WORK -> "Weiter mit $name."
        }
    }
