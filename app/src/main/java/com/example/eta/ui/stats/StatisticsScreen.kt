package com.example.eta.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.growth.hasQuantity
import com.example.eta.domain.growth.isGrowthTask
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.planning.sleepStretches
import com.example.eta.domain.stats.PensumPoint
import com.example.eta.domain.stats.Share
import com.example.eta.domain.stats.StatRange
import com.example.eta.domain.stats.StatSection
import com.example.eta.domain.stats.isOver
import com.example.eta.domain.stats.monthsFitBetter
import com.example.eta.domain.stats.overview
import com.example.eta.domain.stats.quantityPensum
import com.example.eta.domain.stats.showsStat
import com.example.eta.domain.stats.taskStats
import com.example.eta.domain.stats.tasksByName
import com.example.eta.domain.stats.timerPensum
import com.example.eta.ui.components.EtaButton
import com.example.eta.ui.components.EtaButtonStyle
import com.example.eta.ui.components.EtaScreen
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.format.formatShort
import com.example.eta.ui.theme.EtaTheme
import com.example.eta.ui.theme.colorOf
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalDate

/**
 * The Statistiken tab: what the plan and the days that came of it add up to.
 *
 * An info box over the whole of it, then one fold per kind of task, and inside
 * that one fold per task — each with a range of its own, since "how has this
 * gone lately" and "how has it gone altogether" are asked of one task at a time.
 *
 * Three levels, each told from the next by its ground (step 47): a card of the
 * page is a surface with a coloured edge ([StatCard]), what stands inside one is
 * a tile on the page's own background ([StatTile]) — a figure of the overview,
 * or one task —, and inside a task the figures are lines parted by a hairline.
 *
 * Which of the contents are drawn is the settings' doing (`StatSection`).
 */
@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    // The history is read once and held; a visit to the tab reads it again.
    LaunchedEffect(Unit) { viewModel.refresh() }

    val setup = data.setup
    fun shows(section: StatSection) = setup?.showsStat(section) ?: true

    EtaScreen(bottomInset = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(EtaTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.lg),
        ) {
            Column {
                EtaText(text = "Statistiken", style = EtaTheme.typography.title)
                EtaText(
                    text = "Gezählt werden abgeschlossene Tage — heute läuft noch.",
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textSecondary,
                )
            }

            if (!data.loaded) {
                EtaText(
                    text = "Wird berechnet …",
                    style = EtaTheme.typography.body,
                    color = EtaTheme.colors.textMuted,
                )
                return@Column
            }

            val overviewSections = listOf(
                StatSection.WAKE, StatSection.BED, StatSection.PLANNED,
                StatSection.COMPLETED, StatSection.CATEGORIES,
            )
            if (overviewSections.any(::shows)) {
                OverviewBox(data = data, shows = ::shows)
            }

            val tasks = remember(data.definitions) { tasksByName(data.definitions) }
            // The occurrences of each definition, once, for both lists below.
            val byItem = remember(data.blocks) { data.blocks.groupBy { it.item.id } }

            if (shows(StatSection.RECURRING)) {
                FoldCard(
                    title = "Wiederkehrende Tasks",
                    count = tasks.size,
                    tint = EtaTheme.colors.calendar,
                ) {
                    if (tasks.isEmpty()) NothingYet()
                    tasks.forEach { rows ->
                        key(rows.first().normalizedName) {
                            TaskFold(rows = rows, byItem = byItem, today = data.today, plot = false)
                        }
                    }
                }
            }

            if (shows(StatSection.GOALS)) {
                val goals = tasks.filter { rows -> rows.any { it.timerTask || it.hasQuantity } }
                FoldCard(
                    title = "Timer- und Inkrement-Tasks",
                    count = goals.size,
                    tint = EtaTheme.colors.success,
                ) {
                    if (goals.isEmpty()) NothingYet()
                    goals.forEach { rows ->
                        // A key of its own: the same task may stand in the fold above.
                        key("goal:" + rows.first().normalizedName) {
                            TaskFold(rows = rows, byItem = byItem, today = data.today, plot = true)
                        }
                    }
                }
            }
            Spacer(Modifier.size(EtaTheme.spacing.xl))
        }
    }
}

private fun countLabel(count: Int) = when (count) {
    0 -> "Keine"
    1 -> "1 Aufgabe"
    else -> "$count Aufgaben"
}

@Composable
private fun NothingYet() {
    EtaText(
        text = "Noch keine.",
        style = EtaTheme.typography.body,
        color = EtaTheme.colors.textMuted,
    )
}

// --- the three grounds ---------------------------------------------------------------

/** How wide the coloured edge of a card is. */
private val EDGE = 5.dp

/**
 * A card of the page: a surface with an edge in [tint] down its left side, which
 * is what tells one card from the next while scrolling past.
 */
@Composable
private fun StatCard(tint: Color, content: @Composable ColumnScope.() -> Unit) {
    val shape = EtaTheme.shapes.medium
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(EtaTheme.colors.surface)
            .drawBehind { drawRect(color = tint, size = Size(EDGE.toPx(), size.height)) }
            .border(1.dp, EtaTheme.colors.border, shape)
            .padding(
                start = EtaTheme.spacing.lg + EDGE,
                top = EtaTheme.spacing.lg,
                end = EtaTheme.spacing.lg,
                bottom = EtaTheme.spacing.lg,
            ),
        verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md),
        content = content,
    )
}

/** A [StatCard] that folds: its heading, how many tasks it holds, and a ▾/▴. */
@Composable
private fun FoldCard(
    title: String,
    count: Int,
    tint: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    StatCard(tint = tint) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { expanded = !expanded },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                EtaText(text = title, style = EtaTheme.typography.heading)
                EtaText(
                    text = countLabel(count),
                    style = EtaTheme.typography.caption,
                    color = EtaTheme.colors.textMuted,
                )
            }
            Spacer(Modifier.size(EtaTheme.spacing.sm))
            EtaText(
                text = if (expanded) "▴" else "▾",
                style = EtaTheme.typography.label,
                color = EtaTheme.colors.textMuted,
            )
        }
        if (expanded) content()
    }
}

/** What stands inside a card: a box on the page's own ground, set into the surface. */
@Composable
private fun StatTile(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = EtaTheme.shapes.small
    Column(
        modifier = modifier
            .background(EtaTheme.colors.background, shape)
            .border(1.dp, EtaTheme.colors.border, shape)
            .padding(EtaTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs),
        content = content,
    )
}

/** A share drawn: as much of the track filled as the percentage says. */
@Composable
private fun ShareBar(share: Share, tint: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(EtaTheme.colors.border, EtaTheme.shapes.pill),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(((share.percent ?: 0) / 100f).coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(tint, EtaTheme.shapes.pill),
        )
    }
}

@Composable
private fun Hairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(EtaTheme.colors.border),
    )
}

// --- the overview ----------------------------------------------------------------------

/** The four figures over everything, and the hours per category. */
@Composable
private fun OverviewBox(data: StatisticsData, shows: (StatSection) -> Boolean) {
    var rangeName by rememberSaveable { mutableStateOf(StatRange.WEEK.name) }
    val range = StatRange.valueOf(rangeName)

    val figures = remember(data, range) {
        val from = range.from(data.today)
        val setup = data.setup
        overview(
            // Days that are over, in the range.
            blocks = data.blocks.filter { it.block.date < data.today && (from == null || it.block.date >= from) },
            logs = data.logs.filter { it.date < data.today && (from == null || it.date >= from) } +
                // This morning has been answered or it has not; it is over either way.
                data.logs.filter { it.date == data.today }.map { it.copy(bedOnTime = null) },
            sleepMinutesOn = { date ->
                setup?.sleepStretches(date, data.overrides)?.sumOf { it.last - it.first + 1 } ?: 0
            },
        )
    }

    // The figures that are switched on, two to a row; an odd one out takes the
    // whole width rather than leaving half a row empty.
    val tiles = buildList<@Composable (Modifier) -> Unit> {
        if (shows(StatSection.WAKE)) add { modifier ->
            ShareTile(
                label = "Pünktlich aus dem Bett",
                share = figures.wake,
                detail = { "${it.hits} von ${it.of} Morgen" },
                empty = "Noch kein Morgen beantwortet.",
                tint = EtaTheme.colors.accent,
                modifier = modifier,
            )
        }
        if (shows(StatSection.BED)) add { modifier ->
            ShareTile(
                label = "Pünktlich ins Bett",
                share = figures.bed,
                detail = { "${it.hits} von ${it.of} Abenden" },
                empty = "Noch kein Abend beantwortet.",
                tint = EtaTheme.colors.accent,
                modifier = modifier,
            )
        }
        if (shows(StatSection.PLANNED)) add { modifier ->
            ShareTile(
                label = "Zeit eingeplant",
                share = figures.planned,
                detail = {
                    "${it.hits.minutes.formatShort()} von ${it.of.minutes.formatShort()} wacher Zeit"
                },
                empty = "Noch kein abgeschlossener Tag.",
                tint = EtaTheme.colors.calendar,
                modifier = modifier,
            )
        }
        if (shows(StatSection.COMPLETED)) add { modifier ->
            ShareTile(
                label = "Vorgenommene Tasks abgeschlossen",
                share = figures.completed,
                detail = { "${it.hits} von ${it.of}" },
                empty = "Noch nichts vorgenommen.",
                tint = EtaTheme.colors.success,
                modifier = modifier,
            )
        }
    }

    StatCard(tint = EtaTheme.colors.accent) {
        EtaText(text = "Überblick", style = EtaTheme.typography.heading)
        RangeChips(selected = range, onSelect = { rangeName = it.name })

        tiles.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm),
            ) {
                pair.forEach { tile -> tile(Modifier.weight(1f).fillMaxHeight()) }
            }
        }

        if (shows(StatSection.CATEGORIES)) {
            CategoryTile(minutes = figures.categoryMinutes)
        }
    }
}

/** One figure of the overview: what it is, the percentage large, its bar, and of what. */
@Composable
private fun ShareTile(
    label: String,
    share: Share,
    detail: (Share) -> String,
    empty: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    StatTile(modifier = modifier) {
        EtaText(
            text = label,
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textSecondary,
        )
        EtaText(
            text = share.percent?.let { "$it %" } ?: "—",
            style = EtaTheme.typography.title,
            color = if (share.of == 0) EtaTheme.colors.textMuted else tint,
        )
        ShareBar(share = share, tint = tint)
        EtaText(
            text = if (share.of == 0) empty else detail(share),
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
        )
    }
}

/** "Zeit je Kategorie": one bar cut into the three colours, and the hours under it. */
@Composable
private fun CategoryTile(minutes: Map<Category, Int>) {
    val rows = listOf(
        Category.FOKUS to "Fokus",
        Category.NEBENBEI to "Nebenbei",
        Category.ACHTSAM to "Achtsam",
    )
    StatTile(modifier = Modifier.fillMaxWidth()) {
        EtaText(
            text = "Zeit je Kategorie",
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textSecondary,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(EtaTheme.shapes.pill)
                .background(EtaTheme.colors.border),
        ) {
            rows.forEach { (category, _) ->
                val spent = minutes[category] ?: 0
                if (spent > 0) {
                    Box(
                        Modifier
                            .weight(spent.toFloat())
                            .fillMaxHeight()
                            .background(colorOf(category)),
                    )
                }
            }
        }
        rows.forEach { (category, name) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(colorOf(category))
                Spacer(Modifier.size(EtaTheme.spacing.sm))
                EtaText(
                    text = name,
                    style = EtaTheme.typography.body,
                    color = EtaTheme.colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                EtaText(
                    text = (minutes[category] ?: 0).minutes.formatShort(),
                    style = EtaTheme.typography.bodyStrong,
                )
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        Modifier
            .size(10.dp)
            .background(color, CircleShape),
    )
}

/** The four ranges, side by side; the chosen one is the filled button. */
@Composable
private fun RangeChips(
    selected: StatRange,
    onSelect: (StatRange) -> Unit,
    allLabel: String = StatRange.ALL.label,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(EtaTheme.spacing.sm),
    ) {
        StatRange.entries.forEach { range ->
            EtaButton(
                text = if (range == StatRange.ALL) allLabel else range.label,
                style = if (range == selected) EtaButtonStyle.Primary else EtaButtonStyle.Secondary,
                onClick = { onSelect(range) },
            )
        }
    }
}

// --- one task ----------------------------------------------------------------------------

/**
 * One task: its own tile, its own fold, its own range, four figures — and, with
 * [plot], the button that draws how its workload moved.
 *
 * [rows] are the definitions that are this task (one per weekday); their
 * occurrences are counted together. The dot is the task's colour, the one it
 * wears in the planner.
 */
@Composable
private fun TaskFold(
    rows: List<Item>,
    byItem: Map<String, List<BlockWithItem>>,
    today: LocalDate,
    plot: Boolean,
) {
    val item = rows.first()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    StatTile(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { expanded = !expanded },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(colorOf(item))
            Spacer(Modifier.size(EtaTheme.spacing.sm))
            EtaText(
                text = item.name,
                style = EtaTheme.typography.bodyStrong,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(EtaTheme.spacing.sm))
            EtaText(
                text = if (expanded) "▴" else "▾",
                style = EtaTheme.typography.label,
                color = EtaTheme.colors.textMuted,
            )
        }
        if (!expanded) return@StatTile

        Column(
            modifier = Modifier.padding(top = EtaTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md),
        ) {
            var rangeName by rememberSaveable { mutableStateOf(StatRange.MONTH.name) }
            val range = StatRange.valueOf(rangeName)
            RangeChips(
                selected = range,
                onSelect = { rangeName = it.name },
                allLabel = "Seit Erstellung",
            )

            val all = remember(rows, byItem) { rows.flatMap { byItem[it.id].orEmpty() }.map { it.block } }
            val from = range.from(today)
            val inRange = remember(all, from) { all.filter { from == null || it.date >= from } }
            val stats = remember(inRange, today) {
                taskStats(
                    blocks = inRange.filter { it.isOver(today) },
                    dynamic = item.isGrowthTask && item.growthDynamic,
                )
            }

            StatLine(label = "Investierte Zeit", value = stats.time.formatShort())
            Hairline()
            StatLine(
                label = "Ausgefallen",
                share = stats.skipped,
                tint = EtaTheme.colors.danger,
                detail = stats.skipped.takeIf { it.of > 0 }?.let { "${it.hits} von ${it.of} Terminen" },
            )
            Hairline()
            StatLine(
                label = "Plan geändert",
                share = stats.changed,
                tint = EtaTheme.colors.warning,
                detail = if (stats.changed.of == 0) {
                    "Wird erst für Termine ab diesem Update mitgeschrieben."
                } else {
                    "${stats.changed.hits} von ${stats.changed.of} Terminen — verlängert, gekürzt " +
                        "oder verschoben."
                },
            )
            Hairline()
            StatLine(
                label = "Planmäßig abgeschlossen",
                share = stats.asPlanned,
                tint = EtaTheme.colors.success,
                detail = stats.asPlanned.takeIf { it.of > 0 }?.let { "${it.hits} von ${it.of} Terminen" },
            )

            if (plot) {
                var plotting by rememberSaveable { mutableStateOf(false) }
                EtaButton(
                    text = if (plotting) "Verlauf ausblenden" else "Verlauf des Pensums",
                    style = EtaButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { plotting = !plotting },
                )
                if (plotting) {
                    // The earliest thing known about the task is where "seit Erstellung" begins.
                    val first = all.minOfOrNull { it.date } ?: today
                    val start = from ?: first
                    if (item.timerTask) {
                        val points = remember(inRange, item) {
                            timerPensum(inRange.filter { it.date <= today && !it.isDiscarded }, item)
                        }
                        PlotBlock(
                            title = "Timer-Zeit je Termin",
                            points = points,
                            from = start,
                            to = today,
                            format = { it.minutes.formatShort() },
                        )
                    }
                    if (item.hasQuantity) {
                        val points = remember(inRange, item, today) { quantityPensum(inRange, item, today) }
                        PlotBlock(
                            title = "Anzahl je Termin",
                            points = points,
                            from = start,
                            to = today,
                            format = { it.toInt().toString() },
                            note = "Die Anzahl wird erst seit diesem Update je Termin mitgeschrieben.",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlotBlock(
    title: String,
    points: List<PensumPoint>,
    from: LocalDate,
    to: LocalDate,
    format: (Double) -> String,
    note: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
        EtaText(text = title, style = EtaTheme.typography.bodyStrong)
        if (points.none { it.date >= from && it.date <= to }) {
            EtaText(
                text = "In diesem Zeitraum gibt es noch keine Termine.",
                style = EtaTheme.typography.caption,
                color = EtaTheme.colors.textMuted,
            )
        } else {
            PensumChart(
                points = points,
                from = from,
                to = to,
                months = monthsFitBetter(from, to),
                format = format,
            )
        }
        if (note != null) {
            EtaText(text = note, style = EtaTheme.typography.caption, color = EtaTheme.colors.textMuted)
        }
    }
}

/** A figure that is not a share: its name and what it comes to. */
@Composable
private fun StatLine(label: String, value: String) {
    Row {
        EtaText(text = label, style = EtaTheme.typography.body, modifier = Modifier.weight(1f))
        EtaText(text = value, style = EtaTheme.typography.bodyStrong)
    }
}

/** A share of a task's occurrences, in the colour of what it says about them. */
@Composable
private fun StatLine(label: String, share: Share, tint: Color, detail: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
        Row {
            EtaText(text = label, style = EtaTheme.typography.body, modifier = Modifier.weight(1f))
            EtaText(
                text = share.percent?.let { "$it %" } ?: "—",
                style = EtaTheme.typography.bodyStrong,
                color = if (share.of == 0) EtaTheme.colors.textMuted else tint,
            )
        }
        ShareBar(share = share, tint = tint)
        if (detail != null) {
            EtaText(text = detail, style = EtaTheme.typography.caption, color = EtaTheme.colors.textMuted)
        }
    }
}
