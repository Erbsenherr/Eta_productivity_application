package com.example.eta.domain.tutorial

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.MAKE_UP_NAME_PREFIX

/** The screens the tutorial walks through, in order. Each is the app's own. */
enum class TutorialStage { DASHBOARD, REEVALUATION, CONCRETIZE, WEEK, PLANNER }

/**
 * How a step is left.
 *
 * [TEXT] is read and waved on. [TASK] asks for something on the screen above and
 * keeps "Weiter" grey until it is done. [FOLLOW] has no button at all: what it
 * asks for is a button of the screen itself — its own "Weiter", "Abschließen" —
 * and pressing that is what moves on. Two buttons called "Weiter" a thumb apart,
 * only one of which is meant, would be the tutorial getting in its own way.
 */
enum class StepKind { TEXT, TASK, FOLLOW }

/** One thing a [StepKind.TASK] asks for, and whether it has happened. */
data class TutorialCheck(val label: String, val done: Boolean)

/** What the screens tell the tutorial about themselves — see `TutorialGuide`. */
object TutorialSignal {
    /** The page of the dashboard's "now" box: "0" is Gerade, "1" Als Nächstes. */
    const val NOW_PAGE = "now.page"

    /** The page of the Tagesabschluss in front, by its name. */
    const val REEVALUATION_STEP = "reevaluation.step"

    /** How many "Nachholen?" questions are still unanswered. */
    const val OPEN_MAKE_UP_OFFERS = "reevaluation.offers"

    /** The page of the weekly planning in front, by its name. */
    const val WEEK_STEP = "week.step"
}

/**
 * The buttons a step has to open before they can be pressed — see
 * `tutorialAllows`. Anything not named by the current step is grey.
 */
object TutorialGate {
    /** The Tagesabschluss's own "Weiter", and the swipe that does the same. */
    const val REEVALUATION_NEXT = "reevaluation.next"
    const val REEVALUATION_SETTLE = "reevaluation.settle"

    /** "Fällt aus" on one row; the item's id is appended. */
    const val REEVALUATION_DISCARD = "reevaluation.discard:"

    /** "Lassen" under "Nachholen?". No step opens it. */
    const val REEVALUATION_DISMISS_MAKE_UP = "reevaluation.dismissMakeUp"
    const val CONCRETIZE_TODO = "concretize.todo"
    const val CONCRETIZE_RECURRING = "concretize.recurring"

    /** "Löschen" on a note. No step opens it. */
    const val CONCRETIZE_DELETE = "concretize.delete"

    /** The button under the notes that leaves the step. */
    const val CONCRETIZE_DONE = "concretize.done"
    const val WEEK_FINISH = "week.finish"
    const val PLANNER_CONFIRM = "planner.confirm"
}

/** The parts of a screen a step can point at. */
object TutorialSpot {
    const val NOW = "dashboard.now"
    const val PHASE = "dashboard.phase"
    const val QUICK_ADD = "dashboard.quickAdd"
    const val TODAY_TASKS = "dashboard.tasks"
    const val TODO_CATEGORY = "todo.category"
    const val TODO_PRIORITY = "todo.priority"
    const val TODO_UNLOCK = "todo.unlock"
    const val TODO_DURATION = "todo.duration"
    const val RECURRING_SCHEME = "recurring.scheme"
    const val RECURRING_DAYS = "recurring.days"
    const val RECURRING_UNTIL = "recurring.until"
}

/**
 * Everything a step may ask about: what the practice database holds, and what
 * the screens last reported.
 *
 * Read off the database wherever the database knows — a tick, a card in the
 * week, a block on tomorrow — so "done" means the thing really happened and not
 * that a button was pressed. Only what no table records (which page is in front)
 * comes in as a signal.
 */
data class TutorialFacts(
    /** A ToDo and a recurring note of the user's own were made at some point. */
    val todoNoted: Boolean = false,
    val recurringNoted: Boolean = false,
    /** What the user called them, for the texts; null until there is one. */
    val todoName: String? = null,
    val recurringName: String? = null,
    /** Notes still waiting for their answers in the Sammelliste. */
    val openTodoNotes: Int = 0,
    val openRecurringNotes: Int = 0,
    /** Example tasks ticked off today, by item id. */
    val completedToday: Set<String> = emptySet(),
    val mailCompleted: Boolean = false,
    val mailDiscarded: Boolean = false,
    val mailMadeUp: Boolean = false,
    /** The mail's cancellation was put down to höhere Gewalt. */
    val mailExcused: Boolean = false,
    val daySettled: Boolean = false,
    /** ToDos lying in the week list. */
    val weekGoals: Int = 0,
    /** A card was dragged into tomorrow. */
    val plannedTomorrow: Boolean = false,
    /** The latest value of each signal, and every "key=value" ever reported. */
    val signals: Map<String, String> = emptyMap(),
    val seen: Set<String> = emptySet(),
) {
    fun saw(key: String, value: String): Boolean = "$key=$value" in seen

    val mailAnswered: Boolean get() = mailCompleted || mailDiscarded

    /** The names as a text quotes them, with the script's own as the fallback. */
    val todoLabel: String get() = "»${todoName ?: "Katzenstreu kaufen"}«"
    val recurringLabel: String get() = "»${recurringName ?: "Regelmäßig Sport"}«"
}

class TutorialStep(
    val stage: TutorialStage,
    val title: String,
    val text: (TutorialFacts) -> String,
    val kind: StepKind = StepKind.TEXT,
    /** The part of the screen this is about; it is framed and scrolled to. */
    val spot: String? = null,
    val checks: (TutorialFacts) -> List<TutorialCheck> = { emptyList() },
    /**
     * Whether the step may be left. Not always "every check is ticked": a
     * question that can no longer be answered must not hold the tutorial shut.
     */
    val ready: (TutorialFacts) -> Boolean = { facts -> checks(facts).all { it.done } },
    /** Shows the little swipe hint — the step asks for a sideways wipe. */
    val swipeHint: Boolean = false,
    /** From this step on the simulated day stands in the evening. */
    val evening: Boolean = false,
    /** The screen's own buttons this step opens — see [TutorialGate]. */
    val allow: Set<String> = emptySet(),
) {
    /** Whether the tutorial can move past it right now, however it is left. */
    fun passable(facts: TutorialFacts): Boolean = kind != StepKind.TASK || ready(facts)
}

private fun text(stage: TutorialStage, title: String, body: String, spot: String? = null) =
    TutorialStep(stage = stage, title = title, text = { body }, spot = spot)

/**
 * The Quickstart: one simulated day, from the dashboard to tomorrow's plan.
 *
 * The texts describe what the screens **do**, which is not everywhere what the
 * first draft of the script assumed: a catch-up lands in the Sammelliste rather
 * than the week list, a note is not asked for when a Quick-Add is filled in, and
 * the weekly planning opens on its look back.
 */
val QUICKSTART: List<TutorialStep> = listOf(
    // --- The day itself -----------------------------------------------------
    text(
        TutorialStage.DASHBOARD,
        "Das Dashboard",
        "Das ist der Tab »Heute«. Hier siehst du alles, was kurzfristig ansteht. " +
            "Für das Tutorial ist es 10:30 Uhr, und Eta hat dir einen Beispieltag angelegt.",
    ),
    TutorialStep(
        stage = TutorialStage.DASHBOARD,
        title = "Was gerade läuft",
        text = {
            "Die Box »Gerade« zeigt, woran du sitzt: Arbeiten. Wische sie zur Seite, " +
                "um zu sehen, was danach kommt."
        },
        kind = StepKind.TASK,
        spot = TutorialSpot.NOW,
        checks = { listOf(TutorialCheck("Zu »Als Nächstes« wischen", it.saw(TutorialSignal.NOW_PAGE, "1"))) },
        swipeHint = true,
    ),
    text(
        TutorialStage.DASHBOARD,
        "Der ganze Tag",
        "Als Nächstes fütterst du die Katze. Den ganzen Tag findest du unter " +
            "»Heute anstehend«: Frühstück, Arbeiten, Katze füttern und Mail versenden.",
        spot = TutorialSpot.TODAY_TASKS,
    ),
    text(
        TutorialStage.DASHBOARD,
        "Ein Gedanke kommt dazwischen",
        "Mitten in der Arbeit fällt dir ein: Das Katzenstreu geht zur Neige. Und du " +
            "wolltest wieder regelmäßig Sport machen. Beides ist wichtig — nur nicht " +
            "jetzt. Je mehr dir im Kopf herumschwirrt, desto schlechter konzentrierst du dich.",
    ),
    TutorialStep(
        stage = TutorialStage.DASHBOARD,
        title = "Aus dem Kopf, in die App",
        text = {
            "Schreib beides in die Quick-Add-Box — ein Name genügt. Für die zweite " +
                "Notiz wischst du die Box zur Seite. Danach darfst du es vergessen: " +
                "Eta erinnert sich für dich. (Nur zur Übung — nichts davon landet in " +
                "deinen eigenen Listen.)"
        },
        kind = StepKind.TASK,
        spot = TutorialSpot.QUICK_ADD,
        checks = {
            listOf(
                TutorialCheck("»Katzenstreu kaufen« notieren", it.todoNoted),
                TutorialCheck("»Regelmäßig Sport« als Wiederholung notieren", it.recurringNoted),
            )
        },
    ),
    text(
        TutorialStage.DASHBOARD,
        "Kopf frei",
        "Perfekt! Beides ist notiert. Jetzt kannst du dich wieder dem widmen, was " +
            "gerade ansteht — ohne Sorge, etwas zu vergessen.",
    ),
    text(
        TutorialStage.DASHBOARD,
        "Wir spulen vor",
        "Der Tag geht weiter, und irgendwann ist es Abend. Dann hakst du ab, was du " +
            "geschafft hast, gibst deinen Notizen Form und planst den morgigen Tag.",
    ),
    TutorialStep(
        stage = TutorialStage.DASHBOARD,
        title = "Planungszeit",
        text = {
            "Es ist 20 Uhr — die Zeit, die du für deine Tagesplanung festgelegt hast. " +
                "Eta erinnert dich daran, und die Box rückt nach ganz oben. " +
                "Tippe auf »Tag abschließen«."
        },
        kind = StepKind.FOLLOW,
        spot = TutorialSpot.PHASE,
        // Come back to after the day was closed, the button is gone from the
        // box; the coach's own "Weiter" then stands in for it.
        ready = { it.daySettled },
        evening = true,
    ),

    // --- The evening --------------------------------------------------------
    TutorialStep(
        stage = TutorialStage.REEVALUATION,
        title = "Tagesabschluss",
        text = {
            "Hier steht dein Tag noch einmal. Setze einen Haken hinter alles, was du " +
                "geschafft hast — alles außer »Mail versenden«."
        },
        kind = StepKind.TASK,
        // The mail ticked off as well is not what happened today, and the next
        // step needs it open: asked to be taken back rather than waved through.
        checks = { facts ->
            listOfNotNull(
                TutorialCheck(
                    "Frühstück, Arbeiten und Katze füttern abhaken",
                    facts.completedToday.containsAll(TUTORIAL_DONE_IDS),
                ),
                TutorialCheck("Haken bei »Mail versenden« wieder entfernen", false)
                    .takeIf { facts.mailCompleted },
            )
        },
    ),
    TutorialStep(
        stage = TutorialStage.REEVALUATION,
        title = "Was nicht geklappt hat",
        text = {
            "Die Mail hast du heute nicht geschafft, willst sie aber nicht ausfallen " +
                "lassen. Tippe bei ihr auf »Fällt aus« — Eta fragt dann nach. " +
                "Antworte mit »Nachholen«."
        },
        kind = StepKind.TASK,
        checks = { facts ->
            listOf(
                TutorialCheck("»Fällt aus« antippen", facts.mailAnswered),
                TutorialCheck("»Nachholen« antippen", facts.mailMadeUp),
            )
        },
        // "Lassen" is shut in the tutorial, so the question cannot normally be
        // lost. Should it be gone all the same, it cannot be put again, and the
        // step lets go rather than wait for an answer that cannot come.
        ready = { facts ->
            facts.mailMadeUp ||
                (facts.mailDiscarded && facts.signals[TutorialSignal.OPEN_MAKE_UP_OFFERS] == "0")
        },
        allow = setOf(TutorialGate.REEVALUATION_DISCARD + TUTORIAL_MAIL_ID),
    ),
    TutorialStep(
        stage = TutorialStage.REEVALUATION,
        title = "Höhere Gewalt",
        text = {
            "Manchmal fällt etwas aus, ohne dass du etwas dafür kannst. Mit dem " +
                "Punktetracker kostet eine Absage Punkte — außer bei höherer Gewalt. " +
                "Heute war der Mailserver ausgefallen: Halte die Zeile »Mail versenden« " +
                "gedrückt, bis sie sich füllt, und gib den Grund an."
        },
        kind = StepKind.TASK,
        checks = { listOf(TutorialCheck("»Mail versenden« gedrückt halten und begründen", it.mailExcused)) },
    ),
    TutorialStep(
        stage = TutorialStage.REEVALUATION,
        title = "Hervorragend!",
        text = { facts ->
            if (facts.mailMadeUp) {
                "»Nachholen von Mail versenden« liegt jetzt in deiner Sammelliste — " +
                    "dazu gleich mehr. Tippe im Fenster auf »Weiter«."
            } else {
                "Damit ist jede Aufgabe des Tages beantwortet. Tippe im Fenster auf »Weiter«."
            }
        },
        kind = StepKind.FOLLOW,
        ready = { facts ->
            facts.signals[TutorialSignal.REEVALUATION_STEP].let { it != null && it != "TASKS" }
        },
        allow = setOf(TutorialGate.REEVALUATION_NEXT),
    ),
    TutorialStep(
        stage = TutorialStage.REEVALUATION,
        title = "Kurz nachdenken",
        text = {
            "Freiwillig: Halte fest, was gut lief — und was du am Plan ändern würdest, " +
                "falls der Tag seinetwegen stressig war. Tippe dann auf »Weiter«."
        },
        kind = StepKind.FOLLOW,
        ready = { it.signals[TutorialSignal.REEVALUATION_STEP] == "RECURRING" },
        allow = setOf(TutorialGate.REEVALUATION_NEXT),
    ),
    TutorialStep(
        stage = TutorialStage.REEVALUATION,
        title = "Passt das noch?",
        text = {
            "Hier stehen die wiederkehrenden Aufgaben des Tages. »Katze füttern« bleibt " +
                "aktuell; was nicht mehr passt, könntest du hier ausrangieren. " +
                "Schließe den Tag jetzt mit »Abschließen« ab."
        },
        kind = StepKind.FOLLOW,
        ready = { it.daySettled },
        // "Weiter" as well: gone back a page, it is the way to this one again.
        allow = setOf(TutorialGate.REEVALUATION_NEXT, TutorialGate.REEVALUATION_SETTLE),
    ),

    // --- The notes get their answers ----------------------------------------
    TutorialStep(
        stage = TutorialStage.CONCRETIZE,
        title = "Notizen ausfüllen",
        text = { facts ->
            "Jetzt hast du Ruhe für deine Notizen: ${facts.todoLabel} und " +
                "${facts.recurringLabel}. Wir beginnen mit dem ToDo — den Namen kannst " +
                "du auf der Karte noch ändern."
        },
    ),
    text(
        TutorialStage.CONCRETIZE,
        "Kategorie",
        "Wie sehr beansprucht dich die Aufgabe? Fokus: volle Konzentration, etwa " +
            "beim Lesen oder Schreiben. Nebenbei: Musik oder ein Hörbuch laufen mit. " +
            "Achtsam: bewusst ohne Beschallung.",
        spot = TutorialSpot.TODO_CATEGORY,
    ),
    text(
        TutorialStage.CONCRETIZE,
        "Priorität",
        "Beim Füllen einer Woche oder eines Tages bietet Eta dir zuerst das " +
            "Wichtigste an. Niedrigere Stufen kommen erst dran, wenn die höheren " +
            "verplant sind — so schiebst du nichts Wichtiges versehentlich auf.",
        spot = TutorialSpot.TODO_PRIORITY,
    ),
    text(
        TutorialStage.CONCRETIZE,
        "Freigeschaltet ab",
        "Manches wird erst später relevant. Bis zu diesem Tag hält Eta die Aufgabe " +
            "aus deiner Planung heraus, damit sie deine Listen nicht verstopft.",
        spot = TutorialSpot.TODO_UNLOCK,
    ),
    TutorialStep(
        stage = TutorialStage.CONCRETIZE,
        title = "Dauer",
        text = {
            "Schätze, wie lange du brauchst. Eta rechnet mit: In eine Woche passt " +
                "nicht mehr, als sie freie Stunden hat. Tippe dann auf der Karte auf " +
                "»Übernehmen«."
        },
        kind = StepKind.TASK,
        spot = TutorialSpot.TODO_DURATION,
        checks = { listOf(TutorialCheck("ToDo übernehmen", it.todoNoted && it.openTodoNotes == 0)) },
        allow = setOf(TutorialGate.CONCRETIZE_TODO),
    ),
    text(
        TutorialStage.CONCRETIZE,
        "Die wiederkehrende Aufgabe",
        "Vieles ist wie beim ToDo. Neu ist die Skizze deiner Woche: Grün ist frei, " +
            "grau belegt, und deine Aufgabe erscheint farbig, sobald du Tage wählst. " +
            "So vermeidest du Überschneidungen. Im Tutorial ist die Woche fast leer.",
        spot = TutorialSpot.RECURRING_SCHEME,
    ),
    text(
        TutorialStage.CONCRETIZE,
        "Wochentage und Uhrzeit",
        "Wähle beliebige Tage und einen Beginn. Bei mehreren Tagen erscheint der " +
            "Haken »Abweichende Uhrzeiten« — damit bekommt jeder Tag seine eigene.",
        spot = TutorialSpot.RECURRING_DAYS,
    ),
    TutorialStep(
        stage = TutorialStage.CONCRETIZE,
        title = "Wiederholen bis",
        text = {
            "Manches endet an einem festen Tag, etwa das Lernen für eine Prüfung. " +
                "Dafür gibt es »Wiederholen bis« — hier brauchst du es nicht. " +
                "Tippe auf der Karte auf »Übernehmen«."
        },
        kind = StepKind.TASK,
        spot = TutorialSpot.RECURRING_UNTIL,
        checks = {
            listOf(
                TutorialCheck(
                    "Wiederkehrende Aufgabe übernehmen",
                    it.recurringNoted && it.openRecurringNotes == 0,
                ),
            )
        },
        allow = setOf(TutorialGate.CONCRETIZE_RECURRING),
    ),
    TutorialStep(
        stage = TutorialStage.CONCRETIZE,
        title = "Geschafft",
        text = { "Alle Notizen sind ausgefüllt. Als Nächstes simulieren wir die Wochenplanung." },
        allow = setOf(TutorialGate.CONCRETIZE_DONE),
    ),

    // --- The week -----------------------------------------------------------
    TutorialStep(
        stage = TutorialStage.WEEK,
        title = "Wochenplanung",
        text = {
            "Einmal pro Woche planst du die nächsten sieben Tage. Es beginnt mit einem " +
                "kurzen Rückblick. Tippe im Fenster auf »Weiter«."
        },
        kind = StepKind.FOLLOW,
        ready = { it.signals[TutorialSignal.WEEK_STEP] == "PLAN" },
    ),
    TutorialStep(
        stage = TutorialStage.WEEK,
        title = "Was kommt in die Woche?",
        text = { facts ->
            "Links liegen die ToDos, die noch in keiner Woche stecken. Wiederkehrende " +
                "Aufgaben fehlen hier: Sie sind ohnehin jede Woche eingeplant. An jeder " +
                "Karte steht ihre Dauer. Tippe auf ${facts.todoLabel} und beobachte " +
                "die freien Stunden. Fällt dir beim Planen noch etwas ein, legst du " +
                "es mit »ToDo anlegen« gleich hier an."
        },
        kind = StepKind.TASK,
        checks = { listOf(TutorialCheck("Ein ToDo in »Diese Woche« schieben", it.weekGoals > 0)) },
    ),
    TutorialStep(
        stage = TutorialStage.WEEK,
        title = "Die Woche steht",
        text = { "Super! Jetzt fehlt nur noch der morgige Tag. Tippe auf »Woche steht«." },
        kind = StepKind.FOLLOW,
        // Nothing to read off: leaving the screen is the step.
        ready = { false },
        allow = setOf(TutorialGate.WEEK_FINISH),
    ),

    // --- Tomorrow -----------------------------------------------------------
    TutorialStep(
        stage = TutorialStage.PLANNER,
        title = "Der Tagesplaner",
        text = {
            "Oben liegen im »Revolver« deine ToDos für die Woche — die wichtigste " +
                "Stufe zuerst —, darunter der morgige Tag. Ziehe die mittlere Karte " +
                "nach unten auf eine freie Uhrzeit."
        },
        kind = StepKind.TASK,
        checks = { listOf(TutorialCheck("Ein ToDo in den Tag ziehen", it.plannedTomorrow)) },
    ),
    TutorialStep(
        stage = TutorialStage.PLANNER,
        title = "Gut gemacht!",
        text = {
            "Verplante Karten kannst du verschieben oder zurück auf den Revolver " +
                "ziehen. Gedrückt halten zeigt weitere Optionen. Das war der " +
                "Quickstart — viel Erfolg mit Eta!"
        },
        allow = setOf(TutorialGate.PLANNER_CONFIRM),
    ),
)

/**
 * Where a press on one of a screen's own ways out leads, from step [index].
 *
 * The screens keep their buttons — "Woche steht", "Abschließen", "Zurück" — and
 * any of them may be pressed at any step. It counts when nothing the stage still
 * asks for is left undone; then the tutorial moves on to the next stage, or, in
 * the last one, to its closing words. Null is "not yet", and [steps].size "over".
 */
fun exitTarget(steps: List<TutorialStep>, index: Int, stage: TutorialStage, facts: TutorialFacts): Int? {
    val current = steps.getOrNull(index) ?: return null
    if (current.stage != stage) return null
    val rest = steps.drop(index).takeWhile { it.stage == stage }
    if (rest.any { it.kind == StepKind.TASK && !it.ready(facts) }) return null
    val next = index + rest.size
    return when {
        next < steps.size -> next
        index == steps.lastIndex -> steps.size
        else -> steps.lastIndex
    }
}

/**
 * What the practice database says, as the steps ask about it.
 *
 * A pure function of five lists, so the readings the steps depend on can be
 * pinned without a database: the Sammelliste, the week list, today's and
 * tomorrow's blocks, and whether today was settled.
 */
fun tutorialFacts(
    collection: List<Item>,
    week: List<Item>,
    today: List<BlockWithItem>,
    tomorrow: List<BlockWithItem>,
    daySettled: Boolean,
): TutorialFacts {
    // The user's own cards. A catch-up is a ToDo in the Sammelliste as well, but
    // it is Eta's doing, and must not pass for the note the tutorial asked for.
    fun Item.isOwnTodo() = type == ItemType.TODO && !name.startsWith(MAKE_UP_NAME_PREFIX)
    val ownTodos = collection.filter { it.isOwnTodo() } + week.filter { it.isOwnTodo() }
    val notes = collection.filter { it.type == ItemType.RECURRING }
    val mail = today.firstOrNull { it.item.id == TUTORIAL_MAIL_ID }?.block

    return TutorialFacts(
        todoNoted = ownTodos.isNotEmpty(),
        recurringNoted = notes.isNotEmpty(),
        todoName = ownTodos.firstOrNull()?.name,
        recurringName = notes.firstOrNull()?.name,
        openTodoNotes = collection.count { it.type == ItemType.TODO && !it.isConcretized },
        openRecurringNotes = notes.count { !it.isConcretized },
        completedToday = today.filter { it.block.isCompleted }.mapTo(mutableSetOf()) { it.item.id },
        mailCompleted = mail?.isCompleted == true,
        mailDiscarded = mail?.isDiscarded == true,
        mailMadeUp = mail?.makeUpItemId != null,
        mailExcused = mail?.isExcused == true,
        daySettled = daySettled,
        weekGoals = week.count { it.type == ItemType.TODO },
        plannedTomorrow = tomorrow.any { it.block.isHandPlaced },
    )
}

/**
 * These facts with what [earlier] already knew kept.
 *
 * A note leaves the Sammelliste the moment it is filled in — a standing task
 * moves to its own list, a ToDo into a day — and "was one made" must not turn
 * false again because of it, nor its name be forgotten.
 */
fun TutorialFacts.remembering(earlier: TutorialFacts): TutorialFacts = copy(
    todoNoted = todoNoted || earlier.todoNoted,
    recurringNoted = recurringNoted || earlier.recurringNoted,
    todoName = todoName ?: earlier.todoName,
    recurringName = recurringName ?: earlier.recurringName,
)
