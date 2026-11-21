# Capture UI6 reference screenshots and the car's Android version
Type: task
Status: resolved
Blocked by:

## Question

What are the exact UI6 colors and alphas, and which Android version runs on the head unit? Camera photos distort color, so we need pixel-exact screenshots.

HITL checklist (car parked, developer mode/ADB on, laptop connected):
1. `adb devices`: the unit is listed.
2. `adb shell getprop ro.build.version.sdk` and `adb shell getprop ro.build.version.release`: note both.
3. Blur and wallpaper checks (see section 5 of `.scratch/ui6-glass/research/wallpaper-and-blur.md` on branch `research/wallpaper-and-blur`):
   - `adb shell getprop ro.surface_flinger.supports_background_blur` (must be 1 for the blurred-wallpaper option)
   - `adb shell wm disable-blur` (prints "Blur supported/enabled", API 31+)
   - `adb shell dumpsys wallpaper | grep -iE "mWallpaperComponent|mWidth|mHeight"` (static ImageWallpaper vs BYD live wallpaper service)
4. For each of these screens, open it on the car, then run `adb exec-out screencap -p > ui6-<name>.png`:
   - media / music player (playing)
   - a list screen (e.g. Settings list with one item selected)
   - a dialog with a blue OK button (e.g. Version Details; **crop or blur IMEI/ICCID/number before sharing**)
   - the seat ventilation screen (warm background)
   - the same screen after changing the wallpaper, if easy
5. Put the PNGs in `.scratch/ui6-glass/reference/` (gitignore them if they contain personal data).

The answer records the SDK level, the blur property, the wallpaper component, the file names, and the sampled hex/alpha values: BYD blue, card fill, dialog fill, divider, text.

## Comments

- 2026-11-21: Claimed. The car is not connected (adb sees only `emulator-5554`), so this waits for a session in the car. Steps 1–4 are scripted in `.scratch/ui6-glass/capture-ui6.sh`: it writes `reference/facts.txt` and `reference/ui6-*.png`. `reference/` is gitignored. After the run, a session samples the hex/alpha values from the PNGs and writes the Answer.
- 2026-11-21: Car connected over ADB on Wi‑Fi (phone hotspot, no internet needed on the car; `adb connect <car-ip>:5555`). Facts read:
  - DiLink 5.0 (`DiLink5_0_For_BYD_AUTO`), Android 12, **SDK 32**, build `SKQ1.230128.001`.
  - `ro.surface_flinger.supports_background_blur=1`; `wm disable-blur`: blur supported **true**, enabled **true**.
  - Wallpaper: `com.byd.wallpaperhome/com.byd.wallpaper.service.BydWallpaperService` (BYD **live** wallpaper service, not static ImageWallpaper), 1920×1920.
  - Screen 1920×1080, density 240. `com.android.documentsui` is present (SAF picker exists).
  - Still to do: the screenshots (step 4) and the colour sampling.

## Answer

Captured on the car on 2026-11-21 over Wi‑Fi ADB. The steps were run by hand rather than with `capture-ui6.sh`, which needs an interactive Enter per screen. Files are in `reference/` (gitignored): `facts.txt` plus `ui6-media-player`, `ui6-settings-list`, `ui6-dialog-blue-ok` (the Wi‑Fi dialog, no IMEI), `ui6-seat-ventilation`, `ui6-display-settings` and `ui6-after-wallpaper-change` (`.png`).

**Device**

- SDK 32 (Android 12), DiLink 5.0, system V1.7.0.
- Blur is supported and enabled.
- The wallpaper is `com.byd.wallpaperhome/…BydWallpaperService`, a live service, 1920×1920. It is changed from the home screen, not from Settings → Display.
- Screen 1920×1080 at **240 dpi**, so 1 dp = 1.5 px. The emulator AVD uses 160.

**The background is the wallpaper.** After the wallpaper changed, the same Settings screen went from warm olive/brown (about `#665046`) to blue‑grey (about `#2E363E`), and every glass surface followed it. The bottom nav bar is solid `#20232A`.

**Values.** Glass alphas are white overlays measured against the background right next to them, in the same image. Expect ±0.03, because the wallpaper is a gradient.

| Token | Value |
|---|---|
| BYD blue (Wi‑Fi toggle track; the "playing" bars icon is the same hue) | **`#3388FF`** |
| Toggle knob | `#F8F9FA` |
| Glass tile / button (Settings tiles, seat-screen buttons, segmented track) | white **≈ 10 %** (0.09–0.12) |
| Glass card, larger (album art card) | white ≈ 15 % |
| Selected nav chip (left column and second column) | white **≈ 19 %** (0.18–0.20) |
| Selected list row (media list) | white ≈ 6 % (0.05–0.07) |
| List divider (1 px) | white ≈ 6 % |
| Selected segment (Display Mode "Adaptive") | **solid light `#E4E2DB`**, dark text. Not blue |
| Light action button ("Update") | solid `#DCDEDE`, dark text |
| Dialog panel | **opaque `#2B2E33`**: the same over every background, so no visible blur |
| Dialog divider (1 px) | `#3C3E43` (≈ white 7 % on the panel) |
| Scrim behind a dialog | black **≈ 60 %** |
| Primary text | `#FFFFFF` |
| Secondary text on glass | ≈ `#D2D1C4` on warm (≈ white 80 %); `#C0C1C2` on the dialog |
| Text dimmed behind a scrim | `#666666` |

Notes for the next tickets:

- UI6 marks a **selected segment** with a **light solid fill**, not BYD blue. Blue shows up only on the toggle and the "playing" indicator. This pulls against the Accent decision (blue for the selected speed and tab). Prototype the Player screen in UI6 glass and Glass state styles and driving contrast should weigh both.
- UI6 dialogs are **opaque**, not frosted. The frosted look in the photos came from the camera.
- Display Mode also has **Light** and **Adaptive** on this car. Light stays out of scope.
