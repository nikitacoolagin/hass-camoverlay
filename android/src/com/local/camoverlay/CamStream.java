package com.local.camoverlay;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Pulls frames from a go2rtc endpoint and delivers decoded Bitmaps on the UI thread.
 * Auto-detects multipart MJPEG (stream.mjpeg) vs a single-image endpoint (frame.jpeg),
 * which it polls in a loop. Reconnects on error.
 */
public class CamStream {

    public interface FrameListener {
        void onFrame(Bitmap bmp);
    }

    private final String url;
    private final int targetWidth;
    private final FrameListener listener;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private volatile boolean running = false;
    private Thread thread;

    public CamStream(String url, int targetWidth, FrameListener listener) {
        this.url = url;
        this.targetWidth = targetWidth;
        this.listener = listener;
    }

    public void start() {
        if (running) return;
        running = true;
        thread = new Thread(new Runnable() {
            public void run() { loop(); }
        }, "CamStream");
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) thread.interrupt();
        thread = null;
    }

    private void loop() {
        while (running) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(8000);
                conn.connect();
                String ct = conn.getContentType();
                if (ct != null && ct.toLowerCase().contains("multipart")) {
                    readMultipart(conn, ct);
                } else {
                    // single image -> poll
                    byte[] data = readAll(conn.getInputStream());
                    deliver(data);
                    conn.disconnect();
                    conn = null;
                    sleep(120);
                }
            } catch (Throwable t) {
                sleep(700);
            } finally {
                if (conn != null) {
                    try { conn.disconnect(); } catch (Throwable ignored) {}
                }
            }
        }
    }

    private void readMultipart(HttpURLConnection conn, String contentType) throws Exception {
        String boundary = "--myboundary";
        int bi = contentType.toLowerCase().indexOf("boundary=");
        if (bi >= 0) {
            String b = contentType.substring(bi + 9).trim();
            if (b.startsWith("\"") && b.endsWith("\"")) b = b.substring(1, b.length() - 1);
            boundary = "--" + b;
        }
        BufferedInputStream in = new BufferedInputStream(conn.getInputStream(), 32768);
        while (running) {
            // read until we hit a boundary line
            String line;
            // skip until boundary
            do {
                line = readLine(in);
                if (line == null) return;
            } while (!line.startsWith(boundary));
            // read part headers, capture content-length
            int len = -1;
            while ((line = readLine(in)) != null && line.length() > 0) {
                String low = line.toLowerCase();
                if (low.startsWith("content-length:")) {
                    try { len = Integer.parseInt(line.substring(15).trim()); } catch (Exception ignored) {}
                }
            }
            if (line == null) return;
            byte[] data;
            if (len > 0) {
                data = new byte[len];
                int off = 0;
                while (off < len) {
                    int r = in.read(data, off, len - off);
                    if (r < 0) return;
                    off += r;
                }
            } else {
                data = readUntilBoundary(in, boundary);
                if (data == null) return;
            }
            deliver(data);
        }
    }

    private void deliver(byte[] jpeg) {
        if (jpeg == null || jpeg.length < 100) return;
        final Bitmap bmp = decodeScaled(jpeg, targetWidth);
        if (bmp == null) return;
        ui.post(new Runnable() {
            public void run() {
                if (running) listener.onFrame(bmp);
                else bmp.recycle();
            }
        });
    }

    private static Bitmap decodeScaled(byte[] data, int targetW) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        int sample = 1;
        if (targetW > 0 && o.outWidth > targetW) {
            while (o.outWidth / (sample * 2) >= targetW) sample *= 2;
        }
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        d.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(data, 0, data.length, d);
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(65536);
        byte[] buf = new byte[16384];
        int r;
        while ((r = in.read(buf)) > 0) bos.write(buf, 0, r);
        return bos.toByteArray();
    }

    // reads a line terminated by \n (handles \r\n), bytes interpreted as ASCII
    private static String readLine(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(64);
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') bos.write(c);
        }
        if (c == -1 && bos.size() == 0) return null;
        return new String(bos.toByteArray(), "US-ASCII");
    }

    private static byte[] readUntilBoundary(InputStream in, String boundary) throws Exception {
        byte[] bnd = ("\n" + boundary).getBytes("US-ASCII");
        ByteArrayOutputStream bos = new ByteArrayOutputStream(65536);
        int match = 0;
        int c;
        while ((c = in.read()) != -1) {
            bos.write(c);
            if (c == (bnd[match] & 0xff)) {
                match++;
                if (match == bnd.length) {
                    byte[] all = bos.toByteArray();
                    int keep = all.length - bnd.length;
                    byte[] out = new byte[keep];
                    System.arraycopy(all, 0, out, 0, keep);
                    return out;
                }
            } else {
                match = (c == (bnd[0] & 0xff)) ? 1 : 0;
            }
        }
        return null;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
