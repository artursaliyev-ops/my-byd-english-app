# Can the app show the DiLink wallpaper behind it, blurred?
Type: research
Status: resolved
Blocked by:

## Question

On the BYD DiLink head unit (minSdk 28 / targetSdk 33, Android version TBD by 01), can a Compose app:
1. Show the **system wallpaper** behind its window (`android:windowShowWallpaper`, translucent window theme), and does DiLink's launcher/wallpaper service actually provide it to third-party apps?
2. **Blur** what is behind a panel: `RenderEffect`/`Modifier.blur` (API 31+), `windowBackgroundBlurRadius`/`FLAG_BLUR_BEHIND` (API 31+, needs `ro.surface_flinger.supports_background_blur`), and fallbacks for API 28–30 (e.g. sampling the wallpaper drawable via `WallpaperManager` and blurring it ourselves, permission needs).
3. What does each option cost (GPU on a car SoC, READ_EXTERNAL_STORAGE/wallpaper permission on 33)?

The answer gives the feasible background options ranked by fit, which become the prototype variants in 03.

## Comments
Research running on branch `research/wallpaper-and-blur` → `.scratch/ui6-glass/research/wallpaper-and-blur.md` (throwaway, never merged).

## Answer

Findings: branch `research/wallpaper-and-blur`, file `.scratch/ui6-glass/research/wallpaper-and-blur.md` (commit 5b996ce, throwaway, never merged). Ranked background options:
1. **Translucent window + `windowShowWallpaper` + `windowBackgroundBlurRadius` (API 31+)**: the system blurs the real wallpaper, no permissions, almost no code. Needs `ro.surface_flinger.supports_background_blur=1` on the car. The system can switch blur off (battery saver, thermal), so the app listens with `addCrossWindowBlurEnabledListener` and falls back to option 2.
2. **Same window without blur (API 28+)**: the wallpaper shows through tinted, semi-opaque panels. About 5 lines of theme XML. Needs a car check that the BYD wallpaper is a real system wallpaper (`dumpsys wallpaper`).
3. **Plan B**: a bundled UI6-style background image + Haze 2.1.0 for in-app glass (blur on 31+, tint below). Independent of the car.
4. **Avoid**: `WallpaperManager.getDrawable()` + own blur. It needs storage/all-files permission and gives a stale image with BYD's live wallpaper.
5. `Modifier.blur`/`RenderEffect` can't blur behind the window. `getWallpaperColors()` (no permission) can tint panels to match the wallpaper in any option.

The car-side checks (API level, blur support, wallpaper service) were added to ticket 01. Untested: the "non-floating window still gets blur" claim (from AOSP 12 source, not the docs) and the GPU cost (an estimate).
