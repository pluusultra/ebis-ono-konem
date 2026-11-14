#!/usr/bin/env sh
# ПК-версия под Linux. Нужен JDK 17+ (например, `sudo apt install openjdk-17-jdk`).
#   ./build-desktop.sh         — папка с исполняемым EbisOnoKonem (без установки) + переносной jar
#   ./build-desktop.sh --deb   — ещё и пакет .deb (нужны dpkg и fakeroot)
#   ./build-desktop.sh --rpm   — ещё и пакет .rpm (нужен rpmbuild)
set -e
cd "$(dirname "$0")"

if ! command -v java >/dev/null 2>&1 && [ -z "$JAVA_HOME" ]; then
    echo "Нужен JDK 17+: sudo apt install openjdk-17-jdk (или аналог для твоего дистрибутива)" >&2
    exit 1
fi

TASKS=":desktop:createDistributable :desktop:packageUberJarForCurrentOS"
for arg in "$@"; do
    case "$arg" in
        --deb) TASKS="$TASKS :desktop:packageDeb" ;;
        --rpm) TASKS="$TASKS :desktop:packageRpm" ;;
    esac
done

chmod +x ./gradlew
# Без Android SDK модуль :app просто не подключается (см. settings.gradle.kts).
./gradlew $TASKS --console=plain

OUT=dist/desktop
BIN=desktop/build/compose
rm -rf "$OUT"
mkdir -p "$OUT"
cp -r "$BIN/binaries/main/app/EbisOnoKonem" "$OUT/EbisOnoKonem"
cp "$(ls -t "$BIN"/jars/*.jar | head -n 1)" "$OUT/ebis-ono-konem.jar"
for f in $(ls -t "$BIN"/binaries/main/deb/*.deb 2>/dev/null | head -n 1) $(ls -t "$BIN"/binaries/main/rpm/*.rpm 2>/dev/null | head -n 1); do
    [ -f "$f" ] && cp "$f" "$OUT/"
done
echo "Готово: $OUT"
echo "Запуск: $OUT/EbisOnoKonem/bin/EbisOnoKonem   (или: java -jar $OUT/ebis-ono-konem.jar)"
