<div align="center">

# 📺 CamOverlay

**Show any RTSP/MJPEG camera on your Android TV or phone — fullscreen or as a floating picture-in-picture window — driven entirely from Home Assistant over MQTT.**

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![HACS: Custom](https://img.shields.io/badge/HACS-Custom-41BDF5.svg)](https://hacs.xyz/)
[![Home Assistant](https://img.shields.io/badge/Home%20Assistant-integration-blue.svg)](https://www.home-assistant.io/)
[![Android](https://img.shields.io/badge/Android-4.2%2B%20(API%2017)-green.svg)](#)

### 🌐 Language · Язык

**[🇬🇧 English](#-english)**  ·  **[🇷🇺 Русский](#-русский)**

</div>

---

<a id="-english"></a>

## 🇬🇧 English

> Pop a camera onto the big screen the moment someone rings the doorbell, walks up the driveway, or calls the intercom — without leaving whatever you were watching.

### Contents

- [What it is](#what-it-is)
- [Features](#features)
- [How it works](#how-it-works)
- [Requirements](#requirements)
- [Installation](#installation)
- [Entities](#entities-per-device)
- [Services](#services)
- [Multiple cameras](#multiple-cameras)
- [Automations: motion, doorbell, intercom](#automations--the-good-part)
- [MQTT contract](#mqtt-contract)
- [Troubleshooting](#troubleshooting)
- [License](#license)

### What it is

CamOverlay comes in two parts that talk to each other purely over MQTT:

1. **A tiny Android app** (`android/`) that runs in the background on the TV or phone, connects to your MQTT broker, and draws the camera **on top of whatever is on screen** — your movie, the launcher, a game, anything.
2. **A Home Assistant integration** (`custom_components/camoverlay/`, HACS-installable) that **auto-discovers** your devices and gives you buttons, selects and services to control them from automations and dashboards.

No ADB and no root are required for control — the app simply listens on MQTT. It autostarts on boot and keeps a foreground service alive.

### Features

- 🖥️ **Fullscreen** camera on a device, or 🪟 **picture-in-picture** in any corner.
- 🔲 **Multi-camera grid** — show **1, 2, 3 or 4** cameras at once, tiled in one PiP window (or fullscreen). The layout adapts to the count: **2** side-by-side, **3–4** in a 2×2.
- 📐 **Adaptive PiP size** — `1/4` (half the screen width) or `1/16` (a quarter of the screen width); the window keeps a 16:9 ratio so it always fits.
- ✋ On-screen **close (✕)** and **fullscreen (⛶)** controls on phones.
- 🔎 **Auto-discovery** in Home Assistant — each `tv`, `phone`, … becomes a HA device with its own entities.
- 📡 Per-device **online** sensor and **state** sensor.
- ⚙️ Services for automations: `camoverlay.fullscreen`, `camoverlay.pip`, `camoverlay.stop`.

### How it works

```
┌──────────────┐   MQTT cmd    ┌───────────────┐   draws overlay   ┌──────────────┐
│ Home         │ ────────────► │ CamOverlay    │ ────────────────► │  TV / phone  │
│ Assistant    │               │ Android app   │                   │  screen      │
│ (automation) │ ◄──────────── │ (foreground   │   pulls frames    └──────┬───────┘
└──────────────┘  availability │  service)     │ ◄──────────────┐         │
                  + state      └───────────────┘   MJPEG frames  │   go2rtc / camera
                                                                 └─────────┘
```

Home Assistant publishes a command (`pip` / `full` / `stop`) to `camoverlay/<device>/cmd`. The app renders the requested camera(s) by pulling single-JPEG frames from go2rtc (or any MJPEG frame URL), and publishes its `availability` and `state` back so HA always knows what each screen is showing.

### Requirements

- Home Assistant with the **MQTT** integration configured and a broker (e.g. the Mosquitto add-on).
- An Android device (TV or phone) that can reach the broker.
- A camera reachable over HTTP as a **single JPEG frame** URL, such as [go2rtc](https://github.com/AlexxIT/go2rtc)'s `http://HOST:1984/api/frame.jpeg?src=CAMERA`.

### Installation

#### 1. Home Assistant integration (HACS)

[![Open your Home Assistant instance and open a repository inside the Home Assistant Community Store.](https://my.home-assistant.io/badges/hacs_repository.svg)](https://my.home-assistant.io/redirect/hacs_repository/?owner=nikitacoolagin&repository=hass-camoverlay&category=integration)

1. In **HACS → ⋮ (top right) → Custom repositories**, add `https://github.com/nikitacoolagin/hass-camoverlay` with category **Integration**. (The badge above only works once the repository is known to HACS.)
2. Install **CamOverlay**, then restart Home Assistant.
3. **Settings → Devices & Services → Add Integration → CamOverlay.** Set the MQTT topic prefix (default `camoverlay`) and, optionally:
   - a **go2rtc URL** (e.g. `http://192.168.1.10:1984`) — **required** for multi-camera grids by *name* (see [Multiple cameras](#multiple-cameras));
   - a list of **camera source names** (one per line);
   - a camera entity for dashboards.

> **Manual install:** copy `custom_components/camoverlay/` into your HA `config/custom_components/` folder and restart.

#### 2. Android app

Grab `camoverlay.apk` from the [Releases](../../releases) page, or build it yourself (see [`android/README.md`](android/README.md)).

Install it on the TV/phone, open it once, and use the in-app checklist to:

- set the **MQTT broker** (host/port/user/pass) and the **camera stream URL**;
- set the **device name** (`tv`, `phone`, …) — this is what shows up in Home Assistant;
- grant **Display over other apps**, **Notifications** (Android 13+) and disable **battery optimization**;
- enable **autostart**.

> 💡 **On Android TV / Xiaomi (MiTV) and similar OEMs**, you must also enable **OEM autostart / "don't kill in background"** for CamOverlay in the system settings — otherwise the OEM kills the background service as soon as the app leaves the foreground, and the device drops offline. This is an OS power-management policy, not an app bug.

Once the app connects, the device appears automatically in Home Assistant.

### Entities (per device)

| Entity | Type | Purpose |
| --- | --- | --- |
| `binary_sensor.camoverlay_<device>_online` | connectivity | App connected to the broker |
| `sensor.camoverlay_<device>_state` | sensor | `idle` / `full` / `pip:<size>:<corner>` |
| `button.camoverlay_<device>_fullscreen` | button | Show fullscreen |
| `button.camoverlay_<device>_show_pip` | button | Show PiP with the selected size/corner |
| `button.camoverlay_<device>_stop` | button | Hide overlay / close fullscreen |
| `select.camoverlay_<device>_pip_size` | select | `1/4` or `1/16` |
| `select.camoverlay_<device>_pip_corner` | select | corner for the PiP |

### Services

```yaml
# Fullscreen on the TV
service: camoverlay.fullscreen
data:
  device: tv

# Picture-in-picture, small, bottom-right, on the phone
service: camoverlay.pip
data:
  device: phone
  size: small      # quarter | small
  corner: br       # tl | tr | bl | br

# Stop
service: camoverlay.stop
data:
  device: tv
```

### Multiple cameras

Pass a `cameras` list (up to 4) to `camoverlay.pip` or `camoverlay.fullscreen` to tile several streams in one window. Entries are either **source names** (resolved against the **go2rtc URL** set in the integration options) or **full frame URLs**.

```yaml
# Four cameras in a 2×2 grid, fullscreen on the TV (by go2rtc source name)
service: camoverlay.fullscreen
data:
  device: tv
  cameras: [entrance, yard, gate, garage]

# Two cameras side-by-side, small PiP, bottom-right on the phone (by full URL)
service: camoverlay.pip
data:
  device: phone
  size: small
  corner: br
  cameras:
    - http://192.168.1.10:1984/api/frame.jpeg?src=entrance
    - http://192.168.1.10:1984/api/frame.jpeg?src=yard
```

Layout by camera count: **1** → single, **2** → side-by-side, **3–4** → 2×2 grid. Omitting `cameras` keeps the single-camera behaviour (the app uses the stream URL configured on the device). The per-device **Show PiP** button always shows that single camera; use a service call (or a dashboard button with a `perform-action` tap action) for multi-camera grids.

### Automations — the good part

This is where CamOverlay earns its keep. Trigger an overlay from **any** Home Assistant event and pull the camera onto the screen automatically. Three classic patterns:

#### 🚶 Motion → pop a PiP, hide it when motion clears

Show the driveway camera in the top-right corner the moment motion is detected, and tidy up automatically once it's been quiet for a while.

```yaml
automation:
  - alias: "CamOverlay: driveway motion → PiP"
    trigger:
      - platform: state
        entity_id: binary_sensor.driveway_motion
        to: "on"
    action:
      - service: camoverlay.pip
        data:
          device: tv
          size: quarter
          corner: tr
          cameras: [driveway]

  - alias: "CamOverlay: hide PiP when motion clears"
    trigger:
      - platform: state
        entity_id: binary_sensor.driveway_motion
        to: "off"
        for: "00:00:30"
    action:
      - service: camoverlay.stop
        data:
          device: tv
```

#### 🔔 Doorbell → fullscreen the entrance, auto-close after a bit

Someone presses the doorbell: throw the entrance camera fullscreen on the TV, then drop back to whatever was playing after 20 seconds.

```yaml
automation:
  - alias: "CamOverlay: doorbell → fullscreen entrance"
    trigger:
      - platform: state
        entity_id: binary_sensor.doorbell_button
        to: "on"
    action:
      - service: camoverlay.fullscreen
        data:
          device: tv
          cameras: [entrance]
      - delay: "00:00:20"
      - service: camoverlay.stop
        data:
          device: tv
```

Prefer a non-intrusive PiP instead of a fullscreen takeover? Swap the first action for:

```yaml
      - service: camoverlay.pip
        data:
          device: tv
          size: quarter
          corner: tr
          cameras: [entrance]
```

#### 📞 Intercom call → entrance + gate side by side

When the intercom rings, show **both** the door panel and the street gate at once so you can see the visitor and the surroundings. (Use whatever trigger your intercom integration exposes — a `binary_sensor`, an `event`, or an MQTT message.)

```yaml
automation:
  - alias: "CamOverlay: intercom call → entrance + gate"
    trigger:
      - platform: state
        entity_id: binary_sensor.intercom_call
        to: "on"
    action:
      - service: camoverlay.pip
        data:
          device: tv
          size: quarter      # 1/2 of screen width — comfortable for two tiles
          corner: tr
          cameras: [entrance, gate]
```

For a full 2×2 wall (e.g. entrance, gate, yard, garage) on a long ring, use `camoverlay.fullscreen` with four cameras.

> **Tip:** point automations at multiple devices by repeating the service call with `device: tv` and `device: phone`, or wrap them in a script and call it with a `device` variable.

### MQTT contract

The integration and app talk over these topics (prefix configurable, default `camoverlay`):

| Topic | Direction | Payload |
| --- | --- | --- |
| `camoverlay/<device>/cmd` | HA → app | `{"action":"full"}`, `{"action":"stop"}`, `{"action":"pip","size":"quarter\|small","corner":"tl\|tr\|bl\|br"}`. Either action may also carry `"cameras":["url1",...]` (up to 4) to tile multiple streams. |
| `camoverlay/<device>/availability` | app → HA | `online` / `offline` (retained, Last-Will) |
| `camoverlay/<device>/state` | app → HA | `idle` / `full` / `pip:<size>:<corner>` (retained) |

Because the contract is plain MQTT, you can also drive the app from any other client — e.g. publish a command straight from a Node-RED flow or `mosquitto_pub`.

### Troubleshooting

- **Device shows as offline / drops after a while** — enable OEM autostart and disable battery optimization for CamOverlay (see the install note above). On Android TV / MiTV this is the usual culprit.
- **Multi-camera by *name* doesn't work** — make sure the **go2rtc URL** is set in the integration options; without it, bare names can't be resolved and only full frame URLs work.
- **Nothing shows up** — check `binary_sensor.camoverlay_<device>_online` is `on`, and that the device can open the frame URL in a browser.

### License

MIT — see [LICENSE](LICENSE).

<div align="right"><a href="#-camoverlay">⬆️ Back to top</a> · <a href="#-русский">🇷🇺 Читать по-русски</a></div>

---

<a id="-русский"></a>

## 🇷🇺 Русский

> Выводите камеру на большой экран в тот самый момент, когда кто-то звонит в дверь, заходит во двор или вызывает по домофону — не отрываясь от того, что вы смотрите.

### Содержание

- [Что это](#что-это)
- [Возможности](#возможности)
- [Как это работает](#как-это-работает)
- [Требования](#требования)
- [Установка](#установка)
- [Сущности](#сущности-на-каждое-устройство)
- [Сервисы](#сервисы)
- [Несколько камер](#несколько-камер)
- [Автоматизации: движение, звонок, домофон](#автоматизации--самое-вкусное)
- [Контракт MQTT](#контракт-mqtt)
- [Если что-то не так](#если-что-то-не-так)
- [Лицензия](#лицензия)

### Что это

CamOverlay состоит из двух частей, которые общаются между собой исключительно по MQTT:

1. **Маленькое Android-приложение** (`android/`) — работает в фоне на телевизоре или телефоне, подключается к вашему MQTT-брокеру и рисует камеру **поверх всего, что сейчас на экране**: фильма, лаунчера, игры — чего угодно.
2. **Интеграция для Home Assistant** (`custom_components/camoverlay/`, ставится через HACS) — **сама находит** ваши устройства и даёт кнопки, селекты и сервисы для управления ими из автоматизаций и дашбордов.

Для управления **не нужны ни ADB, ни root** — приложение просто слушает MQTT. Оно автозапускается при включении и держит живой foreground-сервис.

### Возможности

- 🖥️ Камера **на весь экран** или 🪟 **картинка-в-картинке** (PiP) в любом углу.
- 🔲 **Сетка из нескольких камер** — показ **1, 2, 3 или 4** камер одновременно в одном PiP-окне (или на весь экран). Раскладка подстраивается под количество: **2** — бок о бок, **3–4** — сеткой 2×2.
- 📐 **Адаптивный размер PiP** — `1/4` (половина ширины экрана) или `1/16` (четверть ширины); окно сохраняет соотношение 16:9 и всегда помещается.
- ✋ Экранные кнопки **закрыть (✕)** и **на весь экран (⛶)** на телефонах.
- 🔎 **Автообнаружение** в Home Assistant — каждое `tv`, `phone`, … становится отдельным устройством HA со своими сущностями.
- 📡 На каждое устройство — сенсор **онлайн** и сенсор **состояния**.
- ⚙️ Сервисы для автоматизаций: `camoverlay.fullscreen`, `camoverlay.pip`, `camoverlay.stop`.

### Как это работает

```
┌──────────────┐   MQTT-команда  ┌───────────────┐   рисует оверлей  ┌──────────────┐
│ Home         │ ──────────────► │ Приложение    │ ────────────────► │ Экран ТВ /   │
│ Assistant    │                 │ CamOverlay    │                   │ телефона     │
│ (автоматиз.) │ ◄────────────── │ (foreground-  │   тянет кадры     └──────┬───────┘
└──────────────┘  доступность    │  сервис)      │ ◄──────────────┐         │
                  + состояние    └───────────────┘   кадры MJPEG   │   go2rtc / камера
                                                                   └─────────┘
```

Home Assistant публикует команду (`pip` / `full` / `stop`) в `camoverlay/<устройство>/cmd`. Приложение отрисовывает нужные камеры, забирая одиночные JPEG-кадры из go2rtc (или из любого URL MJPEG-кадра), и публикует обратно свою `availability` и `state` — так HA всегда знает, что показано на каждом экране.

### Требования

- Home Assistant с настроенной интеграцией **MQTT** и брокером (например, аддон Mosquitto).
- Android-устройство (ТВ или телефон), которое видит брокер.
- Камера, доступная по HTTP как **URL одиночного JPEG-кадра** — например, у [go2rtc](https://github.com/AlexxIT/go2rtc): `http://ХОСТ:1984/api/frame.jpeg?src=КАМЕРА`.

### Установка

#### 1. Интеграция для Home Assistant (HACS)

[![Open your Home Assistant instance and open a repository inside the Home Assistant Community Store.](https://my.home-assistant.io/badges/hacs_repository.svg)](https://my.home-assistant.io/redirect/hacs_repository/?owner=nikitacoolagin&repository=hass-camoverlay&category=integration)

1. В **HACS → ⋮ (вверху справа) → Пользовательские репозитории** добавьте `https://github.com/nikitacoolagin/hass-camoverlay` с категорией **Integration**. (Бейдж выше работает только после того, как HACS узнает о репозитории.)
2. Установите **CamOverlay** и перезапустите Home Assistant.
3. **Настройки → Устройства и службы → Добавить интеграцию → CamOverlay.** Задайте префикс MQTT-топиков (по умолчанию `camoverlay`) и, при желании:
   - **go2rtc URL** (например, `http://192.168.1.10:1984`) — **обязателен** для сеток из нескольких камер *по имени* (см. [Несколько камер](#несколько-камер));
   - список **имён камер-источников** (по одному в строке);
   - сущность камеры для дашбордов.

> **Ручная установка:** скопируйте `custom_components/camoverlay/` в папку `config/custom_components/` вашего HA и перезапустите.

#### 2. Android-приложение

Возьмите `camoverlay.apk` со страницы [Releases](../../releases) или соберите сами (см. [`android/README.md`](android/README.md)).

Установите на ТВ/телефон, откройте один раз и по внутреннему чек-листу:

- задайте **MQTT-брокер** (хост/порт/логин/пароль) и **URL потока камеры**;
- задайте **имя устройства** (`tv`, `phone`, …) — именно оно появится в Home Assistant;
- выдайте разрешения **Поверх других окон**, **Уведомления** (Android 13+) и отключите **экономию батареи**;
- включите **автозапуск**.

> 💡 **На Android TV / Xiaomi (MiTV) и подобных OEM** дополнительно включите **OEM-автозапуск / «не выгружать из памяти»** для CamOverlay в системных настройках — иначе OEM убивает фоновый сервис, как только приложение уходит с переднего плана, и устройство уходит в офлайн. Это политика энергосбережения ОС, а не баг приложения.

Как только приложение подключится, устройство автоматически появится в Home Assistant.

### Сущности (на каждое устройство)

| Сущность | Тип | Назначение |
| --- | --- | --- |
| `binary_sensor.camoverlay_<устройство>_online` | связь | Приложение подключено к брокеру |
| `sensor.camoverlay_<устройство>_state` | сенсор | `idle` / `full` / `pip:<размер>:<угол>` |
| `button.camoverlay_<устройство>_fullscreen` | кнопка | Показать на весь экран |
| `button.camoverlay_<устройство>_show_pip` | кнопка | Показать PiP с выбранным размером/углом |
| `button.camoverlay_<устройство>_stop` | кнопка | Скрыть оверлей / закрыть полноэкранный режим |
| `select.camoverlay_<устройство>_pip_size` | селект | `1/4` или `1/16` |
| `select.camoverlay_<устройство>_pip_corner` | селект | угол для PiP |

### Сервисы

```yaml
# На весь экран на телевизоре
service: camoverlay.fullscreen
data:
  device: tv

# Картинка-в-картинке, маленькая, правый нижний угол, на телефоне
service: camoverlay.pip
data:
  device: phone
  size: small      # quarter | small
  corner: br       # tl | tr | bl | br

# Стоп
service: camoverlay.stop
data:
  device: tv
```

### Несколько камер

Передайте список `cameras` (до 4) в `camoverlay.pip` или `camoverlay.fullscreen`, чтобы выложить несколько потоков в одном окне. Элементы — это либо **имена источников** (резолвятся через **go2rtc URL** из опций интеграции), либо **полные URL кадров**.

```yaml
# Четыре камеры сеткой 2×2 на весь экран ТВ (по имени источника go2rtc)
service: camoverlay.fullscreen
data:
  device: tv
  cameras: [entrance, yard, gate, garage]

# Две камеры бок о бок, маленький PiP, правый нижний угол на телефоне (по полному URL)
service: camoverlay.pip
data:
  device: phone
  size: small
  corner: br
  cameras:
    - http://192.168.1.10:1984/api/frame.jpeg?src=entrance
    - http://192.168.1.10:1984/api/frame.jpeg?src=yard
```

Раскладка по количеству: **1** → одна, **2** → бок о бок, **3–4** → сетка 2×2. Если `cameras` опустить — поведение с одной камерой (приложение берёт URL потока из настроек устройства). Кнопка **Показать PiP** всегда показывает эту одну камеру; для сеток используйте вызов сервиса (или кнопку на дашборде с действием `perform-action`).

### Автоматизации — самое вкусное

Вот ради чего всё затевалось. Запускайте оверлей по **любому** событию Home Assistant и выводите камеру на экран автоматически. Три классических сценария:

#### 🚶 Движение → всплывает PiP, исчезает когда движения нет

Показать камеру двора в правом верхнем углу, как только зафиксировано движение, и автоматически убрать её, когда уже какое-то время тихо.

```yaml
automation:
  - alias: "CamOverlay: движение во дворе → PiP"
    trigger:
      - platform: state
        entity_id: binary_sensor.driveway_motion
        to: "on"
    action:
      - service: camoverlay.pip
        data:
          device: tv
          size: quarter
          corner: tr
          cameras: [driveway]

  - alias: "CamOverlay: скрыть PiP когда движение прекратилось"
    trigger:
      - platform: state
        entity_id: binary_sensor.driveway_motion
        to: "off"
        for: "00:00:30"
    action:
      - service: camoverlay.stop
        data:
          device: tv
```

#### 🔔 Дверной звонок → камера у входа на весь экран, автозакрытие

Кто-то нажал звонок: вывести камеру у входа на весь экран ТВ, а через 20 секунд вернуться к тому, что показывалось.

```yaml
automation:
  - alias: "CamOverlay: звонок → вход на весь экран"
    trigger:
      - platform: state
        entity_id: binary_sensor.doorbell_button
        to: "on"
    action:
      - service: camoverlay.fullscreen
        data:
          device: tv
          cameras: [entrance]
      - delay: "00:00:20"
      - service: camoverlay.stop
        data:
          device: tv
```

Хотите ненавязчивый PiP вместо захвата всего экрана? Замените первое действие на:

```yaml
      - service: camoverlay.pip
        data:
          device: tv
          size: quarter
          corner: tr
          cameras: [entrance]
```

#### 📞 Вызов по домофону → вход + калитка рядом

Когда домофон звонит, показать **сразу обе** камеры — дверную панель и уличную калитку — чтобы видеть и гостя, и обстановку. (Используйте тот триггер, который даёт ваша интеграция домофона — `binary_sensor`, `event` или сообщение MQTT.)

```yaml
automation:
  - alias: "CamOverlay: домофон → вход + калитка"
    trigger:
      - platform: state
        entity_id: binary_sensor.intercom_call
        to: "on"
    action:
      - service: camoverlay.pip
        data:
          device: tv
          size: quarter      # 1/2 ширины экрана — удобно для двух плиток
          corner: tr
          cameras: [entrance, gate]
```

Для полной стены 2×2 (например, вход, калитка, двор, гараж) на долгий вызов используйте `camoverlay.fullscreen` с четырьмя камерами.

> **Совет:** чтобы автоматизация работала на нескольких устройствах, повторите вызов сервиса с `device: tv` и `device: phone`, либо оберните его в скрипт и вызывайте с переменной `device`.

### Контракт MQTT

Интеграция и приложение общаются по этим топикам (префикс настраивается, по умолчанию `camoverlay`):

| Топик | Направление | Полезная нагрузка |
| --- | --- | --- |
| `camoverlay/<устройство>/cmd` | HA → приложение | `{"action":"full"}`, `{"action":"stop"}`, `{"action":"pip","size":"quarter\|small","corner":"tl\|tr\|bl\|br"}`. Любое действие может также нести `"cameras":["url1",...]` (до 4) для нескольких потоков. |
| `camoverlay/<устройство>/availability` | приложение → HA | `online` / `offline` (retained, Last-Will) |
| `camoverlay/<устройство>/state` | приложение → HA | `idle` / `full` / `pip:<размер>:<угол>` (retained) |

Поскольку контракт — это обычный MQTT, приложением можно управлять и из любого другого клиента: например, опубликовать команду прямо из потока Node-RED или через `mosquitto_pub`.

### Если что-то не так

- **Устройство офлайн / отваливается через время** — включите OEM-автозапуск и отключите экономию батареи для CamOverlay (см. примечание при установке). На Android TV / MiTV это самая частая причина.
- **Не работают камеры *по имени*** — проверьте, что в опциях интеграции задан **go2rtc URL**; без него «голые» имена не резолвятся, работают только полные URL кадров.
- **Ничего не появляется** — убедитесь, что `binary_sensor.camoverlay_<устройство>_online` в состоянии `on`, и что устройство открывает URL кадра в браузере.

### Лицензия

MIT — см. [LICENSE](LICENSE).

<div align="right"><a href="#-camoverlay">⬆️ Наверх</a> · <a href="#-english">🇬🇧 Read in English</a></div>
