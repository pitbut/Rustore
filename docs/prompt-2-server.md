# ПРОМПТ ДЛЯ CLAUDE CODE — сервер «Клуб знатоков» (запускать на VPS)

Ты — старший backend-разработчик и DevOps. Построй и разверни на этом VPS авторитарный игровой сервер для Android-приложения «Клуб знатоков» (мультиплеерная интеллектуальная игра «знатоки против зрителей»). Работай автономно, этапами, после каждого этапа проверяй. Мелкие решения принимай сам и записывай в `DECISIONS.md`. Спрашивай меня только когда без ответа нельзя продолжать.

---

## 0. ГЛАВНОЕ ПРАВИЛО: НЕ СЛОМАТЬ ТО, ЧТО УЖЕ РАБОТАЕТ НА VPS

На этом сервере уже работает мой сайт (и, возможно, другие сервисы). **Он должен продолжать работать без единой минуты простоя.** Это важнее любой функции игры.

### 0.1. Сначала разведка — только чтение
До любых изменений выполни и сохрани в `/opt/znatoki/RECON.md`:
- ОС и версия, свободные RAM/диск/CPU (`uname -a`, `cat /etc/os-release`, `free -h`, `df -h`, `nproc`).
- Какие сервисы запущены и какие порты заняты: `ss -tulpn`, `systemctl list-units --type=service --state=running`, `docker ps -a` (если есть docker), `pm2 ls` (если есть).
- Какой веб-сервер обслуживает сайт (nginx / apache / caddy / traefik / docker-контейнер) и его конфиги: `nginx -T` или аналог. Перечисли все `server_name`, корни сайтов, где лежат SSL-сертификаты и чем они продлеваются (certbot timer / acme.sh / caddy).
- Файрвол: `ufw status verbose`, `iptables -S`, `nft list ruleset` (что есть).
- Установленные Node.js / PostgreSQL / Redis / Docker и их версии — **которыми может пользоваться сайт**.
- Cron-задачи (`crontab -l`, `/etc/cron.*`).

Покажи мне краткую сводку разведки и план интеграции **до** первых изменений. Если сайт работает в Docker или через нестандартную схему — сначала спроси.

### 0.2. Жёсткие запреты
- ❌ Не трогать файлы, корни, конфиги и контейнеры существующего сайта. Не менять его `server_name`, `location`, сертификаты.
- ❌ Не перезапускать, не останавливать и не обновлять сервисы, которыми пользуется сайт (nginx — только `reload` после `nginx -t`, никогда `restart`/`stop`).
- ❌ Не делать `apt upgrade` / `apt dist-upgrade`, не обновлять системные Node.js/PHP/Python/PostgreSQL. Ставить только недостающее, в изолированном виде.
- ❌ Не занимать порты 80/443 напрямую — только через уже работающий веб-сервер (новый виртуальный хост). Не занимать порты, уже занятые по `ss -tulpn`.
- ❌ Не включать/перенастраивать файрвол так, чтобы закрыть уже открытые порты. Только **добавлять** точечные правила для новых портов, если они реально нужны.
- ❌ Не использовать общую БД сайта. Для игры — отдельная БД/контейнер и отдельный пользователь.
- ❌ Не удалять ничего, что создал не ты. `docker system prune`, `rm -rf` вне `/opt/znatoki` — запрещены.
- ❌ Никаких секретов в git, логах и в ответах мне в чате (показывай только последние 4 символа ключей).

### 0.3. Как вносить изменения в веб-сервер
1. Бэкап: `cp -a /etc/nginx /root/backup-nginx-$(date +%F-%H%M)` (или аналог для другого сервера).
2. Новый **отдельный** файл конфигурации только для `game.robutpit.com` (например `/etc/nginx/sites-available/znatoki.conf` + симлинк), не правя существующие файлы.
3. `nginx -t` → только при успехе `systemctl reload nginx`.
4. Сразу проверить, что старый сайт отвечает как раньше: `curl -sS -o /dev/null -w '%{http_code}' https://<домен-сайта>/` до и после (коды и заголовки должны совпасть).
5. Если что-то пошло не так — откатить из бэкапа, `nginx -t`, `reload`, и сообщить мне.

SSL для `game.robutpit.com` — тем же инструментом, которым уже выпускаются сертификаты сайта (например `certbot --nginx -d game.robutpit.com` **только для нового домена**, или `certbot certonly --webroot`). Не перевыпускать и не трогать сертификаты сайта.

### 0.4. Изоляция
- Всё своё — в `/opt/znatoki` (код, `docker-compose.yml`, `.env`, бэкапы, логи).
- Предпочтительно Docker Compose с собственной сетью; наружу публикуются порты **только на 127.0.0.1** (например `127.0.0.1:4100:4100`), а веб-сервер проксирует на них.
- Если Docker не установлен — установи его из официального репозитория (это не затрагивает сайт), либо, если я против Docker, — Node через `nvm` под отдельным пользователем `znatoki` + systemd-юнит.
- Ограничь ресурсы контейнеров (`mem_limit`, `cpus`), чтобы игра не «съела» память сайта. Если RAM < 2 ГБ — предупреди меня и выбери облегчённую схему.

### 0.5. DNS
Мне нужно будет добавить A-запись `game.robutpit.com` → IP этого VPS. Скажи точно, какую запись создать, и дождись, пока я подтвержу (проверь `dig +short game.robutpit.com`).

---

## 1. Стек
- **Node.js 22 LTS + TypeScript**, **Fastify** (REST), **Socket.IO 4** (namespace `/game`), **Zod** (валидация входа), **Prisma** + **PostgreSQL 16** (отдельный контейнер), **Redis 7** (состояние комнат, таймеры, rate-limit, Socket.IO adapter).
- **LiveKit** для голоса. По умолчанию — **LiveKit Cloud** (бесплатный тариф): не требует открывать UDP-порты на VPS и не трогает сайт. Самостоятельный `livekit-server` — только если я попрошу; тогда он требует UDP-порты (7881 и диапазон RTC) и отдельный поддомен — сначала покажи мне, какие порты откроешь.
- **Claude API** (ключ я дам в `.env`): ИИ-судья ответов, модерация вопросов и чата, генерация вопросов, «зал из ИИ». Модели — через переменные окружения: `CLAUDE_MODEL_FAST=claude-haiku-5-5` (модерация, зал), `CLAUDE_MODEL_JUDGE=claude-sonnet-5-5` (судья, генерация), `CLAUDE_MODEL_APPEAL=claude-opus-5-5` (апелляция). Таймауты, ретраи, кэш вердиктов, лимит расходов в день (`AI_DAILY_BUDGET_USD`), при недоступности ИИ — безопасный фолбэк (fuzzy-проверка, вопрос остаётся «на проверке»).
- Почта: SMTP из `.env` (подтверждение e-mail, сброс пароля). Если SMTP нет — коды пишутся в лог (только в dev).
- Пуши: FCM и RuStore Push через абстракцию; ключи из `.env`, если их нет — модуль выключен.
- Логи: pino (JSON), без паролей/токенов/e-mail в открытом виде.

## 2. Клиент уже готов — сервер обязан соответствовать ему
Android-клиент лежит в репозитории **https://github.com/pitbut/Rustore** (Kotlin). Перед началом прочитай:
- `app/src/main/java/uz/robutpit/znatoki/domain/model/Contract.kt` и `Social.kt` — **точные имена полей и enum'ов JSON**;
- `app/src/main/java/uz/robutpit/znatoki/data/remote/ApiService.kt` — все REST-пути и тела;
- `app/src/main/java/uz/robutpit/znatoki/data/remote/RemoteGameGateway.kt` — имена Socket.IO-событий и форматы;
- `app/src/main/java/uz/robutpit/znatoki/data/mock/MockGameEngine.kt` — **эталонная реализация игровой логики** (фазы, тайминги, голосования, зал, апелляция). Серверный движок должен вести себя так же;
- `app/src/main/java/uz/robutpit/znatoki/domain/game/SpinMath.kt` и `AnswerChecker.kt` — алгоритмы волчка и проверки (перенеси один-в-один на TypeScript и покрой теми же тестами);
- `DECISIONS.md` — раздел «Предлагаемые дополнения к ОБЩЕМУ КОНТРАКТУ»: всё оттуда **обязательно** реализовать;
- `app/src/main/assets/sample_questions.json` — 60 стартовых вопросов (импортировать в БД сидом);
- `store/legal/privacy.html`, `store/legal/terms.html` — отдать по `/legal/privacy` и `/legal/terms`.

JSON: camelCase, enum'ы как в клиенте (`Phase` — `"DISCUSS"`, `SectorType` — `"blitz"`, `Verdict` — `"disputed"` и т. д.). Время — epoch ms серверного времени.

## 3. ОБЩИЙ КОНТРАКТ (источник истины — сервер)

### Правила игры (сервер авторитарен)
- Команда знатоков 1–6 человек, у команды капитан. Соперник — «зрители» (просто счёт). Игра до **6 очков**: верный ответ → очко знатокам, неверный или нет ответа → очко зрителям.
- Стол: **13 секторов** (или 7). Вопросы набираются заранее по секторам. Волчок указывает сектор; сыгранные пропускаются (к следующему несыгранному по часовой). Если все сыграны, а 6 очков нет — стол открывается заново.
- Типы: `normal`, `blitz` (3 вопроса по 20 с, каждый — отдельное очко), `superblitz` (3 вопроса, отвечает один знаток, у остальных микрофоны выключены сервером; игрока выбирает случайность или капитан — настройка), `blackbox` (в reveal — пояснение/картинка), `special13` (по умолчанию выключен).
- Обсуждение **60 с** (30/60/90). Ответ капитана — **10 с**.
- **Ранний ответ**: остаток, округлённый вниз до 5 с, уходит в копилку команды.
- **Доп. минута**: любой знаток → голосование команды (простое большинство, 10 с) → +60 с. Лимит 1 за игру (настройка), затем — из копилки (до 60 с).
- **Помощь зала**: 1 раз за игру, запрашивает капитан. Окно 30 с: зрители присылают версии (`audience:suggest`) и голосуют (`audience:vote`), команда видит топ-3. Нет живых зрителей — одна версия от ИИ (`fromAi: true`). Не засчитывается автоматически.
- **Исключение голосованием**: инициирует любой член команды (кроме цели); голосуют остальные, нужно строго больше половины «за» и минимум 2 голоса; 20 с; кулдаун инициации 2 мин; 3 неудачи подряд — блок инициатора до конца игры; исключённый не может вернуться в комнату 10 мин. Создатель постоянной команды исключает из состава без голосования, но не из идущей игры.
- **Проверка ответа**: нормализация (регистр, ё/е, пунктуация, артикли) → точное совпадение с `answer`/`accepted` → fuzzy (допуск опечаток по длине) → если неоднозначно — ИИ-судья (`correct|incorrect|disputed`). `disputed` засчитывается с пометкой. **Апелляция** (1 раз за игру, при `incorrect` с непустым ответом) — повторная проверка более сильной моделью.
- **Переподключение**: место держится 90 с; ушёл капитан — капитанство следующему; связь потеряли больше половины команды — автопауза до 3 мин (`PAUSED`).
- **Зрители**: видят табло и вопросы, слышат ведущего (TTS на клиенте), пишут в чат зала; **голос и чат команды не получают**; ответ видят только после reveal.
- **Боты** на пустые места (новичок/средний/эрудит): пишут реплики в чат команды, голосуют, отвечают с вероятностью по уровню и сложности (см. мок).
- **Рейтинг** (Elo-подобный): у вопроса — рейтинг сложности, у игрока — личный. Считается после рейтинговой игры (≥6 раундов, ≥2 живых игрока); игры с ботами весят 0.5; комнаты по коду с друзьями — нерейтинговые, если не включено явно; капитану +10 % к модулю дельты. Сезоны по 3 месяца, мягкий сброс. Рейтинг вопросов обновляется по результатам.

### Транспорт
- База: `https://game.robutpit.com`. REST: `/api/v1`, JSON, `Authorization: Bearer <access JWT>`. Ошибки: `{"error":{"code":"...","message":"..."}}` (коды как в клиенте: `invalid_email`, `weak_password`, `nickname_taken`, `invalid_credentials`, `room_not_found`, `room_full`, `not_captain`, `wrong_phase`, `vote_in_progress`, `no_extra_time`, `kick_cooldown`, `kick_blocked`, `appeal_unavailable`, `quota_exceeded`, …).
- Access JWT 15 мин, refresh 30 дней с ротацией и отзывом; пароли — argon2id.
- Socket.IO, namespace `/game`, токен в `auth: {token}`, транспорт websocket. Проксирование WebSocket в nginx: `proxy_http_version 1.1; proxy_set_header Upgrade $http_upgrade; proxy_set_header Connection "upgrade"; proxy_read_timeout 120s;`.
- Время: `GET /api/v1/time` → `{"now": <epoch ms>}`.
- Голос: LiveKit, токен только с сервера `GET /api/v1/rooms/{id}/livekit-token` → `{"url","token"}`. Права: знатоки — publish+subscribe в аудиокомнате команды; зрители — без треков команды; в суперблице publish только у выбранного игрока (обновлять права через LiveKit Server API).

### REST (`/api/v1`)
- Auth: `POST /auth/register {email,password,nickname}`, `POST /auth/login`, `POST /auth/refresh {refreshToken}`, `POST /auth/forgot {email}`, `POST /auth/reset {token,password}`, `POST /auth/verify-email {code}`, `GET/PATCH /me {nickname?,avatar?}`, `DELETE /me` (удаление аккаунта — реально удалять/анонимизировать), `GET /me/history`.
- Игроки: `GET /players/{id}`, `GET /players/search?q=`, `GET /leaderboard?scope=global|friends|team|season&season=`, `GET /question-of-day`.
- Друзья: `GET /friends`, `POST /friends/requests {playerId}`, `POST /friends/requests/{id}/accept|decline`, `DELETE /friends/{playerId}`, `POST /blocks {playerId}`, `DELETE /blocks/{playerId}`.
- Команды: `GET /teams` (мои), `POST /teams {name,emblem}`, `GET /teams/{id}`, `PATCH /teams/{id} {name?,emblem?,captainId?}`, `POST /teams/{id}/invites {playerId}`, `GET /invites`, `POST /invites/{id}/accept` (→ `{roomId?}`) `|decline`, `DELETE /teams/{id}/members/{playerId}`, `POST /teams/{id}/leave`, `POST /teams/{id}/start-room` (→ `{roomId}`, рассылает приглашения).
- Комнаты: `POST /rooms {settings}` (→ `{roomId}`), `GET /rooms?public=1`, `POST /rooms/join {code}` (→ `{roomId}`), `GET /rooms/{id}`, `POST /rooms/{id}/invites {playerId}`, `GET /rooms/{id}/livekit-token`, `POST /matchmaking/queue` (→ `{roomId}`), `DELETE /matchmaking/queue`.
- Вопросы: `GET /packs`, `POST /packs {title,questionIds}`, `POST /questions` (→ ИИ-модерация, статус `pending` → `approved|rejected` + `moderationReason`), `GET /questions/mine`, `POST /questions/{id}/feedback {liked,tooEasy,tooHard,report,reason}`, `POST /questions/generate {topic,difficulty,count,lang}` (→ `{drafts,quotaLeft,quotaTotal}`, квота 20/день на пользователя).
- Прочее: `POST /reports {targetType,targetId,reason}`, `POST /devices {provider:"fcm"|"rustore",token}`, `GET /config` (→ `{minClientVersion,latestVersion,features,privacyUrl,termsUrl,supportEmail:"gtnz1071980@yandex.com"}`).
- Вне `/api/v1`: `GET /legal/privacy`, `GET /legal/terms`, `GET /join/{code}` (HTML-страница «Открыть в приложении» + ссылка на RuStore), `GET /healthz`.

### Socket.IO `/game`
Клиент → сервер: `room:join {roomId|code, role:"player"|"spectator"}`, `room:leave`, `room:ready {ready}`, `room:start`, `room:settings {…}`, `room:captain {playerId}`, `room:add_bot {level}`, `game:spin`, `game:ready_answer`, `game:answer {text, viaVoice}`, `game:extra_minute_request`, `game:call_audience`, `game:appeal`, `audience:suggest {text}`, `audience:vote {suggestionId}`, `vote:cast {voteId, yes}`, `kick:start {targetId}`, `chat:message {scope:"team"|"hall", text}`, `chat:report {messageId}`, `game:ack {seq}`.

Сервер → клиент (все игровые несут `seq`, монотонный в комнате): `room:state` (полный снимок `RoomState`, при join/reconnect/ack с отставанием), `game:phase {phase,endsAt,round,sector}`, `game:spin_result {sectorIndex,startAngle,totalRotationDeg,durationMs,seed,startedAt}`, `game:question` (**без ответа**: `id,type,text,imageUrl,hint,subIndex,subTotal,topic`), `game:reveal {answer,comment,source,verdict,scoreDelta,givenAnswer,questionId,appealable}`, `game:score {experts,viewers}`, `vote:started {voteId,kind,targetId,endsAt,yes,no,eligible,initiatorId}`, `vote:progress {voteId,yes,no}`, `vote:result {voteId,kind,passed,yes,no,targetId}`, `audience:started {endsAt}`, `audience:suggestions {suggestions:[…]}`, `time:extended {addedSec,newEndsAt}`, `player:kicked {playerId}`, `player:status {id,online}`, `chat:message {id,scope,authorId,authorName,text,ts}`, `host:say {text, voiceStyle}` (`host`, `bot1`…`bot5`), `game:over {winner,ratingDeltas,stats}`, `error {code}`.
- Зрителям не отправлять `chat:message` со `scope:"team"` и реплики ботов-«версии».
- Волчок: результат генерирует **только сервер** (перенеси `SpinMath.makeSpin`), `startedAt` — серверное время начала.

### Фазы
`LOBBY → SPIN → INTRO → DISCUSS → ANSWER → CHECK → REVEAL → SCORE → (SPIN | GAME_OVER)`. Для `blitz`/`superblitz` `INTRO…SCORE` повторяются по 3 раза с короткими таймерами. Подфазы: `VOTE_EXTRA_MINUTE`, `AUDIENCE_HELP`, `PAUSED` (голосования и зал в клиенте идут поверх `DISCUSS`, таймер обсуждения не останавливается).

### Модель вопроса
`{id, type, lang, topic, text, imageUrl?, hint?, answer, accepted:[], rejected:[], comment, source, author, license, difficulty (1–5), rating, status: draft|pending|approved|rejected|hidden, origin: user|ai|imported|admin, createdAt, moderationReason?, parts?:[{text,answer,accepted,comment}]}` (для блица/суперблица — 3 части в `parts`).

## 4. Архитектура сервера
- Модули: `auth`, `users`, `social` (друзья, блоки, жалобы), `teams`, `rooms` + `matchmaking`, `game` (движок), `questions` (база, пакеты, модерация, генерация), `ai` (клиент Claude, промпты, бюджет), `voice` (LiveKit), `push`, `admin`.
- **Игровой движок**: один владелец комнаты (процесс) + состояние в Redis; таймеры по серверному времени; все действия игроков — через очередь команд комнаты (без гонок), идемпотентность (повтор того же действия не ломает состояние), rate-limit на события сокета.
- Восстановление: при `game:ack {seq}` с отставанием или `room:join` повторно — полный `room:state`.
- **Модерация**: чат — фильтр мата + асинхронная ИИ-проверка + жалобы; вопросы — ИИ (факты, оригинальность, лексика, нет ли ответа в тексте) → `approved/rejected` с причиной; админ-эндпоинты (под отдельным ролевым доступом) для ручной модерации жалоб и вопросов.
- Промпты ИИ — в отдельных файлах, с JSON-ответом и валидацией Zod; в промпт судьи не передавать персональные данные.
- Безопасность: helmet-аналог для Fastify, CORS только нужный, лимиты размера тела, rate-limit на auth (брутфорс), защита от перебора кодов комнат, проверка прав на каждое действие (капитан/хост/участник/зритель).

## 5. Деплой (не ломая сайт — см. §0)
- `/opt/znatoki/docker-compose.yml`: `api` (Node), `postgres`, `redis`; порты только `127.0.0.1:4100`; volumes для БД; `restart: unless-stopped`; `mem_limit`.
- `.env` (права 600): `DATABASE_URL`, `REDIS_URL`, `JWT_SECRET` (сгенерируй), `ANTHROPIC_API_KEY`, `CLAUDE_MODEL_*`, `AI_DAILY_BUDGET_USD`, `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`, `SMTP_*`, `FCM_*`, `RUSTORE_PUSH_*`, `PUBLIC_BASE_URL=https://game.robutpit.com`, `SUPPORT_EMAIL=gtnz1071980@yandex.com`. Создай `.env.example` без значений. Недостающие ключи спроси у меня одним списком.
- nginx-хост `game.robutpit.com`: `/api/`, `/socket.io/`, `/legal/`, `/join/`, `/healthz` → `127.0.0.1:4100` (с WebSocket-заголовками). HTTPS, редирект с HTTP.
- Миграции Prisma при старте, сид 60 вопросов.
- Бэкап БД: ежедневный `pg_dump` в `/opt/znatoki/backups` с хранением 14 дней (cron под своим пользователем, не трогая чужие задачи).
- Обновление: `git pull && docker compose up -d --build` — скрипт `/opt/znatoki/deploy.sh`.
- Мониторинг: `/healthz` (БД, Redis), логи с ротацией (docker `max-size`).

## 6. Тесты
- Unit: `SpinMath` (тот же тест: 1000 seed — финальный угол в секторе, пропуск сыгранных), `AnswerChecker` (те же кейсы, что в клиенте), Elo, правила голосований (порог, кулдаун, блок после 3 неудач), копилка времени.
- Интеграционные: полный цикл игры через Socket.IO-клиент с ускоренным временем (как `FakeGameServerTest` в клиенте) — до 6 очков, все типы секторов, доп. минута, исключение, помощь зала, апелляция, переподключение с `seq`, пауза при потере половины команды.
- REST: auth (регистрация/логин/refresh/удаление), друзья, команды, вопросы, квота генерации.
- Контрактный тест: JSON ответов сервера декодируется моделями клиента (сверить с `ContractParsingTest.kt`).

## 7. Порядок работы
1. **Разведка (§0.1)** → сводка мне → план. Ничего не менять до моего «ок».
2. Каркас проекта, Docker Compose на 127.0.0.1, БД, миграции, сид, `/healthz`, `/time`, `/config`, `/legal/*`.
3. Auth + профиль + удаление аккаунта.
4. Комнаты, лобби, Socket.IO, игровой движок, боты — полный цикл (без ИИ, судья = fuzzy).
5. Друзья, команды, приглашения, рейтинг, зрители, помощь зала.
6. ИИ: судья, апелляция, модерация, генерация, зал из ИИ, бюджет.
7. LiveKit-токены и права (суперблиц).
8. nginx-хост + SSL **по правилам §0.3**, проверка, что сайт работает как раньше.
9. Бэкапы, деплой-скрипт, нагрузочная проверка (50 комнат по 6 игроков) — без влияния на сайт.
10. Отчёт.

## 8. Критерии готовности
- Старый сайт отвечает так же, как до начала работ (сравни коды/заголовки/время ответа до и после; приложи вывод).
- `https://game.robutpit.com/healthz` → 200; `/api/v1/time` и `/legal/privacy` работают.
- Android-клиент с `USE_MOCK=false` проходит регистрацию и полную игру до 6 очков против ботов.
- Все тесты зелёные.

## 9. Финальный отчёт мне
Что сделано и что нет; какие порты/домены/записи DNS добавлены; какие файлы созданы или изменены вне `/opt/znatoki` (полный список с путями бэкапов); как откатить всё одной командой (скрипт `/opt/znatoki/uninstall.sh`, который убирает только созданное тобой); какие ключи нужно добавить в `.env`; как обновлять и смотреть логи; сколько RAM/CPU занимает игра.
