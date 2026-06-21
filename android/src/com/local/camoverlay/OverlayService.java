package com.local.camoverlay;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws one or more cameras as a floating overlay window. Two layouts are
 * supported: a corner "picture-in-picture" card ({@code mode=pip}) and a
 * full-screen grid ({@code mode=full}).
 *
 * <p>Both layouts are plain overlay windows owned by this Service — deliberately
 * NOT an Activity. On some TV ROMs (MiTV) the system force-stops a package the
 * moment one of its activities leaves the foreground ("force stop … to free
 * resource"), which would also kill {@link ControlService} and drop the MQTT
 * link. A service-owned overlay never pauses an activity, so it is left alone.
 */
public class OverlayService extends Service {

    private WindowManager wm;
    private View decor;          // what we hand to the WindowManager
    private final List<StreamCell> cells = new ArrayList<>();
    private String[] currentUrls;
    private boolean shown = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getStringExtra("action") : "show";
        if ("stop".equals(action) || ("toggle".equals(action) && shown)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        // (re)build the overlay with the latest parameters
        teardown();

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        boolean isTv = getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        String mode = intent != null ? intent.getStringExtra("mode") : null;

        if ("full".equals(mode)) {
            buildFull(intent, isTv);
        } else {
            buildPip(intent, isTv);
        }

        if (!shown) return START_NOT_STICKY;
        for (StreamCell c : cells) c.start();
        return START_STICKY;
    }

    // ---------------------------------------------------------------- full screen

    /** Full-screen grid of cameras, each cell filling its area (CENTER_CROP). */
    private void buildFull(Intent intent, boolean isTv) {
        String[] urls = normalize(intent != null ? intent.getStringArrayExtra("urls") : null,
                Config.DEFAULT_FULL_URL);
        currentUrls = urls;
        int cols = intent != null ? intent.getIntExtra("cols", 1) : 1;
        int rows = intent != null ? intent.getIntExtra("rows", 1) : 1;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int targetW = Math.max(320, screenW / Math.max(1, cols));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // Weighted grid that fills the screen: cells sit edge-to-edge, square corners.
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (int r = 0; r < rows; r++) {
            LinearLayout rowLl = new LinearLayout(this);
            rowLl.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < cols; c++) {
                int idx = r * cols + c;
                LinearLayout.LayoutParams clp =
                        new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
                if (idx < urls.length) {
                    StreamCell cell = StreamCell.create(this, urls[idx], targetW);
                    cells.add(cell);
                    rowLl.addView(cell.view, clp);
                } else {
                    View empty = new View(this);
                    empty.setBackgroundColor(0xFF0C0E13);
                    rowLl.addView(empty, clp);
                }
            }
            grid.addView(rowLl, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        root.addView(grid, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Touch close only on phones/tablets (TV is driven by remote / HA via MQTT).
        if (!isTv) {
            TextView close = makeButton("✕", Gravity.TOP | Gravity.RIGHT);
            close.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { stopSelf(); }
            });
            root.addView(close);
        }

        decor = root;
        int overlayType = (Build.VERSION.SDK_INT >= 26)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.OPAQUE);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        try {
            wm.addView(decor, lp);
            shown = true;
        } catch (Throwable t) {
            stopSelf();
        }
    }

    // ---------------------------------------------------------------- picture-in-picture

    /** Corner card with one or more cameras in a grid (the PiP layout). */
    private void buildPip(Intent intent, boolean isTv) {
        int cols = 1, rows = 1, cellW = 640, cellH = 360, margin = 32;
        int gravityX = Gravity.RIGHT, gravityY = Gravity.TOP;
        String[] urls = null;
        if (intent != null) {
            cols = intent.getIntExtra("cols", cols);
            rows = intent.getIntExtra("rows", rows);
            cellW = intent.getIntExtra("cellW", cellW);
            cellH = intent.getIntExtra("cellH", cellH);
            if (intent.hasExtra("margin")) margin = intent.getIntExtra("margin", margin);
            urls = intent.getStringArrayExtra("urls");
            String corner = intent.getStringExtra("corner");
            if ("tl".equals(corner)) { gravityX = Gravity.LEFT;  gravityY = Gravity.TOP; }
            else if ("bl".equals(corner)) { gravityX = Gravity.LEFT;  gravityY = Gravity.BOTTOM; }
            else if ("br".equals(corner)) { gravityX = Gravity.RIGHT; gravityY = Gravity.BOTTOM; }
            else { gravityX = Gravity.RIGHT; gravityY = Gravity.TOP; }
        }
        urls = normalize(urls, Config.DEFAULT_PIP_URL);
        currentUrls = urls;

        // Plain rectangular card, no border / rounding / inset — the cameras fill
        // it edge to edge. A soft elevation keeps the floating PiP readable.
        FrameLayout card = new FrameLayout(this);
        card.setBackgroundColor(0xFF0C0E13);
        card.setPadding(0, 0, 0, 0);
        if (Build.VERSION.SDK_INT >= 21) {
            card.setElevation(dp(12));
        }

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        int gap = 0;
        for (int r = 0; r < rows; r++) {
            LinearLayout rowLl = new LinearLayout(this);
            rowLl.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < cols; c++) {
                int idx = r * cols + c;
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(cellW, cellH);
                clp.setMargins(c > 0 ? gap : 0, r > 0 ? gap : 0, 0, 0);
                if (idx < urls.length) {
                    StreamCell cell = StreamCell.create(this, urls[idx], cellW);
                    cells.add(cell);
                    rowLl.addView(cell.view, clp);
                } else {
                    View empty = new View(this);
                    empty.setBackgroundColor(Color.BLACK);
                    rowLl.addView(empty, clp);
                }
            }
            grid.addView(rowLl, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        card.addView(grid, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        // Touch controls only make sense on phones/tablets (TV is driven by remote/HA).
        if (!isTv) {
            final String[] fsUrls = urls;
            TextView fsBtn = makeButton("⛶", Gravity.TOP | Gravity.LEFT);
            fsBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    int[] cr = ControlService.gridDims(fsUrls.length);
                    Intent fi = new Intent(OverlayService.this, OverlayService.class);
                    fi.putExtra("action", "show");
                    fi.putExtra("mode", "full");
                    fi.putExtra("cols", cr[0]);
                    fi.putExtra("rows", cr[1]);
                    fi.putExtra("urls", fsUrls);
                    try { startService(fi); } catch (Throwable ignored) {}
                }
            });
            card.addView(fsBtn);

            TextView closeBtn = makeButton("✕", Gravity.TOP | Gravity.RIGHT);
            closeBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { stopSelf(); }
            });
            card.addView(closeBtn);
        }

        // Transparent wrapper so the card's elevation shadow has room to render.
        FrameLayout wrapper = new FrameLayout(this);
        int shadowPad = dp(12);
        wrapper.setPadding(shadowPad, shadowPad, shadowPad, shadowPad);
        wrapper.addView(card, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        decor = wrapper;

        int overlayType = (Build.VERSION.SDK_INT >= 26)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = gravityX | gravityY;
        // Offset by the margin minus the shadow padding so the visible card keeps
        // the requested distance from the screen edge.
        int edge = Math.max(0, margin - shadowPad);
        lp.x = edge;
        lp.y = edge;

        try {
            wm.addView(decor, lp);
            shown = true;
        } catch (Throwable t) {
            stopSelf();
        }
    }

    /** Drop empty entries and fall back to the default URL where needed. */
    private static String[] normalize(String[] urls, String fallback) {
        if (urls == null || urls.length == 0) return new String[]{ fallback };
        String[] out = new String[urls.length];
        for (int i = 0; i < urls.length; i++) {
            out[i] = (urls[i] == null || urls[i].length() == 0) ? fallback : urls[i];
        }
        return out;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** Circular, semi-transparent control button with a white glyph. */
    private TextView makeButton(String label, int gravity) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(Color.WHITE);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        int size = dp(34);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xB3000000);
        bg.setStroke(dp(1), 0x40FFFFFF);
        t.setBackground(bg);
        t.setClickable(true);
        if (Build.VERSION.SDK_INT >= 21) t.setElevation(dp(2));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
        lp.gravity = gravity;
        int m = dp(8);
        lp.setMargins(m, m, m, m);
        t.setLayoutParams(lp);
        return t;
    }

    private void teardown() {
        for (StreamCell c : cells) c.destroy();
        cells.clear();
        if (wm != null && decor != null) {
            try { wm.removeView(decor); } catch (Throwable ignored) {}
        }
        decor = null; shown = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        teardown();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
