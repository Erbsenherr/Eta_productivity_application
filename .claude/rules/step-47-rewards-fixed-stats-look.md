---
paths:
  - "app/src/**/ui/rewards/**"
  - "app/src/**/*Reward*.kt"
  - "app/src/**/ui/stats/**"
---

### Step 47: a reward that is not changed, and a Statistiken page with three grounds

Three changes from `update.txt`, nothing asked first. No schema change.

#### A reward is made and deleted, never changed

**This overrules "Tap edits" and the editor's second life in *Step 35*.** Name,
price and binding are what was promised when the reward was set up.

- **A tap on a card does nothing**; the long press still deletes, ≡ still
  drags, "Einlösen" still takes a full one. Reordering is not a change to the
  entry and stays.
- `RewardDraft` has no `id` and no `of(…)`; `RewardsViewModel.create` replaced
  `save`; **`RewardRepository.update` is gone**, and with it the rule that a
  lowered price cuts the progress. The three windows (`EditorStage`) are now
  only how one is *made*, and the first says so before "Erstellen".
- **A binding can still be lost, never gained**: tasks another reward holds
  cannot be ticked for a new one (`taken`), so making a reward does not reach
  into an existing one — but a standing task that is ended or renamed still
  drops out of a binding by the name rule of step 35.
- The example in the name field is an ice cream ("Ein großer Eisbecher in der
  Eisdiele"), and the tutorial's two sentences follow both changes.
- Interpretation, not asked: "nicht veränderbar" was read as name, price and
  binding. Someone who wants a different price deletes and makes it again, and
  loses what was earned — the confirmation says how much.

#### Statistiken: three grounds

Asked for: prettier, and boxes easier to tell apart. Nothing in
`domain/stats/` changed — the same figures, drawn differently, all in
`StatisticsScreen.kt`:

- **`StatCard`** — a card of the page: surface, with an edge of 5 dp in a tint
  of its own down the left (`accent` for Überblick, `calendar` for the standing
  tasks, `success` for Timer and Inkrement). `FoldCard` is the one that folds;
  it replaced `EtaSurface` + `EtaExpander` here because the heading had to be a
  heading.
- **`StatTile`** — what stands inside a card, on the page's `background` with a
  border, so it reads as set into the surface. The overview's four figures are
  tiles two to a row (`chunked(2)`, `IntrinsicSize.Min` for equal heights; an
  odd one takes the whole row), each with the percentage in the title style and
  a `ShareBar`. One task is a tile too, with a dot in `colorOf(item)`.
- **Lines parted by a `Hairline`** inside a task. The three shares wear what
  they say: Ausgefallen `danger`, Plan geändert `warning`, Planmäßig `success`.
- **"Zeit je Kategorie"** is one bar cut by `weight` into the three category
  colours, with the hours under it.
- A share of nothing ("—") is drawn muted, not in its tint.

**Verified by the compiler, the suite (694, none new — no rule of the domain
changed), `assembleRelease` and `lint`.** Nobody has seen either screen; of the
Statistiken page that means the whole of the change — whether two tiles side by
side leave room for "Vorgenommene Tasks abgeschlossen" on a narrow phone least
of all.
