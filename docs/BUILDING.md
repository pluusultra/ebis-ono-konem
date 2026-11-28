# Сборка и устройство

Как собрать каждую версию и как всё устроено внутри. Коротко — в [README](../README.md).

## Модули

- `app` — Android (Jetpack Compose).
- `desktop` — ПК (Compose Multiplatform): тот же вид, те же шрифты и логотип.
- `shared` — общий код: модель задачи, формат `ebis-tasks.json`, слияние по задачам, разбор быстрого ввода, тексты.

## Синхронизация телефона и ПК

1. Заведи папку, которую синхронизируешь между телефоном и ПК (Syncthing, облако — что угодно).
2. Телефон: Настройки → «🔄 Синхронизация с ПК» → «📁 Выбрать папку».
3. ПК: при первом запуске выбери ту же папку.

Обе стороны пишут в `ebis-tasks.json` сразу после каждого изменения и сливают чужие правки по задачам:
у каждой задачи побеждает более свежая правка (`updatedAt`), удаления помнятся 60 дней.
Перед каждой записью приложение сначала перечитывает файл и вливает пришедшие правки, а конфликтные копии
Syncthing (`ebis-tasks.sync-conflict-*.json`) сливает в задачи и удаляет. Телефон, пока открыт, проверяет файл
каждые 3 секунды, ПК — каждые полторы.

## ПК: окно, трей, уведомления

- Окно без системной рамки, всегда 1100×760: свернуть и закрыть — в шапке, тащится за шапку, положение запоминается.
- Крестик прячет окно в трей (можно выключить в настройках). Запускается только одна копия: повторный запуск открывает уже работающее окно.
- Уведомления Windows — те же, что на телефоне (утро, нытьё, позорный столб, вечер, напоминалки по времени), с кнопками
  «Сделал», «Завтра, честно», «Нахер». Чтобы Windows показывала имя и значок, приложение при запуске кладёт ярлык
  «Ебись оно конём» в меню «Пуск» (AppUserModelID `Konem.EbisOnoKonem`) и регистрирует ссылку `ebis-konem://` для кнопок.
  Показ идёт через Windows PowerShell 5.1 (WinRT). На Linux — `notify-send`, без кнопок.
- Трей на Linux — по протоколу StatusNotifierItem (D-Bus, `LinuxTray.kt`): KDE, GNOME с AppIndicator, XFCE, Cinnamon.
  Если такого трея нет, остаётся обычный трей Java, а без трея крестик сворачивает окно.
- Автозапуск с Windows (сразу в трей, ключ `--hidden`) работает только у собранного `EbisOnoKonem.exe`.
- В настройках: оформление (узор и цвет фона, цвет шапки, шрифт Onest / Roboto / Montserrat / Nunito, масштаб)
  и переназначаемые горячие клавиши.

## Сборка

**Android для RuStore (Windows):** `setup-toolchain.ps1` (один раз), затем `./build.ps1` → `dist/rustore/nu-ego-konem.apk`.
Для следующего релиза передай номер версии, например `./build.ps1 -Version 2.4.2`.
Своим ключом APK подписывается, только если рядом есть `keystore.properties` и `keystore/ebis.jks` (их нет в репозитории,
см. [RELEASING.md](RELEASING.md)); без них — отладочным ключом.

Название для карточки — «Ну его конём». Идентификатор пакета `ru.konem.planner` позволяет установить версию рядом с обычной.
Задачи можно перенести через экспорт и импорт JSON в настройках. Для карточки снимай скриншоты этого APK.

**ПК, Windows:** после `setup-toolchain.ps1`:

```powershell
.\build-desktop.ps1             # dist\desktop\EbisOnoKonem\EbisOnoKonem.exe + переносной jar
.\build-desktop.ps1 -Installer  # ещё и установщик EbisOnoKonem-<версия>-windows-x64-setup.exe (нужен Inno Setup 6)
```

Установщик описан в `packaging/windows/ebis.iss`: ставит в `%LOCALAPPDATA%\Programs\EbisOnoKonem` без прав
администратора, новая версия встаёт поверх, старая установка из `.msi` удаляется сама. Картинки мастера —
`packaging/windows/make-wizard-images.py`.

**ПК, Linux:** нужен JDK 17+ (`sudo apt install openjdk-17-jdk`):

```sh
./build-desktop.sh          # dist/desktop/EbisOnoKonem/bin/EbisOnoKonem + переносной jar
./build-desktop.sh --deb    # ещё и пакет .deb
```

Готовые пакеты для раздачи (`.deb` и `.tar.gz` с `install.sh` без root; fakeroot не нужен):
после `./gradlew :desktop:createDistributable` запустить `packaging/linux/make-packages.sh` → `dist/linux/`,
AppImage — `packaging/linux/make-appimage.sh` (нужен [appimagetool](https://github.com/AppImage/appimagetool)).
Собирается и в WSL (Ubuntu) — JDK 17 можно просто распаковать в домашнюю папку.

Проверить сборку, не трогая свои задачи и настройки: запуск с `-Debis.profile=test`
(для exe — через `JAVA_TOOL_OPTIONS`), у профиля своя папка и свои настройки.

Переносной `ebis-ono-konem.jar` запускается и на Windows, и на Linux: `java -jar ebis-ono-konem.jar` (Java 17+).
Для цветных эмодзи на Linux нужен шрифт Noto Color Emoji (`sudo apt install fonts-noto-color-emoji`).

## ПК: клавиши

`Ctrl+N` — накинуть задачу (понимает «завтра в 15:00», «в пт», «каждый понедельник», «срочно»),
`Ctrl+1/2/3` — вкладки, клик по карточке — правка, `Ctrl+Enter` — сохранить, `Esc` — закрыть.

## Картинка окна и иконки

`./gradlew :desktop:test` рисует окно в `desktop/build/preview/` и пересобирает иконки в `desktop/icons/` без открытия окна.
