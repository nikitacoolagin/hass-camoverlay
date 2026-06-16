package com.local.camoverlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

/** App configuration, persisted in SharedPreferences. Override defaults below or set them in-app. */
public class Prefs {

    private static final String FILE = "camoverlay";

    public static final String K_HOST = "broker_host";
    public static final String K_PORT = "broker_port";
    public static final String K_USER = "broker_user";
    public static final String K_PASS = "broker_pass";
    public static final String K_DEVICE = "device_id";
    public static final String K_URL = "stream_url";
    public static final String K_AUTOSTART = "autostart";

    public static final String DEF_HOST = "";       // set in the app's settings screen
    public static final int DEF_PORT = 1883;
    public static final String DEF_USER = "";
    public static final String DEF_PASS = "";
    public static final String DEF_URL = Config.DEFAULT_FULL_URL;

    private final SharedPreferences sp;
    private final boolean isTv;

    public Prefs(Context c) {
        sp = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        isTv = c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || c.getPackageManager().hasSystemFeature("android.software.leanback");
    }

    public boolean isTv() { return isTv; }

    public String host()   { return sp.getString(K_HOST, DEF_HOST); }
    public int port()      { return sp.getInt(K_PORT, DEF_PORT); }
    public String user()   { return sp.getString(K_USER, DEF_USER); }
    public String pass()   { return sp.getString(K_PASS, DEF_PASS); }
    public String url()    { return sp.getString(K_URL, DEF_URL); }
    public boolean autostart() { return sp.getBoolean(K_AUTOSTART, true); }

    public String device() {
        return sp.getString(K_DEVICE, isTv ? "tv" : "phone");
    }

    public String baseTopic()  { return "camoverlay/" + device(); }
    public String cmdTopic()   { return baseTopic() + "/cmd"; }
    public String availTopic() { return baseTopic() + "/availability"; }
    public String stateTopic() { return baseTopic() + "/state"; }

    public void save(String host, int port, String user, String pass,
                     String device, String url, boolean autostart) {
        SharedPreferences.Editor e = sp.edit();
        e.putString(K_HOST, host);
        e.putInt(K_PORT, port);
        e.putString(K_USER, user);
        e.putString(K_PASS, pass);
        e.putString(K_DEVICE, device);
        e.putString(K_URL, url);
        e.putBoolean(K_AUTOSTART, autostart);
        e.apply();
    }
}
