---
paths:
  - "app/src/**/ui/lists/**"
---

### The "Smart toDos" (Listen) tab

`ui/lists/` shows the six lists of `Konzept.md` stacked on one screen, and is
**read-only about placement**. Every one of these lists is what a phase left behind
— the Sperrliste of the weekly sweep, the Wochenliste of the weekly planning, the
Tagesliste of the day planner — so *moving* a card here would be a second route
around the rules those phases enforce. The way to move a card is to run the phase
that moves it. Keep it that way when adding to this screen.

It needed no new data at first: `observeStage`, `observeDay`, `observeDayPlan` and
`observeErfolgsliste` already carried everything. Sections collapse; the Sperrliste
and the Erfolgsliste start closed, being backlog and history rather than today. The
one gesture from the concept: **long-pressing "Liste für Morgen" opens the
planner**, which is how that list is reopened for editing.

Two sections are not among the concept's six, and both exist because nothing else
on the screen could hold them:

- **`ListSection.TERMINE`** — imported appointments **strictly after today**,
  cancelled ones left out. Every other list here is today, tomorrow or an undated
  backlog, so an appointment three weeks out was in the database, drawn in a
  planner nobody has opened, and visible nowhere. Strictly after, because today is
  the Tagesliste's and tomorrow the Morgenliste's. The rows lead with the **date**,
  unlike every other list here, since the day is the thing being looked up.
  Read-only: it is a block, and the planner owns when it happens.
- **`ListSection.WIEDERKEHREND`** — every live recurring definition, and unlike the
  lists around it **editing is its purpose**: a day holds one occurrence, and this
  is where the task itself is. Everything the questionnaire laid down appears here
  without any migration, those rows always having been ordinary definitions. See
  *The standing schedule* under *Step 18*. Since step 25 the box opens with the
  **incomplete recurring notes** (moved out of the Sammelliste on this screen only —
  they still sit in `Stage.COLLECTION` and on its one-month clock) and the
  **Wochenschema** below them — see *Step 25*.

**Attributes are editable, placement is not.** A wrong duration or priority is a
typo, and running a whole planning phase to fix a typo is the tail wagging the dog.
Tapping a row opens `ItemDetailDialog`, which offers **Bearbeiten** and
**Erledigt** on the **Sammelliste** and the **Wochenliste** only, with deleting in
a wastebasket in the corner — see *Step 21*. Tages- and Morgenliste show the name
and nothing else; the Sperrliste is a rule with a date on it; the Erfolgsliste has
no card behind its rows at all, so a long name there gets three lines rather than a
popup that could add nothing.

