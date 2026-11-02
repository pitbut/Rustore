# ПРОМПТ ДЛЯ CLAUDE CODE НА VPS — зрители слышат обсуждение команды (v1.0.8)

Сервер «Клуб знатоков» в `/opt/znatoki`, голос LiveKit уже установлен. Правила §0 первого промпта действуют: **сайт не трогать**, nginx не менять (здесь он и не нужен), ничего не удалять вне `/opt/znatoki`, без `apt upgrade`, секреты не выводить. Перед изменениями — бэкап кода/БД.

Клиент обновлён: `app/src/main/java/uz/robutpit/znatoki/domain/model/Contract.kt` в https://github.com/pitbut/Rustore (поле `RoomSettings.spectatorsHearTeam`).

## Изменение правила
Раньше зрители не получали голос команды. Теперь:
- Новая настройка комнаты **`spectatorsHearTeam: boolean`, по умолчанию `true`**. Принимается в `POST /rooms {settings}` и `room:settings`, хранится с комнатой, отдаётся в `room:state.settings`. Для уже существующих комнат без поля считать `true`.
- Если `spectatorsHearTeam = true`, `GET /api/v1/rooms/{id}/livekit-token` для **зрителя** (вошёл в комнату с `role:"spectator"`) выдаёт токен в ту же аудиокомнату команды с правами:
  `roomJoin: true, canSubscribe: true, canPublish: false, canPublishData: false, hidden: true`
  (`hidden` — зритель не появляется у игроков в списке участников голосового канала).
- Если `spectatorsHearTeam = false` — зрителю `403 {"error":{"code":"voice_forbidden"}}`, как было.
- Знатокам права не меняются (publish только микрофон, суперблиц — как сейчас). Зритель **никогда** не может публиковать звук: даже если клиент попробует, LiveKit должен отклонить (это обеспечивает `canPublish: false`).
- Identity зрителя в токене — `spectator:<id игрока>`, чтобы не путать с игроками.
- Лимит участников LiveKit-комнаты: подними `max_participants` в `/opt/znatoki/livekit/livekit.yaml` до **60** (6 игроков + зрители), перезапусти `znatoki-livekit`. Если зрителей больше — лишним вернуть `429 {"error":{"code":"voice_full"}}`, игра у них продолжается без голоса.

## Тесты
- зритель при `spectatorsHearTeam=true` получает токен с `canPublish=false`, `canSubscribe=true`, `hidden=true`;
- при `false` — 403 `voice_forbidden`;
- зритель не может опубликовать трек (интеграционный тест через LiveKit: попытка публикации отклонена);
- игроки и суперблиц работают как раньше.

## Проверка и отчёт
Все тесты зелёные, деплой, `/healthz` → 200, сайт отвечает как до изменений. Отчёт: что изменено, как откатить.
