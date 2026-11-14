#!/usr/bin/env sh
# Установка «Ебись оно конём» без root: в ~/.local, с ярлыком в меню приложений.
# Запуск из распакованного архива: ./install.sh
set -e
cd "$(dirname "$0")"

DEST="${XDG_DATA_HOME:-$HOME/.local/share}/ebis-ono-konem/app"
APPS="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
ICONS="${XDG_DATA_HOME:-$HOME/.local/share}/icons/hicolor/256x256/apps"
BIN="$HOME/.local/bin"

# Если приложение открыто — закрываем, иначе файлы не заменятся чисто.
pkill -f "ebis-ono-konem/app/bin/EbisOnoKonem" 2>/dev/null || true

rm -rf "$DEST"
mkdir -p "$DEST" "$APPS" "$ICONS" "$BIN"
cp -r EbisOnoKonem/. "$DEST/"
chmod +x "$DEST/bin/EbisOnoKonem"
cp ebis-ono-konem.png "$ICONS/ebis-ono-konem.png"
ln -sf "$DEST/bin/EbisOnoKonem" "$BIN/ebis-ono-konem"

cat > "$APPS/ebis-ono-konem.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=Ебись оно конём
Comment=Трекер херни, которую надо сделать
Exec="$DEST/bin/EbisOnoKonem"
Icon=ebis-ono-konem
Terminal=false
Categories=Office;Utility;
StartupWMClass=ru-konem-ebis-desktop-MainKt
EOF
chmod +x "$APPS/ebis-ono-konem.desktop"
command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database "$APPS" >/dev/null 2>&1 || true

echo "Готово. Ищи «Ебись оно конём» в меню приложений или запусти: $DEST/bin/EbisOnoKonem"
command -v notify-send >/dev/null 2>&1 || echo "Совет: для уведомлений поставь пакет libnotify (notify-send)."
