# CamOverlay

Show an RTSP/MJPEG camera **on an Android TV or phone** — fullscreen or as a floating
picture-in-picture window — controlled entirely from **Home Assistant** over MQTT.

It comes in two parts:

1. **A tiny Android app** (`android/`) that runs in the background on the TV/phone,
   connects to your MQTT broker, and draws the camera on top of whatever is on screen.
2. **A Home Assistant integration** (`custom_components/camoverlay/`, HACS-installable)
   that auto-discovers your devices and gives you buttons, selects and services to
   control them.

No ADB, no root required for control (the app listens on MQTT). The app autostarts on
boot and keeps a foreground service alive.

---

## Features

- **Fullscreen** camera on a device, or **picture-in-picture** in any corner.
- **Adaptive PiP size** — `1/4` (half the screen width) or `1/16` (quarter of the
  screen width); the window keeps a 16:9 ratio so it always fits.
- On-screen **close (✕)** and **fullscreen (⛶)** controls on phones.
- **Auto-discovery** of devices in Home Assistant (each `tv`, `phone`, … becomes a HA
  device with its own entities).
- Per-device **online** sensor and **state** sensor.
- Services for automations: `camoverlay.fullscreen`, `camoverlay.pip`, `camoverlay.stop`.

---

## Requirements

- Home Assistant with the **MQTT** integration configured and a broker (e.g. the
  Mosquitto add-on).
- An Android device (TV or phone) that can reach the broker.
- A camera reachable over HTTP MJPEG. The app expects a "single JPEG frame" URL such as
  [go2rtc](https://github.com/AlexxIT/go2rtc)'s
  `http://HOST:1984/api/frame.jpeg?src=CAMERA`.

---

## Installation

### 1. Home Assistant integration (HACS)

[![Open your Home Assistant instance and open a repository inside the Home Assistant Community Store.](https://my.home-assistant.io/badges/hacs_repository.svg)](https://my.home-assistant.io/redirect/hacs_repository/?owner=nikitacoolagin&repository=hass-camoverlay)

1. HACS → **Custom repositories** → add this repo as an **Integration**.
2. Install **CamOverlay**, then restart Home Assistant.
3. **Settings → Devices & Services → Add Integration → CamOverlay.**
   Set the MQTT topic prefix (default `camoverlay`) and, optionally, a camera entity
   for dashboards.

> Manual install: copy `custom_components/camoverlay/` into your HA
> `config/custom_components/` folder and restart.

### 2. Android app

Grab `camoverlay.apk` from the [Releases](../../releases) page, or build it yourself
(see [`android/README.md`](android/README.md)).

Install it on the TV/phone, open it once, and use the in-app checklist to:

- set the **MQTT broker** (host/port/user/pass) and the **camera stream URL**;
- set the **device name** (`tv`, `phone`, …) — this is what shows up in Home Assistant;
- grant **Display over other apps**, **Notifications** (Android 13+) and disable
  **battery optimization**;
- enable **autostart**.

Once the app connects, the device appears automatically in Home Assistant.

---

## Entities (per device)

| Entity | Type | Purpose |
| --- | --- | --- |
| `binary_sensor.camoverlay_<device>_online` | connectivity | App connected to the broker |
| `sensor.camoverlay_<device>_state` | sensor | `idle` / `full` / `pip:<size>:<corner>` |
| `button.camoverlay_<device>_fullscreen` | button | Show fullscreen |
| `button.camoverlay_<device>_show_pip` | button | Show PiP with the selected size/corner |
| `button.camoverlay_<device>_stop` | button | Hide overlay / close fullscreen |
| `select.camoverlay_<device>_pip_size` | select | `1/4` or `1/16` |
| `select.camoverlay_<device>_pip_corner` | select | corner for the PiP |

## Services

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

---

## MQTT contract

The integration and app talk over these topics (prefix configurable, default
`camoverlay`):

| Topic | Direction | Payload |
| --- | --- | --- |
| `camoverlay/<device>/cmd` | HA → app | `{"action":"full"}`, `{"action":"stop"}`, `{"action":"pip","size":"quarter\|small","corner":"tl\|tr\|bl\|br"}` |
| `camoverlay/<device>/availability` | app → HA | `online` / `offline` (retained, Last-Will) |
| `camoverlay/<device>/state` | app → HA | `idle` / `full` / `pip:<size>:<corner>` (retained) |

Because the contract is plain MQTT, you can also drive the app from any other client.

---

## License

MIT — see [LICENSE](LICENSE).
