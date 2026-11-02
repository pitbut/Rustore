# ПРОМПТ ДЛЯ CLAUDE CODE НА VPS — свой голосовой сервер LiveKit (без сторонних сервисов)

Сервер «Клуб знатоков» уже работает в `/opt/znatoki`. Нужно включить **голосовой чат команды**, поставив **собственный LiveKit-сервер на этот VPS** (open-source `livekit-server`, лицензия Apache 2.0). Никаких облачных аккаунтов — ключи генерируем сами.

## 0. Правила безопасности (как в первом промпте — обязательно)
- **Сайт не трогать.** Его конфиги, корни, сертификаты, контейнеры — не изменять. nginx — только новый `location` в нашем файле `/etc/nginx/sites-available/znatoki.conf` (бэкап перед правкой), `nginx -t`, затем `reload` (не `restart`).
- Сравнить ответ сайта до и после (коды/заголовки) и приложить вывод.
- Ничего не удалять вне `/opt/znatoki`, без `apt upgrade`, секреты не выводить (только последние 4 символа).
- Порты 80/443 не занимать. Файрвол: **только добавлять** правила для новых портов, ничего не закрывать.

## 1. Разведка (только чтение) — сначала покажи мне
- Свободны ли порты: `7880/tcp` (будет только на 127.0.0.1), `7881/tcp`, `3478/udp`, диапазон `50000–50200/udp` (`ss -tulpn`).
- Какой файрвол активен (`ufw`, `iptables`, `nftables`) и есть ли **файрвол у хостинг-провайдера** (панель облака) — если есть, скажи мне точно, какие правила добавить в панели.
- Свободная RAM (LiveKit для наших нагрузок ~150–300 МБ).
- Внешний IP сервера.

## 2. Установка LiveKit
- Бинарник `livekit-server` (официальный релиз с GitHub, последняя стабильная версия) в `/opt/znatoki/livekit/`, запуск от пользователя `znatoki` через systemd-юнит `znatoki-livekit.service` с `Restart=always`, `MemoryMax=512M`. (Docker тоже допустим, если сервер уже на Docker — без `network_mode: host` для чужих сервисов.)
- Сгенерируй пару ключей: `livekit-server generate-keys` → `LIVEKIT_API_KEY` / `LIVEKIT_API_SECRET`.
- Конфиг `/opt/znatoki/livekit/livekit.yaml` (права 600):
  ```yaml
  port: 7880
  bind_addresses: ["127.0.0.1"]   # сигналинг только локально, наружу — через nginx
  rtc:
    tcp_port: 7881
    port_range_start: 50000
    port_range_end: 50200
    use_external_ip: true
  turn:
    enabled: true
    udp_port: 3478                 # для мобильных сетей за NAT
    domain: game.robutpit.com
  keys:
    <API_KEY>: <API_SECRET>
  room:
    empty_timeout: 300
    max_participants: 12
  logging:
    level: info
  ```
- Файрвол: открыть `7881/tcp`, `3478/udp`, `50000:50200/udp`. Больше ничего не трогать.

## 3. nginx — через уже существующий домен game.robutpit.com
В **наш** файл `znatoki.conf` (в server-блок 443) добавить:
```nginx
location /rtc {
    proxy_pass http://127.0.0.1:7880;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_read_timeout 3600s;
}
location /twirp/ {
    proxy_pass http://127.0.0.1:7880;
    proxy_set_header Host $host;
}
```
`nginx -t` → `reload` → проверка сайта.

## 4. Подключить к игровому серверу
- В `/opt/znatoki/.env`: `LIVEKIT_URL=wss://game.robutpit.com`, `LIVEKIT_API_KEY=…`, `LIVEKIT_API_SECRET=…` (те же, что в `livekit.yaml`). Перезапустить API.
- `GET /api/v1/config` → `features.voice: true`.
- `GET /api/v1/rooms/{id}/livekit-token` → `{url:"wss://game.robutpit.com", token}`. Права токена по контракту: знатоки — publish+subscribe в аудиокомнате своей команды; зрители — без треков команды; в суперблице publish только у выбранного игрока (через LiveKit Server API `UpdateParticipant`).
- Identity участника в токене = id игрока (клиент по нему подсвечивает «говорит»).

## 5. Проверка
1. `systemctl status znatoki-livekit` — active; в логе нет ошибок про порты/IP.
2. С сервера: два тестовых участника через `livekit-cli` (или скрипт на `livekit-server-sdk`) подключаются к одной комнате, видят друг друга.
3. Проверь доступность снаружи: `curl -I https://game.robutpit.com/rtc` (должен ответить LiveKit, не 404 сайта).
4. Сайт отвечает как до изменений.
5. Отчёт: какие порты открыты (в системе и что добавить в панели провайдера), где конфиг и ключи, сколько памяти занял LiveKit, как откатить (`systemctl disable --now znatoki-livekit`, удалить правила файрвола, убрать два `location` из `znatoki.conf`, вернуть старые значения в `.env`). Добавь шаги отката в `/opt/znatoki/uninstall.sh`.
