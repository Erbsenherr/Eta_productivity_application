package com.example.eta.ui.stats

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.example.eta.ui.components.EtaExpander
import com.example.eta.ui.components.EtaScreen
import com.example.eta.ui.components.EtaSurface
import com.example.eta.ui.components.EtaText
import com.example.eta.ui.format.formatShort
import com.example.eta.ui.theme.EtaTheme
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalDate

/**
 * The Statistiken tab: what the plan and the days that came of it add up to.
 *
 * An info box over the whole of it, then one fold per kind of task, and inside
 * that one fold per task — each with a range of its own, since "how has this
 * gone lately" and "how has it gone altogether" are asked of one task at a time.
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
                EtaSurface(modifier = Modifier.fillMaxWidth()) {
                    EtaExpander(
                        label = "Wiederkehrende Tasks",
                        summary = countLabel(tasks.size),
                    ) {
                        if (tasks.isEmpty()) NothingYet()
                        tasks.forEach { rows ->
                            key(rows.first().normalizedName) {
                                TaskFold(rows = rows, byItem = byItem, today = data.today, plot = false)
                            }
                        }
                    }
                }
            }

            if (shows(StatSection.GOALS)) {
                val goals = tasks.filter { rows -> rows.any { it.timerTask || it.hasQuantity } }
                EtaSurface(modifier = Modifier.fillMaxWidth()) {
                    EtaExpander(
                        label = "Timer- und Inkrement-Tasks",
                        summary = countLabel(goals.size),
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

    EtaSurface(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.md)) {
            EtaText(text = "Überblick", style = EtaTheme.typography.heading)
            RangeChips(selected = range, onSelect = { rangeName = it.name })

            if (shows(StatSection.WAKE)) {
                ShareLine(
                    label = "Pünktlich aus dem Bett",
                    share = figures.wake,
                    detail = { "${it.hits} von ${it.of} Morgen" },
                    empty = "Noch kein Morgen beantwortet.",
                )
            }
            if (shows(StatSection.BED)) {
                ShareLine(
                    label = "Pünktlich ins Bett",
                    share = figures.bed,
                    detail = { "${it.hits} von ${it.of} Abenden" },
                    empty = "Noch kein Abend beantwortet.",
                )
            }
            if (shows(StatSection.PLANNED)) {
                ShareLine(
                    label = "Zeit eingeplant",
                    share = figures.planned,
                    detail = {
                        "${it.hits.minutes.formatShort()} von ${it.of.minutes.formatShort()} wacher Zeit"
                    },
                    empty = "Noch kein abgeschlossener Tag.",
                )
            }
            if (shows(StatSection.COMPLETED)) {
                ShareLine(
                    label = "Vorgenommene Tasks abgeschlossen",
                    share = figures.completed,
                    detail = { "${it.hits} von ${it.of}" },
                    empty = "Noch nichts vorgenommen.",
                )
            }
            if (shows(StatSection.CATEGORIES)) {
                Column(verticalArrangement = Arrangement.spacedBy(EtaTheme.spacing.xs)) {
                    EtaText(text = "Zeit je Kategorie", style = EtaTheme.typography.bodyStrong)
                    listOf(
                        Category.FOKUS to "Fokus",
                        Category.NEBENBEI to "Nebenbei",
                        Category.ACHTSAM to "Achtsam",
                    ).forEach { (category, name) ->
                        val spent = (figures.categoryMinutes[category] ?: 0).minutes
                        Row {
                            EtaText(
                                text = name,
                                style = EtaTheme.typography.body,
                                color = EtaTheme.colors.textSecondary,
                                modifier = Modifier.weight(1f),
                            )
                            EtaText(text = spent.formatShort(), style = EtaTheme.typography.bodyStrong)
                        }
                    }
                }
            }
        }
    }
}

/** "Pünktlich aus dem Bett — 73 %", and under it what that is a share of. */
@Composable
private fun ShareLine(
    label: String,
    share: Share,
    detail: (Share) -> String,
    empty: String,
) {
    Column {
        Row {
            EtaText(text = label, style = EtaTheme.typography.body, modifier = Modifier.weight(1f))
            EtaText(
                text = share.percent?.let { "$it %" } ?: "—",
                style = EtaTheme.typography.bodyStrong,
                color = EtaTheme.colors.accent,
            )
        }
        EtaText(
            text = if (share.of == 0) empty else detail(share),
            style = EtaTheme.typography.caption,
            color = EtaTheme.colors.textMuted,
        )
    }
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

/**
 * One task: its own fold, its own range, four figures — and, with [plot], the
 * button that draws how its workload moved.
 *
 * [rows] are the definitions that are this task (one per weekday); their
 * occurrences are counted together.
 */
@Composable
private fun TaskFold(
    rows: List<Item>,
    byItem: Map<String, List<BlockWithItem>>,
    today: LocalDate,
    plot: Boolean,
) {
    val item = rows.first()
    EtaExpander(label = item.name) {
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

        StatLine("Investierte Zeit", stats.time.formatShort())
        StatLine(
            label = "Ausgefallen",
            value = stats.skipped.percent?.let { "$it %" } ?: "—",
            detail = stats.skipped.takeIf { it.of > 0 }?.let { "${it.hits} von ${it.of} Terminen" },
        )
        StatLine(
            label = "Plan geändert",
            value = stats.changed.percent?.let { "$it %" } ?: "—",
            detail = if (stats.changed.of == 0) {
                "Wird erst für Termine ab diesem Update mitgeschrieben."
            } else {
                "${stats.changed.hits} von ${stats.changed.of} Terminen — verlängert, gekürzt " +
                    "oder verschoben."
            },
        )
        StatLine(
            label = "Planmäßig abgeschlossen",
            value = stats.asPlanned.percent?.let { "$it %" } ?: "—",
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

@Composable
private fun StatLine(label: String, value: String, detail: String? = null) {
    Column {
        Row {
            EtaText(text = label, style = EtaTheme.typography.body, modifier = Modifier.weight(1f))
            EtaText(text = value, style = EtaTheme.typography.bodyStrong, color = EtaTheme.colors.accent)
        }
        if (detail != null) {
            EtaText(text = detail, style = EtaTheme.typography.caption, color = EtaTheme.colors.textMuted)
        }
    }
}
