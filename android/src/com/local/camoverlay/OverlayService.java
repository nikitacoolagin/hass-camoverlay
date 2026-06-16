package com.local.camoverlay;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws one or more cameras as a floating overlay window (the "picture-in-picture"
 * mode for devices without native PiP). Up to 4 cameras are laid out in a grid.
 * Toggled on/off via start intents.
 */
public class OverlayService extends Service {

    private WindowManager wm;
    private FrameLayout root;
    private final List<CamStream> streams = new ArrayList<>();
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

        boolean isTv = getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        int border = dp(2);

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        root = new FrameLayout(this);
        GradientDrawable frame = new GradientDrawable();
        frame.setColor(Color.BLACK);
        frame.setStroke(border, Color.WHITE);
        root.setBackground(frame);
        root.setPadding(border, border, border, border);

        // Grid of camera cells; one CamStream per cell.
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        int gap = dp(1);
        for (int r = 0; r < rows; r++) {
            LinearLayout rowLl = new LinearLayout(this);
            rowLl.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < cols; c++) {
                int idx = r * cols + c;
                ImageView iv = new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setBackgroundColor(Color.BLACK);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(cellW, cellH);
                clp.setMargins(c > 0 ? gap : 0, r > 0 ? gap : 0, 0, 0);
                rowLl.addView(iv, clp);
                if (idx < urls.length) {
                    final ImageView target = iv;
                    streams.add(new CamStream(urls[idx], cellW, new CamStream.FrameListener() {
                        public void onFrame(Bitmap bmp) { target.setImageBitmap(bmp); }
                    }));
                }
            }
            grid.addView(rowLl, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        root.addView(grid, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        // Touch controls only make sense on phones/tablets (TV is driven by remote/HA).
        if (!isTv) {
            TextView fsBtn = makeButton("⛶", Gravity.TOP | Gravity.LEFT);
            fsBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Intent fi = new Intent(OverlayService.this, FullActivity.class);
                    fi.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    try { startActivity(fi); } catch (Throwable ignored) {}
                    stopSelf();
                }
            });
            root.addView(fsBtn);

            TextView closeBtn = makeButton("✕", Gravity.TOP | Gravity.RIGHT);
            closeBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { stopSelf(); }
            });
            root.addView(closeBtn);
        }

        int overlayType = (android.os.Build.VERSION.SDK_INT >= 26)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = gravityX | gravityY;
        lp.x = margin;
        lp.y = margin;

        try {
            wm.addView(root, lp);
        } catch (Throwable t) {
            stopSelf();
            return START_NOT_STICKY;
        }
        shown = true;

        for (CamStream s : streams) s.start();
        return START_STICKY;
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

    private TextView makeButton(String label, int gravity) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(Color.WHITE);
        t.setTextSize(18);
        int pad = dp(8);
        t.setPadding(pad, dp(2), pad, dp(2));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(0xCC000000);
        bg.setCornerRadius(dp(6));
        t.setBackground(bg);
        t.setClickable(true);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = gravity;
        int m = dp(6);
        lp.setMargins(m, m, m, m);
        t.setLayoutParams(lp);
        return t;
    }

    private void teardown() {
        for (CamStream s : streams) s.stop();
        streams.clear();
        if (wm != null && root != null) {
            try { wm.removeView(root); } catch (Throwable ignored) {}
        }
        root = null; shown = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        teardown();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
