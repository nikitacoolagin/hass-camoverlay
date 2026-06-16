# CamOverlay — Android app

A small, dependency-free Android app that draws a camera stream over other apps and is
controlled from Home Assistant over MQTT. No Gradle, no AndroidX — it builds with plain
`javac` → `d8` → `aapt2`.

## What it does

- Connects to your MQTT broker and subscribes to `camoverlay/<device>/cmd`.
- Renders the camera fullscreen (an `Activity`) or as a floating overlay window
  (`SYSTEM_ALERT_WINDOW`), with on-screen controls on phones.
- Runs as a foreground service, autostarts on boot, and publishes a retained
  `online`/`offline` availability (Last-Will) and a `state` topic.
- Provides a settings/diagnostics screen (broker, stream URL, device name,
  permission checklist), adaptive to TV (D-pad) and phone (touch).

## Source

```
src/com/local/camoverlay/
  MainActivity.java     # settings + readiness checklist UI (no XML, built in code)
  ControlService.java   # foreground service: MQTT client + command dispatch
  MqttClient.java       # hand-rolled MQTT 3.1.1 client over a raw socket
  OverlayService.java   # the floating PiP window
  FullActivity.java     # the fullscreen view
  CamStream.java        # MJPEG frame fetcher
  Prefs.java            # SharedPreferences config
  BootReceiver.java     # autostart on boot
  Config.java           # default stream URLs (edit or set in-app)
```

## Configuration

Everything can be set on the app's settings screen at runtime. If you want different
build-time defaults, edit:

- `Config.java` — default stream URL (e.g. `http://HOST:1984/api/frame.jpeg?src=CAMERA`).
- `Prefs.java` — default broker host/port/user/pass.

## Build (Windows / PowerShell)

You need a JDK 17 and the Android SDK build-tools + `platforms/android-34`. The scripts
expect them under `tools/jdk` and `tools/sdk` at the repo root (adjust paths inside the
scripts if yours differ), plus a `debug.keystore` for signing.

```powershell
# TV build (targetSdk 22 — installs on old Android TV)
./build.ps1

# Phone build (targetSdk 33, cleartext enabled)
./build-phone.ps1
```

Each script runs `javac` → `d8` → `aapt2 link` (manifest only, no resource
compilation — the UI is built in code) → `aapt add classes.dex` → `zipalign` →
`apksigner`, producing a signed APK.

## Manifests

- `AndroidManifest.xml` — TV build (adds LEANBACK launcher).
- `AndroidManifest.phone.xml` — phone build (adds `POST_NOTIFICATIONS`,
  `usesCleartextTraffic`).

## Permissions

- **Display over other apps** (`SYSTEM_ALERT_WINDOW`) — required for PiP and for
  launching the fullscreen view from the background.
- **Notifications** (Android 13+) — for the foreground-service notification.
- **Ignore battery optimizations** — to keep the service alive.
- **Receive boot completed** — autostart.
