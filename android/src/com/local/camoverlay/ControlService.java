package com.local.camoverlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.view.WindowManager;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Always-on foreground service. Connects to MQTT, listens for commands on
 * camoverlay/<device>/cmd and launches the fullscreen / PiP / stop actions.
 * Publishes a retained availability topic (online/offline via Last-Will).
 */
public class ControlService extends Service {

    // diagnostics read by MainActivity
    public static volatile boolean RUNNING = false;
    public static volatile boolean MQTT_CONNECTED = false;
    public static volatile String LAST_CMD = "-";
    public static volatile long LAST_CMD_TIME = 0;
    public static volatile String LAST_ERROR = "-";
    public static volatile String BROKER = "-";

    private MqttClient mqtt;
    private Prefs prefs;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        startForegroundNotice();
        connectMqtt();
        RUNNING = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void connectMqtt() {
        if (mqtt != null) mqtt.stop();
        BROKER = prefs.host() + ":" + prefs.port();
        String clientId = "camoverlay-" + prefs.device() + "-" + (System.currentTimeMillis() % 100000);
        mqtt = new MqttClient(
                prefs.host(), prefs.port(), clientId, prefs.user(), prefs.pass(),
                prefs.cmdTopic(), prefs.availTopic(), "offline",
                new MqttClient.Listener() {
                    public void onConnected() {
                        MQTT_CONNECTED = true;
                        LAST_ERROR = "-";
                    }
                    public void onMessage(String topic, String payload) {
                        handleCommand(payload);
                    }
                    public void onDisconnected(String reason) {
                        MQTT_CONNECTED = false;
                        LAST_ERROR = reason;
                    }
                });
        mqtt.start();
    }

    private void handleCommand(final String payload) {
        LAST_CMD = payload;
        LAST_CMD_TIME = System.currentTimeMillis();
        main.post(new Runnable() {
            public void run() { dispatch(payload); }
        });
    }

    private void dispatch(String payload) {
        String action;
        String size = "quarter", corner = "br", url = null;
        String[] cameras = null;
        try {
            String t = payload.trim();
            if (t.startsWith("{")) {
                JSONObject j = new JSONObject(t);
                action = j.optString("action", "");
                size = j.optString("size", size);
                corner = j.optString("corner", corner);
                if (j.has("url")) url = j.optString("url");
                JSONArray arr = j.optJSONArray("cameras");
                if (arr != null && arr.length() > 0) {
                    int n = Math.min(arr.length(), 4);   // grid supports up to 4
                    cameras = new String[n];
                    for (int k = 0; k < n; k++) cameras[k] = arr.optString(k);
                }
            } else {
                // plain text: "full" | "stop" | "pip quarter br"
                String[] p = t.split("\\s+");
                action = p.length > 0 ? p[0] : "";
                if (p.length > 1) size = p[1];
                if (p.length > 2) corner = p[2];
            }
        } catch (Throwable e) {
            LAST_ERROR = "bad cmd: " + e.getMessage();
            return;
        }

        // One or many camera URLs; null entries fall back to the device default.
        String[] urls = (cameras != null) ? cameras : new String[]{ url };

        if ("full".equals(action)) {
            Intent i = new Intent(this, FullActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            i.putExtra("urls", urls);
            try { startActivity(i); } catch (Throwable e) { LAST_ERROR = "full: " + e.getMessage(); }
            publishState("full");
        } else if ("pip".equals(action)) {
            int[] cr = gridDims(urls.length);
            int cols = cr[0], rows = cr[1];
            int totalW = pipWidth(size);
            int cellW = totalW / cols;
            int cellH = Math.round(cellW * 9f / 16f);
            Intent i = new Intent(this, OverlayService.class);
            i.putExtra("action", "show");
            i.putExtra("cols", cols);
            i.putExtra("rows", rows);
            i.putExtra("cellW", cellW);
            i.putExtra("cellH", cellH);
            i.putExtra("corner", corner);
            i.putExtra("margin", 24);
            i.putExtra("urls", urls);
            try { startService(i); } catch (Throwable e) { LAST_ERROR = "pip: " + e.getMessage(); }
            publishState("pip:" + size + ":" + corner);
        } else if ("stop".equals(action)) {
            Intent i = new Intent(this, OverlayService.class);
            i.putExtra("action", "stop");
            try { startService(i); } catch (Throwable ignored) {}
            FullActivity.finishVisible();
            publishState("idle");
        } else {
            LAST_ERROR = "unknown action: " + action;
        }
    }

    /** Total PiP block width: 1/2 (quarter) or 1/4 (small) of the current screen width. */
    private int pipWidth(String size) {
        Point p = new Point();
        try {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            Display d = wm.getDefaultDisplay();
            d.getRealSize(p);
        } catch (Throwable t) { p.x = 1280; p.y = 720; }
        int screenW = p.x;                                            // current-orientation width
        return "small".equals(size) ? screenW / 4 : screenW / 2;      // 1/4 or 1/2 of screen width
    }

    /** Grid columns/rows for a given camera count (1..4). Shared with FullActivity. */
    static int[] gridDims(int n) {
        if (n <= 1) return new int[]{1, 1};
        if (n == 2) return new int[]{2, 1};
        return new int[]{2, 2};                                       // 3 or 4 cameras
    }

    private void publishState(String state) {
        if (mqtt != null) mqtt.publish(prefs.stateTopic(), state, true);
    }

    private void startForegroundNotice() {
        String chan = "camoverlay";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel c = new NotificationChannel(chan, "CamOverlay", NotificationManager.IMPORTANCE_MIN);
            c.setShowBadge(false);
            nm.createNotificationChannel(c);
        }
        Intent open = new Intent(this, MainActivity.class);
        int piFlags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, piFlags);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, chan)
                : new Notification.Builder(this);
        b.setContentTitle("CamOverlay")
                .setContentText("Слушает команды MQTT")
                .setSmallIcon(android.R.drawable.presence_video_online)
                .setOngoing(true)
                .setContentIntent(pi);
        if (Build.VERSION.SDK_INT >= 16) b.setPriority(Notification.PRIORITY_MIN);
        startForeground(1, b.build());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        RUNNING = false;
        MQTT_CONNECTED = false;
        if (mqtt != null) {
            mqtt.publish(prefs.availTopic(), "offline", true);
            mqtt.stop();
            mqtt = null;
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
