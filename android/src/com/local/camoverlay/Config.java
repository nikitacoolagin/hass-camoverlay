package com.local.camoverlay;

public final class Config {
    // Fallback URLs if the launching intent does not provide one.
    public static final String DEFAULT_FULL_URL =
            "http://192.168.2.113:1984/api/frame.jpeg?src=podezd";
    public static final String DEFAULT_PIP_URL =
            "http://192.168.2.113:1984/api/frame.jpeg?src=podezd";
    private Config() {}
}
