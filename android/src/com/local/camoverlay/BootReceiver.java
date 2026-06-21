package com.local.camoverlay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * (Re)starts ControlService after boot and on the periodic watchdog alarm
 * scheduled by ControlService. Both are gated on the autostart pref, so a
 * device the user has set to "always on" recovers the MQTT listener by itself
 * even on ROMs that kill background services.
 */
public class BootReceiver extends BroadcastReceiver {

    /** Explicit alarm action fired by ControlService's watchdog. */
    public static final String ACTION_WATCHDOG = "com.local.camoverlay.WATCHDOG";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String a = intent.getAction();
        boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(a)
                || "android.intent.action.QUICKBOOT_POWERON".equals(a);
        boolean watchdog = ACTION_WATCHDOG.equals(a);
        if (!boot && !watchdog) return;
        if (!new Prefs(context).autostart()) return;
        Intent svc = new Intent(context, ControlService.class);
        // Android 12+ forbids starting a foreground service from the background
        // (ForegroundServiceStartNotAllowedException). Apps holding the overlay
        // permission are exempt, which is our normal state; if the start is still
        // refused we swallow it and let the next watchdog tick / boot retry.
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(svc);
            else context.startService(svc);
        } catch (Throwable ignored) {}
    }
}
