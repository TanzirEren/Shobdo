package com.tanzirdev.shobdo;

import java.util.List;
import java.util.Locale;

public class SrtWriter {
    public static String ts(long ms, char sep) {
        if (ms < 0) ms = 0;
        return String.format(Locale.US, "%02d:%02d:%02d%c%03d", ms / 3600000, ms / 60000 % 60, ms / 1000 % 60, sep, ms % 1000);
    }

    public static String srt(List<Caption> l) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (Caption c : l) {
            sb.append(i++).append('\n').append(ts(c.start, ',')).append(" --> ").append(ts(c.end, ','))
              .append('\n').append(c.text).append("\n\n");
        }
        return sb.toString();
    }

    public static String vtt(List<Caption> l) {
        StringBuilder sb = new StringBuilder("WEBVTT\n\n");
        for (Caption c : l)
            sb.append(ts(c.start, '.')).append(" --> ").append(ts(c.end, '.')).append('\n').append(c.text).append("\n\n");
        return sb.toString();
    }

    public static String txt(List<Caption> l) {
        StringBuilder sb = new StringBuilder();
        for (Caption c : l) sb.append(c.text).append('\n');
        return sb.toString();
    }
}
