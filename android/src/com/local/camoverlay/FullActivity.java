package com.local.camoverlay;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.VideoView;

import java.util.ArrayList;
import java.util.List;

public class FullActivity extends Activity {

    private static volatile FullActivity instance;

    /** Called from ControlService (on the main thread) to close the fullscreen view. */
    public static void finishVisible() {
        FullActivity a = instance;
        if (a != null) a.finish();
    }

    private final List<CamStream> streams = new ArrayList<>();
    private final List<VideoView> videos = new ArrayList<>();
    private final List<WebView> webViews = new ArrayList<>();

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

        String[] urls = normalize(getIntent().getStringArrayExtra("urls"));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        int[] cr = ControlService.gridDims(urls.length);
        int cols = cr[0], rows = cr[1];
        int targetW = Math.max(320, getResources().getDisplayMetrics().widthPixels / cols);

        // Weighted grid that fills the screen: vertical rows, horizontal cells.
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
                    String url = urls[idx];
                    if (isWebViewUrl(url)) {
                        WebView wv = makeWebView();
                        wv.loadUrl(url);
                        rowLl.addView(wv, clp);
                        webViews.add(wv);
                    } else if (isNativeVideoUrl(url)) {
                        VideoView vv = new VideoView(this);
                        vv.setBackgroundColor(Color.BLACK);
                        vv.setVideoURI(Uri.parse(url));
                        vv.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                            public void onPrepared(MediaPlayer mp) {
                                try { mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT); } catch (Throwable ignored) {}
                                mp.start();
                            }
                        });
                        rowLl.addView(vv, clp);
                        videos.add(vv);
                    } else {
                        ImageView iv = new ImageView(this);
                        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        iv.setBackgroundColor(Color.BLACK);
                        rowLl.addView(iv, clp);
                        final ImageView target = iv;
                        streams.add(new CamStream(url, targetW, new CamStream.FrameListener() {
                            public void onFrame(Bitmap bmp) { target.setImageBitmap(bmp); }
                        }));
                    }
                } else {
                    View empty = new View(this);
                    empty.setBackgroundColor(Color.BLACK);
                    rowLl.addView(empty, clp);
                }
            }
            grid.addView(rowLl, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        root.addView(grid, new FrameLayout.LayoutParams(
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
    }

    /** Drop empty entries; fall back to the single legacy "url" extra or the default. */
    private String[] normalize(String[] urls) {
        if (urls == null || urls.length == 0) {
            String single = getIntent().getStringExtra("url");
            return new String[]{ single != null ? single : Config.DEFAULT_FULL_URL };
        }
        String[] out = new String[urls.length];
        for (int i = 0; i < urls.length; i++) {
            out[i] = (urls[i] == null || urls[i].length() == 0) ? Config.DEFAULT_FULL_URL : urls[i];
        }
        return out;
    }

    @Override protected void onStart() {
        super.onStart();
        for (CamStream s : streams) s.start();
        for (VideoView v : videos) {
            try { v.start(); } catch (Throwable ignored) {}
        }
    }

    @Override protected void onStop() {
        super.onStop();
        for (CamStream s : streams) s.stop();
        for (VideoView v : videos) {
            try { v.pause(); } catch (Throwable ignored) {}
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (instance == this) instance = null;
        for (CamStream s : streams) s.stop();
        for (VideoView v : videos) {
            try { v.stopPlayback(); } catch (Throwable ignored) {}
        }
        videos.clear();
        for (WebView wv : webViews) {
            try {
                wv.stopLoading();
                wv.loadUrl("about:blank");
                wv.destroy();
            } catch (Throwable ignored) {}
        }
        webViews.clear();
    }

    private WebView makeWebView() {
        WebView wv = new WebView(this);
        wv.setBackgroundColor(Color.BLACK);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        return wv;
    }

    private static boolean isWebViewUrl(String url) {
        if (url == null) return false;
        String low = url.toLowerCase();
        return low.contains("/stream.html") || low.contains("mode=webrtc");
    }

    private static boolean isNativeVideoUrl(String url) {
        if (url == null) return false;
        String low = url.toLowerCase();
        return low.startsWith("rtsp://")
                || low.endsWith(".m3u8")
                || low.contains("/api/stream.m3u8")
                || low.contains("/api/ws?src=");
    }
}
