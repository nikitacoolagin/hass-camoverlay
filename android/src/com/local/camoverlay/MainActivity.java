package com.local.camoverlay;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * Setup screen: edit broker/stream config, grant the permissions the app needs,
 * see a readiness checklist and live diagnostics. Layout is built in code (the
 * build links only the manifest, no resources) and adapts for TV vs touch.
 */
public class MainActivity extends Activity {

    private Prefs prefs;
    private boolean tv;

    private EditText edHost, edPort, edUser, edPass, edDevice, edUrl;
    private CheckBox cbAutostart;
    private Button toggleBtn;
    private TextView stService, stMqtt, stOverlay, stNotif, stBattery, diag;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        public void run() { refresh(); ui.postDelayed(this, 1000); }
    };

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
    private int fs(int phone, int tvSz) { return tv ? tvSz : phone; }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        tv = prefs.isTv();

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF101216);
        LinearLayout root = col();
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        sv.addView(root);

        root.addView(title("CamOverlay"));
        root.addView(sub(tv ? "Управление с пульта" : "Настройка"));

        // ---- Checklist ----
        root.addView(header("Готовность"));
        stService = new TextView(this);
        stMqtt = new TextView(this);
        stOverlay = new TextView(this);
        stNotif = new TextView(this);
        stBattery = new TextView(this);

        toggleBtn = button(toggleLabel(), new View.OnClickListener() {
            public void onClick(View v) { toggleService(); }
        });
        root.addView(checkRow(stService, toggleBtn));
        root.addView(checkRow(stMqtt, null));
        root.addView(checkRow(stOverlay, button("Выдать", new View.OnClickListener() {
            public void onClick(View v) { grantOverlay(); }
        })));
        if (Build.VERSION.SDK_INT >= 33) {
            root.addView(checkRow(stNotif, button("Выдать", new View.OnClickListener() {
                public void onClick(View v) { grantNotif(); }
            })));
        }
        if (Build.VERSION.SDK_INT >= 23) {
            root.addView(checkRow(stBattery, button("Настроить", new View.OnClickListener() {
                public void onClick(View v) { grantBattery(); }
            })));
        }
        TextView autoLabel = new TextView(this);
        autoLabel.setText("Автозапуск (OEM)");
        root.addView(checkRow(autoLabel, button("Открыть", new View.OnClickListener() {
            public void onClick(View v) { openAppDetails(); }
        })));
        root.addView(autostartHint());

        // ---- Config ----
        root.addView(header("Брокер MQTT и поток"));
        edHost = field(root, "Адрес брокера", prefs.host(), InputType.TYPE_CLASS_TEXT);
        edPort = field(root, "Порт", String.valueOf(prefs.port()), InputType.TYPE_CLASS_NUMBER);
        edUser = field(root, "Пользователь", prefs.user(), InputType.TYPE_CLASS_TEXT);
        edPass = field(root, "Пароль", prefs.pass(),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        edDevice = field(root, "Имя устройства (топик)", prefs.device(), InputType.TYPE_CLASS_TEXT);
        edUrl = field(root, "URL потока (кадр JPEG)", prefs.url(), InputType.TYPE_CLASS_TEXT);

        cbAutostart = new CheckBox(this);
        cbAutostart.setText("Автозапуск при включении");
        cbAutostart.setTextColor(0xFFE6E6E6);
        cbAutostart.setTextSize(fs(15, 19));
        cbAutostart.setChecked(prefs.autostart());
        cbAutostart.setPadding(0, dp(8), 0, dp(8));
        root.addView(cbAutostart);

        root.addView(button("Сохранить и перезапустить", new View.OnClickListener() {
            public void onClick(View v) { saveConfig(); }
        }));

        // ---- Diagnostics ----
        root.addView(header("Диагностика"));
        diag = new TextView(this);
        diag.setTextColor(0xFFBFC7D5);
        diag.setTextSize(fs(13, 16));
        diag.setPadding(0, dp(4), 0, dp(4));
        root.addView(diag);

        setContentView(sv);
        if (tv) toggleBtn.requestFocus();

        if (prefs.autostart() && !ControlService.RUNNING) startControl();
    }

    private void startControl() {
        Intent svc = new Intent(this, ControlService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
        else startService(svc);
    }

    @Override protected void onResume() { super.onResume(); ui.post(ticker); }
    @Override protected void onPause() { super.onPause(); ui.removeCallbacks(ticker); }

    // ---------- actions ----------

    private void toggleService() {
        if (ControlService.RUNNING) {
            stopService(new Intent(this, ControlService.class));
        } else {
            startControl();
        }
        ui.postDelayed(new Runnable() { public void run() { refresh(); } }, 600);
    }

    private void grantOverlay() {
        if (Build.VERSION.SDK_INT >= 23) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    private void grantNotif() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }

    private void grantBattery() {
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Throwable t) {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }
        }
    }

    private void openAppDetails() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Throwable ignored) {}
    }

    private void saveConfig() {
        int port = Prefs.DEF_PORT;
        try { port = Integer.parseInt(edPort.getText().toString().trim()); } catch (Exception ignored) {}
        String dev = edDevice.getText().toString().trim();
        if (dev.length() == 0) dev = tv ? "tv" : "phone";
        prefs.save(
                edHost.getText().toString().trim(), port,
                edUser.getText().toString().trim(), edPass.getText().toString(),
                dev, edUrl.getText().toString().trim(), cbAutostart.isChecked());
        // restart service to pick up new config
        Intent svc = new Intent(this, ControlService.class);
        stopService(svc);
        ui.postDelayed(new Runnable() {
            public void run() {
                Intent s = new Intent(MainActivity.this, ControlService.class);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(s);
                else startService(s);
            }
        }, 500);
        Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show();
    }

    // ---------- status ----------

    private boolean overlayOk() {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
    }

    private boolean notifOk() {
        if (Build.VERSION.SDK_INT < 33) return true;
        return checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private boolean batteryOk() {
        if (Build.VERSION.SDK_INT < 23) return true;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private String toggleLabel() {
        return ControlService.RUNNING ? "Остановить" : "Запустить";
    }

    private void refresh() {
        setStatus(stService, "Сервис", ControlService.RUNNING);
        setStatus(stMqtt, "MQTT подключён", ControlService.MQTT_CONNECTED);
        setStatus(stOverlay, "Поверх других окон", overlayOk());
        if (Build.VERSION.SDK_INT >= 33) setStatus(stNotif, "Уведомления", notifOk());
        if (Build.VERSION.SDK_INT >= 23) setStatus(stBattery, "Без эконома батареи", batteryOk());
        if (toggleBtn != null) toggleBtn.setText(toggleLabel());

        long ago = ControlService.LAST_CMD_TIME == 0 ? -1
                : (System.currentTimeMillis() - ControlService.LAST_CMD_TIME) / 1000;
        StringBuilder sb = new StringBuilder();
        sb.append("IP устройства: ").append(localIp()).append('\n');
        sb.append("Брокер: ").append(ControlService.BROKER).append('\n');
        sb.append("Топик команд: ").append(prefs.cmdTopic()).append('\n');
        sb.append("Топик доступности: ").append(prefs.availTopic()).append('\n');
        sb.append("MQTT: ").append(ControlService.MQTT_CONNECTED ? "подключён" : "нет").append('\n');
        sb.append("Посл. команда: ").append(ControlService.LAST_CMD);
        if (ago >= 0) sb.append("  (").append(ago).append(" c назад)");
        sb.append('\n');
        sb.append("Посл. ошибка: ").append(ControlService.LAST_ERROR);
        diag.setText(sb.toString());
    }

    private void setStatus(TextView tvw, String label, boolean ok) {
        tvw.setText((ok ? "✓ " : "✗ ") + label);
        tvw.setTextColor(ok ? 0xFF5BD18B : 0xFFE5734D);
    }

    // ---------- view helpers ----------

    private LinearLayout col() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    private TextView title(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(Color.WHITE); t.setTextSize(fs(24, 30));
        t.setPadding(0, 0, 0, dp(4));
        return t;
    }

    private TextView sub(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(0xFF8A93A2); t.setTextSize(fs(14, 18));
        t.setPadding(0, 0, 0, dp(12));
        return t;
    }

    private TextView header(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(0xFF7FB2FF); t.setTextSize(fs(16, 20));
        t.setPadding(0, dp(18), 0, dp(8));
        return t;
    }

    private TextView autostartHint() {
        TextView t = new TextView(this);
        t.setText("Автозапуск OEM: на Xiaomi/Samsung включите вручную");
        t.setTextColor(0xFF8A93A2); t.setTextSize(fs(12, 15));
        return t;
    }

    private LinearLayout checkRow(TextView status, Button action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        status.setTextColor(0xFFE6E6E6);
        status.setTextSize(fs(15, 19));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        status.setLayoutParams(lp);
        row.addView(status);
        if (action != null) row.addView(action);
        return row;
    }

    private Button button(String text, View.OnClickListener cl) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(fs(14, 18));
        btn.setAllCaps(false);
        btn.setOnClickListener(cl);
        btn.setFocusable(true);
        btn.setFocusableInTouchMode(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        btn.setLayoutParams(lp);
        return btn;
    }

    private EditText field(LinearLayout parent, String label, String value, int inputType) {
        TextView l = new TextView(this);
        l.setText(label); l.setTextColor(0xFF8A93A2); l.setTextSize(fs(13, 16));
        l.setPadding(0, dp(8), 0, dp(2));
        parent.addView(l);
        EditText e = new EditText(this);
        e.setText(value);
        e.setInputType(inputType);
        e.setTextColor(Color.WHITE);
        e.setTextSize(fs(15, 19));
        e.setSingleLine(true);
        parent.addView(e);
        return e;
    }

    private String localIp() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                 en.hasMoreElements(); ) {
                NetworkInterface ni = en.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (Enumeration<InetAddress> a = ni.getInetAddresses(); a.hasMoreElements(); ) {
                    InetAddress addr = a.nextElement();
                    if (!addr.isLoopbackAddress() && addr.getAddress().length == 4) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Throwable ignored) {}
        return "?";
    }
}
