package com.al3xkras.hls_downloader.event;

import com.al3xkras.hls_downloader.config.ShutdownManager;
import org.springframework.context.ApplicationEvent;

public class QuitEvent extends ApplicationEvent {
    private final ShutdownManager manager;
    private final boolean forceStop;
    private final int shutdownTimeout;

    public QuitEvent(ShutdownManager manager, boolean forceStop, int shutdownTimeout) {
        super(shutdownTimeout);
        this.manager = manager;
        this.forceStop = forceStop;
        this.shutdownTimeout = shutdownTimeout;
    }

    public ShutdownManager getManager() {
        return manager;
    }

    public boolean isForceStop() {
        return forceStop;
    }

    public int getShutdownTimeout() {
        return shutdownTimeout;
    }
}
