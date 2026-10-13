package com.reya.hvylia;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Spotify track for the download service, and the gap-filler for it: the track page's public link-preview tags
 * (artist, length, cover, album). Only metadata — the audio itself comes from YouTube.
 */
final class SpotifyMeta {
    static final class Track {
        String id = "";
        String title = "";
        String artists = "";
        String album = "";
        String cover = "";
        long seconds;
    }

    private SpotifyMeta() { }

    /** Spotify image URLs carry the size in the hash prefix; ask for the 640 px cover. */
    static String bigCover(String url) {
        if (url == null) return "";
        return url.replace("ab67616d00004851", "ab67616d0000b273").replace("ab67616d00001e02", "ab67616d0000b273");
    }

    /** Fills missing artist / length / cover / album of a track from its public page's link-preview tags. */
    static void complete(Track t) {
        if (t.id.isEmpty() || (!t.artists.isEmpty() && t.seconds > 0 && !t.cover.isEmpty() && !t.album.isEmpty())) return;
        try {
            Track f = parseHtml(fetch("https://open.spotify.com/track/" + t.id));
            if (t.title.isEmpty()) t.title = f.title;
            if (t.artists.isEmpty()) t.artists = f.artists;
            if (t.seconds <= 0) t.seconds = f.seconds;
            if (t.cover.isEmpty()) t.cover = f.cover;
            if (t.album.isEmpty()) t.album = f.album;
        } catch (IOException ignored) { }
    }

    static Track parseHtml(String html) {
        Track t = new Track();
        t.title = meta(html, "og:title");
        t.cover = meta(html, "og:image");
        t.artists = meta(html, "music:musician_description");
        try { t.seconds = Long.parseLong(meta(html, "music:duration").trim()); } catch (NumberFormatException ignored) { }
        String desc = meta(html, "og:description"); // e.g. "Artist · Album · Song · 2021"
        String[] parts = desc.split(" · ");
        if (t.artists.isEmpty() && parts.length >= 2) t.artists = parts[0].trim();
        if (parts.length >= 4) t.album = parts[1].trim();
        return t;
    }

    private static String meta(String html, String name) {
        Matcher m = Pattern.compile("<meta[^>]+(?:property|name)=\"" + Pattern.quote(name) + "\"[^>]*content=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE).matcher(html);
        if (!m.find()) {
            m = Pattern.compile("<meta[^>]+content=\"([^\"]*)\"[^>]*(?:property|name)=\"" + Pattern.quote(name) + "\"", Pattern.CASE_INSENSITIVE).matcher(html);
            if (!m.find()) return "";
        }
        return unescape(m.group(1));
    }

    static String unescape(String s) {
        Matcher m = Pattern.compile("&(#x?[0-9a-fA-F]+|amp|lt|gt|quot|apos);").matcher(s);
        StringBuffer b = new StringBuffer();
        while (m.find()) {
            String e = m.group(1), r;
            switch (e) {
                case "amp": r = "&"; break;
                case "lt": r = "<"; break;
                case "gt": r = ">"; break;
                case "quot": r = "\""; break;
                case "apos": r = "'"; break;
                default:
                    int cp = e.startsWith("#x") || e.startsWith("#X") ? Integer.parseInt(e.substring(2), 16) : Integer.parseInt(e.substring(1));
                    r = new String(Character.toChars(cp));
            }
            m.appendReplacement(b, Matcher.quoteReplacement(r));
        }
        m.appendTail(b);
        return b.toString();
    }

    private static String fetch(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", YtDownloader.USER_AGENT);
            c.setRequestProperty("Accept-Language", "uk,en;q=0.8");
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            InputStream in = c.getInputStream();
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                for (int n; (n = in.read(buf)) > 0 && out.size() < 2_000_000; ) out.write(buf, 0, n);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            } finally { in.close(); }
        } finally {
            c.disconnect();
        }
    }
}
