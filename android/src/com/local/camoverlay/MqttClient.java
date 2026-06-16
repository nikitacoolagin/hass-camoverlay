package com.local.camoverlay;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.io.ByteArrayOutputStream;

/**
 * Minimal MQTT 3.1.1 client (QoS 0 only), no external dependencies.
 * Runs its own thread: connects, subscribes, dispatches PUBLISH messages,
 * keeps the connection alive with PINGREQ, and reconnects with backoff.
 * Supports a retained Last-Will so the broker marks us offline if we drop.
 */
public class MqttClient {

    public interface Listener {
        void onConnected();
        void onMessage(String topic, String payload);
        void onDisconnected(String reason);
    }

    private final String host;
    private final int port;
    private final String clientId;
    private final String user;
    private final String pass;
    private final String subTopic;
    private final String willTopic;
    private final String willPayload;
    private final Listener listener;
    private final int keepAlive = 60;

    private volatile boolean running = false;
    private volatile boolean connected = false;
    private Thread thread;
    private Socket socket;
    private OutputStream out;
    private InputStream in;
    private long lastPing = 0;

    public MqttClient(String host, int port, String clientId, String user, String pass,
                      String subTopic, String willTopic, String willPayload, Listener l) {
        this.host = host;
        this.port = port;
        this.clientId = clientId;
        this.user = user;
        this.pass = pass;
        this.subTopic = subTopic;
        this.willTopic = willTopic;
        this.willPayload = willPayload;
        this.listener = l;
    }

    public boolean isConnected() { return connected; }

    public void start() {
        if (running) return;
        running = true;
        thread = new Thread(new Runnable() { public void run() { loop(); } }, "mqtt");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        closeSocket();
        if (thread != null) { thread.interrupt(); thread = null; }
    }

    private void loop() {
        int backoff = 1000;
        while (running) {
            try {
                connect();
                backoff = 1000;
                // publish retained availability "online"
                publish(willTopic, "online", true);
                subscribe(subTopic);
                connected = true;
                if (listener != null) listener.onConnected();
                lastPing = System.currentTimeMillis();
                readLoop();
            } catch (Throwable t) {
                connected = false;
                if (listener != null) listener.onDisconnected(String.valueOf(t.getMessage()));
            } finally {
                connected = false;
                closeSocket();
            }
            if (!running) break;
            try { Thread.sleep(backoff); } catch (InterruptedException e) { break; }
            backoff = Math.min(backoff * 2, 30000);
        }
    }

    private void connect() throws Exception {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), 10000);
        s.setSoTimeout(1000);
        s.setTcpNoDelay(true);
        out = s.getOutputStream();
        in = s.getInputStream();
        socket = s;

        ByteArrayOutputStream vh = new ByteArrayOutputStream();
        writeString(vh, "MQTT");
        vh.write(4); // protocol level 3.1.1
        int flags = 0x02; // clean session
        boolean hasUser = user != null && user.length() > 0;
        boolean hasPass = pass != null && pass.length() > 0;
        boolean hasWill = willTopic != null && willTopic.length() > 0;
        if (hasUser) flags |= 0x80;
        if (hasPass) flags |= 0x40;
        if (hasWill) { flags |= 0x04; flags |= 0x20; } // will + will-retain, QoS 0
        vh.write(flags);
        vh.write((keepAlive >> 8) & 0xFF);
        vh.write(keepAlive & 0xFF);

        ByteArrayOutputStream pl = new ByteArrayOutputStream();
        writeString(pl, clientId);
        if (hasWill) { writeString(pl, willTopic); writeString(pl, "offline"); }
        if (hasUser) writeString(pl, user);
        if (hasPass) writeString(pl, pass);

        ByteArrayOutputStream pkt = new ByteArrayOutputStream();
        pkt.write(0x10);
        writeRemaining(pkt, vh.size() + pl.size());
        vh.writeTo(pkt);
        pl.writeTo(pkt);
        out.write(pkt.toByteArray());
        out.flush();

        // read CONNACK
        int type = readByteBlocking();
        if (type != 0x20) throw new Exception("expected CONNACK, got " + type);
        int rl = readRemainingLength();
        byte[] body = readN(rl);
        if (body.length < 2 || body[1] != 0) throw new Exception("CONNECT refused rc=" + (body.length >= 2 ? body[1] : -1));
    }

    private void subscribe(String topic) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(0x00); body.write(0x01); // packet id = 1
        writeString(body, topic);
        body.write(0x00); // QoS 0
        ByteArrayOutputStream pkt = new ByteArrayOutputStream();
        pkt.write(0x82);
        writeRemaining(pkt, body.size());
        body.writeTo(pkt);
        out.write(pkt.toByteArray());
        out.flush();
    }

    public synchronized void publish(String topic, String payload, boolean retain) {
        try {
            if (out == null) return;
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            writeString(body, topic);
            byte[] pb = payload.getBytes("UTF-8");
            body.write(pb);
            ByteArrayOutputStream pkt = new ByteArrayOutputStream();
            pkt.write(0x30 | (retain ? 0x01 : 0x00));
            writeRemaining(pkt, body.size());
            body.writeTo(pkt);
            out.write(pkt.toByteArray());
            out.flush();
        } catch (Throwable ignored) {}
    }

    private void readLoop() throws Exception {
        while (running) {
            // keepalive ping
            long now = System.currentTimeMillis();
            if (now - lastPing > keepAlive * 1000L / 2) {
                ping();
                lastPing = now;
            }
            int first;
            try {
                first = in.read();
            } catch (SocketTimeoutException te) {
                continue;
            }
            if (first < 0) throw new Exception("stream closed");
            int type = first & 0xF0;
            int rl = readRemainingLength();
            byte[] body = readN(rl);
            if (type == 0x30) { // PUBLISH (QoS 0)
                int tl = ((body[0] & 0xFF) << 8) | (body[1] & 0xFF);
                String topic = new String(body, 2, tl, "UTF-8");
                String payload = new String(body, 2 + tl, body.length - 2 - tl, "UTF-8");
                if (listener != null) listener.onMessage(topic, payload);
            }
            // PINGRESP / SUBACK / others: ignore
        }
    }

    private void ping() throws Exception {
        out.write(new byte[]{(byte) 0xC0, 0x00});
        out.flush();
    }

    private int readByteBlocking() throws Exception {
        while (running) {
            try {
                int b = in.read();
                if (b < 0) throw new Exception("closed");
                return b;
            } catch (SocketTimeoutException te) { /* retry */ }
        }
        throw new Exception("stopped");
    }

    private int readRemainingLength() throws Exception {
        int multiplier = 1, value = 0, digit;
        do {
            digit = readByteBlocking();
            value += (digit & 0x7F) * multiplier;
            multiplier *= 128;
        } while ((digit & 0x80) != 0);
        return value;
    }

    private byte[] readN(int n) throws Exception {
        byte[] buf = new byte[n];
        int off = 0;
        while (off < n) {
            int r;
            try { r = in.read(buf, off, n - off); }
            catch (SocketTimeoutException te) { continue; }
            if (r < 0) throw new Exception("closed mid-packet");
            off += r;
        }
        return buf;
    }

    private static void writeString(ByteArrayOutputStream o, String s) {
        try {
            byte[] b = s.getBytes("UTF-8");
            o.write((b.length >> 8) & 0xFF);
            o.write(b.length & 0xFF);
            o.write(b);
        } catch (Exception ignored) {}
    }

    private static void writeRemaining(ByteArrayOutputStream o, int len) {
        do {
            int d = len % 128;
            len /= 128;
            if (len > 0) d |= 0x80;
            o.write(d);
        } while (len > 0);
    }

    private void closeSocket() {
        try { if (socket != null) socket.close(); } catch (Throwable ignored) {}
        socket = null; out = null; in = null;
    }
}
