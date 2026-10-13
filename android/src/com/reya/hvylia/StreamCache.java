package com.reya.hvylia;

import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Video details from NewPipeExtractor, fetched once and shared: the YouTube tab starts the lookup as soon as
 * a video page opens, so the download sheet and the download itself usually find it ready.
 */
final class StreamCache {
    private static final long TTL_MS = 30 * 60 * 1000L; // stream URLs stay valid for hours; stay well inside that
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Map<String, Entry> MAP = new HashMap<>();

    private static final class Entry {
        final Future<StreamInfo> future;
        final long at;
        Entry(Future<StreamInfo> future, long at) { this.future = future; this.at = at; }
    }

    private StreamCache() { }

    static String canonical(String videoId) {
        return "https://www.youtube.com/watch?v=" + videoId;
    }

    /** Starts (or reuses) the lookup for a video URL. */
    static Future<StreamInfo> prefetch(final String url) {
        long now = System.currentTimeMillis();
        synchronized (MAP) {
            for (Iterator<Entry> it = MAP.values().iterator(); it.hasNext(); ) {
                if (now - it.next().at > TTL_MS) it.remove();
            }
            Entry e = MAP.get(url);
            if (e != null && !failed(e.future)) return e.future;
            Future<StreamInfo> f = POOL.submit(() -> {
                YtDownloader.ensureInit();
                return StreamInfo.getInfo(ServiceList.YouTube, url);
            });
            MAP.put(url, new Entry(f, now));
            return f;
        }
    }

    /** Waits for the lookup; a failed lookup is forgotten so the next call tries again. */
    static StreamInfo get(String url) throws Exception {
        try {
            return prefetch(url).get();
        } catch (ExecutionException e) {
            synchronized (MAP) { MAP.remove(url); }
            Throwable c = e.getCause();
            if (c instanceof Exception) throw (Exception) c;
            throw new IOException(c);
        }
    }

    /** Drops a lookup whose stream addresses stopped working, so the next call fetches fresh ones. */
    static void forget(String url) {
        synchronized (MAP) { MAP.remove(url); }
    }

    private static boolean failed(Future<StreamInfo> f) {
        if (!f.isDone()) return false;
        try { f.get(); return false; } catch (Exception e) { return true; }
    }
}
