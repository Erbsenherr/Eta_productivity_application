package com.example.eta.ui.contracts

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eta.domain.contract.SlotStatus
import com.example.eta.domain.model.Contract
import com.example.eta.domain.model.ContractState
import com.example.eta.domain.model.REINSTATEMENT_STREAK
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.LocalPointsVisible
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaScreen
import com.example.eta.ui.components.EtaExpander
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.SignatureView
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.format.formatPoints
import com.example.eta.ui.theme.EtaTheme
import kotlinx.datetime.LocalDate

/** The Selbstverträge tab: three slots, the legacy contracts, and what is behind them. */
@Composable
fun ContractsScreen(
    viewModel: ContractsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val refusal by viewModel.refusal.collectAsStateWithLifecycle()
    var signingSlot by remember { mutableStateOf<Int?>(null) }

    // Long-pressing a contract opens the one change it is allowed. When it is not
    // allowed, the same press says which rule stands in the way — a gesture that
    // silently does nothing is indistinguishable from one that is not there.
    var editingContract by remember { mutableStateOf<Contract?>(null) }
    var editRefused by remember { mutableStateOf<String?>(null) }

    fun openEdit(contract: Contract) {
        val refusal = editRefusal(contract)
        if (refusal == null) editingContract = contract else editRefused = refusal
    }

    EtaScreen(modifier = modifier, bottomInset = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(EtaTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg),
        ) {
            Column {
                EtaText(text = "Selbstverträge", style = EtaTheme.typography.display)
                EtaText(
                    text = "Drei Verträge gleichzeitig. Legacy-Verträge zählen nicht mit.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
                )
            }

            state.expiring.forEach { contract ->
                ExpiringBox(
                    contract = contract,
                    onExtend = { viewModel.extend(contract, weeks = 4) },
                    onEnd = { viewModel.fulfil(contract) },
                )
            }

            // Above the slots, because until it is answered the slot below is not
            // really free — it is holding a contract nobody has decided about.
            state.awaitingDecision.forEach { contract ->
                ProbationDecisionBox(
                    contract = contract,
                    onRestart = { viewModel.restartProbation(contract) },
                    onAbandon = { viewModel.abandonProbation(contract) },
                )
            }

            state.slots.forEachIndexed { slot, status ->
                SlotBox(
                    slot = slot,
                    status = status,
                    today = state.today,
                    onReinstate = { contract -> viewModel.restartProbation(contract) },
                    upgradable = state.upgradable.any { (status as? SlotStatus.Taken)?.contract?.id == it.id },
                    onSign = { signingSlot = slot },
                    onUpgrade = { contract -> viewModel.upgradeToLegacy(contract) },
                    onLongPress = ::openEdit,
                )
            }

            if (state.legacy.isNotEmpty()) {
                EtaSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
                        EtaText(text = "Legacy-Verträge", style = EtaTheme.typography.heading)
                        EtaText(
                            text = if (LocalPointsVisible.current) {
                                "Kosten keinen Slot, zahlen ein Fünftel — und müssen trotzdem " +
                                    "gehalten werden. Zusammen höchstens 2 Punkte am Tag."
                            } else {
                                "Kosten keinen Slot — und müssen trotzdem gehalten werden."
                            },
                            style = EtaTheme.typography.caption,
                            color = EtaTheme.colors.textMuted,
                        )
                        state.legacy.forEach { c -> ContractRow(c, onLongPress = { openEdit(c) }) }
                    }
                }
            }

            // Broken contracts are not listed, at the user's request. What a breach
            // costs is still on the screen where it bites — the slot it locks says
            // so — and one that was carried on after the breach is in its slot.
            if (state.fulfilled.isNotEmpty()) {
                EtaSurface(modifier = Modifier.fillMaxWidth()) {
                    EtaExpander(
                        label = "Abgeschlossen",
                        hint = "Ausgelaufen, ohne gebrochen worden zu sein.",
                        summary = if (state.fulfilled.size == 1) {
                            "1 Vertrag"
                        } else {
                            "${state.fulfilled.size} Verträge"
                        },
                    ) {
                        state.fulfilled.forEach { c ->
                            ContractRow(c, onLongPress = { openEdit(c) })
                        }
                    }
                }
            }

            Spacer(Modifier.size(EtaTheme.spacing.xl))
        }
    }

    editingContract?.let { contract ->
        EditContractDialog(
            contract = contract,
            today = state.today,
            onDismiss = { editingContract = null },
            onSave = { title, conditions, breach ->
                viewModel.editWording(contract, title, conditions, breach)
                editingContract = null
            },
        )
    }

    editRefused?.let { reason ->
        RefusalDialog(reason = reason, onDismiss = { editRefused = null })
    }

    signingSlot?.let { slot ->
        SignContractDialog(
            slot = slot,
            today = state.today,
            refusal = refusal,
            onDismiss = {
                signingSlot = null
                viewModel.dismissRefusal()
            },
            onSign = { title, conditions, breach, effort, signature, weeks ->
                viewModel.sign(
                    slot = slot,
                    title = title,
                    conditions = conditions,
                    breachDefinition = breach,
                    effort = effort,
                    signature = signature,
                    weeks = weeks,
                    onSigned = { signingSlot = null },
                )
            },
        )
    }
}

@Composable
private fun SlotBox(
    slot: Int,
    status: SlotStatus,
    today: LocalDate,
    onReinstate: (Contract) -> Unit,
    upgradable: Boolean,
    onSign: () -> Unit,
    onUpgrade: (Contract) -> Unit,
    onLongPress: (Contract) -> Unit,
) {
    // A locked slot is drawn red **through**, not merely outlined in red: it is
    // not a slot with a warning on it, it is a slot that is out of action. The
    // border alone read as decoration next to the taken slot's accent.
    val locked = status is SlotStatus.Locked || status is SlotStatus.Serving

    EtaSurface(
        modifier = Modifier.fillMaxWidth(),
        color = if (locked) EtaTheme.colors.dangerSoft else EtaTheme.colors.surface,
        borderColor = when (status) {
            is SlotStatus.Taken -> EtaTheme.colors.accent
            is SlotStatus.Locked, is SlotStatus.Serving -> EtaTheme.colors.danger
            SlotStatus.Free -> EtaTheme.colors.border
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(
                text = "Slot ${slot + 1}",
                style = EtaTheme.typography.label,
                color = EtaTheme.colors.textMuted,
            )

            when (status) {
                SlotStatus.Free -> {
                    EtaText(
                        text = "Frei.",
                        style = EtaTheme.typography.body,
                        color = EtaTheme.colors.textMuted,
                    )
                    EtaButton(text = "Vertrag schließen", onClick = onSign)
                }

                is SlotStatus.Taken -> {
                    ContractRow(status.contract, onLongPress = { onLongPress(status.contract) })
                    if (upgradable) {
                        EtaText(
                            text = "Läuft seit einem Monat — kann Legacy werden und den " +
                                "Slot freigeben.",
                            style = EtaTheme.typography.caption,
                            color = EtaTheme.colors.textSecondary,
                        )
                        EtaButton(
                            text = "Zu Legacy machen",
                            style = EtaButtonStyle.Secondary,
                            onClick = { onUpgrade(status.contract) },
                        )
                    }
                }

                is SlotStatus.Locked -> {
                    EtaText(
                        text = "Gesperrt bis ${status.until.formatLong()}.",
                        style = EtaTheme.typography.body,
                        color = EtaTheme.colors.danger,
                    )
                    EtaText(
                        text = "»${status.brokenContract.title}« wurde gebrochen. Die Sperre " +
                            "läuft ab dem Tag des Vertragsschlusses.",
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textMuted,
                    )
                }

                // Broken, and still being held to. The contract is drawn like any
                // other — because it is still one, and still answered for every
                // evening — over the red the breach earned it.
                is SlotStatus.Serving -> {
                    EtaText(
                        text = if (status.decisionDue) {
                            "Sperre abgelaufen — der Vertrag wartet auf eine Entscheidung."
                        } else {
                            "Gebrochen, wird weitergeführt. Gesperrt bis " +
                                "${status.until.formatLong()}."
                        },
                        style = EtaTheme.typography.body,
                        color = EtaTheme.colors.danger,
                    )
                    ContractRow(status.contract, onLongPress = { onLongPress(status.contract) })
                    EtaText(
                        text = if (LocalPointsVisible.current) {
                            "Wird abends weiterhin abgefragt, bringt aber keine Punkte."
                        } else {
                            "Wird abends weiterhin abgefragt."
                        },
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textMuted,
                    )
                    // The way back in before the lock runs out: two weeks kept
                    // without a gap. Counted where the contract is read, so the
                    // run is something to watch grow rather than a surprise.
                    val streak = status.contract.probationStreak(today)
                    if (status.contract.canBeReinstated(today)) {
                        EtaText(
                            text = "$streak Abende in Folge gehalten — der Vertrag kann " +
                                "wieder eingesetzt werden. Er zahlt dann wieder, und die " +
                                "Laufzeit beginnt bei null: bis Legacy braucht es erneut " +
                                "einen vollen Monat.",
                            style = EtaTheme.typography.caption,
                            color = EtaTheme.colors.success,
                        )
                        EtaButton(
                            text = "Wieder einsetzen",
                            onClick = { onReinstate(status.contract) },
                        )
                    } else if (!status.decisionDue) {
                        EtaText(
                            text = "$streak von $REINSTATEMENT_STREAK Abenden in Folge " +
                                "gehalten. Sind es $REINSTATEMENT_STREAK ohne Lücke, " +
                                "lässt er sich wieder einsetzen.",
                            style = EtaTheme.typography.caption,
                            color = EtaTheme.colors.textMuted,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A served-out breach, and the two things left to do with it.
 *
 * The month is over, so the slot is no longer being punished — but it is still
 * held, because the contract in it has been answered for every evening since and
 * nobody has said what happens next. The two answers are genuinely different
 * promises: letting it go frees the slot for something new, taking it up again
 * starts the same words from zero and needs a full month before it can become
 * legacy.
 */
@Composable
private fun ProbationDecisionBox(
    contract: Contract,
    onRestart: () -> Unit,
    onAbandon: () -> Unit,
) {
    EtaSurface(
        modifier = Modifier.fillMaxWidth(),
        borderColor = EtaTheme.colors.warning,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(
                text = "Sperre abgelaufen",
                style = EtaTheme.typography.heading,
                color = EtaTheme.colors.warning,
            )
            EtaText(text = contract.title, style = EtaTheme.typography.bodyStrong)
            EtaText(
                text = "Du hast diesen Vertrag nach dem Bruch weitergeführt. Der Monat ist " +
                    "vorbei: aufkündigen gibt den Slot frei, neu starten behält den Wortlaut " +
                    "und setzt die Laufzeit auf null.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                EtaButton(text = "Neu starten", onClick = onRestart)
                EtaButton(
                    text = "Aufkündigen",
                    style = EtaButtonStyle.Secondary,
                    onClick = onAbandon,
                )
            }
        }
    }
}

@Composable
private fun ExpiringBox(contract: Contract, onExtend: () -> Unit, onEnd: () -> Unit) {
    EtaSurface(
        modifier = Modifier.fillMaxWidth(),
        borderColor = EtaTheme.colors.warning,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(
                text = "Laufzeit erreicht",
                style = EtaTheme.typography.heading,
                color = EtaTheme.colors.warning,
            )
            EtaText(text = contract.title, style = EtaTheme.typography.bodyStrong)
            EtaText(
                text = "Verlängern oder beenden — beendet wird ohne Sperre, das ist kein Bruch.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
                EtaButton(text = "Vier Wochen weiter", onClick = onExtend)
                EtaButton(text = "Beenden", style = EtaButtonStyle.Secondary, onClick = onEnd)
            }
        }
    }
}

/**
 * One contract, as it reads in a slot box or in a list.
 *
 * Long-pressing it is the way to the single wording change it may have. Nothing
 * else on the row moves, which is deliberate: everything a contract does happens
 * in the evening, and this screen is where it is read.
 */
@Composable
private fun ContractRow(contract: Contract, onLongPress: () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxWidth().pointerInput(contract.id) {
            detectTapGestures(onLongPress = { onLongPress() })
        },
        verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EtaText(
                text = contract.title,
                style = EtaTheme.typography.bodyStrong,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            EtaText(
                text = when {
                    // The payout is the one figure on this row; with the points
                    // out of sight the row says only which contract it is.
                    !LocalPointsVisible.current -> ""
                    contract.isOnProbation -> "ohne Punkte"
                    else -> "${formatPoints(contract.dailyPayout)}/Tag"
                },
                style = EtaTheme.typography.label,
                color = when (contract.state) {
                    ContractState.BROKEN, ContractState.PROBATION -> EtaTheme.colors.danger
                    else -> EtaTheme.colors.success
                },
            )
        }
        EtaText(
            text = contract.conditions,
            style = EtaTheme.typography.body,
            color = EtaTheme.colors.textSecondary,
        )
        EtaText(
            text = when (contract.state) {
                ContractState.BROKEN -> "Gebrochen · unterschrieben ${contract.signedOn.formatLong()}"
                ContractState.PROBATION ->
                    "Gebrochen, weitergeführt · Sperre bis " +
                        (contract.slotLockedUntil?.formatLong() ?: "—")

                ContractState.FULFILLED -> "Ausgelaufen · ${contract.endsOn.formatLong()}"
                ContractState.LEGACY -> "Legacy seit ${contract.legacySince?.formatLong() ?: "—"}"
                ContractState.ACTIVE -> "Bis ${contract.endsOn.formatLong()}"
            },
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
        )
        // Drawn rather than printed, and only where it is the promise that is being
        // read: a running contract. What was signed is the strongest thing on the
        // card, and a row of coordinates is not a signature.
        if (contract.state == ContractState.ACTIVE || contract.state == ContractState.LEGACY) {
            SignatureView(signature = contract.signature, height = 40.dp)
        }
        if (contract.editedAt != null) {
            EtaText(
                text = "Bereits einmal geändert — die Laufzeit lief ab dann neu.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
        } else if (contract.isEditable) {
            EtaText(
                text = "Lange drücken, um den Wortlaut einmalig zu ändern.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
        }
    }
}

/** The one-sentence reason a long press led nowhere. */
@Composable
private fun RefusalDialog(reason: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        EtaSurface(
            modifier = Modifier.widthIn(max = 380.dp),
            color = EtaTheme.colors.surfaceRaised,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg)) {
                EtaText(text = "Nicht änderbar", style = EtaTheme.typography.title)
                EtaText(
                    text = reason,
                    style = EtaTheme.typography.body,
                    color = EtaTheme.colors.textSecondary,
                )
                Row {
                    Spacer(Modifier.weight(1f))
                    EtaButton(text = "Verstanden", onClick = onDismiss)
                }
            }
        }
    }
}
