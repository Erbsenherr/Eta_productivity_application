Das ToDo-System basiert auf ToDo Objekten. Je nach Eigenschaften ist ein "ToDo" (Name subject to change) entweder ein bloßes ToDo, eine wiederkehrende Aufgabe/Tätigkeit oder eine Deadline.

Wiederkehrende Tätigkeiten sind immer datiert und nicht verschiebbar, genauso sind es Deadlines. Der einzige Unterschied ist, dass Deadlines mit Timer im Dashboard angezeigt werden sollen.

ToDos sollen hinterher als Karten flexibel in den Wochen bzw. Tagesplaner eingebaut werden eingebaut werden können.

Folgende Eigenschaften definieren den Charakter eines solchen ToDos:
*Legende:
/ = entweder-oder-Auswahl
| = Mehrfachauswahl möglich*

Typ: ToDo/Deadline/Wiederkehrend/Spend
Wenn ToDo:
Name: Name der Aufgabe
Startzeit: Zeitpunkt, ab dem das ToDo als für den Wochenplaner einbaubar erscheint. Eingabe ist ein Datum, das ToDo wird dann für eine Woche früher freigeschaltet.
Prognostizierte Dauer: Stunden:Minuten
Priorität: (Dropdown:
	1. Muss zeitnahe geschehen
	2. Muss geschehen
	3. Ich möchte, dass es zeitnah geschieht
	4. Ich möchte, dass es geschieht)
Kategorie: Fokus/Nebenbei/Achtsam
**Yield**: (wird berechnet und daher nur angezeigt) Zeit in Stunden multipliziert mit 1 wenn Kategorie = Fokus bzw. mit 0.5 wenn Kategorie = Nebenbei bzw. mit 1 wenn Nebenbei, allerdings Achtsam

Außerdem: Jedes toDo bekommt einen random Key und wird automatisch in die Sammelliste befördert. 

Wenn Wiederkehrend:
Name: Name der Aufgabe
Wiederkehrend wenn: (Mehrere Optionen sind geplant)
	- Monatlich: In der x-ten Woche des Monats,  Mo|Di|Mi|Do|Fr|Sa|So
	- Biweekly: KW gerade/ungerade, Mo|Di|Mi|Do|Fr|Sa|So
	- Weekly: Mo|Di|Mi|Do|Fr|Sa|So
Kategorie: Fokus/Nebenbei/Achtsam
**Yield**: (wird berechnet und daher nur angezeigt) Zeit in Stunden multipliziert mit 1 wenn Kategorie = Fokus bzw. mit 0.5 wenn Kategorie = Nebenbei bzw. mit 1 wenn Nebenbei, allerdings Achtsam

Wenn Deadline:
Name: Name der Aufgabe
Deadline: Datum und Uhrzeit der Deadline
Kategorie: Fokus/Nebenbei/Achtsam
**Yield**: (wird berechnet und daher nur angezeigt) Zeit in Stunden multipliziert mit 1 wenn Kategorie = Fokus bzw. mit 0.5 wenn Kategorie = Nebenbei bzw. mit 1 wenn Nebenbei, allerdings Achtsam

Wenn Spend: Siehe [[Belohnungssystem]]