package com.hakiosk.android;

public class PageSpec {
    public final String url;
    public final int durationSeconds;
    public final int zoomPercent;

    public PageSpec(String url, int durationSeconds) {
        this(url, durationSeconds, 0);
    }

    public PageSpec(String url, int durationSeconds, int zoomPercent) {
        this.url = url;
        this.durationSeconds = durationSeconds <= 0 ? 15 : durationSeconds;
        this.zoomPercent = zoomPercent > 0 ? zoomPercent : 0;
    }
}
