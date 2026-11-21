# Shape of the UI6 glass spec for hand-off
Type: grilling
Status: resolved
Blocked by: 05, 06

## Question

What does the hand-off spec look like? Settle:
- where the glass tokens live in `Theme.kt`: a custom `Ui6Colors` set via CompositionLocal, or mapped onto Material slots;
- whether the spec is a markdown doc in `docs/`, the prototype branch, or both;
- what the restyle hand-off must contain to be implementable in one go: the token table, the per-screen notes, the window/scrim setup, and the car check list.

## Answer

Settled with the user on 2026-11-21, and written in the same session.

- **Tokens**: a `Ui6` token set in `Theme.kt`, provided through a CompositionLocal. It also fills the main Material slots (`primary` = blue, `surface` = glass and so on), so stock components default sensibly. Material slots alone can't express pressed glass, the scrim or the inset.
- **Spec**: one markdown doc, [`docs/ui6-glass-spec.md`](../../../docs/ui6-glass-spec.md). It links to the prototype branch and the local reference screenshots, and the code is not the spec. Contents:
  - window and background;
  - tokens;
  - contrast;
  - states;
  - per-screen notes;
  - icons;
  - the car check list.
- **ADR**: [`docs/adr/0001-window-shows-system-wallpaper.md`](../../../docs/adr/0001-window-shows-system-wallpaper.md).
- **Hand-off**: an implementation ticket outside this map, [`.scratch/ui6-restyle/issues/01-apply-ui6-glass.md`](../../ui6-restyle/issues/01-apply-ui6-glass.md) (`Status: ready-for-agent`).
