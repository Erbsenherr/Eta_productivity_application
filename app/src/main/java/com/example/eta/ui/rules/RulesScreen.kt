package com.example.eta.ui.rules

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eta.domain.model.Rule
import com.example.eta.ui.components.ConfirmDialog
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaDialog
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaScreen
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.EtaTextField
import com.example.eta.ui.theme.EtaTheme

/** The rule in the editor: an existing one, or a new one that has no row yet. */
private data class Editing(val rule: Rule?)

/**
 * The Regelwerk tab (step 53): rules in question-and-answer form.
 *
 * A tap on a question unfolds its answer, and folds it again; holding a rule
 * opens it for changing or deleting; "Regel hinzufügen" writes a new one. The
 * rules are the user's word to themselves — nothing in the app enforces them.
 */
@Composable
fun RulesScreen(viewModel: RulesViewModel) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Editing?>(null) }
    var deleting by remember { mutableStateOf<Rule?>(null) }

    EtaScreen(bottomInset = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(EtaTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg),
        ) {
            Column {
                EtaText(text = "Regelwerk", style = EtaTheme.typography.title)
                EtaText(
                    text = "Was gilt, wenn …? Einmal entschieden, muss es am Tag selbst nicht " +
                        "mehr entschieden werden. Antippen zeigt die Regel, gedrückt halten " +
                        "bearbeitet oder löscht sie.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
                )
            }

            EtaButton(text = "Regel hinzufügen", onClick = { editing = Editing(null) })

            if (rules.isEmpty()) {
                EtaText(
                    text = "Noch keine Regeln. Zum Beispiel: »Was passiert, wenn ich krank " +
                        "bin?« — und darunter, was dann gilt.",
                    style = EtaTheme.typography.body,
                    color = EtaTheme.colors.textMuted,
                )
            }

            // Keyed by id: an unfolded answer belongs to its rule, not to a slot.
            rules.forEach { rule ->
                key(rule.id) {
                    RuleCard(rule = rule, onLongPress = { editing = Editing(rule) })
                }
            }
        }
    }

    editing?.let { current ->
        RuleEditDialog(
            rule = current.rule,
            onDismiss = { editing = null },
            onSave = { question, answer ->
                viewModel.save(current.rule?.id, question, answer)
                editing = null
            },
            onDelete = current.rule?.let { rule ->
                {
                    editing = null
                    deleting = rule
                }
            },
        )
    }

    // Asked by the screen, not inside the editor: a window on top of a window.
    deleting?.let { rule ->
        ConfirmDialog(
            title = "Regel löschen?",
            message = "»${rule.question}« und was dazu festgehalten ist, sind danach weg.",
            confirm = "Löschen",
            onDismiss = { deleting = null },
            onConfirm = {
                viewModel.delete(rule.id)
                deleting = null
            },
        )
    }
}

/** One rule: the question, and the answer under it once tapped. */
@Composable
private fun RuleCard(rule: Rule, onLongPress: () -> Unit) {
    var open by rememberSaveable(rule.id) { mutableStateOf(false) }
    // Through State: `pointerInput` keeps the lambda it launched with.
    val latestOnLongPress by rememberUpdatedState(onLongPress)

    EtaSurface(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(rule.id) {
                detectTapGestures(
                    onTap = { open = !open },
                    onLongPress = { latestOnLongPress() },
                )
            },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EtaText(
                    text = rule.question,
                    style = EtaTheme.typography.bodyStrong,
                    modifier = Modifier.weight(1f),
                )
                EtaText(
                    text = if (open) "▴" else "▾",
                    style = EtaTheme.typography.label,
                    color = EtaTheme.colors.textMuted,
                )
            }
            if (open) {
                EtaText(
                    text = rule.answer.ifBlank { "Noch nichts festgehalten." },
                    style = EtaTheme.typography.body,
                    color = if (rule.answer.isBlank()) EtaTheme.colors.textMuted else EtaTheme.colors.textSecondary,
                )
            }
        }
    }
}

/** Writing a rule, or changing one — then with deleting as the way out beside it. */
@Composable
private fun RuleEditDialog(
    rule: Rule?,
    onDismiss: () -> Unit,
    onSave: (question: String, answer: String) -> Unit,
    /** Null for a new rule, which has nothing to delete. */
    onDelete: (() -> Unit)?,
) {
    var question by remember(rule?.id) { mutableStateOf(rule?.question.orEmpty()) }
    var answer by remember(rule?.id) { mutableStateOf(rule?.answer.orEmpty()) }

    EtaDialog(title = if (rule == null) "Neue Regel" else "Regel bearbeiten", onDismiss = onDismiss) {
        EtaField(label = "Frage") {
            EtaTextField(
                value = question,
                onValueChange = { question = it },
                placeholder = "Was passiert, wenn …?",
            )
        }
        EtaField(label = "Regel") {
            EtaTextField(
                value = answer,
                onValueChange = { answer = it },
                placeholder = "Dann gilt …",
                singleLine = false,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            if (onDelete != null) {
                EtaButton(text = "Löschen", style = EtaButtonStyle.Secondary, onClick = onDelete)
            } else {
                EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            }
            Spacer(Modifier.weight(1f))
            EtaButton(
                text = "Sichern",
                enabled = question.isNotBlank(),
                onClick = { onSave(question, answer) },
            )
        }
    }
}
