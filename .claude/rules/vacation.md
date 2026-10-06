---
paths:
  - "app/src/**/domain/vacation/**"
  - "app/src/**/ui/vacation/**"
  - "app/src/**/*Vacation*.kt"
---

### Vacation mode

`ui/vacation/` enters a date range; `domain/vacation/` decides what it means. Per
recurring task: **Aussetzen**, **Verschieben** (with a time), or nothing — and nothing
is the default. A holiday only changes what it was told to change; defaulting the
other way would empty the schedule the moment a range was entered.

- The decision belongs to the **definition** and lives in `vacation_rules`.
  Suppressing a holiday by deleting blocks does not hold, for the reason under *Model*
  — only a stored decision that expansion consults stays put.
- **"Verschieben" is an override, not an edit.** `recurrenceRule` and `startTime` are
  never touched, so the ordinary schedule is simply what returns when the range ends:
  nothing has to be restored and there is no cleanup to forget.
- The open question about catch-ups **answered itself**: a suspended task produces no
  block at all, so the evening never sees an occurrence to drop and never offers
  "Nachholen von …". Deciding in advance not to do something is not the same as
  dropping it, and the model says so without a special case.
- `expandRecurring` takes a **list** of plans, because a fortnight being materialized
  can easily reach past the end of one holiday into the next.
- `VacationRepository.save` also brings **already-materialized** days into line —
  expansion only decides what to *create*, and blocks laid down before the holiday was
  entered would otherwise sit there. It never touches days already past: what
  happened, happened, and the Erfolgsliste says so.

Reachable from the settings tab, where the standing configuration lives.

