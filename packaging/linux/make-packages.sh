#!/usr/bin/env bash
# Из собранной папки (./gradlew :desktop:createDistributable) делает:
#   ebis-ono-konem_<версия>_amd64.deb      — для Ubuntu / Mint / Debian и родни
#   ebis-ono-konem-<версия>-linux-x64.tar.gz — для любого дистрибутива, ставится install.sh без root
# fakeroot не нужен: владелец файлов в .deb выставляется через dpkg-deb --root-owner-group.
#   packaging/linux/make-packages.sh <куда_положить>
set -e
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HERE="$ROOT/packaging/linux"
OUT="$(mkdir -p "${1:-$ROOT/dist/linux}" && cd "${1:-$ROOT/dist/linux}" && pwd)"
IMAGE="$ROOT/desktop/build/compose/binaries/main/app/EbisOnoKonem"
ICON="$ROOT/desktop/icons/icon.png"
[ -x "$IMAGE/bin/EbisOnoKonem" ] || { echo "Нет $IMAGE — сначала ./gradlew :desktop:createDistributable" >&2; exit 1; }
# jpackage пишет версию строкой java-options=-Djpackage.app-version=…; если её нет — из самого свежего jar.
VERSION="$(sed -n 's/^app\.version=//p; s/.*jpackage\.app-version=//p' "$IMAGE/lib/app/EbisOnoKonem.cfg" 2>/dev/null | head -n 1)"
[ -n "$VERSION" ] || VERSION="$(ls -t "$ROOT"/desktop/build/compose/jars/*.jar 2>/dev/null | sed -n 's/.*-\([0-9.]*\)\.jar$/\1/p' | head -n 1)"
[ -n "$VERSION" ] || VERSION="1.0.0"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# ── .deb ──
D="$WORK/deb"
mkdir -p "$D/DEBIAN" "$D/opt/ebis-ono-konem" "$D/usr/share/applications" "$D/usr/share/icons/hicolor/256x256/apps" "$D/usr/bin"
cp -r "$IMAGE/." "$D/opt/ebis-ono-konem/"
cp "$ICON" "$D/usr/share/icons/hicolor/256x256/apps/ebis-ono-konem.png"
ln -s /opt/ebis-ono-konem/bin/EbisOnoKonem "$D/usr/bin/ebis-ono-konem"
cat > "$D/usr/share/applications/ebis-ono-konem.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=Ебись оно конём
Comment=Трекер херни, которую надо сделать
Exec=/opt/ebis-ono-konem/bin/EbisOnoKonem
Icon=ebis-ono-konem
Terminal=false
Categories=Office;Utility;
StartupWMClass=ru-konem-ebis-desktop-MainKt
EOF
SIZE="$(du -sk "$D" | cut -f1)"
cat > "$D/DEBIAN/control" <<EOF
Package: ebis-ono-konem
Version: $VERSION
Section: utils
Priority: optional
Architecture: amd64
Maintainer: konem <konem@localhost>
Installed-Size: $SIZE
Recommends: libnotify-bin, fonts-noto-color-emoji
Description: Ebis ono konem - task tracker
 Личный трекер задач «Ебись оно конём», ПК-версия. Java внутри, ставить отдельно не нужно.
EOF
cat > "$D/DEBIAN/postinst" <<'EOF'
#!/bin/sh
command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database -q /usr/share/applications || true
command -v gtk-update-icon-cache >/dev/null 2>&1 && gtk-update-icon-cache -q -t /usr/share/icons/hicolor || true
exit 0
EOF
chmod 755 "$D/DEBIAN/postinst"
chmod -R u+rwX,go+rX,go-w "$D"
rm -f "$OUT"/ebis-ono-konem_*_amd64.deb
dpkg-deb --root-owner-group -Zxz --build "$D" "$OUT/ebis-ono-konem_${VERSION}_amd64.deb" >/dev/null

# ── tar.gz с install.sh ──
T="$WORK/tar/ebis-ono-konem-$VERSION"
mkdir -p "$T"
cp -r "$IMAGE" "$T/EbisOnoKonem"
cp "$ICON" "$T/ebis-ono-konem.png"
cp "$HERE/install.sh" "$HERE/uninstall.sh" "$T/"
sed -i 's/\r$//' "$T"/*.sh
chmod 755 "$T"/*.sh "$T/EbisOnoKonem/bin/EbisOnoKonem"
rm -f "$OUT"/ebis-ono-konem-*-linux-x64.tar.gz
tar czf "$OUT/ebis-ono-konem-$VERSION-linux-x64.tar.gz" -C "$WORK/tar" "ebis-ono-konem-$VERSION"

echo "Готово ($VERSION):"
ls -la "$OUT"
