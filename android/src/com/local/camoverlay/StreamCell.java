package com.local.camoverlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.VideoView;

/**
 * One camera cell that renders its stream the best way for the given URL, so the PiP and the
 * full-screen overlays behave identically without duplicating the dispatch logic:
 *
 *   - WebView             -> go2rtc WebRTC / MSE player page (real-time, low latency)
 *   - VideoView           -> native HLS (.m3u8) or RTSP
 *   - ImageView+CamStream -> polled JPEG / MJPEG frames (the legacy default)
 *
 * Call {@link #create} to build the cell, add {@link #view} to your layout, then drive it with
 * {@link #start()} / {@link #stop()} / {@link #destroy()} from the host's lifecycle. The host
 * window must be hardware-accelerated (FLAG_HARDWARE_ACCELERATED) or the WebView path renders
 * a black surface.
 */
public class StreamCell {

    public final View view;
    private CamStream cam;
    private VideoView video;
    private WebView web;

    private StreamCell(View view) { this.view = view; }

    /** Build the right view for {@code url}. {@code sizeHintPx} is the JPEG decode target width. */
    public static StreamCell create(Context ctx, String url, int sizeHintPx) {
        if (isWebPlayer(url)) {
            WebView wv = new WebView(ctx);
            wv.setBackgroundColor(Color.BLACK);
            WebSettings s = wv.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setMediaPlaybackRequiresUserGesture(false);   // autoplay the camera
            s.setLoadWithOverviewMode(true);
            s.setUseWideViewPort(true);
            // Make the go2rtc <video> fill the cell edge-to-edge (no letterbox), matching
            // the MJPEG CENTER_CROP behaviour.
            wv.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView v, String u) {
                    v.evaluateJavascript(
                            "(function(){var c='html,body{margin:0;padding:0;background:#000;"
                          + "height:100%;overflow:hidden}video{width:100%!important;height:100%"
                          + "!important;object-fit:cover!important}';var st=document.createElement"
                          + "('style');st.appendChild(document.createTextNode(c));"
                          + "document.head.appendChild(st);})();", null);
                }
            });
            wv.loadUrl(url);
            StreamCell c = new StreamCell(wv);
            c.web = wv;
            return c;
        }
        if (isNativeVideo(url)) {
            VideoView vv = new VideoView(ctx);
            vv.setBackgroundColor(Color.BLACK);
            vv.setVideoURI(Uri.parse(url));
            vv.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                public void onPrepared(MediaPlayer mp) {
                    try { mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT); } catch (Throwable ignored) {}
                    mp.setLooping(true);
                    mp.start();
                }
            });
            StreamCell c = new StreamCell(vv);
            c.video = vv;
            return c;
        }
        // default: polled JPEG / MJPEG decoded into an ImageView (fills the cell, no letterbox)
        ImageView iv = new ImageView(ctx);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackgroundColor(Color.BLACK);
        final ImageView target = iv;
        StreamCell c = new StreamCell(iv);
        c.cam = new CamStream(url, sizeHintPx, new CamStream.FrameListener() {
            public void onFrame(Bitmap bmp) { target.setImageBitmap(bmp); }
        });
        return c;
    }

    public void start() {
        if (cam != null) cam.start();
        if (video != null) { try { video.start(); } catch (Throwable ignored) {} }
        if (web != null) { try { web.onResume(); } catch (Throwable ignored) {} }
    }

    public void stop() {
        if (cam != null) cam.stop();
        if (video != null) { try { video.pause(); } catch (Throwable ignored) {} }
        if (web != null) { try { web.onPause(); } catch (Throwable ignored) {} }
    }

    public void destroy() {
        if (cam != null) { cam.stop(); cam = null; }
        if (video != null) { try { video.stopPlayback(); } catch (Throwable ignored) {} video = null; }
        if (web != null) {
            try {
                web.stopLoading();
                web.loadUrl("about:blank");
                web.destroy();
            } catch (Throwable ignored) {}
            web = null;
        }
    }

    /** go2rtc WebRTC / MSE player pages — rendered in a WebView. */
    public static boolean isWebPlayer(String url) {
        if (url == null) return false;
        String low = url.toLowerCase();
        return low.contains("/stream.html")
                || low.contains("/webrtc.html")
                || low.contains("mode=webrtc")
                || low.contains("webrtc");
    }

    /** Streams a native MediaPlayer can play directly — HLS or RTSP. */
    public static boolean isNativeVideo(String url) {
        if (url == null) return false;
        String low = url.toLowerCase();
        return low.startsWith("rtsp://")
                || low.endsWith(".m3u8")
                || low.contains("stream.m3u8");
    }
}
