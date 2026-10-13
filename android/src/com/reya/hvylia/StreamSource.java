package com.reya.hvylia;

import android.media.MediaDataSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A YouTube audio stream for MediaPlayer. YouTube serves each connection slowly and wants the right headers,
 * so the stream is fetched in ranged chunks into a temporary file: the chunk the player asks for first, the
 * rest in the background from where the player is reading (seeking stays quick, replays need no network).
 */
final class StreamSource extends MediaDataSource {
    static final int CHUNK = 512 * 1024;

    static final class ForbiddenException extends IOException {
        ForbiddenException() { super("HTTP 403"); }
    }

    private final String url;
    private final long size;
    private final File file;
    private final FileChannel ch;
    private final boolean[] have; // guarded by locks[i]
    private final Object[] locks;
    private final Thread ahead;
    private volatile int cursor;
    private volatile boolean closed;

    /** Fetches the first chunk (and learns the size) before returning; call off the main thread. */
    StreamSource(String url, long knownSize, File file) throws IOException {
        this.url = url;
        this.file = file;
        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        raf.setLength(0);
        ch = raf.getChannel();
        try {
            long total = fetch(0, CHUNK - 1, knownSize);
            if (total <= 0) throw new IOException("unknown size");
            size = total;
        } catch (IOException e) {
            ch.close();
            file.delete();
            throw e;
        }
        int n = (int) ((size + CHUNK - 1) / CHUNK);
        have = new boolean[n];
        locks = new Object[n];
        for (int i = 0; i < n; i++) locks[i] = new Object();
        have[0] = true;
        ahead = new Thread(this::fillAhead, "stream-ahead");
        ahead.setDaemon(true);
        ahead.start();
    }

    @Override
    public int readAt(long position, byte[] buffer, int offset, int length) throws IOException {
        if (closed) throw new IOException("closed");
        if (position >= size) return -1;
        if (length <= 0) return 0;
        int c = (int) (position / CHUNK);
        cursor = c;
        ensure(c);
        long end = Math.min(size, (long) (c + 1) * CHUNK);
        int n = (int) Math.min(length, end - position);
        ByteBuffer b = ByteBuffer.wrap(buffer, offset, n);
        while (b.hasRemaining()) {
            int r = ch.read(b, position + (n - b.remaining()));
            if (r < 0) throw new IOException("short file");
        }
        return n;
    }

    @Override
    public long getSize() { return size; }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        ahead.interrupt();
        try { ch.close(); } catch (IOException ignored) { }
        file.delete();
    }

    /** How much of the stream is already here, 0…1. */
    float buffered() {
        int got = 0;
        for (int i = 0; i < have.length; i++) synchronized (locks[i]) { if (have[i]) got++; }
        return have.length == 0 ? 1f : got / (float) have.length;
    }

    private void ensure(int c) throws IOException {
        synchronized (locks[c]) {
            if (have[c]) return;
            long start = (long) c * CHUNK;
            fetch(start, Math.min(size, start + CHUNK) - 1, size);
            have[c] = true;
        }
    }

    /** Downloads the missing chunks, starting where the player reads. */
    private void fillAhead() {
        try {
            while (!closed) {
                int next = -1;
                int from = cursor;
                for (int k = 0; k < have.length && next < 0; k++) {
                    int i = (from + k) % have.length;
                    synchronized (locks[i]) { if (!have[i]) next = i; }
                }
                if (next < 0) return; // all here
                ensure(next);
            }
        } catch (IOException e) {
            // The player's own reads retry and report; nothing more to do here.
        }
    }

    /** Writes bytes [start, end] into the file; returns the stream's total size. Retries short network hiccups. */
    private long fetch(long start, long end, long known) throws IOException {
        long pos = start;
        int failures = 0;
        byte[] buf = new byte[64 * 1024];
        while (true) {
            if (closed) throw new IOException("closed");
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            try {
                c.setConnectTimeout(15000);
                c.setReadTimeout(20000);
                YtDownloader.streamHeaders(c, url);
                c.setRequestProperty("Range", "bytes=" + pos + "-" + end);
                int code = c.getResponseCode();
                if (code == 403) throw new ForbiddenException();
                if (code == 416 && known > 0 && pos >= known) return known;
                if (code != 200 && code != 206) throw new IOException("HTTP " + code);
                if (code == 200 && pos > 0) throw new IOException("ranges not supported");
                long total = known > 0 ? known : totalSize(c, code);
                long last = total > 0 ? Math.min(end, total - 1) : end;
                InputStream in = c.getInputStream();
                try {
                    for (int n; pos <= last && (n = in.read(buf, 0, (int) Math.min(buf.length, last - pos + 1))) > 0; ) {
                        ByteBuffer b = ByteBuffer.wrap(buf, 0, n);
                        while (b.hasRemaining()) ch.write(b, pos + (n - b.remaining()));
                        pos += n;
                    }
                } finally {
                    in.close();
                }
                if (pos <= last) throw new IOException("short read");
                return total;
            } catch (ForbiddenException e) {
                throw e;
            } catch (IOException e) {
                if (closed || ++failures > 3) throw e;
                try { Thread.sleep(700L * failures); } catch (InterruptedException ie) { throw new IOException("closed"); }
            } finally {
                c.disconnect();
            }
        }
    }

    private static long totalSize(HttpURLConnection c, int code) {
        String range = c.getHeaderField("Content-Range");
        if (range != null) {
            Matcher m = Pattern.compile("/(\\d+)").matcher(range);
            if (m.find()) return Long.parseLong(m.group(1));
        }
        return code == 200 ? c.getContentLengthLong() : -1;
    }
}
