package com.reya.hvylia;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Track details from open.spotify.com pages: the open page in the Spotify tab (read with JavaScript)
 * and, to fill gaps, the page's public link-preview tags. Only metadata — the audio itself comes from YouTube.
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

    static final class Page {
        String kind = ""; // track, album, playlist
        String id = "";
        String title = "";
        String subtitle = "";
        String cover = "";
        final List<Track> tracks = new ArrayList<>();
    }

    private static final Pattern PATH = Pattern.compile("^/(?:intl-[a-z-]+/)?(track|album|playlist)/([A-Za-z0-9]{10,})");

    /** Reads the open page: header, link-preview tags and (for albums and playlists) the visible track rows. */
    static final String PAGE_JS = "(function(){"
            + "function meta(n){var e=document.querySelector('meta[property=\"'+n+'\"],meta[name=\"'+n+'\"]');return e?e.content||'':''}"
            + "function txt(e){return e?(e.textContent||'').trim():''}"
            + "var m=location.pathname.match(/^\\/(?:intl-[a-z-]+\\/)?(track|album|playlist)\\/([A-Za-z0-9]+)/);if(!m)return JSON.stringify({});"
            + "var h1=txt(document.querySelector('main h1, h1'));"
            + "var cl=[].slice.call(document.querySelectorAll('[data-testid=\"creator-link\"]'));"
            + "if(!cl.length)cl=[].slice.call(document.querySelectorAll('main a[href*=\"/artist/\"]')).filter(function(a){return !a.closest('[data-testid=\"tracklist-row\"]')}).slice(0,1);"
            + "var creators=cl.slice(0,4).map(txt).filter(Boolean);"
            + "var out={kind:m[1],id:m[2],title:h1||meta('og:title'),ogTitle:meta('og:title'),desc:meta('og:description'),image:meta('og:image'),"
            + "duration:+meta('music:duration')||0,artist:meta('music:musician_description'),creators:creators,tracks:[]};"
            + "var hi=document.querySelector('[data-testid=\"entity-image\"] img, main img[srcset]');if(hi&&(!out.image||out.ogTitle!==out.title))out.image=hi.src||out.image;"
            + "if(m[1]==='track'){var dur=0;[].slice.call(document.querySelectorAll('main span, main div')).some(function(e){if(e.children.length)return false;"
            + "var q=txt(e).match(/^(\\d{1,2}):(\\d{2})$/);if(q){dur=+q[1]*60+ +q[2];return true}return false});if(!out.duration)out.duration=dur;}"
            + "else{[].slice.call(document.querySelectorAll('[data-testid=\"tracklist-row\"]')).forEach(function(r){"
            + "var t=r.querySelector('a[href*=\"/track/\"]');if(!t)return;var id=(t.getAttribute('href').match(/track\\/([A-Za-z0-9]+)/)||[])[1]||'';"
            + "var as=[].slice.call(r.querySelectorAll('a[href*=\"/artist/\"]')).map(txt).filter(Boolean);"
            + "var al=r.querySelector('a[href*=\"/album/\"]');var img=r.querySelector('img');var d=0;"
            + "[].slice.call(r.querySelectorAll('div,span')).some(function(e){if(e.children.length)return false;var q=txt(e).match(/^(\\d{1,2}):(\\d{2})$/);if(q){d=+q[1]*60+ +q[2];return true}return false});"
            + "out.tracks.push({id:id,title:txt(t),artists:as.join(', '),album:txt(al),image:img?img.src:'',duration:d})})}"
            + "return JSON.stringify(out)})()";

    private SpotifyMeta() { }

    /** "track", "album", "playlist" or null for other Spotify pages. */
    static String kindOf(String url) {
        Matcher m = matcher(url);
        return m != null ? m.group(1) : null;
    }

    static String idOf(String url) {
        Matcher m = matcher(url);
        return m != null ? m.group(2) : null;
    }

    private static Matcher matcher(String url) {
        try {
            URL u = new URL(url);
            if (!"open.spotify.com".equalsIgnoreCase(u.getHost())) return null;
            Matcher m = PATH.matcher(u.getPath());
            return m.find() ? m : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Spotify image URLs carry the size in the hash prefix; ask for the 640 px cover. */
    static String bigCover(String url) {
        if (url == null) return "";
        return url.replace("ab67616d00004851", "ab67616d0000b273").replace("ab67616d00001e02", "ab67616d0000b273");
    }

    /** Parses what PAGE_JS returned (evaluateJavascript hands back a JSON-encoded string). */
    static Page fromPageJs(String evaluated) {
        Page p = new Page();
        try {
            Object v = new org.json.JSONTokener(evaluated).nextValue();
            JSONObject o = new JSONObject(v instanceof String ? (String) v : String.valueOf(v));
            p.kind = o.optString("kind");
            p.id = o.optString("id");
            p.title = o.optString("title");
            p.cover = bigCover(o.optString("image"));
            JSONArray creators = o.optJSONArray("creators");
            List<String> names = new ArrayList<>();
            if (creators != null) for (int i = 0; i < creators.length(); i++) names.add(creators.optString(i));
            String artist = o.optString("artist");
            if (names.isEmpty() && !artist.isEmpty()) names.add(artist);
            p.subtitle = YtSearch.joinArtists(names);
            if ("track".equals(p.kind)) {
                Track t = new Track();
                t.id = p.id;
                t.title = p.title;
                t.artists = p.subtitle;
                t.cover = p.cover;
                t.seconds = o.optLong("duration");
                p.tracks.add(t);
            } else {
                JSONArray rows = o.optJSONArray("tracks");
                for (int i = 0; rows != null && i < rows.length(); i++) {
                    JSONObject r = rows.getJSONObject(i);
                    Track t = new Track();
                    t.id = r.optString("id");
                    t.title = r.optString("title");
                    t.artists = r.optString("artists");
                    t.album = "album".equals(p.kind) ? p.title : r.optString("album");
                    t.cover = "album".equals(p.kind) && !p.cover.isEmpty() ? p.cover : bigCover(r.optString("image"));
                    t.seconds = r.optLong("duration");
                    if (t.artists.isEmpty()) t.artists = p.subtitle;
                    if (!t.title.isEmpty() && !containsId(p.tracks, t.id)) p.tracks.add(t);
                }
            }
        } catch (Exception ignored) { }
        return p;
    }

    private static boolean containsId(List<Track> list, String id) {
        if (id.isEmpty()) return false;
        for (Track t : list) if (id.equals(t.id)) return true;
        return false;
    }

    /** Fills missing artist / length / cover / album of a track from its public page's link-preview tags. */
    static void complete(Track t) {
        if (t.id.isEmpty() || (!t.artists.isEmpty() && t.seconds > 0 && !t.cover.isEmpty())) return;
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
