# Клуб знатоков — Android

Мультиплеерная интеллектуальная игра «знатоки против зрителей»: волчок, команда до 6 человек с голосовым обсуждением, блиц, суперблиц, чёрный ящик, помощь зала, рейтинг, друзья и команды, пользовательские вопросы и ИИ-генератор. Языки: ru и uz.

## Быстрый старт
```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # unit, интеграционные и Robolectric UI-тесты (+ скриншоты в app/build/screenshots)
./gradlew bundleRelease        # AAB для RuStore (см. RELEASE.md)
```
Нужны JDK 17+ и Android SDK (platform 36). Откройте папку в Android Studio.

## Mock-режим
По умолчанию приложение работает на встроенном `FakeGameServer`: сервер не нужен. Вход — любой e-mail и пароль от 8 символов. Отключить мок можно в debug: «Настройки → Отладка → Mock-сервер», а для release — флаг `USE_MOCK` в `app/build.gradle.kts`.

## Что подставить
| Что | Где |
|---|---|
| URL сервера | `gradle.properties` → `SERVER_BASE_URL` (сейчас `https://game.robutpit.com`) |
| Package name | `gradle.properties` → `APP_ID` (сейчас `uz.robutpit.znatoki`) |
| Версия | `APP_VERSION_CODE`, `APP_VERSION_NAME` |
| Ключ подписи | `keystore.properties` (см. RELEASE.md) или секреты GitHub Actions |
| Мок → реальный сервер | `USE_MOCK` в `app/build.gradle.kts` (release) |
| Ключи LiveKit, Claude | **Не нужны в приложении**: только сервер выдаёт токены |
| FCM / RuStore Push | реализовать `PushProvider` (см. DECISIONS.md) |

## Структура
```
core/       часы сервера (offset), ошибки, фильтр мата, защита от двойных нажатий, язык
domain/     контракт (модели), GameGateway, SpinMath, GameReducer, AnswerChecker
data/mock   FakeGameServer + MockGameEngine (боты, фазы, голосования, зал)
data/remote Retrofit API, Socket.IO-клиент, TokenStore (Keystore)
data/local  Room-кэш, DataStore-настройки
voice/      LiveKit, TTS ведущего, распознавание речи, foreground-service
sound/      синтез звуков и музыки
design/     тема (тёмная/светлая), компоненты, аватары, таймер
feature/    auth, home, lobby, game (стол, вопрос, голосования), social, questions, profile, settings
```

## Тесты
- `SpinMathTest` — угол волчка совпадает с сектором сервера для 1000 случайных seed, детерминизм, монотонность кривой.
- `ServerClockTest` — offset и нормализация времени.
- `GameReducerTest` — конечный автомат клиента, seq, resync.
- `ContractParsingTest` — разбор JSON контракта и проверка 60 встроенных вопросов.
- `AnswerCheckerTest` — нормализация, fuzzy.
- `FakeGameServerTest` — клиент проходит целые игры до 6 очков (время ускорено ×100): все типы секторов, доп. минута, исключение, помощь зала, апелляция; стол из 7 секторов; REST мока.
- `ScreensUiTest` (Robolectric) — ключевые компоненты: стол, вопрос, чёрный ящик, ответ, защита голосования от случайных нажатий, итог игры, чат и ввод ответа.

Документы: `DECISIONS.md`, `ASSETS_LICENSES.md`, `RELEASE.md`, `STORE_CHECKLIST.md`, `store/`.
