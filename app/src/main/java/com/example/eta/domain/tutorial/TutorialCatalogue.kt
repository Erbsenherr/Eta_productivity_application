package com.example.eta.domain.tutorial

/**
 * The tutorials there are.
 *
 * The Quickstart is the one a first run is led through. The others each explain
 * one thing a newcomer does not need on the first day — an Advanced Feature, or
 * the Extras — and are opened by whoever goes looking: from the tutorial page of
 * the settings, or from the Advanced Features page beside the switch they are
 * about. The points are not among them: they are on by default and part of the
 * Quickstart.
 */
enum class TutorialId(
    val title: String,
    val summary: String,
    /**
     * Whether the practice app shows the Advanced Features. The Quickstart shows
     * what a fresh install shows; a tutorial *about* a feature has it switched on.
     */
    val advanced: Boolean,
) {
    QUICKSTART(
        title = "Quickstart",
        summary = "Etwa fünf Minuten: ein simulierter Tag, vom Dashboard bis zum Plan für morgen.",
        advanced = false,
    ),
    LISTS(
        title = "Listen",
        summary = "Einmal durch jede Liste: wofür sie da ist und was Gedrückthalten bewirkt.",
        advanced = false,
    ),
    EXTRAS(
        title = "Extras",
        summary = "Wege, Pause, Töne, Pomodoro, Erinnerung, Deadline und Subtasks — Schritt für Schritt.",
        advanced = false,
    ),
    GROWTH(
        title = "Growth-Tasks",
        summary = "Aufgaben, die klein anfangen und mit jedem Abschluss wachsen.",
        advanced = true,
    ),
    CONTRACTS(
        title = "Verträge",
        summary = "Versprechen an dich selbst, mit Unterschrift und täglicher Frage.",
        advanced = true,
    ),
    REWARDS(
        title = "Belohn-o-mat",
        summary = "Langfristige Belohnungen, die sich Abend für Abend füllen.",
        advanced = true,
    ),
    ;

    val steps: List<TutorialStep>
        get() = when (this) {
            QUICKSTART -> QUICKSTART_STEPS
            LISTS -> LIST_STEPS
            EXTRAS -> EXTRAS_STEPS
            GROWTH -> GROWTH_STEPS
            CONTRACTS -> CONTRACT_STEPS
            REWARDS -> REWARD_STEPS
        }

    companion object {
        /** The ones about an Advanced Feature or the Extras: everything but the Quickstart. */
        val further: List<TutorialId> get() = entries - QUICKSTART

        fun named(name: String?): TutorialId? = entries.firstOrNull { it.name == name }
    }
}

/** The note the Extras tutorial opens on. */
const val TUTORIAL_EXTRAS_NOTE = "Zahnarzt"

private const val WHERE_TO_SWITCH =
    "Ein- und ausschalten lässt sich das unter Einstellungen → Advanced Features."

private fun text(stage: TutorialStage, title: String, body: String, spot: String? = null) =
    TutorialStep(stage = stage, title = title, text = { body }, spot = spot)

/**
 * The Listen tab, list by list: what each is for, and what holding does there.
 *
 * Holding is the tab's one hidden gesture, and it means two different things:
 * on the **heading** of three lists it makes something new for that list, and
 * on an **entry** it opens the window that edits or removes it. One list is
 * tried out — a ToDo made from the Sammelliste's heading — and the rest is told,
 * each with its list framed.
 */
val LIST_STEPS: List<TutorialStep> = listOf(
    text(
        TutorialStage.LISTS,
        "Der Reiter Listen",
        "Hier liegt jede Aufgabe in genau einer Liste — je nachdem, wie weit sie " +
            "ist: notiert, für die Woche vorgenommen, für einen Tag geplant, erledigt. " +
            "Ein Tipp auf eine Überschrift klappt die Liste auf und zu. Verschoben " +
            "wird zwischen den Listen nicht von Hand, sondern in den Planungsphasen.",
    ),
    text(
        TutorialStage.LISTS,
        "Sammelliste",
        "Alles, was du notiert und noch nicht verplant hast — auch Quick-Adds, die " +
            "noch auf ihre Angaben warten. Was hier länger als einen Monat liegt, " +
            "wandert auf die Sperrliste; das Dashboard warnt rechtzeitig.",
        spot = TutorialSpot.LIST_COLLECTION,
    ),
    TutorialStep(
        stage = TutorialStage.LISTS,
        title = "Gedrückt halten: Neues anlegen",
        text = {
            "Halte die Überschrift »Sammelliste« gedrückt: Es öffnet sich ein " +
                "Fenster für ein neues, fertig ausgefülltes ToDo. Gib einen Namen " +
                "ein und sichere es. Das Fenster verdeckt diese Leiste; danach geht " +
                "es hier weiter."
        },
        kind = StepKind.TASK,
        spot = TutorialSpot.LIST_COLLECTION,
        checks = { listOf(TutorialCheck("Ein ToDo über die Überschrift anlegen", it.todoNoted)) },
    ),
    text(
        TutorialStage.LISTS,
        "Gedrückt halten: Einträge",
        "Dein ToDo steht jetzt in der Sammelliste. Einen Eintrag antippen oder " +
            "gedrückt halten öffnet sein Fenster: »Bearbeiten« ändert die Angaben, " +
            "»Erledigt« hakt ihn sofort ab, und der Papierkorb oben rechts löscht " +
            "ihn. Beides fragt vorher nach.",
        spot = TutorialSpot.LIST_COLLECTION,
    ),
    text(
        TutorialStage.LISTS,
        "Wochenliste",
        "Was du dir für diese Woche vorgenommen hast und noch keinem Tag zugeordnet " +
            "ist. Gefüllt wird sie in der Wochenplanung; aus ihr bedient sich der " +
            "Tagesplaner. Überschrift gedrückt halten legt ein ToDo direkt für diese " +
            "Woche an; Einträge lassen sich wie in der Sammelliste bearbeiten und löschen.",
        spot = TutorialSpot.LIST_WEEK,
    ),
    text(
        TutorialStage.LISTS,
        "Wiederkehrende Aufgaben",
        "Dein fester Wochenplan, mit der Skizze der Woche: Grün ist frei, Grau " +
            "belegt. Überschrift gedrückt halten legt eine neue wiederkehrende " +
            "Aufgabe an. Ein Eintrag öffnet die Aufgabe mit all ihren Wochentagen — " +
            "zum Bearbeiten oder, wenn sie nicht mehr passt, zum Beenden.",
        spot = TutorialSpot.LIST_RECURRING,
    ),
    text(
        TutorialStage.LISTS,
        "Tagesliste",
        "Alles, was heute im Plan steht, mit Uhrzeit. Diese Liste zeigt nur an: Ein " +
            "Eintrag öffnet sein Fenster mit »Im Tagesplan öffnen«, und der Planer " +
            "springt genau an diese Stelle — dort wird verschoben, gekürzt oder abgesagt.",
        spot = TutorialSpot.LIST_TODAY,
    ),
    text(
        TutorialStage.LISTS,
        "Liste für Morgen",
        "Erscheint, sobald du den morgigen Tag in der Tagesplanung bestätigt hast. " +
            "Überschrift gedrückt halten öffnet die Planung für morgen wieder, falls " +
            "sich etwas geändert hat.",
        spot = TutorialSpot.LIST_TOMORROW,
    ),
    text(
        TutorialStage.LISTS,
        "Erfolgsliste",
        "Was du abgehakt hast, mit Datum und Uhrzeit. Wiederkehrende Aufgaben " +
            "fehlen hier absichtlich — sonst stünde jeden Tag dasselbe darin. " +
            "Hier gibt es nichts zu bearbeiten: Es ist deine Chronik.",
        spot = TutorialSpot.LIST_DONE,
    ),
    text(
        TutorialStage.LISTS,
        "Termine",
        "Kalendertermine an späteren Tagen, mit Datum. Sie kommen aus dem Google " +
            "Kalender, falls du ihn verbunden hast, und werden in den Planungsphasen " +
            "übernommen.",
        spot = TutorialSpot.LIST_APPOINTMENTS,
    ),
    text(
        TutorialStage.LISTS,
        "Sperrliste",
        "Was einen Monat ungeplant in der Sammelliste lag, ist hier ein halbes Jahr " +
            "gesperrt und lässt sich so lange nicht neu anlegen. Das soll dich nicht " +
            "bestrafen, sondern ehrlich machen: Was du einen Monat nicht anfasst, " +
            "willst du gerade nicht wirklich. Das waren die Listen!",
        spot = TutorialSpot.LIST_LOCKED,
    ),
)

/**
 * The Extras, one at a time, on the card of a note being filled in.
 *
 * On the evening's card rather than in a dialog: a dialog is a window of its
 * own that covers the coach, and the box is the same one wherever it is mounted.
 */
val EXTRAS_STEPS: List<TutorialStep> = listOf(
    text(
        TutorialStage.EXTRAS,
        "Extras",
        "Extras sind Zusätze, die die meisten Aufgaben nicht brauchen. Deshalb liegen " +
            "sie eingeklappt unter jeder Karte — beim Ausfüllen einer Notiz, beim " +
            "Bearbeiten in den Listen und im Tagesplaner. Hier am Beispiel »Zahnarzt«.",
    ),
    TutorialStep(
        stage = TutorialStage.EXTRAS,
        title = "Aufklappen",
        text = {
            "Zugeklappt zeigt die Zeile nur, wie viele Extras aktiv sind. " +
                "Tippe auf »Extras«, um sie zu öffnen."
        },
        kind = StepKind.TASK,
        spot = TutorialSpot.EXTRAS,
        checks = { listOf(TutorialCheck("»Extras« aufklappen", it.saw(TutorialSignal.EXTRAS_OPEN, "1"))) },
    ),
    text(
        TutorialStage.EXTRAS,
        "Ton am Ende",
        "Eta meldet sich, wenn die geplante Zeit der Aufgabe abgelaufen ist — " +
            "praktisch für alles, worin man sich leicht verliert.",
        spot = TutorialSpot.EXTRA_SOUND,
    ),
    text(
        TutorialStage.EXTRAS,
        "Anfahrt und Rückweg",
        "Zum Zahnarzt musst du erst hinkommen. Anfahrt und Rückweg gehören zur " +
            "Aufgabe: Sie belegen Zeit im Tag, werden mit ihr verschoben und zählen " +
            "nicht für Punkte. »Als Nächstes« zeigt dann, wann du losmusst.",
        spot = TutorialSpot.EXTRA_TRAVEL,
    ),
    text(
        TutorialStage.EXTRAS,
        "Pause danach",
        "Eine Pause direkt im Anschluss. Passt sie beim Einplanen nirgends mehr hin, " +
            "fragt Eta, ob die Aufgabe ohne sie geplant werden soll — die Wege " +
            "dagegen entfallen nie.",
        spot = TutorialSpot.EXTRA_BREAK,
    ),
    text(
        TutorialStage.EXTRAS,
        "Pomodoro",
        "Teilt die Aufgabe in Abschnitte aus Arbeit und Pause, jeweils mit einem Ton " +
            "angekündigt. Für einen einzelnen Termin geht das auch auf dem Dashboard: " +
            "die Box »Gerade« gedrückt halten.",
        spot = TutorialSpot.EXTRA_POMODORO,
    ),
    text(
        TutorialStage.EXTRAS,
        "Erinnerung",
        "Eine Benachrichtigung vor dem Termin, mit einstellbarem Vorlauf und eigener " +
            "Botschaft — etwa »Versichertenkarte einpacken«. Alle anstehenden " +
            "Erinnerungen findest du im Reiter »Erinnerungen«.",
        spot = TutorialSpot.EXTRA_REMINDER,
    ),
    text(
        TutorialStage.EXTRAS,
        "Deadline",
        "Bis wann muss das ToDo erledigt sein? Solange eine Deadline läuft, zeigt " +
            "das Dashboard sie mit Countdown. Nur ToDos haben eine — wiederkehrende " +
            "Aufgaben laufen einfach weiter.",
        spot = TutorialSpot.EXTRA_DEADLINE,
    ),
    text(
        TutorialStage.EXTRAS,
        "Subtasks",
        "Die Schritte innerhalb einer Aufgabe, einzeln abhakbar. Bleibt am Abend " +
            "etwas offen, kannst du den Rest in die nächste Woche übernehmen. Im " +
            "Routine-Modus arbeitest du die Schritte der Reihe nach ab.",
        spot = TutorialSpot.EXTRA_SUBTASKS,
    ),
    text(
        TutorialStage.EXTRAS,
        "Das waren die Extras",
        "Wiederkehrende Aufgaben kennen zwei weitere: das Mengen-Inkrement und die " +
            "Growth-Task — zu ihr gibt es ein eigenes Tutorial. Für einen einzelnen " +
            "Termin änderst du die Extras im Tagesplaner: den Block gedrückt halten.",
    ),
)

val GROWTH_STEPS: List<TutorialStep> = listOf(
    text(
        TutorialStage.GROWTH,
        "Growth-Tasks",
        "Manches lässt sich in voller Länge nicht anfangen: eine Stunde lesen, " +
            "dreißig Minuten meditieren. Eine Growth-Task beginnt deshalb kurz und " +
            "wird mit jedem bestätigten Abschluss etwas länger — bis zu ihrer Zielzeit.",
    ),
    text(
        TutorialStage.GROWTH,
        "Drei Angaben",
        "Eine Growth-Task ist eine wiederkehrende Aufgabe mit drei Zusätzen: der " +
            "Dauer, mit der sie startet, der Zielzeit und dem Inkrement — dem, was " +
            "ein Schritt hinzufügt. Im Formular stehen sie unter »Extras«; das " +
            "Häkchen »Growth-Task« ist dort schon gesetzt.",
    ),
    TutorialStep(
        stage = TutorialStage.GROWTH,
        title = "Eine anlegen",
        text = {
            "Tippe auf »Growth-Task erstellen«. Gib einen Namen ein — etwa »Lesen« —, " +
                "wähle Wochentage und tippe auf »Erstellen«. Das Fenster verdeckt " +
                "diese Leiste; danach geht es hier weiter."
        },
        kind = StepKind.TASK,
        checks = { listOf(TutorialCheck("Eine Growth-Task erstellen", it.growthTasks > 0)) },
    ),
    text(
        TutorialStage.GROWTH,
        "So wächst sie",
        "Die Aufgabe steht jetzt in deinem Wochenplan wie jede wiederkehrende. Hakst " +
            "du sie ab und schließt den Tag ab, wächst sie um ihr Inkrement. Mit " +
            "»dynamischer Zeitsetzung« sucht sie sich ab ihrer frühesten Uhrzeit " +
            "selbst den nächsten freien Platz.",
    ),
    text(
        TutorialStage.GROWTH,
        "Die Reihenfolge",
        "Antippen öffnet eine Growth-Task zum Bearbeiten. Lange drücken und ziehen " +
            "ändert die Reihenfolge: Wollen zwei dynamische Aufgaben denselben Platz, " +
            "bekommt ihn die weiter oben. $WHERE_TO_SWITCH",
    ),
)

val CONTRACT_STEPS: List<TutorialStep> = listOf(
    text(
        TutorialStage.CONTRACTS,
        "Selbstverträge",
        "Ein Vertrag ist ein Versprechen an dich selbst — »Kein Handy im Bett«, " +
            "»Täglich 2 Liter Wasser«. Jeden Abend fragt Eta: gehalten oder " +
            "gebrochen? Ein gehaltener Tag bringt Punkte: 0,5, 1 oder 1,5, je nach Aufwand.",
    ),
    text(
        TutorialStage.CONTRACTS,
        "Drei Slots",
        "Mehr als drei Verträge laufen nie gleichzeitig. Wird einer gebrochen, " +
            "bleibt sein Slot einen Monat ab Vertragsschluss gesperrt. Deshalb steht " +
            "vor jedem Vertrag, was als Bruch gilt — und deine Unterschrift.",
    ),
    TutorialStep(
        stage = TutorialStage.CONTRACTS,
        title = "Einen schließen",
        text = {
            "Tippe in einem freien Slot auf »Vertrag schließen«. Fülle Kurzname, " +
                "Konditionen und Vertragsbruch aus, setze den Haken bei der Garantie, " +
                "unterschreibe mit dem Finger und tippe auf »Unterschreiben«. Das " +
                "Fenster verdeckt diese Leiste; danach geht es hier weiter."
        },
        kind = StepKind.TASK,
        checks = { listOf(TutorialCheck("Einen Vertrag unterschreiben", it.contracts > 0)) },
    ),
    text(
        TutorialStage.CONTRACTS,
        "Er läuft",
        "Der Vertrag belegt jetzt seinen Slot und wird jeden Abend im " +
            "Tagesabschluss abgefragt. Gedrückt halten erlaubt eine einzige Änderung " +
            "am Wortlaut. Kurz vor dem Ende der Laufzeit kannst du ihn verlängern " +
            "oder abschließen.",
    ),
    text(
        TutorialStage.CONTRACTS,
        "Legacy-Verträge",
        "Ein lange gehaltener Vertrag kann zum Legacy-Vertrag werden: Er gibt seinen " +
            "Slot frei, zahlt ein Fünftel und will trotzdem gehalten werden. " +
            WHERE_TO_SWITCH,
    ),
)

val REWARD_STEPS: List<TutorialStep> = listOf(
    text(
        TutorialStage.REWARDS,
        "Der Belohn-o-mat",
        "Hier sammelst du Belohnungen, auf die du länger hinarbeitest — ein Buch, " +
            "ein Konzert, ein freier Tag. Jede hat einen Namen und einen Preis in Punkten.",
    ),
    TutorialStep(
        stage = TutorialStage.REWARDS,
        title = "Eine anlegen",
        text = {
            "Tippe auf »Belohnung erstellen«, gib ihr einen Namen und einen Preis und " +
                "tippe auf »Erstellen«. Das Fenster verdeckt diese Leiste; danach " +
                "geht es hier weiter."
        },
        kind = StepKind.TASK,
        checks = { listOf(TutorialCheck("Eine Belohnung erstellen", it.rewards > 0)) },
    ),
    text(
        TutorialStage.REWARDS,
        "So füllt sie sich",
        "Die Karte ist zugleich der Balken. Jeden Abend fließen die Punkte deiner " +
            "erledigten Aufgaben in die oberste Belohnung — sie trägt »Füllt sich " +
            "gerade«, und der Tagesabschluss zeigt das auf einer eigenen Seite. Dein Punktekonto bleibt davon unberührt: " +
            "Der Belohn-o-mat zählt nebenher mit.",
    ),
    text(
        TutorialStage.REWARDS,
        "Reihenfolge und Bindung",
        "Gefüllt wird immer nur die oberste Belohnung; am ≡ ziehst du eine andere " +
            "nach oben. Anders mit Aufgabenbindung: Eine Belohnung, die an »Sport« " +
            "gebunden ist, sammelt dessen Punkte an jeder Stelle der Liste. Eine " +
            "Aufgabe gehört dabei zu genau einer Belohnung und zählt für keine andere.",
    ),
    text(
        TutorialStage.REWARDS,
        "Einlösen",
        "Ist eine Belohnung voll, löst du sie hier ein und gönnst sie dir. Antippen " +
            "bearbeitet eine Belohnung, gedrückt halten löscht sie. $WHERE_TO_SWITCH",
    ),
)
