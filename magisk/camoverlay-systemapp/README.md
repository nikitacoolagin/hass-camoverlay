# CamOverlay as a persistent system app (experimental)

This Magisk module turns CamOverlay into a **persistent system app**. It exists
because some TV ROMs (notably Xiaomi MiTV) kill normal user apps the moment they
leave the foreground (`force stop … to free resource`) and resource-kill heavy
processes such as the WebRTC WebView. A `FLAG_SYSTEM` app with
`android:persistent="true"` is kept running by the framework and is spared by
those killers — no overlay keep-alive, no root watchdog needed.

> **Branch status:** experimental (`system-app` branch). Requires root + Magisk
> and a reboot. Modifies the (systemless) `/system` mount. Test before relying on it.

## What's inside

```
camoverlay-systemapp/
├── module.prop
├── service.sh                       # grants the SYSTEM_ALERT_WINDOW app-op after boot
└── system/app/CamOverlay/CamOverlay.apk   # mounted into /system/app → scanned as a system app
```

The APK here is the standard CamOverlay build with `android:persistent="true"`
added — it is harmless when sideloaded normally (the flag is ignored for
non-system installs).

## Install / test

1. Remove the regular user install so the system copy is the only one:
   ```sh
   pm uninstall com.local.camoverlay      # ignore "not installed"
   ```
2. Copy this folder to `/data/adb/modules/camoverlay_systemapp` (root), or zip it
   and flash via the Magisk app.
3. Reboot.
4. Verify it came up as a persistent system app:
   ```sh
   dumpsys package com.local.camoverlay | grep -E "flags|pkgFlags"   # expect SYSTEM, PERSISTENT
   dumpsys activity processes | grep camoverlay                       # expect a persistent proc (low oomAdj)
   ```
5. Provision MQTT config as usual (the app keeps its `shared_prefs`).

To remove: delete `/data/adb/modules/camoverlay_systemapp`, reboot, and reinstall
the normal APK from `dist/`.
