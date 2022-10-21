package com.al3xkras.hls_downloader.event;

public class HlsDownloadEvent {

    private boolean isCompleted = false;
    private static final long timeLimitMillis = 1000*60*10;
    private final String webpageUrl;
    private final String filename;
    private final String mainUrl;
    private final long timestamp = System.currentTimeMillis();

    public HlsDownloadEvent(String webpageUrl, String filename, String mainUrl) {
        this.webpageUrl = webpageUrl;
        this.filename = filename;
        this.mainUrl = mainUrl;
    }

    public String getWebpageUrl() {
        if (isCompleted)
            throw new IllegalStateException();
        return webpageUrl;
    }

    public String getFilename() {
        if (isCompleted)
            throw new IllegalStateException();
        return filename;
    }

    public String getMainUrl() {
        if (isCompleted)
            throw new IllegalStateException();
        return mainUrl;
    }

    public boolean isCompleted() {
        isCompleted = isCompleted || Math.abs(System.currentTimeMillis() - getTimestamp()) < timeLimitMillis;
        return isCompleted;
    }

    private long getTimestamp() {
        return 0;
    }
}
