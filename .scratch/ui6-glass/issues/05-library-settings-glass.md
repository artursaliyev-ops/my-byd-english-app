# Lesson list and Settings screens in glass
Type: grilling
Status: resolved
Blocked by: 04

## Question

How do the Lesson list and the Settings screen look in UI6 glass, beyond the tile states already decided in Glass state styles and driving contrast? Settle:
- the I/II/III tab row (selected tab in BYD blue fill, underline, or light segment);
- the header (title, counts line, the Lesson N / Change folder / ⚙ buttons);
- the tile grid spacing and corner radius at 240 dpi;
- the Button mode choice buttons;
- the **Last button** log panel (monospace text on glass, or a darker inset panel).

Reference screenshots: `.scratch/ui6-glass/reference/` (Settings list, Display settings).

## Answer

Settled with the user on 2026-11-21. Measured on the car's Settings tiles: corner radius ≈ 10 dp (15 px at 240 dpi), gap ≈ 12 dp (18 px). The app uses 16 dp for both today. Error text is drawn in `colorScheme.error` (red) today.

| Element | Decision |
|---|---|
| I/II/III tabs | A segmented control on a glass track (white 10 %). The selected tab is a **BYD blue fill** with white text. The Material underline indicator goes. |
| Header | The title is white and the counts line is white 70 %. "▶ Lesson N" is **blue** (primary action). "Change folder" and ⚙ are borderless glass. |
| Tile geometry | Corner radius **10 dp**, gap **12 dp**, applied to every glass panel and button (the transcript panel too). Tile size and column count stay as they are. |
| Button mode choice | The same segmented control as the tabs. |
| Last button log | A **darker inset panel** (black 35 %). The newest line is white, older lines white 65 %. Monospace stays. |
| Settings text | Section titles are white, explanations white 70 %. |
| Empty and error screens | The primary button is blue and the secondary one glass. The error text is **white with a leading ⚠**, not red. |
