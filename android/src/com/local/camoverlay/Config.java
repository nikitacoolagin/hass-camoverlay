package com.local.camoverlay;

public final class Config {
    // Fallback stream URLs if the launching intent does not provide one.
    // Override these here, or set the stream URL in the app's settings screen.
    // Example (go2rtc MJPEG frame endpoint):
    //   http://YOUR_HOST:1984/api/frame.jpeg?src=YOUR_CAMERA
    public static final String DEFAULT_FULL_URL =
            "http://CHANGE_ME:1984/api/frame.jpeg?src=camera";
    public static final String DEFAULT_PIP_URL =
            "http://CHANGE_ME:1984/api/frame.jpeg?src=camera";
    private Config() {}
}
