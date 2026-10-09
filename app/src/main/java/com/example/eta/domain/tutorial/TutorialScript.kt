package com.example.eta.domain.tutorial

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.MAKE_UP_NAME_PREFIX

/** The screens the tutorial walks through, in order. Each is the app's own. */
enum class TutorialStage {
    DASHBOARD,
    REEVALUATION,
    CONCRETIZE,
    WEEK,
    PLANNER,

    /** The evening's card of a note, for the Extras under it. */
    EXTRAS,

    /** The Listen tab, for the walk over its lists. */
    LISTS,

    /** The three tabs of the Advanced Features, each for its own tutorial. */
    GROWTH,
    CONTRACTS,
    REWARDS,
}

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

    /** Which revolver the day planner has out, by its name. */
    const val PLANNER_REVOLVER = "planner.revolver"

    /** "1" once the Extras box of a card has been unfolded. */
    const val EXTRAS_OPEN = "extras.open"
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

    /** The button under the notes that leaves the step. No step opens it. */
    const val CONCRETIZE_DONE = "concretize.done"

    /** Not a button: the line under that one saying the day planning comes later. */
    const val CONCRETIZE_LATER_NOTE = "concretize.laterNote"
    const val WEEK_FINISH = "week.finish"
    const val PLANNER_CONFIRM = "planner.confirm"
}

/** The parts of a screen a step can point at. */
object TutorialSpot {
    const val NOW = "dashboard.now"
    const val PHASE = "dashboard.phase"
    const val POINTS = "dashboard.points"
    const val EXTRAS = "extras.box"
    const val EXTRA_SOUND = "extras.sound"
    const val EXTRA_TRAVEL = "extras.travel"
    const val EXTRA_BREAK = "extras.break"
    const val EXTRA_POMODORO = "extras.pomodoro"
    const val EXTRA_REMINDER = "extras.reminder"
    const val EXTRA_DEADLINE = "extras.deadline"
    const val EXTRA_SUBTASKS = "extras.subtasks"

    /**
     * One list of the Listen tab, by the name of its section — the tab frames
     * its cards as `LIST_PREFIX + section.name`.
     */
    const val LIST_PREFIX = "lists."
    const val LIST_COLLECTION = LIST_PREFIX + "SAMMELLISTE"
    const val LIST_WEEK = LIST_PREFIX + "WOCHENLISTE"
    const val LIST_RECURRING = LIST_PREFIX + "WIEDERKEHREND"
    const val LIST_TODAY = LIST_PREFIX + "TAGESLISTE"
    const val LIST_TOMORROW = LIST_PREFIX + "MORGEN"
    const val LIST_DONE = LIST_PREFIX + "ERFOLG"
    const val LIST_APPOINTMENTS = LIST_PREFIX + "TERMINE"
    const val LIST_LOCKED = LIST_PREFIX + "SPERRLISTE"
    const val QUICK_ADD = "dashboard.quickAdd"

    /** The two dots of the Quick-Add box: the way to its second page. */
    const val QUICK_ADD_PAGES = "dashboard.quickAdd.pages"
    const val TODO_SAVE = "todo.save"
    const val RECURRING_SAVE = "recurring.save"
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
    /** Tasks ticked off today, by item id. */
    val completedToday: Set<String> = emptySet(),
    /** Blocks of today, the mail aside, that are not ticked off. */
    val othersOpen: Int = 0,
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
    /** What the feature tutorials have the user make. */
    val growthTasks: Int = 0,
    val contracts: Int = 0,
    val rewards: Int = 0,
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
    /**
     * Where the frame moves to as the step goes on — null keeps [spot]. For a
     * step of two halves, so the frame is on the half still to do.
     */
    val spotFor: ((TutorialFacts) -> String?)? = null,
    val checks: (TutorialFacts) -> List<TutorialCheck> = { emptyList() },
    /**
     * Whether the step may be left. Not always "every check is ticked": a
     * question that can no longer be answered must not hold the tutorial shut.
     */
    val ready: (TutorialFacts) -> Boolean = { facts -> checks(facts).all { it.done } },
    /** Whether to show the little swipe hint: the step asks for a sideways wipe now. */
    val swipeHint: (TutorialFacts) -> Boolean = { false },
    /** From this step on the simulated day stands in the evening. */
    val evening: Boolean = false,
    /** The screen's own buttons this step opens — see [TutorialGate]. */
    val allow: Set<String> = emptySet(),
) {
    /** Whether the tutorial can move past it right now, however it is left. */
    fun passable(facts: TutorialFacts): Boolean = kind != StepKind.TASK || ready(facts)

    /** The part to frame right now. */
    fun spotAt(facts: TutorialFacts): String? = spotFor?.invoke(facts) ?: spot
}

private fun text(stage: TutorialStage, title: String, body: String, spot: String? = null) =
    TutorialStep(stage = stage, title = title, text = { body }, spot = spot)

/**
 * The Quickstart: one simulated day, from the dashboard to tomorrow's plan.
 *
 * The texts describe what the screens **do**, which is not everywhere what the
 * first draft of the script assumed: a catch-up lands in the Sammelliste rather
 * than the week list, a note is not asked for when a Quick-Add is filled in, and
 * the weekly planning opens on the devaluation and its look back.
 *
 * **The points are part of it** (third round): the tracker is on for a new setup
 * and on in the practice app, so the account, what a category pays, the evening's
 * settlement and the second revolver each get a step — and one sentence says
 * where the whole thing is switched off.
 */
val QUICKSTART_STEPS: List<TutorialStep> = listOf(
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
        swipeHint = { true },
    ),
    text(
        TutorialStage.DASHBOARD,
        "Der ganze Tag",
        "Als Nächstes fütterst du die Katze. Den ganzen Tag findest du unter " +
            "»Heute anstehend«: Frühstück, Arbeiten, Katze füttern und Mail versenden " +
            "— dazu alles, was Eta aus der Einrichtung kennt, von der Morgenroutine " +
            "bis zur Freizeit am Abend.",
        spot = TutorialSpot.TODAY_TASKS,
    ),
    text(
        TutorialStage.DASHBOARD,
        "Dein Punktekonto",
        "Erledigte Aufgaben bringen Punkte. Gutgeschrieben wird erst am Abend — bis " +
            "dahin zeigt die Box, was heute schon erarbeitet und was geplant ist. " +
            "Ausgeben kannst du sie für Dinge, die du dir gönnst; dazu später mehr. " +
            "Wer lieber ohne Punkte plant, schaltet den Punktetracker unter " +
            "Einstellungen → Advanced Features aus.",
        spot = TutorialSpot.POINTS,
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
        text = { facts ->
            if (facts.todoNoted && !facts.recurringNoted) {
                "Die erste Notiz steht. »Regelmäßig Sport« soll sich wiederholen und " +
                    "gehört auf die zweite Seite der Box: Wische sie zur Seite oder " +
                    "tippe auf den zweiten Punkt oben rechts. Dort notierst du es."
            } else {
                "Schreib beides in die Quick-Add-Box — ein Name genügt. Beginne mit " +
                    "»Katzenstreu kaufen«. Danach darfst du es vergessen: Eta erinnert " +
                    "sich für dich. (Nur zur Übung — nichts davon landet in deinen " +
                    "eigenen Listen.)"
            }
        },
        kind = StepKind.TASK,
        spot = TutorialSpot.QUICK_ADD,
        // Once the ToDo is noted the frame leaves the box for its two dots:
        // what is missing now is not typing, it is finding the second page.
        spotFor = { facts ->
            if (facts.todoNoted && !facts.recurringNoted) TutorialSpot.QUICK_ADD_PAGES else null
        },
        swipeHint = { it.todoNoted && !it.recurringNoted },
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
                    "Alles außer »Mail versenden« abhaken",
                    facts.completedToday.isNotEmpty() && facts.othersOpen == 0,
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
                "lassen. Tippe bei ihr auf »Fällt aus« — Eta fragt dann nach. Die " +
                "Frage erscheint unter der Liste: Scrolle nach unten und antworte " +
                "mit »Nachholen«."
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
            "Manchmal fällt etwas aus, ohne dass du etwas dafür kannst. Eine Absage " +
                "am selben Tag kostet Punkte — außer bei höherer Gewalt. " +
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
    text(
        TutorialStage.REEVALUATION,
        "Die Abrechnung",
        "So rechnet Eta den Tag ab: Punkte für erledigte Aufgaben, Abzüge für " +
            "Abgesagtes und für Zeit, die gar nicht verplant war — zwei Stunden am Tag " +
            "dürfen leer bleiben, was darüber liegt, kostet. Gebucht wird erst beim " +
            "Abschließen.",
    ),
    TutorialStep(
        stage = TutorialStage.REEVALUATION,
        title = "Warum leere Zeit Punkte kostet",
        text = {
            "Eta geht davon aus, dass ein Tag so wenig unverplante Zeit haben sollte " +
                "wie möglich. Das heißt nicht weniger Freizeit — nur, dass auch sie " +
                "im Plan steht. Gerade wer sich mit einem strukturierten Alltag " +
                "schwertut, profitiert davon. Den Abzug kannst du unter " +
                "Einstellungen → Advanced Features anpassen oder abschalten. " +
                "Tippe im Fenster auf »Weiter«."
        },
        kind = StepKind.FOLLOW,
        ready = { facts ->
            facts.signals[TutorialSignal.REEVALUATION_STEP].let { it == "JOURNAL" || it == "RECURRING" }
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
            "Achtsam: bewusst ohne Beschallung. Danach richten sich die Punkte: Fokus " +
            "und Achtsam bringen 1 Punkt pro Stunde, Nebenbei einen halben.",
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
    text(
        TutorialStage.CONCRETIZE,
        "Dauer",
        "Schätze, wie lange du brauchst. Eta rechnet mit: In eine Woche passt nicht " +
            "mehr, als sie freie Stunden hat.",
        spot = TutorialSpot.TODO_DURATION,
    ),
    // A step of its own, with the frame on the button: asked for while the
    // frame still stood on the field before it, "Weiter" stayed grey for a
    // reason the screen did not show.
    TutorialStep(
        stage = TutorialStage.CONCRETIZE,
        title = "Übernehmen",
        text = { "Damit ist das ToDo fertig. Tippe auf der Karte auf »Übernehmen«." },
        kind = StepKind.TASK,
        spot = TutorialSpot.TODO_SAVE,
        checks = { listOf(TutorialCheck("ToDo übernehmen", it.todoNoted && it.openTodoNotes == 0)) },
        allow = setOf(TutorialGate.CONCRETIZE_TODO),
    ),
    text(
        TutorialStage.CONCRETIZE,
        "Die wiederkehrende Aufgabe",
        "Vieles ist wie beim ToDo. Neu ist die Skizze deiner Woche: Grün ist frei, " +
            "grau belegt, und deine Aufgabe erscheint farbig, sobald du Tage wählst. " +
            "So vermeidest du Überschneidungen.",
        spot = TutorialSpot.RECURRING_SCHEME,
    ),
    text(
        TutorialStage.CONCRETIZE,
        "Wochentage und Uhrzeit",
        "Wähle beliebige Tage und einen Beginn. Bei mehreren Tagen erscheint der " +
            "Haken »Abweichende Uhrzeiten« — damit bekommt jeder Tag seine eigene.",
        spot = TutorialSpot.RECURRING_DAYS,
    ),
    text(
        TutorialStage.CONCRETIZE,
        "Wiederholen bis",
        "Manches endet an einem festen Tag, etwa das Lernen für eine Prüfung. Dafür " +
            "gibt es »Wiederholen bis« — hier brauchst du es nicht.",
        spot = TutorialSpot.RECURRING_UNTIL,
    ),
    TutorialStep(
        stage = TutorialStage.CONCRETIZE,
        title = "Übernehmen",
        text = {
            "Sind Wochentage gewählt, ist auch diese Aufgabe fertig. " +
                "Tippe auf der Karte auf »Übernehmen«."
        },
        kind = StepKind.TASK,
        spot = TutorialSpot.RECURRING_SAVE,
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
        // The screen's own button would lead to the day planning, which the
        // tutorial reaches after the week; it stays grey and a line says so.
        allow = setOf(TutorialGate.CONCRETIZE_LATER_NOTE),
    ),

    // --- The week -----------------------------------------------------------
    TutorialStep(
        stage = TutorialStage.WEEK,
        title = "Wochenplanung",
        text = {
            "Einmal pro Woche planst du die nächsten sieben Tage. Zuerst der " +
                "Wertverfall: Angesparte Punkte verlieren jede Woche 30 % — sie sind " +
                "zum Ausgeben da. Dann ein kurzer Rückblick. Tippe im Fenster zweimal " +
                "auf »Weiter«."
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
    text(
        TutorialStage.PLANNER,
        "Kurze Blöcke lesen",
        "Manche Blöcke sind zu kurz, um ihren Namen zu zeigen — »Katze füttern« " +
            "etwa dauert nur eine Viertelstunde. Tippe einen Block an, und sein Name " +
            "erscheint groß über dem Tag, mit Uhrzeit und Notiz.",
    ),
    TutorialStep(
        stage = TutorialStage.PLANNER,
        title = "Der zweite Revolver",
        text = {
            "Oben steht die Prognose: so viele Punkte bringt der Tag, wenn alles " +
                "erledigt wird. Der Knopf unten wechselt den Revolver. " +
                "Tippe auf »Punkte«."
        },
        kind = StepKind.TASK,
        checks = {
            listOf(
                TutorialCheck(
                    "Zum Revolver »Punkte« wechseln",
                    it.saw(TutorialSignal.PLANNER_REVOLVER, "SPEND"),
                ),
            )
        },
    ),
    text(
        TutorialStage.PLANNER,
        "Punkte verdienen und ausgeben",
        "Auch diese Karten ziehst du in den Tag. »Custom Earn« ist für Verdienst, " +
            "den keine Aufgabe abbildet, und bringt 1,5 Punkte pro Stunde. »Custom " +
            "Spend« ist dein Lohn: eine Stunde Serie oder Spielen kostet 5 Punkte. " +
            "Beide Sätze lassen sich beim Einplanen anpassen. Ausgeben geht nur mit " +
            "Guthaben — bei einem Punktestand von 0 oder darunter ist die Karte gesperrt.",
    ),
    text(
        TutorialStage.PLANNER,
        "Der dritte Revolver: Pause",
        "Ein weiterer Tipp auf den Knopf führt zur Pause: eine Viertelstunde, die du " +
            "ohne Umstände hinter eine oder mehrere Aufgaben ziehst. Verbringe Pausen " +
            "am besten mit Entspannung — und behalte deine Bildschirmzeit im Blick.",
    ),
    TutorialStep(
        stage = TutorialStage.PLANNER,
        title = "Gut gemacht!",
        text = {
            "Verplante Karten kannst du verschieben oder zurück auf den Revolver " +
                "ziehen. Gedrückt halten zeigt weitere Optionen. Das war der " +
                "Quickstart! Zu den Extras, den Growth-Tasks, den Verträgen und dem " +
                "Belohn-o-mat gibt es eigene Tutorials: Einstellungen → Tutorial."
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
    growthTasks: Int = 0,
    contracts: Int = 0,
    rewards: Int = 0,
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
        othersOpen = today.count { it.item.id != TUTORIAL_MAIL_ID && !it.block.isCompleted },
        mailCompleted = mail?.isCompleted == true,
        mailDiscarded = mail?.isDiscarded == true,
        mailMadeUp = mail?.makeUpItemId != null,
        mailExcused = mail?.isExcused == true,
        daySettled = daySettled,
        weekGoals = week.count { it.type == ItemType.TODO },
        plannedTomorrow = tomorrow.any { it.block.isHandPlaced },
        growthTasks = growthTasks,
        contracts = contracts,
        rewards = rewards,
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
