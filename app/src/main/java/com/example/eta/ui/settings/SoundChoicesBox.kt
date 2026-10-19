package com.example.eta.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import com.example.eta.alarm.EtaSound
import com.example.eta.domain.sound.EtaSoundFile
import com.example.eta.domain.sound.SoundChoice
import com.example.eta.domain.sound.SoundEvent
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaChoice
import com.example.eta.ui.components.EtaDialog
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.theme.EtaTheme

/**
 * "Sounds" on the Audio page (step 51): what every moment Eta makes itself
 * heard plays — one of Eta's own files, or a sound of the phone's, picked in
 * Android's own list.
 *
 * Applied at once, like the mode above it, and kept beside it in
 * `SharedPreferences`: the sounds are played from receivers that have a context
 * and nothing else.
 */
@Composable
internal fun SoundChoicesBox() {
    val context = LocalContext.current
    // Read again whenever the dialog closes, so a row says what was just chosen.
    var version by remember { mutableIntStateOf(0) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Sounds", style = EtaTheme.typography.heading)
            EtaText(
                text = "Für jeden Anlass ein eigener Sound: einer von Eta oder ein Ton " +
                    "deines Handys. Tippe auf einen Anlass, um ihn zu ändern.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
            SoundEvent.entries.forEach { event ->
                val choice = remember(version, event) { EtaSound.choiceOf(context, event) }
                SoundRow(
                    title = event.label,
                    current = describe(context, event, choice),
                    onClick = { open = event.name },
                )
            }
        }
    }

    SoundEvent.entries.firstOrNull { it.name == open }?.let { event ->
        SoundPickDialog(
            event = event,
            onDismiss = {
                open = null
                version++
            },
        )
    }
}

@Composable
private fun SoundRow(title: String, current: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            EtaText(text = title, style = EtaTheme.typography.bodyStrong)
            EtaText(
                text = current,
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textSecondary,
            )
        }
        EtaText(text = "›", style = EtaTheme.typography.heading, color = EtaTheme.colors.textMuted)
    }
}

/**
 * One moment's choice. Choosing an entry plays it, so the list can be listened
 * through; nothing is kept until "Übernehmen".
 */
@Composable
private fun SoundPickDialog(event: SoundEvent, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var draft by remember(event) { mutableStateOf(EtaSound.choiceOf(context, event)) }

    DisposableEffect(event) { onDispose { EtaSound.stopPreview() } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val picked = result.data?.let {
            IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } ?: return@rememberLauncherForActivityResult
        draft = SoundChoice.System(picked.toString())
    }

    // The entries: the default, Eta's files, and the system sound once there is one.
    val options = buildList {
        add(SoundChoice.Default to "Standard (${defaultName(event)})")
        EtaSoundFile.entries.forEach { add(SoundChoice.Eta(it) to it.label) }
        (draft as? SoundChoice.System)?.let { add(it to "Handy: ${systemTitle(context, it)}") }
    }

    EtaDialog(title = event.label, onDismiss = onDismiss) {
        EtaChoice(
            options = options,
            selected = draft,
            onSelect = {
                draft = it
                EtaSound.preview(context, event, it)
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(
                text = "Anhören",
                style = EtaButtonStyle.Secondary,
                onClick = { EtaSound.preview(context, event, draft) },
            )
            EtaButton(
                text = "Vom Handy …",
                style = EtaButtonStyle.Secondary,
                onClick = {
                    EtaSound.stopPreview()
                    picker.launch(systemPickerIntent(context, event, draft))
                },
            )
        }
        EtaText(
            text = "Zu hören ist er so laut wie dein Wecker — Eta spielt über die " +
                "Weckerlautstärke.",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            EtaButton(
                text = "Übernehmen",
                onClick = {
                    EtaSound.setChoice(context, event, draft)
                    onDismiss()
                },
            )
        }
    }
}

/**
 * Android's own list of sounds — alarm tones, ringtones and notification
 * sounds alike, since a gentle notification sound can be the right thing for a
 * task and a ringtone for the morning.
 */
private fun systemPickerIntent(context: Context, event: SoundEvent, current: SoundChoice): Intent =
    Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, event.label)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
        // "Lautlos" is the Audio page's mode, not a sound.
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        val existing = when {
            current is SoundChoice.System -> current.uri.toUri()
            event == SoundEvent.WAKE && current == SoundChoice.Default -> EtaSound.phoneAlarmTone()
            else -> null
        }
        existing?.let { putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, it) }
    }

private fun defaultName(event: SoundEvent): String =
    event.default?.label ?: "Wecker des Handys"

private fun describe(context: Context, event: SoundEvent, choice: SoundChoice): String = when (choice) {
    SoundChoice.Default -> "Standard (${defaultName(event)})"
    is SoundChoice.Eta -> choice.file.label
    is SoundChoice.System -> "Handy: ${systemTitle(context, choice)}"
}

/** The name the phone gives a sound; a removed or unreadable one is just "Ton". */
private fun systemTitle(context: Context, choice: SoundChoice.System): String =
    runCatching { RingtoneManager.getRingtone(context, choice.uri.toUri())?.getTitle(context) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: "Ton"
