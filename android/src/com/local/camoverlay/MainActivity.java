package com.local.camoverlay;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * Setup screen: edit broker/stream config, grant the permissions the app needs,
 * see a readiness checklist and live diagnostics. The whole UI is built in code
 * with a small Material-style design system ({@link Theme}) that adapts to the
 * system light / dark theme and to TV vs touch.
 */
public class MainActivity extends Activity {

    private Prefs prefs;
    private Theme th;
    private boolean tv;

    private EditText edHost, edPort, edUser, edPass, edDevice, edUrl;
    private Switch swAutostart;
    private Button toggleBtn;
    private TextView stService, stMqtt, stOverlay, stNotif, stBattery, diag;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        public void run() { refresh(); ui.postDelayed(this, 1000); }
    };

    private int dp(int v) { return th.dp(v); }
    private int fs(int phone, int tvSz) { return tv ? tvSz : phone; }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        th = new Theme(this);
        tv = prefs.isTv();

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(th.bg);
        sv.setFillViewport(true);
        LinearLayout root = col();
        int pad = dp(tv ? 28 : 18);
        root.setPadding(pad, dp(tv ? 24 : 18), pad, dp(28));
        sv.addView(root);

        root.addView(appBar());

        // ---- Readiness card ----
        LinearLayout ready = card(root, "Готовность");
        stService = new TextView(this);
        stMqtt = new TextView(this);
        stOverlay = new TextView(this);
        stNotif = new TextView(this);
        stBattery = new TextView(this);

        toggleBtn = tonalButton(toggleLabel(), new View.OnClickListener() {
            public void onClick(View v) { toggleService(); }
        });
        ready.addView(checkRow(stService, toggleBtn));
        ready.addView(divider());
        ready.addView(checkRow(stMqtt, null));
        ready.addView(divider());
        ready.addView(checkRow(stOverlay, tonalButton("Выдать", new View.OnClickListener() {
            public void onClick(View v) { grantOverlay(); }
        })));
        if (Build.VERSION.SDK_INT >= 33) {
            ready.addView(divider());
            ready.addView(checkRow(stNotif, tonalButton("Выдать", new View.OnClickListener() {
                public void onClick(View v) { grantNotif(); }
            })));
        }
        if (Build.VERSION.SDK_INT >= 23) {
            ready.addView(divider());
            ready.addView(checkRow(stBattery, tonalButton("Настроить", new View.OnClickListener() {
                public void onClick(View v) { grantBattery(); }
            })));
        }
        ready.addView(divider());
        TextView autoLabel = rowLabel("Автозапуск (OEM)");
        ready.addView(checkRow(autoLabel, tonalButton("Открыть", new View.OnClickListener() {
            public void onClick(View v) { openAppDetails(); }
        })));
        ready.addView(hint("На Xiaomi / Samsung включите автозапуск вручную в настройках системы."));

        // ---- Config card ----
        LinearLayout cfg = card(root, "Брокер MQTT и поток");
        edHost = field(cfg, "Адрес брокера", prefs.host(), InputType.TYPE_CLASS_TEXT);
        edPort = field(cfg, "Порт", String.valueOf(prefs.port()), InputType.TYPE_CLASS_NUMBER);
        edUser = field(cfg, "Пользователь", prefs.user(), InputType.TYPE_CLASS_TEXT);
        edPass = field(cfg, "Пароль", prefs.pass(),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        edDevice = field(cfg, "Имя устройства (топик)", prefs.device(), InputType.TYPE_CLASS_TEXT);
        edUrl = field(cfg, "URL потока (кадр JPEG)", prefs.url(), InputType.TYPE_CLASS_TEXT);

        cfg.addView(switchRow());
        cfg.addView(filledButton("Сохранить и перезапустить", new View.OnClickListener() {
            public void onClick(View v) { saveConfig(); }
        }));

        // ---- Diagnostics card ----
        LinearLayout dg = card(root, "Диагностика");
        diag = new TextView(this);
        diag.setTextColor(th.onSurfaceVariant);
        diag.setTextSize(fs(13, 16));
        diag.setLineSpacing(dp(2), 1f);
        diag.setTypeface(Typeface.MONOSPACE);
        dg.addView(diag);

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
                dev, edUrl.getText().toString().trim(), swAutostart.isChecked());
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
        int color = ok ? th.success : th.error;
        tvw.setText((ok ? "●  " : "○  ") + label);
        tvw.setBackground(th.chip(color));
        tvw.setTextColor(color);
        int padH = dp(12), padV = dp(7);
        tvw.setPadding(padH, padV, padH, padV);
    }

    // ---------- view helpers ----------

    private LinearLayout col() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    /** Title row with a rounded logo badge. */
    private View appBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(2), 0, 0, dp(16));

        int badge = dp(tv ? 52 : 44);
        TextView logo = new TextView(this);
        logo.setText("◉"); // fisheye glyph as a camera-lens mark
        logo.setGravity(Gravity.CENTER);
        logo.setTextColor(th.onPrimary);
        logo.setTextSize(fs(22, 26));
        GradientDrawable lg = new GradientDrawable();
        lg.setColor(th.primary);
        lg.setCornerRadius(dp(14));
        logo.setBackground(lg);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(badge, badge);
        llp.rightMargin = dp(14);
        bar.addView(logo, llp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText("CamOverlay");
        t.setTextColor(th.onBg);
        t.setTextSize(fs(24, 30));
        t.setTypeface(Typeface.DEFAULT_BOLD);
        TextView s = new TextView(this);
        s.setText(tv ? "Управление с пульта" : "Настройка");
        s.setTextColor(th.onSurfaceVariant);
        s.setTextSize(fs(14, 18));
        texts.addView(t);
        texts.addView(s);
        bar.addView(texts);
        return bar;
    }

    /** Create a titled Material card, add it to the parent and return its body. */
    private LinearLayout card(LinearLayout parent, String titleText) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(th.card());
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(2));
        int p = dp(18);
        card.setPadding(p, dp(16), p, dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        card.setLayoutParams(lp);

        TextView h = new TextView(this);
        h.setText(titleText);
        h.setTextColor(th.primary);
        h.setTextSize(fs(15, 19));
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setPadding(0, 0, 0, dp(6));
        card.addView(h);

        parent.addView(card);
        return card;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(th.outline);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1) / 2 + 1));
        v.setLayoutParams(lp);
        v.setAlpha(0.6f);
        return v;
    }

    private TextView rowLabel(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(th.onBg);
        return t;
    }

    private TextView hint(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(th.onSurfaceVariant);
        t.setTextSize(fs(12, 15));
        t.setPadding(0, dp(8), 0, 0);
        return t;
    }

    private LinearLayout checkRow(TextView status, Button action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        status.setTextSize(fs(15, 19));
        row.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View spacer = new View(this);
        row.addView(spacer, new LinearLayout.LayoutParams(
                0, 1, 1f));

        if (action != null) row.addView(action);
        return row;
    }

    private Button styledButton(String text, View.OnClickListener cl, boolean filled) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(fs(14, 18));
        btn.setAllCaps(false);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setOnClickListener(cl);
        btn.setFocusable(true);
        btn.setFocusableInTouchMode(false);
        btn.setMinHeight(0);
        btn.setMinimumHeight(0);
        if (Build.VERSION.SDK_INT >= 21) btn.setStateListAnimator(null);
        if (filled) {
            btn.setBackground(th.filledButton());
            btn.setTextColor(th.onPrimary);
            btn.setPadding(dp(20), dp(13), dp(20), dp(13));
        } else {
            btn.setBackground(th.tonalButton());
            btn.setTextColor(th.onPrimaryContainer);
            btn.setPadding(dp(18), dp(9), dp(18), dp(9));
        }
        return btn;
    }

    private Button tonalButton(String text, View.OnClickListener cl) {
        return styledButton(text, cl, false);
    }

    private Button filledButton(String text, View.OnClickListener cl) {
        Button btn = styledButton(text, cl, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        btn.setLayoutParams(lp);
        btn.setGravity(Gravity.CENTER);
        return btn;
    }

    private View switchRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, dp(2));

        TextView label = new TextView(this);
        label.setText("Автозапуск при включении");
        label.setTextColor(th.onBg);
        label.setTextSize(fs(15, 19));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        label.setLayoutParams(lp);
        row.addView(label);

        swAutostart = new Switch(this);
        swAutostart.setChecked(prefs.autostart());
        if (Build.VERSION.SDK_INT >= 21) {
            android.content.res.ColorStateList thumb = new android.content.res.ColorStateList(
                    new int[][]{ new int[]{ android.R.attr.state_checked }, new int[]{} },
                    new int[]{ th.primary, th.dark ? 0xFF8B96A5 : 0xFFFFFFFF });
            android.content.res.ColorStateList track = new android.content.res.ColorStateList(
                    new int[][]{ new int[]{ android.R.attr.state_checked }, new int[]{} },
                    new int[]{ Theme.blend(th.primary, th.surface, 0.4f), th.outline });
            swAutostart.setThumbTintList(thumb);
            swAutostart.setTrackTintList(track);
        }
        row.addView(swAutostart);
        return row;
    }

    private EditText field(LinearLayout parent, String label, String value, int inputType) {
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(th.onSurfaceVariant);
        l.setTextSize(fs(13, 16));
        l.setPadding(dp(2), dp(12), 0, dp(4));
        parent.addView(l);

        EditText e = new EditText(this);
        e.setText(value);
        e.setInputType(inputType);
        e.setTextColor(th.onBg);
        e.setHintTextColor(th.onSurfaceVariant);
        e.setTextSize(fs(15, 19));
        e.setSingleLine(true);
        e.setBackground(th.field());
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
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
