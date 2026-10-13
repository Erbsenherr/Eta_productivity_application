package com.example.eta.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.Note
import com.example.eta.ui.attributes.TodoAttributeFields
import com.example.eta.ui.attributes.TodoAttributes
import com.example.eta.ui.components.ConfirmDialog
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaDialog
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaScreen
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.EtaTextField
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.subtasks.foldCandidatesExcept
import com.example.eta.ui.theme.EtaTheme
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** What the list is ordered by; the direction is a second, separate answer. */
private enum class NoteSort(val label: String) { DATE("Datum"), TITLE("Alphabetisch") }

/** The note in the editor: an existing one, or a new one that has no row yet. */
private data class Editing(val note: Note?)

/** What "Taskify" was pressed on — the editor's text as it stood, saved or not. */
private data class Taskifying(
    val id: String?,
    val title: String,
    val text: String,
    val before: Instant?,
)

private fun Instant.formatDay(): String =
    toLocalDateTime(TimeZone.currentSystemDefault()).date.formatLong()

/**
 * The Notiz-Merger tab: notes with a title and a text, sorted and searched, and
 * at the end of each a "Taskify" button that makes a ToDo of it.
 */
@Composable
fun NotesScreen(viewModel: NotesViewModel) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val foldable by viewModel.foldable.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()

    var sortName by rememberSaveable { mutableStateOf(NoteSort.DATE.name) }
    var descending by rememberSaveable { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    val sort = NoteSort.valueOf(sortName)

    var editing by remember { mutableStateOf<Editing?>(null) }
    var taskifying by remember { mutableStateOf<Taskifying?>(null) }
    var finishing by remember { mutableStateOf<Taskifying?>(null) }

    val shown = remember(notes, sort, descending, query) {
        val needle = query.trim()
        val matching = notes.filter {
            needle.isEmpty() ||
                it.title.contains(needle, ignoreCase = true) ||
                it.text.contains(needle, ignoreCase = true)
        }
        val ordered = when (sort) {
            NoteSort.DATE -> matching.sortedBy { it.updatedAt }
            NoteSort.TITLE -> matching.sortedBy { it.title.lowercase() }
        }
        if (descending) ordered.reversed() else ordered
    }

    EtaScreen(bottomInset = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(EtaTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg),
        ) {
            Column {
                EtaText(text = "Notiz-Merger", style = EtaTheme.typography.title)
                EtaText(
                    text = "Notizen festhalten — und mit »Taskify« zu ToDos machen.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
                )
            }

            notice?.let { text ->
                EtaSurface(modifier = Modifier.fillMaxWidth(), borderColor = EtaTheme.colors.accent) {
                    Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                        EtaText(
                            text = text,
                            style = EtaTheme.typography.body,
                            color = EtaTheme.colors.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        EtaButton(text = "OK", style = EtaButtonStyle.Secondary, onClick = viewModel::dismissNotice)
                    }
                }
            }

            EtaButton(text = "Neue Notiz", onClick = { editing = Editing(null) })

            if (notes.isNotEmpty()) {
                EtaTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Suchen …",
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm),
                ) {
                    NoteSort.entries.forEach { option ->
                        EtaButton(
                            text = option.label,
                            style = if (option == sort) EtaButtonStyle.Primary else EtaButtonStyle.Secondary,
                            onClick = { sortName = option.name },
                        )
                    }
                    EtaButton(
                        text = if (descending) "↓ Absteigend" else "↑ Aufsteigend",
                        style = EtaButtonStyle.Secondary,
                        onClick = { descending = !descending },
                    )
                }
            }

            if (notes.isEmpty()) {
                EtaSurface(modifier = Modifier.fillMaxWidth()) {
                    EtaText(
                        text = "Noch keine Notizen.",
                        style = EtaTheme.typography.body,
                        color = EtaTheme.colors.textMuted,
                    )
                }
            } else if (shown.isEmpty()) {
                EtaText(
                    text = "Keine Notiz passt zur Suche.",
                    style = EtaTheme.typography.body,
                    color = EtaTheme.colors.textMuted,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                shown.forEach { note ->
                    key(note.id) { NoteRow(note = note, onClick = { editing = Editing(note) }) }
                }
            }
            Spacer(Modifier.size(EtaTheme.spacing.xl))
        }
    }

    editing?.let { current ->
        // Keyed by the note, so opening another one starts from its own text.
        key(current.note?.id ?: "new") {
            NoteEditorDialog(
                note = current.note,
                onClose = { editing = null },
                onSave = { title, text ->
                    viewModel.save(current.note?.id, title, text)
                    editing = null
                },
                onDelete = {
                    current.note?.let { viewModel.delete(it.id) }
                    editing = null
                },
                onTaskify = { title, text ->
                    taskifying = Taskifying(current.note?.id, title, text, current.note?.taskifiedAt)
                    editing = null
                },
            )
        }
    }

    taskifying?.let { pending ->
        TaskifyDialog(
            pending = pending,
            onDismiss = { taskifying = null },
            onLater = {
                viewModel.taskifyLater(pending.id, pending.title, pending.text)
                taskifying = null
            },
            onNow = {
                finishing = pending
                taskifying = null
            },
        )
    }

    finishing?.let { pending ->
        TaskifyNowDialog(
            pending = pending,
            today = viewModel.today,
            foldable = foldable,
            onDismiss = { finishing = null },
            onCreate = { name, attributes ->
                viewModel.taskifyNow(pending.id, pending.title, pending.text, name, attributes)
                finishing = null
            },
        )
    }
}

@Composable
private fun NoteRow(note: Note, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        ) {
            EtaText(
                text = note.title.ifBlank { "Ohne Titel" },
                style = EtaTheme.typography.bodyStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (note.text.isNotBlank()) {
                EtaText(
                    text = note.text,
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            EtaText(
                text = note.updatedAt.formatDay() +
                    (note.taskifiedAt?.let { " · ToDo erstellt am ${it.formatDay()}" } ?: ""),
                style = EtaTheme.typography.caption,
                color = if (note.taskifiedAt != null) EtaTheme.colors.accent else EtaTheme.colors.textMuted,
            )
        }
    }
}

/**
 * A note being written or changed.
 *
 * Leaving it with something unsaved — a tap beside the window, the back key,
 * "Schließen" — **asks** rather than throwing the text away or saving it
 * unasked: both are the wrong guess half the time. "Taskify" is the last thing
 * in the note, as asked, and takes the text as it stands.
 */
@Composable
private fun NoteEditorDialog(
    note: Note?,
    onClose: () -> Unit,
    onSave: (title: String, text: String) -> Unit,
    onDelete: () -> Unit,
    onTaskify: (title: String, text: String) -> Unit,
) {
    var title by remember { mutableStateOf(note?.title.orEmpty()) }
    var text by remember { mutableStateOf(note?.text.orEmpty()) }
    var asking by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val dirty = title != note?.title.orEmpty() || text != note?.text.orEmpty()
    val leave = { if (dirty) asking = true else onClose() }

    EtaDialog(title = if (note == null) "Neue Notiz" else "Notiz", onDismiss = leave) {
        EtaField(label = "Titel") {
            EtaTextField(value = title, onValueChange = { title = it }, placeholder = "Worum geht es?")
        }
        EtaField(label = "Text") {
            EtaTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = "Was willst du festhalten?",
                singleLine = false,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Schließen", style = EtaButtonStyle.Secondary, onClick = leave)
            Spacer(Modifier.weight(1f))
            EtaButton(
                text = "Speichern",
                enabled = dirty && (title.isNotBlank() || text.isNotBlank()),
                onClick = { onSave(title.trim(), text.trim()) },
            )
        }
        if (note != null) {
            EtaButton(
                text = "Notiz löschen",
                style = EtaButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
                onClick = { deleting = true },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
            EtaButton(
                text = "Taskify",
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank(),
                onClick = { onTaskify(title.trim(), text.trim()) },
            )
            EtaText(
                text = if (title.isBlank()) {
                    "Braucht einen Titel — er wird der Name des ToDos."
                } else {
                    "Macht aus der Notiz ein ToDo: der Titel wird sein Name, der Text seine " +
                        "Notiz. Die Notiz wird dabei gesichert und bleibt erhalten."
                },
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
        }
    }

    if (asking) {
        EtaDialog(title = "Notiz speichern?", onDismiss = { asking = false }) {
            EtaText(
                text = "Du hast Änderungen, die noch nicht gespeichert sind.",
                style = EtaTheme.typography.body,
                color = EtaTheme.colors.textSecondary,
            )
            EtaButton(
                text = "Speichern",
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank() || text.isNotBlank(),
                onClick = { onSave(title.trim(), text.trim()) },
            )
            EtaButton(
                text = "Verwerfen",
                style = EtaButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
                onClick = onClose,
            )
            EtaButton(
                text = "Weiter bearbeiten",
                style = EtaButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
                onClick = { asking = false },
            )
        }
    }

    if (deleting) {
        ConfirmDialog(
            title = "Notiz löschen?",
            message = "»${note?.title.orEmpty().ifBlank { "Ohne Titel" }}« wird gelöscht. Ein ToDo, " +
                "das daraus entstanden ist, bleibt.",
            confirm = "Löschen",
            onDismiss = { deleting = false },
            onConfirm = onDelete,
        )
    }
}

/** Where the ToDo goes: into the Sammelliste as it is, or finished now. */
@Composable
private fun TaskifyDialog(
    pending: Taskifying,
    onDismiss: () -> Unit,
    onLater: () -> Unit,
    onNow: () -> Unit,
) {
    EtaDialog(title = "Taskify", onDismiss = onDismiss) {
        EtaText(
            text = "Aus »${pending.title}« wird ein ToDo.",
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )
        pending.before?.let {
            EtaText(
                text = "Aus dieser Notiz ist am ${it.formatDay()} schon ein ToDo entstanden — " +
                    "es käme ein weiteres dazu.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.warning,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
            EtaButton(
                text = "In die Sammelliste",
                style = EtaButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
                onClick = onLater,
            )
            EtaText(
                text = "Kategorie, Priorität und Dauer fragt die Planungsphase am Abend.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
            EtaButton(
                text = "Jetzt konkretisieren",
                style = EtaButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
                onClick = onNow,
            )
            EtaText(
                text = "Du beantwortest das gleich; das ToDo liegt dann fertig in der Sammelliste.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
        }
    }
}

/** The ordinary ToDo form, opened on the note's title. */
@Composable
private fun TaskifyNowDialog(
    pending: Taskifying,
    today: LocalDate,
    foldable: List<Item>,
    onDismiss: () -> Unit,
    onCreate: (name: String, attributes: TodoAttributes) -> Unit,
) {
    var name by remember { mutableStateOf(pending.title) }
    var attributes by remember { mutableStateOf(TodoAttributes()) }

    EtaDialog(title = "Neue Aufgabe", onDismiss = onDismiss) {
        EtaField(label = "Name") {
            EtaTextField(value = name, onValueChange = { name = it })
        }
        if (pending.text.isNotBlank()) {
            EtaField(label = "Notiz") {
                EtaText(
                    text = pending.text,
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        TodoAttributeFields(
            value = attributes,
            onChange = { attributes = it },
            today = today,
            groupName = name,
            onGroupName = { name = it },
            foldCandidates = foldable.foldCandidatesExcept(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            EtaButton(
                text = "Anlegen",
                enabled = name.isNotBlank(),
                onClick = { onCreate(name.trim(), attributes) },
            )
        }
    }
}
