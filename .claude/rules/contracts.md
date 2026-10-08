---
paths:
  - "app/src/**/domain/contract/**"
  - "app/src/**/domain/reevaluation/**"
  - "app/src/**/ui/contracts/**"
  - "app/src/**/ReevaluationService.kt"
  - "app/src/**/*Contract*.kt"
  - "app/src/**/*Settlement*.kt"
---

### Contracts and the evening settlement

`domain/contract/ContractRules.kt` and `domain/reevaluation/DailySettlement.kt`
hold the rules; `ReevaluationService` is the only thing that writes to the ledger.

- **Three slots, and a breach costs one for a month.** `slotStatuses` is built from
  *all* contracts, broken ones included — a breach keeps holding its slot, which is
  what stops the user signing a replacement the same evening. The lock runs a month
  from **signing**, not from breaking, exactly as `Selbstverträge.md` words it: a
  contract broken late in its term frees its slot sooner than one broken early.
- **Legacy contracts hold no slot, pay a fifth, and are capped at 2 points a day
  together.** The cap is hard and the surplus is forfeited; the crown appears when
  the *gross* would have exceeded it. That is the only reading under which both of
  the document's sentences are true, and the user confirmed it.
- **The settlement is previewed, then booked.** `settleDay` returns a value; the
  reevaluation shows the arithmetic and only the last step writes. Each component
  becomes its own `PointsTransaction` row rather than one net figure — the balance
  is a ledger, and a month later it still has to be readable.
- Unplanned time is charged only against **waking** hours: available minutes are
  the day minus the setup's night, and overlapping blocks count once.
- **A cancellation costs only on a day that had already begun.** Deciding on Monday
  evening that Tuesday's Sport is not happening is *planning*: the hours go back
  into a day that has not started and can still be filled. Only a day already
  confirmed and being lived can break a promise, and that is what the charge is
  for. `cancelledInAdvance` reads that off `discardedAt` against the block's own
  date — two dates already stored, so a column would only be a third thing to keep
  in step — and `cancellationCharged` gathers all three ways out of the charge in
  one place: free time (priced the other way round), an excuse, and advance notice.
  The freed hours are still ordinary hours, so leaving them empty is still charged
  as unplanned time; free is not the same as harmless. See *Step 13* for the price.
- **A planned task that was not done costs its hours as unplanned time.**
  `heldItsTime()` decides whether a block covered the stretch it sat on, and only a
  completed one does. Without this, the day charged for hours that were never
  claimed while letting a broken plan pass free — so planning something and
  abandoning it was strictly cheaper than not planning it, the opposite of what the
  charge is for. Filling the freed slot with something that did happen makes the
  charge go away by itself, because planned time is a union of what stood. A block
  left unanswered counts the same as a dropped one; the reevaluation gate normally
  prevents that, and `CatchUpService` does not. `DailySettlement.droppedHours`
  reports that share separately so the evening can say which half of the empty time
  was the user's own doing.
- **Free time is the mirror image**, and the one exception to the rule above: a
  `ItemRole.FREE_TIME` block that was *discarded* pays 4 points an hour, up to the
  day's free time — since step 18 the sum of the `FREE_TIME` definitions falling on
  the date, not the questionnaire's answer, or the cap and the task would drift
  apart the first time Freizeit was edited. Giving it up is a deliberate forfeit,
  not a failure, which is why it keys off `discardedAt` rather than incompleteness
  and why `heldItsTime()` still counts it as covering its hours: that decision is
  already priced, and the unplanned charge would put a second price on it. The cap
  exists because 4 points an hour against the single point an hour of focused work
  would otherwise make *planning free time in order to skip it* the most lucrative
  thing in the app. Confirmed with the user.

Contract verdicts default to **kept**. The question is "did you hold it", and a
breach is the exception; defaulting the other way would punish a skipped screen.

#### Breaking a contract without giving it up (step 17)

A breach used to have one outcome: the contract ended and its slot was locked for a
month. The user asked for a second, and it is the more interesting one — the
promise was not kept, but *giving it up* is not the only thing to do about that.

**`ContractState.PROBATION`** is a broken contract being served out. It stays in its
slot, the evening keeps asking whether it was held, and it pays **nothing** while
the lock runs. The habit continues and so does the consequence, instead of one
cancelling the other.

A state rather than a boolean on `BROKEN`, because four separate questions key off
it and a flag would be four conditions to keep in step: `isRunning` (so the evening
still asks), `dailyPayout` (zero — it fell into the existing `else` branch and
needed no new case), `slotLockedUntil` (unchanged: serving it out neither shortens
nor lengthens it) and `isEditable` (no — rewriting a broken promise being served
would turn the consequence into a way of picking something easier to keep). The
**settlement needed no change at all**: it pays `ACTIVE` in full and `LEGACY` at a
fifth, and a probation is neither. `ContractDao.findRunning` is the one query that
had to learn the name.

**The choice at the breach** belongs in the evening, because that is where the
breach happens. `ContractQuestion` asks the follow-up only once the first question
has been answered with "gebrochen" — a second question rather than a third option
on the first, since "was it kept" and "what now" are not the same question, and one
list of three would make the breach itself look like two kinds of breach. Not
answering it lets the contract go, which is what a breach always did. Offered for
**slot-holders only**: the whole shape of the thing is about the slot, and a legacy
contract holds none and already pays a fifth. A contract **already on probation**
is asked the first question and no more — `recordVerdict` gives it the date and
nothing else, since the consequence is already running and marking it broken a
second time would lose the fact that it is being served.

**The choice at the end of the month** belongs on the Verträge tab beside
`ExpiringBox`, which is the same shape of question. Two genuinely different
promises:

- **Aufkündigen** (`abandonedContract`) makes it an ordinary broken contract. The
  slot comes free by itself — the lock ran out before the question was put, and
  what was holding the slot was the *serving*, which stops here.
- **Neu starten** (`restartedContract`) keeps every word and puts the term back to
  zero: signed today, running as long as it originally did, so it needs a full
  month again before it can become legacy. A promise made again is a new promise.
  `editedAt` is deliberately **not** cleared: the single wording change is an
  allowance per contract, and breaking one must not hand out another.

**The slot holds until it is answered.** A probation holds it *past the end of its
own lock*, until the user decides. Freeing it the moment the month was up would let
a new contract be signed into it while the old one was still being answered for
every evening — one slot, two promises. `slotStatuses` therefore checks for a
probation in the slot *before* holders or locks, and `SlotStatus.Serving` is its own
status rather than a flag on `Locked`, because the slot is doing something
different: there is a contract in it, drawn as a contract, with `decisionDue`
saying whether the screen has a question to put or a countdown to show.

**Two kept weeks put it back into force** (step 33). A contract on probation
that is answered "gehalten" on `REINSTATEMENT_STREAK` (14) evenings **in a row**
may be reinstated before its lock runs out: "Wieder einsetzen" in its slot calls
the same `restartedContract` the end-of-month "Neu starten" does, which now
accepts either reason. It is `ACTIVE` again — paid, slot no longer red — and the
term is back at zero, so it needs a full month again before it can become legacy.

- **`Contract.probationKeptSince`** is the first day of the current run;
  `lastCheckedOn` is its last. `probationAnswered` is the evening's write: kept on
  the day after the last answer carries the run on, kept after a gap starts a new
  one, not kept clears it. The contract stays on probation whatever the answer.
- **An unanswered evening is a gap.** `probationStreak(today)` is zero once
  `lastCheckedOn` is older than yesterday — yesterday rather than today, because
  today's evening may not have come yet. The reevaluation dates its answers by the
  day it is *done*, so an evening closed after midnight breaks the run; known, and
  the same thing the streak already lives with.
- The slot shows "n von 14 Abenden in Folge gehalten" while it counts, so the run
  is something to watch grow rather than a button that appears from nowhere.
- A contract already on probation at the upgrade starts counting with its next
  kept evening; the migration leaves the column null.

The **signature is drawn with a finger** since step 23, and the two lists of
finished contracts are folded away. What the screen says: **a locked slot is drawn
red through**, not merely outlined —
`colors.dangerSoft` is a token of its own so the dark theme can pick its own wash
rather than getting a washed-out red over near-black. A locked slot is not a slot
with a warning on it, it is a slot that is out of action. **Broken contracts are
not listed at all** since step 31, at the user's request: the "Gebrochene Verträge"
box is gone and `ContractsUiState` no longer carries them. The rows stay in the
database, and what a breach costs is still shown where it bites — the locked slot
names the contract that locked it, and one being served out is in its slot, in red.
"Abgeschlossen" holds expiries only, which is what it was split down to in step 17.

#### Contracts can be changed once (step 11)

Long-pressing a contract opens `EditContractDialog`; the arithmetic is
`editedContract`, so the rule a test asserts and the one the screen applies are the
same code. Four rules, all from the user:

- **Only the text** — title, conditions, breach definition. `effort`, and with it
  the daily payout, deliberately cannot change: an edit that could raise the rate
  would make "bearbeiten" a way to pay yourself more for a promise already
  half-served.
- **Once, ever.** `Contract.editedAt` is the flag, `isEditable` the question. The
  long press stops offering and says why — `editRefusal` picks the sentence,
  because a gesture that silently does nothing is indistinguishable from one that
  is not there.
- **Legacy contracts cannot be edited at all.** They got their reduction by running
  a month; re-writing what they ask would make that month meaningless.
- **The term restarts**, keeping its length — `signedOn` becomes today and `endsOn` moves with it: changing what you promised is making a
  new promise.

