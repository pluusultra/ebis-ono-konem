#!/usr/bin/env bash
# AppImage — один файл для любого дистрибутива: скачал, chmod +x, запустил. Java внутри.
# Из собранной папки (./gradlew :desktop:createDistributable):
#   packaging/linux/make-appimage.sh <куда_положить>
# Нужен appimagetool (https://github.com/AppImage/appimagetool): в PATH или в переменной APPIMAGETOOL.
set -e
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$(mkdir -p "${1:-$ROOT/dist/linux}" && cd "${1:-$ROOT/dist/linux}" && pwd)"
IMAGE="$ROOT/desktop/build/compose/binaries/main/app/EbisOnoKonem"
ICON="$ROOT/desktop/icons/icon.png"
TOOL="${APPIMAGETOOL:-appimagetool}"
[ -x "$IMAGE/bin/EbisOnoKonem" ] || { echo "Нет $IMAGE — сначала ./gradlew :desktop:createDistributable" >&2; exit 1; }
VERSION="$(sed -n 's/^app\.version=//p; s/.*jpackage\.app-version=//p' "$IMAGE/lib/app/EbisOnoKonem.cfg" 2>/dev/null | head -n 1)"
[ -n "$VERSION" ] || VERSION="1.0.0"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

A="$WORK/EbisOnoKonem.AppDir"
mkdir -p "$A/usr/lib"
cp -r "$IMAGE" "$A/usr/lib/ebis-ono-konem"
cp "$ICON" "$A/ebis-ono-konem.png"
cp "$ICON" "$A/.DirIcon"
cat > "$A/AppRun" <<'RUN'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
exec "$HERE/usr/lib/ebis-ono-konem/bin/EbisOnoKonem" "$@"
RUN
chmod 755 "$A/AppRun" "$A/usr/lib/ebis-ono-konem/bin/EbisOnoKonem"
cat > "$A/ebis-ono-konem.desktop" <<DESK
[Desktop Entry]
Type=Application
Name=Ебись оно конём
Comment=Трекер херни, которую надо сделать
Exec=EbisOnoKonem
Icon=ebis-ono-konem
Terminal=false
Categories=Office;Utility;
StartupWMClass=ru-konem-ebis-desktop-MainKt
X-AppImage-Version=$VERSION
DESK

DEST="$OUT/EbisOnoKonem-$VERSION-linux-x86_64.AppImage"
rm -f "$OUT"/EbisOnoKonem-*-linux-x86_64.AppImage
# --appimage-extract-and-run: в контейнерах CI нет FUSE, а appimagetool сам — AppImage.
ARCH=x86_64 "$TOOL" --appimage-extract-and-run "$A" "$DEST" >/dev/null 2>&1 || ARCH=x86_64 "$TOOL" "$A" "$DEST"
chmod 755 "$DEST"
echo "Готово: $DEST"
