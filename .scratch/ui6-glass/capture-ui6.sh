#!/usr/bin/env bash
# Ticket 01 helper: run in the car (parked, ADB on, laptop connected).
# Saves facts to reference/facts.txt and screenshots to reference/ui6-*.png.
set -euo pipefail
cd "$(dirname "$0")"
ADB="${ADB:-/opt/homebrew/share/android-commandlinetools/platform-tools/adb}"
OUT=reference
mkdir -p "$OUT"

echo "Devices:"; "$ADB" devices -l
read -rp "Serial of the car (blank = only device, NOT emulator-*): " SERIAL
A=("$ADB"); [ -n "$SERIAL" ] && A+=(-s "$SERIAL")

{
  echo "sdk=$("${A[@]}" shell getprop ro.build.version.sdk)"
  echo "release=$("${A[@]}" shell getprop ro.build.version.release)"
  echo "supports_background_blur=$("${A[@]}" shell getprop ro.surface_flinger.supports_background_blur)"
  echo "wm disable-blur: $("${A[@]}" shell wm disable-blur 2>&1 || true)"
  echo "--- dumpsys wallpaper"
  "${A[@]}" shell dumpsys wallpaper | grep -iE "mWallpaperComponent|mWidth|mHeight" || true
} | tee "$OUT/facts.txt"

for name in media-player settings-list dialog-blue-ok seat-ventilation after-wallpaper-change; do
  read -rp "Open '$name' on the car, then Enter (s = skip): " k
  [ "$k" = s ] && continue
  "${A[@]}" exec-out screencap -p > "$OUT/ui6-$name.png"
  echo "  saved $OUT/ui6-$name.png"
done

echo "Done. Crop/blur IMEI, ICCID and phone number in ui6-dialog-blue-ok.png before sharing."
