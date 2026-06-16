# CamOverlay

Control a camera overlay on an **Android TV or phone** from Home Assistant over MQTT —
fullscreen or picture-in-picture, no ADB or root needed.

- Auto-discovers your devices (each becomes a HA device).
- Buttons + selects for fullscreen / PiP (size & corner) / stop.
- `online` and `state` sensors per device.
- Services: `camoverlay.fullscreen`, `camoverlay.pip`, `camoverlay.stop`.

Requires the **MQTT** integration and the companion Android app (see the repository
README for the APK and build instructions).
