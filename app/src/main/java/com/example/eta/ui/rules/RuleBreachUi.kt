package com.example.eta.ui.rules

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.eta.domain.model.Rule
import com.example.eta.domain.rules.ActiveConsequence
import com.example.eta.ui.components.ConfirmDialog
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaDialog
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.theme.EtaTheme
import com.example.eta.ui.theme.signalRed

/**
 * "Regelverstoß melden" (step 57): the "!" in the lower right corner of every
 * tab while the Regelwerk is on.
 */
@Composable
fun ReportBreachButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = "Regelverstoß melden"
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(EtaTheme.colors.signalRed)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        EtaText(text = "!", style = EtaTheme.typography.title, color = Color.White)
    }
}

/**
 * "Verstoßen gegen …": every rule, and one is chosen by **holding** it — a tap
 * only says so. A breach brings a consequence, and a thumb brushing past a
 * list should not be able to set one off.
 */
@Composable
fun ReportBreachDialog(
    rules: List<Rule>,
    onDismiss: () -> Unit,
    onReport: (Rule) -> Unit,
) {
    var tapped by remember { mutableStateOf(false) }
    EtaDialog(title = "Regelverstoß melden", onDismiss = onDismiss) {
        EtaText(
            text = if (rules.isEmpty()) {
                "Noch keine Regeln — sie werden im Reiter »Regelwerk« angelegt."
            } else {
                "Verstoßen gegen … — eine Regel gedrückt halten, um sie auszuwählen."
            },
            style = EtaTheme.typography.body,
            color = if (tapped) EtaTheme.colors.warning else EtaTheme.colors.textSecondary,
        )
        Column(
            modifier = Modifier
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm),
        ) {
            rules.forEach { rule ->
                key(rule.id) {
                    val report by rememberUpdatedState(onReport)
                    EtaSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(rule.id) {
                                detectTapGestures(
                                    onTap = { tapped = true },
                                    onLongPress = { report(rule) },
                                )
                            },
                        contentPadding = EtaTheme.spacing.md,
                    ) {
                        Column {
                            EtaText(text = rule.question, style = EtaTheme.typography.bodyStrong)
                            EtaText(
                                text = rule.consequence?.let { "Konsequenz: $it" } ?: "Ohne Konsequenz",
                                style = EtaTheme.typography.caption,
                                color = if (rule.consequence != null) {
                                    EtaTheme.colors.signalRed
                                } else {
                                    EtaTheme.colors.textMuted
                                },
                            )
                        }
                    }
                }
            }
        }
        EtaButton(text = "Abbrechen", style = EtaButtonStyle.Secondary, onClick = onDismiss)
    }
}

/**
 * Today's breaches, in red at the top of the dashboard: what the consequence
 * is, and which rule brought it. A long press takes a report made by mistake
 * back — asked first.
 */
@Composable
fun ConsequenceBox(active: List<ActiveConsequence>, onWithdraw: (breachId: String) -> Unit) {
    if (active.isEmpty()) return
    var withdrawing by remember { mutableStateOf<ActiveConsequence?>(null) }

    EtaSurface(
        modifier = Modifier.fillMaxWidth(),
        borderColor = EtaTheme.colors.signalRed,
        contentPadding = EtaTheme.spacing.md,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaText(
                text = if (active.any { it.rule.consequence != null }) "Konsequenz aktiv" else "Regelverstoß",
                style = EtaTheme.typography.label,
                color = EtaTheme.colors.signalRed,
            )
            active.forEach { entry ->
                key(entry.breach.id) {
                    val latest by rememberUpdatedState(entry)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(entry.breach.id) {
                                detectTapGestures(onLongPress = { withdrawing = latest })
                            },
                    ) {
                        EtaText(
                            text = entry.rule.consequence ?: entry.rule.question,
                            style = EtaTheme.typography.bodyStrong,
                            color = EtaTheme.colors.signalRed,
                        )
                        EtaText(
                            text = if (entry.rule.consequence != null) {
                                "Regel: ${entry.rule.question}" +
                                    if (entry.rule.consequenceOnFreeTime) " · gilt für die Freizeit" else ""
                            } else {
                                "Für diese Regel ist keine Konsequenz festgehalten."
                            },
                            style = EtaTheme.typography.caption,
                            color = EtaTheme.colors.textSecondary,
                        )
                    }
                }
            }
            EtaText(
                text = "Gedrückt halten nimmt eine Meldung zurück.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
        }
    }

    withdrawing?.let { entry ->
        ConfirmDialog(
            title = "Meldung zurücknehmen?",
            message = "Der Verstoß gegen »${entry.rule.question}« wird für heute gelöscht" +
                if (entry.rule.consequenceOnFreeTime) ", und die Freizeit ist wieder frei." else ".",
            confirm = "Zurücknehmen",
            onDismiss = { withdrawing = null },
            onConfirm = {
                onWithdraw(entry.breach.id)
                withdrawing = null
            },
        )
    }
}

/**
 * The morning's reminder (step 57): rules broken in the last two weeks, to be
 * minded today — said as encouragement, not as a reckoning.
 */
@Composable
fun WatchReminderBox(rules: List<Rule>, onDismiss: () -> Unit) {
    if (rules.isEmpty()) return
    EtaSurface(
        modifier = Modifier.fillMaxWidth(),
        borderColor = EtaTheme.colors.accent,
        contentPadding = EtaTheme.spacing.md,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            EtaText(text = "Heute im Blick", style = EtaTheme.typography.label, color = EtaTheme.colors.accent)
            EtaText(
                text = "Ein neuer Tag, eine neue Chance. Darauf lohnt es sich heute besonders zu achten:",
                style = EtaTheme.typography.body,
                color = EtaTheme.colors.textSecondary,
            )
            rules.forEach { rule ->
                key(rule.id) {
                    EtaText(text = "· ${rule.question}", style = EtaTheme.typography.bodyStrong)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                EtaText(
                    text = "Du schaffst das!",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.success,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(EtaTheme.spacing.sm))
                EtaButton(text = "Danke", style = EtaButtonStyle.Secondary, onClick = onDismiss)
            }
        }
    }
}

/** How wide the tab is that stays at the edge, and how far the panel slides out. */
private val TAB_WIDTH = 18.dp
private val PANEL_WIDTH = 64.dp

/**
 * The way to "Regelverstoß melden" (step 58's round): a small tab at the right
 * edge, to be pulled out — by a drag to the left, or a tap — which uncovers
 * the "!". Pushed back, or after a report, it tucks itself away again. Only
 * the tab stands in front of the screen the rest of the time, so the button no
 * longer covers what lies under it.
 */
@Composable
fun ReportBreachTab(onReport: () -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val panelPx = with(density) { PANEL_WIDTH.toPx() }
    // 0 is pulled out all the way; panelPx is tucked away.
    val offset = remember { Animatable(panelPx) }
    val scope = rememberCoroutineScope()
    val report by rememberUpdatedState(onReport)
    val red = EtaTheme.colors.signalRed
    val open = offset.value < panelPx / 2

    fun settle(out: Boolean) {
        scope.launch { offset.animateTo(if (out) 0f else panelPx) }
    }

    Row(
        modifier = modifier
            .offset { IntOffset(offset.value.roundToInt(), 0) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { settle(offset.value < panelPx / 2) },
                    onDragCancel = { settle(offset.value < panelPx / 2) },
                ) { change, amount ->
                    change.consume()
                    scope.launch { offset.snapTo((offset.value + amount).coerceIn(0f, panelPx)) }
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tabLabel = if (open) "Lasche einklappen" else "Regelverstoß melden — Lasche ausklappen"
        Box(
            modifier = Modifier
                .width(TAB_WIDTH)
                .height(56.dp)
                .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                .background(red)
                .clickable { settle(!open) }
                .semantics { contentDescription = tabLabel },
            contentAlignment = Alignment.Center,
        ) {
            EtaText(
                text = if (open) "›" else "‹",
                style = EtaTheme.typography.label,
                color = Color.White,
            )
        }
        Box(
            modifier = Modifier
                .width(PANEL_WIDTH)
                .height(64.dp)
                .background(EtaTheme.colors.surfaceRaised)
                .border(1.dp, EtaTheme.colors.border),
            contentAlignment = Alignment.Center,
        ) {
            ReportBreachButton(
                onClick = {
                    settle(false)
                    report()
                },
            )
        }
    }
}
