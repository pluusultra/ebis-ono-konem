#!/usr/bin/env sh
# Удаление версии, поставленной через install.sh. Задачи и настройки не трогает.
DATA="${XDG_DATA_HOME:-$HOME/.local/share}"
pkill -f "ebis-ono-konem/app/bin/EbisOnoKonem" 2>/dev/null || true
rm -rf "$DATA/ebis-ono-konem/app"
rm -f "$DATA/applications/ebis-ono-konem.desktop" "$DATA/icons/hicolor/256x256/apps/ebis-ono-konem.png" "$HOME/.local/bin/ebis-ono-konem"
rm -f "${XDG_CONFIG_HOME:-$HOME/.config}/autostart/ebis-ono-konem.desktop"
echo "Удалено. Задачи остались в $DATA/ebis-ono-konem и в папке синхронизации."
