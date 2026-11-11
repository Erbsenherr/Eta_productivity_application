---
paths:
  - "app/src/**/ui/rules/**"
  - "app/src/**/domain/rules/**"
  - "app/src/**/Rule*.kt"
  - "app/src/**/ui/components/ConsequenceHatch.kt"
  - "app/src/**/ui/root/EtaApp.kt"
---

### Step 57: the Regelwerk's consequences, a breach reported, and the morning

The fourth step of the `update.txt` begun at step 54. Asked: **two new fields
per rule**, not sections of their own. Database **version 40**:
`rules.exception`, `consequence` (both `TEXT`, nullable),
`consequenceOnFreeTime` (0), and the table `rule_breaches`.

**This overrules step 53's "Nothing in the app reads a rule."**

#### The rule

- **"Ausnahmesituation"** — when the rule does not apply; shown under the
  answer when the card is unfolded, read by nothing.
- **"Konsequenz"** — the user's words for what follows a breach; red on the
  card. **"An die Freizeit knüpfen"** (a `CheckRow`, offered once there is a
  consequence; `RuleRepository.save` drops it without one).
- `RuleDraft` carries the five answers from the editor.

#### A breach

`RuleBreach` (`rule_breaches`: `ruleId` plain, `date`). `RuleRepository
.report(ruleId, date)` / `withdraw(breachId)`; deleting a rule deletes its
breaches by hand. Pure parts in `domain/rules/RuleBreaches.kt`, pinned in
`Step57Test`.

- **The "!" button** (`ReportBreachButton`): bottom right over **every tab**
  while the Regelwerk is on — not over a flow, which covers the tabs.
  `ReportBreachDialog` ("Verstoßen gegen …") lists the rules; **a rule is
  chosen by holding it**, a tap only says so (the hint turns amber). The
  breach is for today.
- **On that day**, `ConsequenceBox` stands in red right under the Heute page's
  header: the consequence (or the question, for a rule without one) and the
  rule. **A long press takes a report back**, asked first — mine; a report
  made by mistake would otherwise stand all day.
- **Tied to the free time**: `freeTimeConsequenceDays` → the root provides
  `LocalFreeTimeConsequence`, and `BlockWithItem.underConsequence()`
  (`ItemRole.FREE_TIME` on such a day) hatches the block red
  (`Modifier.consequenceHatch`, leaning the other way from the every-other-week
  hatching) **in the planner's `Timeline` — so the week timetable too —, the
  "Gerade" box and "Heute anstehend"**. And the consequence is **written into
  the note** of that day's free-time blocks (`withConsequenceNote`, "⚠
  Konsequenz: …", once), taken out again on withdrawal unless the same rule
  stands reported for the day once more. A free time with no block that day
  gets the red box and nothing else.
- **One `RulesViewModel`** (key "rules") serves the tab, the root and the
  dashboard; `RulebookState` is what the others read. It works out "today" on
  every emission.

#### The morning

`rulesToWatch`: rules broken in the `WATCH_DAYS` (14) **before** today — today's
are in the red box already — most recent first, each once. `WatchReminderBox`
("Heute im Blick", "Ein neuer Tag, eine neue Chance …", "Du schaffst das!")
stands on the Heute page **until noon** (`REMINDER_UNTIL_HOUR`) or until
"Danke", remembered per day in `SharedPreferences` ("rules", `reminded`).

**Known limits.** The "!" button can cover the last line of a tab's content.
A consequence tied to the free time is written into the notes when reported;
a free-time block laid down later that day does not get it. Nothing checks
the Ausnahmesituation.

**Verified by the compiler, the suite (778; 4 in `Step57Test`),
`assembleRelease`, `lint` and the migration against `40.json`.** Nobody has
seen any of it.
