# Capture UI6 reference screenshots and the car's Android version
Type: task
Status: claimed
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
