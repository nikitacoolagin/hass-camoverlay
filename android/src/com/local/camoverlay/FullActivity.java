package com.local.camoverlay;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

public class FullActivity extends Activity {

    private static volatile FullActivity instance;

    /** Called from ControlService (on the main thread) to close the fullscreen view. */
    public static void finishVisible() {
        FullActivity a = instance;
        if (a != null) a.finish();
    }

    private CamStream stream;
    private ImageView image;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(image, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // Touch close button only on phones/tablets (TV uses the remote's Back).
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            float d = getResources().getDisplayMetrics().density;
            TextView close = new TextView(this);
            close.setText("✕");
            close.setTextColor(Color.WHITE);
            close.setTextSize(22);
            int pad = Math.round(12 * d);
            close.setPadding(pad, Math.round(4 * d), pad, Math.round(4 * d));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xCC000000);
            bg.setCornerRadius(8 * d);
            close.setBackground(bg);
            close.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { finish(); }
            });
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.TOP | Gravity.RIGHT;
            int m = Math.round(12 * d);
            lp.setMargins(m, m, m, m);
            root.addView(close, lp);
        }

        setContentView(root);

        String url = getIntent().getStringExtra("url");
        if (url == null) url = Config.DEFAULT_FULL_URL;

        stream = new CamStream(url, 1280, new CamStream.FrameListener() {
            public void onFrame(Bitmap bmp) { image.setImageBitmap(bmp); }
        });
    }

    @Override protected void onStart() { super.onStart(); stream.start(); }
    @Override protected void onStop() { super.onStop(); stream.stop(); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (instance == this) instance = null;
        if (stream != null) stream.stop();
    }
}
