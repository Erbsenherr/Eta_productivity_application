Im Setup Questionaire werden die Zeitpunkte für die Planungsphasen festgelegt. Der Nutzer soll über einen Alarm über die Planungsphase informiert werden. Es gibt eine Snooze funktion (5 Minuten) und über eine manuelle "NOTFALL" Eingabe kann sie um eine Anzahl Stunden verschoben werden, andernfalls wird (alle 5 Minuten) der Alarm abgespielt, sofern der Nutzer nicht die App offen hat oder den Planungsprozess vollständig abgeschlossen hat (Bestätigung am Ende)

Wochenplanung:
Teil 1: Inflation. 30% der angesparten Punkte gehen verloren
Teil 2: Wöchentliches Evaluationsfenster. Kleines Textfeld, das verbesserungswünsche zur letzten Woche ermöglicht. (Ähnlich wie bei [[Tägliche Reevaluation]], Einträge werden getimestamped und archiviert)
Teil 3: Der Hauptteil:
Nach Setup-Questionaire und Erstellung der anderen wiederkehrenden Aufgaben wird unter Abzug der Schlafzeiten und der Sozialzeiten ([[Setup-Questionaire]]) eine Freistunden-Anzahl pro Woche berechenbar sein.
Der Wochenplan Bildschirm wird also grob aus einer Tabelle mit zwei Spalten bestehen: Links die Einträge der Sammelliste, rechts die Liste für die geplante Woche. Oben soll die Anzahl der verbliebenen Freistunden (also, unbelegte Zeiteinheiten) angezeigt werden. Für jede Tätigkeit, die mindestens eine Stunde dauert sollen 15 Minuten Pause direkt einberechnet werden, pro 1.5 Stunden oder mehr sollen 25 Minuten Pause verbucht werden.

Tagesplanung:
Die Tagesplanung setzt sich aus folgenden Phasen zusammen:
1. [[Tägliche Reevaluation]]
2. Konkretisierung der Quick-ToDos (Siehe [[Dashboard]])
3. Planung des nächsten Tages
4. Bestätigung
Zu "Planung des nächsten Tages"

Draft des Designs:
![[Paint Draft Dayplanner.png]]
Es ist nur ein Ausschnitt gezeigt, es soll von 0 bis 0 gescrollt werden können. Schlafzeiten sollen farblich hervorgehoben werden.

Es soll oben ein "Revolver-Menü" geben, in dem alle ToDos der zugehörigen Woche enthalten sind. Die Kreise links und rechts sollen also ebenfalls toDos sein. Die ToDo-Blasen aus dem Revolver Menü sollen dann per drag and drop in den Tag gezogen werden. Events, die durch wiederkehrende Tasks in den Tag gesetzt wurden, sollen eckige kanten haben. Events, die durch toDo-Drag and drop in den Tag gezogen wurden, sollen runde kanten haben.

Sollte für den Tag aus dem verbundenen Google-Kalender ein ganztägiges Event angesetzt sein, dann soll hier gefragt werden, ob das event tatsächlich ganztäglich ist, oder ob man start und Endzeiten zufügen möchte, um wiederkehrende Events wie Morgenroutinen nicht zu überspringen.
Generell sollen aus dem Google-Kalender importierte Events anders hervorgehoben werden (nicht im Draft enthalten)

Es soll unten rechts (nicht im draft enthalten) noch einen Knopf geben, mit dem man den Revolver wechseln kann. Der andere Revolver soll (für erste) nur 3 Einträge haben: "Custom Spend", "Custom Earn" und "Social". Upon creation bekommen sie, wie die normalen toDos, einen key. Diese dauern standardmäßig eine Stunde. ALLE tasks (inklusive toDos) können hier allerdings auch noch bearbeitet werden, indem man sie lange gedrückt hält.