# Сборка релиза

## 1. Создать ключ подписи (один раз)
```bash
keytool -genkeypair -v -keystore znatoki-release.jks -alias znatoki \
  -keyalg RSA -keysize 4096 -validity 10000
```
Храните `znatoki-release.jks` и пароли в надёжном месте (менеджер паролей + резервная копия). Потеря ключа = невозможность обновлять приложение.

## 2. keystore.properties (в корне проекта, не коммитится — уже в .gitignore)
```
storeFile=/полный/путь/znatoki-release.jks
storePassword=...
keyAlias=znatoki
keyPassword=...
```
Без этого файла release подписывается debug-ключом (только для тестов!).

## 3. Значения перед релизом
- `gradle.properties`: `APP_VERSION_CODE`, `APP_VERSION_NAME`, `APP_ID`, `SERVER_BASE_URL`.
- `app/build.gradle.kts` → `release { buildConfigField("boolean", "USE_MOCK", "false") }`, когда сервер запущен.
- (опционально) pin сертификата в `app/src/main/res/xml/network_security_config.xml`.

## 4. Сборка
```bash
./gradlew clean testDebugUnitTest assembleRelease bundleRelease
```
Результат:
- APK: `app/build/outputs/apk/release/app-release.apk`
- AAB: `app/build/outputs/bundle/release/app-release.aab`
- mapping для расшифровки крашей: `app/build/outputs/mapping/release/mapping.txt` (сохраняйте для каждой версии).

## 5. GitHub Actions
Workflow `.github/workflows/android.yml`:
- каждый push → тесты + debug APK в Artifacts;
- тег `v*` (например `git tag v1.0.0 && git push --tags`) → подписанные release APK + AAB в Artifacts.

Секреты репозитория (Settings → Secrets and variables → Actions):
| Секрет | Значение |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 znatoki-release.jks` |
| `KEYSTORE_PASSWORD` | пароль хранилища |
| `KEY_ALIAS` | `znatoki` |
| `KEY_PASSWORD` | пароль ключа |

## 6. Публикация в RuStore — по шагам
1. Зарегистрируйтесь на https://console.rustore.ru как разработчик и подтвердите аккаунт.
2. Пройдите верификацию разработчика Android (Google) и зарегистрируйте package name и ключ подписи — см. STORE_CHECKLIST.md.
3. Разместите `store/legal/privacy.html` и `terms.html` на сервере по адресам `/legal/privacy` и `/legal/terms`.
4. В консоли: «Создать приложение» → тип «Игра» → package name `uz.robutpit.znatoki`.
5. Заполните карточку из `store/listing.md`, загрузите иконку, баннер и скриншоты.
6. Анкета: возраст 12+, есть общение пользователей (UGC), сбор e-mail, удаление аккаунта, ссылка на политику.
7. Загрузите `app-release.aab` (и следуйте инструкции консоли по ключу подписи) или подписанный `app-release.apk`.
8. Отправьте на модерацию. Обычно проверка занимает от нескольких часов до нескольких дней; замечания модератора приходят в консоль.
