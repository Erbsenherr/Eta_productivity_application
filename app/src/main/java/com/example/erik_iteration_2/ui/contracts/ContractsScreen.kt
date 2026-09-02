package com.example.erik_iteration_2.ui.contracts

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.erik_iteration_2.domain.contract.SlotStatus
import com.example.erik_iteration_2.domain.model.Contract
import com.example.erik_iteration_2.domain.model.ContractState
import com.example.erik_iteration_2.ui.components.ErikButton
import com.example.erik_iteration_2.ui.components.ErikButtonStyle
import com.example.erik_iteration_2.ui.components.ErikScreen
import com.example.erik_iteration_2.ui.components.ErikSurface
import com.example.erik_iteration_2.ui.components.ErikText
import com.example.erik_iteration_2.ui.format.formatLong
import com.example.erik_iteration_2.ui.format.formatPoints
import com.example.erik_iteration_2.ui.theme.ErikTheme

/** The Selbstverträge tab: three slots, the legacy contracts, and what is behind them. */
@Composable
fun ContractsScreen(
    viewModel: ContractsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val refusal by viewModel.refusal.collectAsStateWithLifecycle()
    var signingSlot by remember { mutableStateOf<Int?>(null) }

    ErikScreen(modifier = modifier, bottomInset = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(ErikTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.lg),
        ) {
            Column {
                ErikText(text = "Selbstverträge", style = ErikTheme.typography.display)
                ErikText(
                    text = "Drei Verträge gleichzeitig. Legacy-Verträge zählen nicht mit.",
                    style = ErikTheme.typography.caption,
                    color = ErikTheme.colors.textSecondary,
                )
            }

            state.expiring.forEach { contract ->
                ExpiringBox(
                    contract = contract,
                    onExtend = { viewModel.extend(contract, weeks = 4) },
                    onEnd = { viewModel.fulfil(contract) },
                )
            }

            state.slots.forEachIndexed { slot, status ->
                SlotBox(
                    slot = slot,
                    status = status,
                    upgradable = state.upgradable.any { (status as? SlotStatus.Taken)?.contract?.id == it.id },
                    onSign = { signingSlot = slot },
                    onUpgrade = { contract -> viewModel.upgradeToLegacy(contract) },
                )
            }

            if (state.legacy.isNotEmpty()) {
                ErikSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.md)) {
                        ErikText(text = "Legacy-Verträge", style = ErikTheme.typography.heading)
                        ErikText(
                            text = "Kosten keinen Slot, zahlen ein Fünftel — und müssen trotzdem " +
                                "gehalten werden. Zusammen höchstens 2 Punkte am Tag.",
                            style = ErikTheme.typography.caption,
                            color = ErikTheme.colors.textMuted,
                        )
                        state.legacy.forEach { ContractRow(it) }
                    }
                }
            }

            if (state.closed.isNotEmpty()) {
                ErikSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.md)) {
                        ErikText(text = "Abgeschlossen", style = ErikTheme.typography.heading)
                        state.closed.forEach { ContractRow(it) }
                    }
                }
            }

            Spacer(Modifier.size(ErikTheme.spacing.xl))
        }
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
    upgradable: Boolean,
    onSign: () -> Unit,
    onUpgrade: (Contract) -> Unit,
) {
    ErikSurface(
        modifier = Modifier.fillMaxWidth(),
        borderColor = when (status) {
            is SlotStatus.Taken -> ErikTheme.colors.accent
            is SlotStatus.Locked -> ErikTheme.colors.danger
            SlotStatus.Free -> ErikTheme.colors.border
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.md)) {
            ErikText(
                text = "Slot ${slot + 1}",
                style = ErikTheme.typography.label,
                color = ErikTheme.colors.textMuted,
            )

            when (status) {
                SlotStatus.Free -> {
                    ErikText(
                        text = "Frei.",
                        style = ErikTheme.typography.body,
                        color = ErikTheme.colors.textMuted,
                    )
                    ErikButton(text = "Vertrag schließen", onClick = onSign)
                }

                is SlotStatus.Taken -> {
                    ContractRow(status.contract)
                    if (upgradable) {
                        ErikText(
                            text = "Läuft seit einem Monat — kann Legacy werden und den " +
                                "Slot freigeben.",
                            style = ErikTheme.typography.caption,
                            color = ErikTheme.colors.textSecondary,
                        )
                        ErikButton(
                            text = "Zu Legacy machen",
                            style = ErikButtonStyle.Secondary,
                            onClick = { onUpgrade(status.contract) },
                        )
                    }
                }

                is SlotStatus.Locked -> {
                    ErikText(
                        text = "Gesperrt bis ${status.until.formatLong()}.",
                        style = ErikTheme.typography.body,
                        color = ErikTheme.colors.danger,
                    )
                    ErikText(
                        text = "»${status.brokenContract.title}« wurde gebrochen. Die Sperre " +
                            "läuft ab dem Tag des Vertragsschlusses.",
                        style = ErikTheme.typography.caption,
                        color = ErikTheme.colors.textMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpiringBox(contract: Contract, onExtend: () -> Unit, onEnd: () -> Unit) {
    ErikSurface(
        modifier = Modifier.fillMaxWidth(),
        borderColor = ErikTheme.colors.warning,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.md)) {
            ErikText(
                text = "Laufzeit erreicht",
                style = ErikTheme.typography.heading,
                color = ErikTheme.colors.warning,
            )
            ErikText(text = contract.title, style = ErikTheme.typography.bodyStrong)
            ErikText(
                text = "Verlängern oder beenden — beendet wird ohne Sperre, das ist kein Bruch.",
                style = ErikTheme.typography.caption,
                color = ErikTheme.colors.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(ErikTheme.spacing.sm)) {
                ErikButton(text = "Vier Wochen weiter", onClick = onExtend)
                ErikButton(text = "Beenden", style = ErikButtonStyle.Secondary, onClick = onEnd)
            }
        }
    }
}

@Composable
private fun ContractRow(contract: Contract) {
    Column(verticalArrangement = Arrangement.spacedBy(ErikTheme.spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ErikText(
                text = contract.title,
                style = ErikTheme.typography.bodyStrong,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ErikText(
                text = "${formatPoints(contract.dailyPayout)}/Tag",
                style = ErikTheme.typography.label,
                color = when (contract.state) {
                    ContractState.BROKEN -> ErikTheme.colors.danger
                    else -> ErikTheme.colors.success
                },
            )
        }
        ErikText(
            text = contract.conditions,
            style = ErikTheme.typography.body,
            color = ErikTheme.colors.textSecondary,
        )
        ErikText(
            text = when (contract.state) {
                ContractState.BROKEN -> "Gebrochen · unterschrieben ${contract.signedOn.formatLong()}"
                ContractState.FULFILLED -> "Ausgelaufen · ${contract.endsOn.formatLong()}"
                ContractState.LEGACY -> "Legacy seit ${contract.legacySince?.formatLong() ?: "—"}"
                ContractState.ACTIVE -> "Bis ${contract.endsOn.formatLong()} · ${contract.signature}"
            },
            style = ErikTheme.typography.caption,
            color = ErikTheme.colors.textMuted,
        )
    }
}
