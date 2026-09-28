# DudeBooru

«Чувак бору» — Android-приложение для Danbooru, Safebooru, Konachan и Yande.re: как сайт, только удобнее.
Лента во всю ширину с папками-источниками, боковое меню, лайки и сохранённые, негативные теги,
цензура NSFW в виде спойлера, рекомендации по лайкам.

Kotlin, Jetpack Compose (Material 3), minSdk 26. Без Google Play Services — сборка годится для F-Droid.

## Модули

- `booru` — чистый Kotlin: движки Danbooru и Moebooru, сеть с ограничением запросов, разбор поиска
  под лимит тегов, склейка каруселей, блэклист, шаблон имени файла, XMP. Тестируется на JVM.
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
