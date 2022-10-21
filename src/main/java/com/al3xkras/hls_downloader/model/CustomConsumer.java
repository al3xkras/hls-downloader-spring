package com.al3xkras.hls_downloader.model;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class CustomConsumer implements BiConsumer<Process,BufferedReader> {
    private boolean downloadStarted = false;
    private double downloadPercentage = 0.0;

    private static final long waitBeforeExitMillis = 10000;

    Pattern regex = Pattern.compile("\\[download\\].+%");

    @Override
    public void accept(Process p, BufferedReader reader) {
        long accepted = System.currentTimeMillis();
        String line;
        while (true) {
            if (!downloadStarted)
                downloadStarted=p.isAlive();
            try {
                boolean isNull=(line = reader.readLine()) == null;
                boolean timedOut=Math.abs(System.currentTimeMillis()-accepted)>waitBeforeExitMillis;
                if (!p.isAlive())
                    break;
                if (isNull && timedOut)
                    break;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            if (line!=null) {
                Matcher matcher = regex.matcher(line);
                if (matcher.find()) {
                    log.info(matcher.group());
                }
            }

        }
    }

    public boolean isDownloadStarted() {
        return downloadStarted;
    }

    public double getDownloadPercentage() {
        return downloadPercentage;
    }
}
