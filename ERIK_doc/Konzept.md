Das Ziel ist eine modern aussehende Productivity App mit einer smarten To-Do Liste/Wochenplaner als Kern und einem Belohnungssystem als Feature.

Die App soll nach best practices entwickelt und erweiterbar sein. Der Code soll so simpel wie möglich, allerdings so komplex wie nötig sein um die Designanforderungen zu erfüllen.

Die App soll durch organisches Swipen das Planen von Tagen ermöglichen, die UI sollte also damit im Hinterkopf konzipiert werden.

gewünscht wäre es, wenn die app ui mit foundations gebaut wird. Sollte das aus Gründen nicht gehen/unpraktisch sein, bitte anmerken



### ToDo Liste / Wochenplaner

Die ToDo Liste soll aus 6 Sublisten bestehen:
- Sperrliste
- Sammelliste
- Wochenliste
- Tagesliste
- Liste für Morgen
- Erfolgsliste
Das Konzept hinter dieser Ansammlung von listen ist das folgende: Alle neuen Einträge (ToDos) gehen in die Sammelliste.

Einmal pro Woche, am Wochenplanungstag (siehe [[Planungsphase]]), werden Ereignisse aus der Sammelliste in die Wochenliste gezogen.

Einmal pro Tag, in der Tagesplanung, siehe [[Planungsphase]], können dann toDos interaktiv in den Tag "gewoben" werden.

Abgehakte toDos (siehe [[Dashboard]] und [[Tägliche Reevaluation]]) wandern in die Erfolgsliste. Verbleibt ein toDo für mehr als einen Monat in der Sammelliste, wird es für ein HALBES JAHR in die Sperrliste geschoben und darf bis dahin nicht mehr erstellt werden.
--> toDos, die binnen einer Woche ablaufen würden, werden als "kritisch" im Dashboard angezeigt

Angezeigt werden sollen die Listen in einem eigenen Reiter "Smart toDos" als 6 übereinander-gestapelte Listen.

Die "Liste für Morgen" erscheint immer nachdem die Planung für den nächsten Tag abgeschlossen wurde und zeigt den Inhalt dieser Planung. Wenn in diesem Schirm der Bildschirm gedrückt gehalten wird, dann soll der Bearbeitungsmodus wieder aktiviert werden (Revolver fährt aus, alles kann bearbeitet werden.)