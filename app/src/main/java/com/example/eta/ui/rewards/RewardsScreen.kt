package com.example.eta.ui.rewards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eta.domain.reward.rolesOf
import com.example.eta.domain.reward.claimsOf
import com.example.eta.domain.reward.RewardRole
import com.example.eta.domain.model.Reward
import com.example.eta.ui.components.ConfirmDialog
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaScreen
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.components.rememberFold
import com.example.eta.ui.format.formatLong
import com.example.eta.ui.format.formatPoints
import com.example.eta.ui.lists.RecurringCreateDialog
import com.example.eta.ui.theme.EtaTheme
import kotlin.math.roundToInt
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The Belohn-o-mat tab: long-term rewards, in the order they are worked off.
 *
 * Only the first one still filling takes the evening's points. The order is the
 * user's to change at any time; what a reward has earned stays with it.
 */
@Composable
fun RewardsScreen(viewModel: RewardsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The editor is three windows that take turns — the reward, its binding menu,
    // and a new standing task made from that menu. One draft carries through all
    // three, and only one of them is ever open: a dialog opened from inside a
    // dialog is a window on top of a window.
    var draft by remember { mutableStateOf<RewardDraft?>(null) }
    var stage by remember { mutableStateOf(EditorStage.REWARD) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val archive = rememberFold("rewards.redeemed")

    EtaScreen(bottomInset = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(EtaTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg),
        ) {
            Column {
                EtaText(text = "Belohn-o-mat", style = EtaTheme.typography.title)
                EtaText(
                    text = "Was du dir erarbeitest. Jeden Abend fließen die Punkte des " +
                        "Tages in die oberste ungebundene Belohnung; gebundene sammeln " +
                        "aus ihren eigenen Aufgaben.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
                )
            }

            if (state.open.isEmpty()) {
                EtaSurface(modifier = Modifier.fillMaxWidth()) {
                    EtaText(
                        text = "Noch keine Belohnung. Gib ihr einen Namen und einen Preis in " +
                            "Punkten — der Abend füllt sie dann nach und nach.",
                        style = EtaTheme.typography.body,
                        color = EtaTheme.colors.textMuted,
                    )
                }
            } else {
                RewardList(
                    rows = state.open,
                    onOpen = { id ->
                        state.open.firstOrNull { it.reward.id == id }?.let { row ->
                            draft = RewardDraft.of(row, state)
                            stage = EditorStage.REWARD
                        }
                    },
                    onDelete = { deleting = it },
                    onRedeem = viewModel::redeem,
                    onReorder = viewModel::reorder,
                )
                EtaText(
                    text = "Antippen zum Bearbeiten, am ≡ ziehen zum Umsortieren, gedrückt " +
                        "halten zum Löschen. Erarbeitete Punkte bleiben bei ihrer Belohnung. " +
                        "Die Reihenfolge zählt für ungebundene Belohnungen; gebundene " +
                        "sammeln an jeder Stelle.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
            }

            EtaButton(
                text = "Belohnung erstellen",
                onClick = {
                    draft = RewardDraft()
                    stage = EditorStage.REWARD
                },
            )

            if (state.redeemed.isNotEmpty()) {
                RedeemedBox(
                    rows = state.redeemed,
                    open = archive.open,
                    onToggle = archive::toggle,
                )
            }

            Spacer(Modifier.size(EtaTheme.spacing.xl))
        }
    }

    draft?.let { current ->
        when (stage) {
            EditorStage.REWARD -> RewardEditDialog(
                draft = current,
                groups = state.groups,
                onChange = { draft = it },
                onBind = { stage = EditorStage.BINDING },
                onDismiss = { draft = null },
                onSave = {
                    viewModel.save(current.id, current.name.trim(), current.cost, current.itemIds)
                    draft = null
                },
            )

            EditorStage.BINDING -> BindingDialog(
                groups = state.groups,
                // What other rewards already hold: a task is bound to one.
                taken = claimsOf(state.open.filter { it.reward.id != current.id }.map { it.target })
                    .mapValues { it.value.name },
                selected = current.itemIds,
                onChange = { draft = current.copy(itemIds = it) },
                onNewTask = { stage = EditorStage.NEW_TASK },
                onDone = { stage = EditorStage.REWARD },
            )

            EditorStage.NEW_TASK -> RecurringCreateDialog(
                definitions = state.definitions,
                onDismiss = { stage = EditorStage.BINDING },
                onCreate = { name, note, attributes ->
                    viewModel.createTask(name, note, attributes) { ids ->
                        // Made in order to be bound, so it comes back ticked.
                        draft = draft?.let { it.copy(itemIds = it.itemIds + ids) }
                    }
                    stage = EditorStage.BINDING
                },
            )
        }
    }

    deleting?.let { id ->
        val row = state.open.firstOrNull { it.reward.id == id }
        if (row == null) {
            deleting = null
        } else {
            ConfirmDialog(
                title = "Wirklich löschen?",
                message = if (row.reward.progress > 0.0) {
                    "»${row.reward.name}« wird entfernt, und die " +
                        "${formatPoints(row.reward.progress)} Punkte, die schon darin " +
                        "stecken, verfallen. Sie gehen an keine andere Belohnung über."
                } else {
                    "»${row.reward.name}« wird entfernt."
                },
                confirm = "Löschen",
                onDismiss = { deleting = null },
                onConfirm = {
                    viewModel.delete(id)
                    deleting = null
                },
            )
        }
    }
}

/** Which of the editor's three windows is open. */
internal enum class EditorStage { REWARD, BINDING, NEW_TASK }

/**
 * The list, reordered by dragging the handle.
 *
 * The same shape as the Growth-Tasks tab's list, with one difference: the drag
 * starts on the **≡** at once rather than after a long press on the row, because
 * the long press on a reward already means "löschen" and one gesture cannot mean
 * both. Rearranged in local state while the finger is down and written once on
 * release.
 *
 * Everything the gestures read goes through a `State` object or
 * `rememberUpdatedState`, and the rows are named by id — a `pointerInput` lambda
 * does not restart on recomposition.
 */
@Composable
private fun RewardList(
    rows: List<RewardRow>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRedeem: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    // The order being shown: the stored one, except while a drag rearranges it.
    var order by remember(rows.map { it.reward.id }) { mutableStateOf(rows.map { it.reward.id }) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    val dragOffset = remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<String, Int>() }
    val gap = with(LocalDensity.current) { EtaTheme.spacing.sm.toPx() }

    val byId = rows.associateBy { it.reward.id }
    // Off the order **as it is shown**, so the status moves with the card while
    // it is being dragged rather than catching up once it is let go.
    val roles = rolesOf(order.mapNotNull { byId[it]?.target })
    val currentOrder = rememberUpdatedState(order)
    val commit = rememberUpdatedState(onReorder)
    val open = rememberUpdatedState(onOpen)
    val delete = rememberUpdatedState(onDelete)
    val step = rememberUpdatedState(gap)

    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
        order.forEach { id ->
            val row = byId[id]
            if (row != null) key(id) {
                val dragging = draggingId == id
                RewardCard(
                    row = row,
                    role = roles[id] ?: RewardRole.WAITING,
                    dragging = dragging,
                    onRedeem = { onRedeem(id) },
                    modifier = Modifier
                        .onSizeChanged { heights[id] = it.height }
                        .offset {
                            IntOffset(0, if (dragging) dragOffset.floatValue.roundToInt() else 0)
                        }
                        .pointerInput(id) {
                            detectTapGestures(
                                onTap = { open.value(id) },
                                onLongPress = { delete.value(id) },
                            )
                        },
                    handle = Modifier.reorderHandle(id) {
                        detectDragGestures(
                            onDragStart = {
                                draggingId = id
                                dragOffset.floatValue = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset.floatValue += amount.y
                                // This row plus the gap under it: what the finger
                                // has to travel for a swap to be meant.
                                val pitch = (heights[id] ?: 0) + step.value
                                if (pitch <= 0f) return@detectDragGestures
                                val moved = (dragOffset.floatValue / pitch).roundToInt()
                                if (moved == 0) return@detectDragGestures

                                val list = currentOrder.value
                                val from = list.indexOf(id)
                                if (from < 0) return@detectDragGestures
                                val to = (from + moved).coerceIn(0, list.size - 1)
                                if (to == from) return@detectDragGestures

                                order = list.toMutableList().apply { add(to, removeAt(from)) }
                                // The row now sits where the finger reached, so
                                // the distance the list moved it comes off the
                                // offset it is still being drawn with.
                                dragOffset.floatValue -= (to - from) * pitch
                            },
                            onDragEnd = {
                                draggingId = null
                                dragOffset.floatValue = 0f
                                commit.value(currentOrder.value)
                            },
                            onDragCancel = {
                                draggingId = null
                                dragOffset.floatValue = 0f
                            },
                        )
                    },
                )
            }
        }
    }
}

/**
 * The ≡ of a row: [drag], and nothing of the row's own gestures.
 *
 * The second detector swallows the press before the row sees it. Without it a
 * thumb resting on the handle for a moment before moving would be a long press
 * on the row — and the long press deletes.
 */
private fun Modifier.reorderHandle(
    id: String,
    drag: suspend androidx.compose.ui.input.pointer.PointerInputScope.() -> Unit,
): Modifier = this
    // Keyed by the id alone: [drag] is a fresh lambda on every recomposition, and
    // a drag recomposes the list at every swap — keying on it would cancel the
    // gesture it is in the middle of.
    .pointerInput(id) { drag() }
    .pointerInput(id) {
        awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume() }
    }

/**
 * One reward.
 *
 * The card **is** the bar: what has been earned washes in from the left, the way
 * a held-down cancellation fills its row, so how far along a reward is can be
 * read off the list without reading a number. The numbers are there all the same
 * — earned and price, always, as asked — with the name on the left, the handle on
 * the right and nothing else.
 *
 * Three states, told apart by colour alone: the one being filled wears the
 * accent, one earned in full turns green and offers "Einlösen", and everything
 * waiting further down stays plain.
 */
@Composable
private fun RewardCard(
    row: RewardRow,
    role: RewardRole,
    dragging: Boolean,
    onRedeem: () -> Unit,
    modifier: Modifier = Modifier,
    handle: Modifier = Modifier,
) {
    val reward = row.reward
    val shape = EtaTheme.shapes.medium
    val full = reward.isFull

    // Slowly, so a card that gained something since it was last looked at is
    // seen filling rather than found filled.
    val fraction by animateFloatAsState(
        targetValue = reward.fraction,
        animationSpec = tween(FILL_MILLIS),
        label = "rewardFill",
    )
    val wash = if (full) EtaTheme.colors.successSoft else EtaTheme.colors.accentSoft
    val edge = when {
        dragging -> EtaTheme.colors.accent
        full -> EtaTheme.colors.success
        role == RewardRole.FILLING || role == RewardRole.COLLECTING -> EtaTheme.colors.accent
        else -> EtaTheme.colors.border
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (dragging) EtaTheme.colors.surfaceRaised else EtaTheme.colors.surface)
            .drawBehind { drawRect(color = wash, size = size.copy(width = size.width * fraction)) }
            .border(1.dp, edge, shape)
            .padding(EtaTheme.spacing.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    EtaText(
                        text = reward.name,
                        style = EtaTheme.typography.bodyStrong,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    EtaText(
                        text = when {
                            full -> "Erarbeitet — bereit zum Einlösen"
                            role == RewardRole.FILLING -> "Füllt sich gerade"
                            role == RewardRole.COLLECTING -> "Sammelt aus gebundenen Aufgaben"
                            else -> "Wartet"
                        },
                        style = EtaTheme.typography.caption,
                        color = when {
                            full -> EtaTheme.colors.success
                            role == RewardRole.FILLING || role == RewardRole.COLLECTING ->
                                EtaTheme.colors.accent
                            else -> EtaTheme.colors.textMuted
                        },
                    )
                }
                Spacer(Modifier.size(EtaTheme.spacing.md))
                Column(horizontalAlignment = Alignment.End) {
                    EtaText(
                        text = "${formatPoints(reward.progress)} / ${formatPoints(reward.cost)}",
                        style = EtaTheme.typography.heading,
                    )
                    EtaText(
                        text = "${(reward.fraction * 100).toInt()} %",
                        style = EtaTheme.typography.caption,
                        color = EtaTheme.colors.textMuted,
                    )
                }
                EtaText(
                    text = "≡",
                    style = EtaTheme.typography.title,
                    color = EtaTheme.colors.textMuted,
                    // Padded on every side: the glyph is small and a thumb is not.
                    modifier = handle.padding(
                        start = EtaTheme.spacing.lg,
                        top = EtaTheme.spacing.sm,
                        bottom = EtaTheme.spacing.sm,
                    ),
                )
            }

            if (row.boundTo.isNotEmpty()) {
                EtaText(
                    text = "Zählt nur: ${row.boundTo.joinToString(", ")}",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (full) {
                EtaButton(text = "Einlösen", onClick = onRedeem)
            }
        }
    }
}

/** How long a card takes to wash in what it gained. */
private const val FILL_MILLIS = 900

/** What has been taken, folded away: a record, not a list to work on. */
@Composable
private fun RedeemedBox(rows: List<RewardRow>, open: Boolean, onToggle: () -> Unit) {
    val toggle = rememberUpdatedState(onToggle)

    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures(onTap = { toggle.value() }) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EtaText(
                    text = "Eingelöst",
                    style = EtaTheme.typography.heading,
                    modifier = Modifier.weight(1f),
                )
                EtaText(
                    text = rows.size.toString(),
                    style = EtaTheme.typography.title,
                    color = EtaTheme.colors.success,
                )
                Spacer(Modifier.size(EtaTheme.spacing.sm))
                EtaText(
                    text = if (open) "▴" else "▾",
                    style = EtaTheme.typography.label,
                    color = EtaTheme.colors.textMuted,
                )
            }
            if (open) {
                rows.forEach { row ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            EtaText(
                                text = row.reward.name,
                                style = EtaTheme.typography.body,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            EtaText(
                                text = redeemedOn(row.reward),
                                style = EtaTheme.typography.caption,
                                color = EtaTheme.colors.textMuted,
                            )
                        }
                        EtaText(
                            text = formatPoints(row.reward.cost),
                            style = EtaTheme.typography.bodyStrong,
                            color = EtaTheme.colors.success,
                        )
                    }
                }
            }
        }
    }
}

private fun redeemedOn(reward: Reward): String =
    reward.redeemedAt
        ?.toLocalDateTime(TimeZone.currentSystemDefault())
        ?.date
        ?.formatLong()
        .orEmpty()
