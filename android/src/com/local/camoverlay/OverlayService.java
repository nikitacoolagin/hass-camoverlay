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
import android.widget.TextView;

/**
 * Draws the camera as a floating overlay window (the "picture-in-picture" mode for
 * Android 6, which has no native PiP). Toggled on/off via start intents.
 */
public class OverlayService extends Service {

    private WindowManager wm;
    private FrameLayout root;
    private ImageView image;
    private CamStream stream;
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

        int w = 640, h = 360;
        String url = Config.DEFAULT_PIP_URL;
        int gravityX = Gravity.RIGHT, gravityY = Gravity.TOP;
        int margin = 32;
        if (intent != null) {
            if (intent.hasExtra("w")) w = intent.getIntExtra("w", w);
            if (intent.hasExtra("h")) h = intent.getIntExtra("h", h);
            if (intent.hasExtra("margin")) margin = intent.getIntExtra("margin", margin);
            if (intent.getStringExtra("url") != null) url = intent.getStringExtra("url");
            String corner = intent.getStringExtra("corner");
            if ("tl".equals(corner)) { gravityX = Gravity.LEFT;  gravityY = Gravity.TOP; }
            else if ("bl".equals(corner)) { gravityX = Gravity.LEFT;  gravityY = Gravity.BOTTOM; }
            else if ("br".equals(corner)) { gravityX = Gravity.RIGHT; gravityY = Gravity.BOTTOM; }
            else { gravityX = Gravity.RIGHT; gravityY = Gravity.TOP; }
        }

        final String fullUrl = url;
        boolean isTv = getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        int border = dp(2);

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        root = new FrameLayout(this);
        GradientDrawable frame = new GradientDrawable();
        frame.setColor(Color.BLACK);
        frame.setStroke(border, Color.WHITE);
        root.setBackground(frame);
        root.setPadding(border, border, border, border);

        image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(image, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

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
                w, h,
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

        stream = new CamStream(url, w, new CamStream.FrameListener() {
            public void onFrame(Bitmap bmp) { if (image != null) image.setImageBitmap(bmp); }
        });
        stream.start();
        return START_STICKY;
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
        if (stream != null) { stream.stop(); stream = null; }
        if (wm != null && root != null) {
            try { wm.removeView(root); } catch (Throwable ignored) {}
        }
        root = null; image = null; shown = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        teardown();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
