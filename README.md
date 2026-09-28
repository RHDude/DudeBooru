# DudeBooru

«Чувак бору» — Android-приложение для Danbooru, Safebooru, Konachan и Yande.re: как сайт, только удобнее.
Лента во всю ширину с папками-источниками, боковое меню, лайки и сохранённые, негативные теги,
цензура NSFW в виде спойлера, рекомендации по лайкам, подписки на художников с уведомлениями.

Kotlin, Jetpack Compose (Material 3), minSdk 26. Без Google Play Services — сборка годится для F-Droid.
Интерфейс на русском и английском.

## Установка

APK — в [Releases](../../releases). Приложение само проверяет новые версии («О приложении» →
«Проверить обновления», плюс уведомление раз в сутки) и ставит их поверх — для этого репозиторий
должен быть публичным: закрытый GitHub анонимно не отдаёт.

Все выпуски подписаны одним ключом. Отпечаток сертификата (SHA-256):
`97c4df13bdc40fe0ba8211e0ea7f9addb5cf9004aa0377b501ded4c23451df58`
(открытая часть — `.github/release/signing-cert.pem`).

## Модули

- `booru` — чистый Kotlin: движки Danbooru и Moebooru, сеть с ограничением запросов, разбор поиска
  под лимит тегов, склейка каруселей, блэклист, шаблон имени файла, XMP, проверка выпусков GitHub.
  Тестируется на JVM.
- `app` — Android: Room, DataStore, WorkManager, интерфейс на Compose.

## Сборка

Нужны JDK 21 и Android SDK (platform 37, build-tools 36).

```
./gradlew :app:assembleDebug
./gradlew :booru:test
```

Тесты против живых сайтов не запускаются по умолчанию:

```
./gradlew :booru:test -Plive --tests '*LiveApiTest*'
```

Если сайты не открываются напрямую, тесты берут прокси из `HTTPS_PROXY`; в приложении прокси задаётся
в Настройки → Сеть.

Сборка для F-Droid без проверки обновлений: `./gradlew :app:assembleRelease -PnoUpdateCheck`.

## Выпуск

GitHub Actions: `CI` собирает и тестирует каждый пуш (debug APK — в артефактах), `Release` выпускает версию
из `app/build.gradle.kts` по коммиту с `[release]` в сообщении. Заметки к выпуску —
`.github/release/notes/<версия>.md`.

Подпись:

- **Секрет `DUDEBOORU_SIGNING_KEY`** (Settings → Secrets and variables → Actions) — закрытый ключ P-256
  в hex (64 символа) или PEM. Тогда `[release]` сразу подписывает и публикует.
- **Без секрета** ключ не покидает владельца: сборка печатает хеш содержимого APK, владелец подписывает его
  (`python3 .github/scripts/apk_v2.py signed-data <хеш> .github/release/signing-cert.pem data.bin`,
  `openssl dgst -sha256 -sign key.pem -out sig.der data.bin`), кладёт `.github/release/signatures/<версия>.json`
  с `version`, `run_id`, `commit`, `signature` (hex) и пушит коммит с `[publish]`.

Локальная подписанная сборка — `keystore.properties` в корне (в git не попадает):

```
storeFile=release.p12
storePassword=…
keyAlias=dudebooru
keyPassword=…
```
