# Как выпустить релиз

Всё собирает GitHub Actions (`.github/workflows/release.yml`). Руками — только тег.

1. Допиши в `CHANGELOG.md` раздел `## [2.1.0] — ДД.ММ.ГГГГ` — он станет описанием релиза.
2. Закоммить и запушь в `main`.
3. Поставь тег и запушь его:

   ```sh
   git tag v2.1.0
   git push origin v2.1.0
   ```

Через ~15 минут в [Releases](https://github.com/pluusultra/ebis-ono-konem/releases) появится релиз со всеми файлами:
APK, установщик и portable-архив для Windows, AppImage, deb, rpm, tar.gz, universal jar и `SHA256SUMS.txt`.

- Тег с дефисом (`v2.1.0-beta.1`) — пре-релиз, на странице «последний релиз» не показывается.
- Пробная сборка без публикации: Actions → Release → Run workflow, указать версию. Файлы — в артефактах запуска.
- Версия везде одна — из тега. Android: `versionCode = X*10000 + Y*100 + Z`, поэтому каждая следующая версия должна быть больше.

## Ключ подписи APK

Все APK подписываются одним ключом — иначе новая версия не встанет поверх старой. В репозитории ключа **нет**.

- Локально: `keystore/ebis.jks` и `keystore.properties` в корне проекта (оба в `.gitignore`):

  ```properties
  storeFile=keystore/ebis.jks
  storePassword=…
  keyAlias=ebis
  keyPassword=…
  ```

- В GitHub: Settings → Secrets and variables → Actions → секреты `ANDROID_KEYSTORE_BASE64`
  (base64 от `ebis.jks`), `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.
  Без них релиз по тегу упадёт, а не выпустит APK с чужим ключом.

**Потерять ключ нельзя**: без него обновление поставится только через удаление приложения (и задач вместе с ним).
Держи копию `ebis.jks` и паролей где-нибудь вне компьютера.
