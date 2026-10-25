package com.example.eta.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.eta.alarm.EtaSound
import com.example.eta.domain.sound.DEFAULT_CONSTANT_VOLUME
import com.example.eta.domain.sound.GroupAudio
import com.example.eta.domain.sound.SoundGroup
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaChoice
import com.example.eta.ui.components.EtaField
import com.example.eta.ui.components.EtaStepper
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.theme.EtaTheme

/** A step of the constant volume, in percent. */
private const val VOLUME_STEP = 10

/**
 * The quiet and the volume of one [SoundGroup] (step 52): whether it plays
 * through "Nicht stören", whether it plays through a phone on silent or
 * vibrate — every combination of the two — and whether it is as loud as the
 * phone says or as loud as chosen here.
 *
 * Applied at once, outside the draft, like the switch it replaced: it lives in
 * `SharedPreferences` for the receivers, and a save button would be a second
 * place for the same answer. [dndOn] and [muted] are the phone's state as last
 * read, for the sentence that says what happens right now.
 */
@Composable
internal fun SoundGroupFields(group: SoundGroup, dndOn: Boolean, muted: Boolean) {
    val context = LocalContext.current
    var audio by remember(group) { mutableStateOf(EtaSound.groupAudio(context, group)) }
    fun change(next: GroupAudio) {
        EtaSound.setGroupAudio(context, group, next)
        audio = next
    }
    DisposableEffect(group) { onDispose { EtaSound.stopPreview() } }

    val noun = if (group == SoundGroup.ALARM) "Alarme" else "Benachrichtigungen"
    val plays = when {
        dndOn -> audio.throughDoNotDisturb
        muted -> audio.throughSilentRinger
        else -> true
    }
    if (dndOn || muted) {
        EtaText(
            text = (if (dndOn) "„Nicht stören“ ist gerade an" else "Das Handy ist gerade lautlos oder auf Vibration") +
                if (plays) " — $noun spielen trotzdem." else " — $noun bleiben still. Hinweise erscheinen trotzdem.",
            style = EtaTheme.typography.caption,
            color = if (plays) EtaTheme.colors.textMuted else EtaTheme.colors.warning,
        )
    }

    EtaField(
        label = "Bei „Nicht stören“",
        hint = "Bei „Lautlos komplett“ schaltet das Handy auch Wecker stumm — daran kann " +
            "Eta nichts ändern.",
    ) {
        EtaChoice(
            options = listOf(false to "Still bleiben", true to "Trotzdem spielen"),
            selected = audio.throughDoNotDisturb,
            onSelect = { change(audio.copy(throughDoNotDisturb = it)) },
        )
    }
    EtaField(
        label = "Bei lautlos oder Vibration",
        hint = if (audio.throughSilentRinger && audio.constantVolume == null && group == SoundGroup.NOTIFICATION) {
            "Gebunden an die Handy-Lautstärke sind Benachrichtigungen dann meist trotzdem " +
                "nicht zu hören — dafür die konstante Lautstärke wählen."
        } else {
            null
        },
    ) {
        EtaChoice(
            options = listOf(false to "Still bleiben", true to "Trotzdem spielen"),
            selected = audio.throughSilentRinger,
            onSelect = { change(audio.copy(throughSilentRinger = it)) },
        )
    }
    EtaField(
        label = "Lautstärke",
        hint = if (audio.constantVolume == null) {
            if (group == SoundGroup.ALARM) {
                "Folgt der Wecker-Lautstärke des Handys."
            } else {
                "Folgt der Benachrichtigungs-Lautstärke des Handys — steht sie auf 0, " +
                    "bleiben die Töne still."
            }
        } else {
            "Immer so laut, egal wo die Regler des Handys stehen — auch wenn die " +
                "Benachrichtigungs-Lautstärke auf 0 ist. Eta stellt dafür kurz die " +
                "Wecker-Lautstärke um und danach zurück."
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaChoice(
                options = listOf(false to "Wie das Handy", true to "Konstant"),
                selected = audio.constantVolume != null,
                onSelect = { constant ->
                    change(audio.copy(constantVolume = if (constant) DEFAULT_CONSTANT_VOLUME else null))
                },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md),
            ) {
                audio.constantVolume?.let { percent ->
                    EtaStepper(
                        value = "$percent %",
                        onDecrement = {
                            change(audio.copy(constantVolume = (percent - VOLUME_STEP).coerceAtLeast(VOLUME_STEP)))
                        },
                        onIncrement = {
                            change(audio.copy(constantVolume = (percent + VOLUME_STEP).coerceAtMost(100)))
                        },
                        valueWidth = 72.dp,
                    )
                }
                EtaButton(
                    text = "Probe hören",
                    style = EtaButtonStyle.Secondary,
                    onClick = { EtaSound.previewGroup(context, group, audio) },
                )
            }
        }
    }
}
