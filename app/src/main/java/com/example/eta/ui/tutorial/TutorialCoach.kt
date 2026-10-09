package com.example.eta.ui.tutorial

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.eta.domain.tutorial.StepKind
import com.example.eta.domain.tutorial.TutorialFacts
import com.example.eta.domain.tutorial.TutorialStep
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaProgressBar
import com.example.eta.ui.components.EtaScreen
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.theme.EtaTheme
import kotlin.math.roundToInt

/** How much of the screen the coach may take before its text scrolls instead. */
private const val COACH_MAX_HEIGHT_FRACTION = 0.4f

/**
 * The tutorial's own bar, under whichever screen is being explained.
 *
 * It says what the step is about and, where the step asks for something, keeps
 * count of it: a ring for what is still to do, a tick once it has happened.
 * "Weiter" is grey until then. A step whose way on is a button of the screen
 * above has no button here at all — see [StepKind.FOLLOW].
 *
 * At the foot of the screen because that is where the thumb is, and capped in
 * height: the screen above is the thing being shown, and a long text scrolls
 * inside the bar rather than pushing that screen away.
 */
@Composable
fun TutorialCoach(
    step: TutorialStep,
    number: Int,
    total: Int,
    facts: TutorialFacts,
    hint: String?,
    onNext: () -> Unit,
    onQuit: () -> Unit,
) {
    val checks = step.checks(facts)
    val passable = step.passable(facts)
    val maxHeight = with(LocalDensity.current) {
        (LocalWindowInfo.current.containerSize.height * COACH_MAX_HEIGHT_FRACTION).toDp()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EtaTheme.colors.surfaceRaised)
            .navigationBarsPadding(),
    ) {
        EtaProgressBar(number.toFloat() / total)

        Column(
            modifier = Modifier
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = EtaTheme.spacing.lg,
                    end = EtaTheme.spacing.lg,
                    top = EtaTheme.spacing.md,
                ),
            verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EtaText(
                    text = "Tutorial · $number von $total",
                    style = EtaTheme.typography.label,
                    color = EtaTheme.colors.textMuted,
                    modifier = Modifier.weight(1f),
                )
                // Keyed by the step, so an armed "Wirklich?" does not wait on
                // the next one for a stray tap.
                QuitLink(key = number, onQuit = onQuit)
            }

            EtaText(text = step.title, style = EtaTheme.typography.heading)
            EtaText(
                text = step.text(facts),
                style = EtaTheme.typography.body,
                color = EtaTheme.colors.textSecondary,
            )

            checks.forEach { check ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EtaText(
                        text = if (check.done) "✓" else "○",
                        style = EtaTheme.typography.bodyStrong,
                        color = if (check.done) EtaTheme.colors.success else EtaTheme.colors.textMuted,
                        modifier = Modifier.width(24.dp),
                    )
                    EtaText(
                        text = check.label,
                        style = EtaTheme.typography.bodyStrong,
                        color = if (check.done) EtaTheme.colors.success else EtaTheme.colors.textPrimary,
                    )
                }
            }

            if (step.swipeHint && !passable) SwipeHint()

            if (hint != null) {
                EtaText(
                    text = hint,
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.warning,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = EtaTheme.spacing.lg, vertical = EtaTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step.kind == StepKind.FOLLOW) {
                EtaText(
                    text = "Weiter geht es oben im Fenster.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
            } else {
                Spacer(Modifier.weight(1f))
                EtaButton(
                    text = if (number == total) "Tutorial abschließen" else "Weiter",
                    enabled = passable,
                    onClick = onNext,
                )
            }
        }
    }
}

/** "Beenden", asked twice: it sits where a thumb reaching for the text may land. */
@Composable
private fun QuitLink(key: Int, onQuit: () -> Unit) {
    var armed by remember(key) { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    EtaText(
        text = if (armed) "Wirklich beenden?" else "Beenden",
        style = EtaTheme.typography.label,
        color = if (armed) EtaTheme.colors.danger else EtaTheme.colors.textMuted,
        modifier = Modifier
            .clickable(interactionSource = interactionSource, indication = null) {
                if (armed) onQuit() else armed = true
            }
            .padding(vertical = EtaTheme.spacing.xs),
    )
}

private const val SWIPE_HINT_MILLIS = 1_400
private val SWIPE_HINT_TRAVEL = 64.dp

/**
 * A dot wiping from right to left, over and over: the gesture the step asks
 * for, shown rather than described. It goes once the wipe has been made.
 */
@Composable
private fun SwipeHint() {
    val progress by rememberInfiniteTransition(label = "swipeHint").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(SWIPE_HINT_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "swipeHintProgress",
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(SWIPE_HINT_TRAVEL + 14.dp)) {
            Box(
                modifier = Modifier
                    .offset { IntOffset((SWIPE_HINT_TRAVEL.toPx() * (1f - progress)).roundToInt(), 0) }
                    .alpha(1f - progress * 0.85f)
                    .size(14.dp)
                    .background(EtaTheme.colors.accent, CircleShape),
            )
        }
        Spacer(Modifier.size(EtaTheme.spacing.sm))
        EtaText(
            text = "zur Seite wischen",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
        )
    }
}

/** A page of the tutorial that is all text: scrolls, and keeps the app's margins. */
@Composable
internal fun TutorialPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(EtaTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg),
        content = content,
    )
}

/** The loop the app is built around, as the welcome page lists it. */
private val LOOP = listOf(
    "Eine Idee" to "Dir fällt etwas ein — meist im falschen Moment.",
    "Raus aus dem Kopf" to "In Sekunden notiert, mit Quick-Add. Der Kopf ist wieder frei.",
    "Form geben" to "Am Abend bekommt die Notiz Dauer, Priorität und Kategorie.",
    "Wochenplanung" to "Du entscheidest, was in deine nächste Woche kommt.",
    "Tagesplanung" to "Du legst fest, wann du es morgen erledigst.",
)

/**
 * The idea of the app on one page.
 *
 * Shown **before the questionnaire** on a first run — which is why it ends by
 * leading into it — and again in front of a tutorial repeated from the
 * settings, where [firstRun] is false and there is no questionnaire to lead to.
 */
@Composable
fun TutorialIntroScreen(
    onStart: () -> Unit,
    action: String = "Einrichtung starten",
    firstRun: Boolean = false,
) {
    EtaScreen {
        TutorialPage {
            EtaText(
                text = if (firstRun) "Willkommen bei Eta" else "So denkt Eta",
                style = EtaTheme.typography.display,
            )
            EtaText(
                text = "Wichtige Aufgaben fallen uns oft im falschen Moment ein. Eta fängt " +
                    "diese Gedanken auf und holt sie im richtigen Moment wieder hervor.",
                style = EtaTheme.typography.body,
                color = EtaTheme.colors.textSecondary,
            )

            EtaSurface(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
                    EtaText(
                        text = "Im Kern ein Wochen- und Tagesplaner mit einem festen Kreislauf:",
                        style = EtaTheme.typography.bodyStrong,
                    )
                    LOOP.forEachIndexed { index, (title, text) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(EtaTheme.colors.accentSoft, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                EtaText(
                                    text = "${index + 1}",
                                    style = EtaTheme.typography.bodyStrong,
                                    color = EtaTheme.colors.accent,
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                EtaText(text = title, style = EtaTheme.typography.bodyStrong)
                                EtaText(
                                    text = text,
                                    style = EtaTheme.typography.caption,
                                    color = EtaTheme.colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }

            if (firstRun) {
                EtaText(
                    text = "Vieles in deinem Alltag hat einen festen Rhythmus — und " +
                        "Regelmäßigkeit schafft Sicherheit. Damit fangen wir an: Richte " +
                        "jetzt die wichtigsten festen Zeiten ein.",
                    style = EtaTheme.typography.body,
                    color = EtaTheme.colors.textSecondary,
                )
            }

            Row {
                Spacer(Modifier.weight(1f))
                EtaButton(text = action, onClick = onStart)
            }
            Spacer(Modifier.size(EtaTheme.spacing.xl))
        }
    }
}
