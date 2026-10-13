package com.reya.hvylia;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Spotify catalogue without an account. Spotify's public embed player (open.spotify.com/embed/…) works for
 * everyone and carries a short-lived anonymous token; with that token the web player's own catalogue queries answer
 * search, albums, playlists and artists. If Spotify changes those queries, albums, playlists and artists still come
 * from the embed pages and search falls back to YouTube Music. Only metadata: the audio is always found on YouTube.
 * (Method as in the MIT-licensed SpotifyScraper project.)
 *
 * Everything is returned as the JSON the Spotify screen (assets/www/spotify) renders:
 * track {id, title, artists, album, cover, thumb, seconds, explicit, yt?},
 * card {kind, id, title, subtitle, cover}, page {kind, id, title, subtitle, cover, tracks, releases, total}.
 */
final class SpotifyApi {
    static final class Link {
        final String kind, id;
        Link(String kind, String id) { this.kind = kind; this.id = id; }
    }

    /** The page is private, removed or not shown without an account. */
    static final class UnavailableException extends IOException {
        UnavailableException() { super("unavailable"); }
    }

    /** Spotify retired one of its catalogue queries (it rotates them now and then). */
    static final class QueryGoneException extends IOException {
        QueryGoneException(String op) { super("query " + op); }
    }

    static final int MAX_TRACKS = 500;
    private static final String BOOT_TRACK = "4uLU6hMCjMI75M1A2tKUQC"; // any public track: its embed page carries the token
    private static final String PATHFINDER = "https://api-partner.spotify.com/pathfinder/v1/query";
    private static final Pattern NEXT_DATA = Pattern.compile("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", Pattern.DOTALL);
    private static final Pattern LINK = Pattern.compile(
            "(?:https?://)?(?:open|play)\\.spotify\\.com/(?:embed/)?(?:intl-[A-Za-z-]+/)?(track|album|playlist|artist)/([A-Za-z0-9]{22})"
            + "|spotify:(track|album|playlist|artist):([A-Za-z0-9]{22})");
    private static final Pattern SHORT = Pattern.compile("https?://(?:spotify\\.link|spoti\\.fi)/[A-Za-z0-9_-]+");

    /** Persisted-query hashes of the web player (as of mid-2026); refreshed from its script if Spotify rotates them. */
    private static final Map<String, String> HASH = new HashMap<>();
    static {
        HASH.put("searchDesktop", "eff59fa0a3d026b88b56fddbcf4bdfa16a186b8175a5c1a358c072e053c2e5b0");
        HASH.put("getAlbum", "b9bfabef66ed756e5e13f68a942deb60bd4125ec1f1be8cc42769dc0259b4b10");
        HASH.put("fetchPlaylist", "a65e12194ed5fc443a1cdebed5fabe33ca5b07b987185d63c72483867ad13cb4");
        HASH.put("queryArtistOverview", "ae0e2958a4ab645b35ca19ac04d0495ae12d9c5d7b7286217674801a9aab281a");
        HASH.put("getTrack", "612585ae06ba435ad26369870deaae23b5c8800a256cd8a57e08eddc25a37294");
    }
    private static boolean rediscovered;
    private static String token = "";
    private static long tokenUntil;

    private SpotifyApi() { }

    // ---------------------------------------------------------------- links

    /** A Spotify link or URI anywhere in the text (shared messages often add words around it); null if none. */
    static Link parseLink(String text) {
        if (text == null) return null;
        Matcher m = LINK.matcher(text);
        if (!m.find()) return null;
        return m.group(1) != null ? new Link(m.group(1), m.group(2)) : new Link(m.group(3), m.group(4));
    }

    /** Like parseLink, but also follows short spotify.link / spoti.fi links (network). */
    static Link resolveLink(String text) throws IOException {
        Link l = parseLink(text);
        if (l != null || text == null) return l;
        Matcher m = SHORT.matcher(text);
        if (!m.find()) return null;
        HttpURLConnection c = open(m.group(), "text/html");
        try {
            int code = c.getResponseCode();
            l = parseLink(c.getURL().toString()); // where the redirects ended
            if (l == null && code == 200) l = parseLink(read(c.getInputStream(), 2_000_000));
            return l;
        } finally {
            c.disconnect();
        }
    }

    // ---------------------------------------------------------------- public calls

    /** Tracks, artists, albums and playlists for a query; YouTube Music songs if Spotify search is out of reach. */
    static JSONObject search(String q) throws Exception {
        try {
            JSONObject vars = new JSONObject().put("searchTerm", q).put("offset", 0).put("limit", 20)
                    .put("numberOfTopResults", 5).put("includeAudiobooks", true).put("includePreReleases", true)
                    .put("includeAlbumPreReleases", false).put("includeAuthors", false).put("includeEpisodeContentRatingsV2", false);
            JSONObject out = parseSearch(query("searchDesktop", vars).optJSONObject("searchV2"));
            return out.put("source", "spotify");
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            try {
                return youTubeSearch(q).put("source", "youtube");
            } catch (Exception ignored) {
                throw e;
            }
        }
    }

    /** A whole album, playlist (up to MAX_TRACKS), artist (top tracks and releases) or a single track. */
    static JSONObject page(String kind, String id) throws Exception {
        try {
            switch (kind) {
                case "album": return album(id);
                case "playlist": return playlist(id);
                case "artist": return parseArtist(query("queryArtistOverview",
                        new JSONObject().put("uri", "spotify:artist:" + id).put("locale", "").put("includePrerelease", false))
                        .optJSONObject("artistUnion"));
                case "track": return parseTrackPage(query("getTrack", new JSONObject().put("uri", "spotify:track:" + id))
                        .optJSONObject("trackUnion"));
                default: throw new UnavailableException();
            }
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            try {
                return parseEmbed(embed(kind, id), kind); // the embed player shows these without any query
            } catch (UnavailableException u) {
                throw u;
            } catch (Exception u) {
                throw e instanceof UnavailableException ? e : u;
            }
        }
    }

    private static JSONObject album(String id) throws Exception {
        JSONObject first = null;
        JSONArray tracks = new JSONArray();
        for (int offset = 0; offset < MAX_TRACKS; offset += 50) {
            JSONObject u = query("getAlbum", new JSONObject().put("uri", "spotify:album:" + id).put("locale", "")
                    .put("offset", offset).put("limit", 50)).optJSONObject("albumUnion");
            JSONObject p = parseAlbum(u);
            if (first == null) first = p;
            JSONArray got = p.getJSONArray("tracks");
            for (int i = 0; i < got.length(); i++) tracks.put(got.get(i));
            if (got.length() == 0 || tracks.length() >= p.optInt("total")) break;
        }
        return first.put("tracks", tracks);
    }

    private static JSONObject playlist(String id) throws Exception {
        JSONObject first = null;
        JSONArray tracks = new JSONArray();
        for (int offset = 0; offset < MAX_TRACKS; offset += 100) {
            JSONObject u = query("fetchPlaylist", new JSONObject().put("uri", "spotify:playlist:" + id)
                    .put("offset", offset).put("limit", 100).put("enableWatchFeedEntrypoint", false)).optJSONObject("playlistV2");
            JSONObject p = parsePlaylist(u);
            if (first == null) first = p;
            JSONArray got = p.getJSONArray("tracks");
            for (int i = 0; i < got.length() && tracks.length() < MAX_TRACKS; i++) tracks.put(got.get(i));
            if (p.optInt("items") == 0 || offset + 100 >= p.optInt("total")) break;
        }
        return first.put("tracks", tracks);
    }

    // ---------------------------------------------------------------- network

    private static JSONObject query(String op, JSONObject vars) throws Exception {
        boolean retried = false, refreshed = false;
        while (true) {
            String hash;
            synchronized (SpotifyApi.class) { hash = HASH.get(op); }
            String url = PATHFINDER + "?operationName=" + enc(op) + "&variables=" + enc(vars.toString())
                    + "&extensions=" + enc("{\"persistedQuery\":{\"version\":1,\"sha256Hash\":\"" + hash + "\"}}");
            HttpURLConnection c = open(url, "application/json");
            c.setRequestProperty("Authorization", "Bearer " + token());
            c.setRequestProperty("app-platform", "WebPlayer");
            int code;
            String body;
            try {
                code = c.getResponseCode();
                InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                body = in == null ? "" : read(in, 8_000_000);
            } finally {
                c.disconnect();
            }
            if (code == 401 && !retried) { dropToken(); retried = true; continue; }
            if (code == 404) throw new UnavailableException();
            JSONObject o;
            try { o = new JSONObject(body); } catch (JSONException e) { throw new IOException("HTTP " + code); }
            JSONArray errors = o.optJSONArray("errors");
            if (errors != null && errors.toString().contains("PersistedQueryNotFound")) {
                if (!refreshed && rediscover()) { refreshed = true; continue; }
                throw new QueryGoneException(op);
            }
            JSONObject data = o.optJSONObject("data");
            if (data == null) throw new IOException("HTTP " + code);
            boolean any = false;
            for (java.util.Iterator<String> it = data.keys(); it.hasNext(); ) {
                Object v = data.opt(it.next());
                if (v instanceof JSONObject && !"NotFound".equals(((JSONObject) v).optString("__typename"))) any = true;
            }
            if (!any) throw new UnavailableException();
            return data;
        }
    }

    /** The embed page's data (props.pageProps.state). Also refreshes the anonymous token for free. */
    static JSONObject embed(String kind, String id) throws IOException {
        HttpURLConnection c = open("https://open.spotify.com/embed/" + kind + "/" + id, "text/html");
        String html;
        try {
            int code = c.getResponseCode();
            if (code == 404) throw new UnavailableException();
            if (code != 200) throw new IOException("HTTP " + code);
            html = read(c.getInputStream(), 4_000_000);
        } finally {
            c.disconnect();
        }
        JSONObject state = embedState(html);
        rememberToken(state);
        return state;
    }

    static JSONObject embedState(String html) throws IOException {
        Matcher m = NEXT_DATA.matcher(html);
        if (!m.find()) throw new IOException("no embed data");
        try {
            JSONObject props = new JSONObject(m.group(1)).getJSONObject("props").getJSONObject("pageProps");
            if (props.has("status") || props.has("forbiddenReason")) throw new UnavailableException();
            return props.getJSONObject("state");
        } catch (JSONException e) {
            throw new IOException("embed data", e);
        }
    }

    private static synchronized void rememberToken(JSONObject state) {
        JSONObject s = state.optJSONObject("settings");
        s = s == null ? null : s.optJSONObject("session");
        if (s == null || s.optString("accessToken").isEmpty()) return;
        token = s.optString("accessToken");
        tokenUntil = s.optLong("accessTokenExpirationTimestampMs", System.currentTimeMillis() + 10 * 60_000L);
    }

    private static synchronized void dropToken() { token = ""; tokenUntil = 0; }

    private static String token() throws IOException {
        synchronized (SpotifyApi.class) {
            if (!token.isEmpty() && System.currentTimeMillis() < tokenUntil - 60_000L) return token;
        }
        embed("track", BOOT_TRACK);
        synchronized (SpotifyApi.class) {
            if (token.isEmpty()) throw new IOException("no token");
            return token;
        }
    }

    /**
     * Looks the current query hashes up in the web player's script (the way other open-source clients do):
     * the main bundle and the search route chunk list them as "name","query","sha256". At most once per run.
     */
    private static boolean rediscover() {
        synchronized (SpotifyApi.class) {
            if (rediscovered) return false;
            rediscovered = true;
        }
        try {
            String html = get("https://open.spotify.com/", 3_000_000);
            Matcher m = Pattern.compile("https://open\\.spotifycdn\\.com/cdn/build/web-player/web-player\\.[0-9a-f]+\\.js").matcher(html);
            if (!m.find()) return false;
            StringBuilder js = new StringBuilder(get(m.group(), 16_000_000));
            Matcher chunk = Pattern.compile("\"xpui-routes-search\"\\s*:\\s*\"([0-9a-f]{6,})\"").matcher(js);
            if (chunk.find()) {
                try {
                    js.append(get("https://open.spotifycdn.com/cdn/build/web-player/xpui-routes-search." + chunk.group(1) + ".js", 8_000_000));
                } catch (IOException ignored) { }
            }
            Matcher q = Pattern.compile("\"(\\w+)\"\\s*,\\s*\"query\"\\s*,\\s*\"([0-9a-f]{64})\"").matcher(js);
            boolean changed = false;
            synchronized (SpotifyApi.class) {
                while (q.find()) {
                    if (HASH.containsKey(q.group(1)) && !q.group(2).equals(HASH.get(q.group(1)))) {
                        HASH.put(q.group(1), q.group(2));
                        changed = true;
                    }
                }
            }
            return changed;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static HttpURLConnection open(String url, String accept) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", YtDownloader.USER_AGENT);
        c.setRequestProperty("Accept", accept);
        c.setRequestProperty("Accept-Language", "uk,en;q=0.8");
        return c;
    }

    private static String get(String url, int limit) throws IOException {
        HttpURLConnection c = open(url, "*/*");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            return read(c.getInputStream(), limit);
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream in, int limit) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[32768];
            for (int n; (n = in.read(buf)) > 0 && out.size() < limit; ) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (java.io.UnsupportedEncodingException e) { throw new AssertionError(e); }
    }

    // ---------------------------------------------------------------- YouTube Music fallback

    private static JSONObject youTubeSearch(String q) throws Exception {
        YtDownloader.ensureInit();
        SearchInfo info = SearchInfo.getInfo(ServiceList.YouTube, ServiceList.YouTube.getSearchQHFactory()
                .fromQuery(q, Collections.singletonList(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS), ""));
        JSONArray tracks = new JSONArray();
        for (InfoItem item : info.getRelatedItems()) {
            if (!(item instanceof StreamInfoItem) || tracks.length() >= 20) continue;
            StreamInfoItem s = (StreamInfoItem) item;
            String thumb = "";
            int best = -1;
            for (Image im : s.getThumbnails()) {
                int w = im.getWidth() > 0 ? im.getWidth() : 300;
                if (w > best && w <= 800) { best = w; thumb = im.getUrl(); }
            }
            tracks.put(track("", s.getName(), DownloadService.channel(s.getUploaderName()), "", thumb, thumb,
                    Math.max(0, s.getDuration()), false).put("yt", s.getUrl()));
        }
        if (tracks.length() == 0) throw new IOException("nothing found");
        return new JSONObject().put("tracks", tracks).put("albums", new JSONArray())
                .put("playlists", new JSONArray()).put("artists", new JSONArray());
    }

    // ---------------------------------------------------------------- parsing (pure, tested on recorded answers)

    static JSONObject parseSearch(JSONObject s) throws JSONException {
        if (s == null) throw new JSONException("searchV2");
        JSONArray tracks = new JSONArray(), albums = new JSONArray(), playlists = new JSONArray(), artists = new JSONArray();
        for (JSONObject item : items(s.optJSONObject("tracksV2"))) {
            JSONObject d = obj(item, "item", "data");
            if (d != null && !str(d, "uri").isEmpty()) tracks.put(gqlTrack(d, null, null));
        }
        for (JSONObject item : items(s.optJSONObject("albumsV2"))) {
            JSONObject d = item.optJSONObject("data");
            if (d == null || str(d, "uri").isEmpty() || str(d, "name").isEmpty()) continue;
            String year = year(d.optJSONObject("date"));
            String by = names(d.optJSONObject("artists"));
            albums.put(card("album", d, by + (year.isEmpty() ? "" : (by.isEmpty() ? "" : " · ") + year), sources(d.optJSONObject("coverArt"))));
        }
        for (JSONObject item : items(s.optJSONObject("playlists"))) {
            JSONObject d = item.optJSONObject("data");
            if (d == null || str(d, "uri").isEmpty() || str(d, "name").isEmpty()) continue;
            JSONObject owner = obj(d, "ownerV2", "data");
            playlists.put(card("playlist", d, owner == null ? "" : str(owner, "name"), playlistImages(d)));
        }
        for (JSONObject item : items(s.optJSONObject("artists"))) {
            JSONObject d = item.optJSONObject("data");
            JSONObject profile = d == null ? null : d.optJSONObject("profile");
            if (profile == null || str(d, "uri").isEmpty() || str(profile, "name").isEmpty()) continue;
            JSONObject c = new JSONObject().put("kind", "artist").put("id", idOf(str(d, "uri"))).put("title", str(profile, "name"))
                    .put("subtitle", "Виконавець");
            JSONArray av = sources(obj(d, "visuals", "avatarImage"));
            c.put("cover", pick(av, 300));
            artists.put(c);
        }
        return new JSONObject().put("tracks", tracks).put("albums", albums).put("playlists", playlists).put("artists", artists);
    }

    static JSONObject parseAlbum(JSONObject u) throws JSONException {
        if (u == null) throw new JSONException("albumUnion");
        String name = str(u, "name");
        JSONArray src = sources(u.optJSONObject("coverArt"));
        String cover = big(src), thumb = pick(src, 300);
        JSONObject tv = u.optJSONObject("tracksV2");
        JSONArray tracks = new JSONArray();
        for (JSONObject item : items(tv)) {
            JSONObject t = item.optJSONObject("track");
            if (t == null || str(t, "uri").isEmpty()) continue;
            tracks.put(gqlTrack(t, name, new String[] { cover, thumb }));
        }
        String type = str(u, "type").toUpperCase(Locale.ROOT);
        String kindName = type.equals("SINGLE") ? "Сингл" : type.equals("EP") ? "EP" : type.equals("COMPILATION") ? "Збірка" : "Альбом";
        String year = year(u.optJSONObject("date"));
        return new JSONObject().put("kind", "album").put("id", idOf(str(u, "uri"))).put("title", name)
                .put("subtitle", names(u.optJSONObject("artists"))).put("label", kindName + (year.isEmpty() ? "" : " · " + year))
                .put("cover", cover).put("tracks", tracks).put("total", tv == null ? tracks.length() : tv.optInt("totalCount", tracks.length()));
    }

    static JSONObject parsePlaylist(JSONObject u) throws JSONException {
        if (u == null) throw new JSONException("playlistV2");
        JSONObject content = u.optJSONObject("content");
        JSONArray tracks = new JSONArray();
        int count = 0;
        for (JSONObject item : items(content)) {
            count++;
            JSONObject d = obj(item, "itemV2", "data");
            if (d == null || !"Track".equals(str(d, "__typename")) || str(d, "uri").isEmpty()) continue;
            tracks.put(gqlTrack(d, null, null));
        }
        JSONObject owner = obj(u, "ownerV2", "data");
        return new JSONObject().put("kind", "playlist").put("id", idOf(str(u, "uri"))).put("title", str(u, "name"))
                .put("subtitle", owner == null ? "" : str(owner, "name")).put("label", "Плейлист")
                .put("description", plain(str(u, "description"))).put("cover", big(playlistImages(u)))
                .put("tracks", tracks).put("items", count)
                .put("total", content == null ? tracks.length() : content.optInt("totalCount", tracks.length()));
    }

    static JSONObject parseArtist(JSONObject u) throws JSONException {
        if (u == null) throw new JSONException("artistUnion");
        JSONObject profile = u.optJSONObject("profile");
        String name = profile == null ? "" : str(profile, "name");
        JSONObject disco = u.optJSONObject("discography");
        JSONArray tracks = new JSONArray(), releases = new JSONArray();
        for (JSONObject item : items(disco == null ? null : disco.optJSONObject("topTracks"))) {
            JSONObject t = item.optJSONObject("track");
            if (t != null && !str(t, "uri").isEmpty()) tracks.put(gqlTrack(t, null, null));
        }
        for (JSONObject r : items(disco == null ? null : disco.optJSONObject("popularReleasesAlbums"))) {
            if (str(r, "uri").isEmpty() && str(r, "id").isEmpty()) continue;
            String type = str(r, "type").toUpperCase(Locale.ROOT);
            String year = year(r.optJSONObject("date"));
            String sub = (type.equals("SINGLE") ? "Сингл" : type.equals("EP") ? "EP" : type.equals("COMPILATION") ? "Збірка" : "Альбом")
                    + (year.isEmpty() ? "" : " · " + year);
            JSONObject c = card("album", r, sub, sources(r.optJSONObject("coverArt")));
            if (c.optString("id").isEmpty()) c.put("id", str(r, "id"));
            releases.put(c);
        }
        return new JSONObject().put("kind", "artist").put("id", idOf(str(u, "uri"))).put("title", name)
                .put("subtitle", "Виконавець").put("label", "Популярні треки")
                .put("cover", big(sources(obj(u, "visuals", "avatarImage"))))
                .put("tracks", tracks).put("releases", releases).put("total", tracks.length());
    }

    static JSONObject parseTrackPage(JSONObject u) throws JSONException {
        if (u == null) throw new JSONException("trackUnion");
        JSONObject album = u.optJSONObject("albumOfTrack");
        JSONObject t = gqlTrack(u, null, null);
        return new JSONObject().put("kind", "track").put("id", t.getString("id")).put("title", t.getString("title"))
                .put("subtitle", t.getString("artists")).put("label", album == null ? "Трек" : "Трек · " + str(album, "name"))
                .put("cover", t.getString("cover")).put("tracks", new JSONArray().put(t)).put("total", 1);
    }

    /** Embed data (props.pageProps.state) → page. Track rows carry no covers or albums there; downloads fill those in. */
    static JSONObject parseEmbed(JSONObject state, String kind) throws JSONException {
        JSONObject e = obj(state, "data", "entity");
        if (e == null) throw new JSONException("entity");
        String title = !str(e, "title").isEmpty() ? str(e, "title") : str(e, "name");
        JSONArray img = new JSONArray();
        JSONObject vi = e.optJSONObject("visualIdentity");
        if (vi != null && vi.optJSONArray("image") != null) img = vi.optJSONArray("image");
        if (img.length() == 0) img = sources(e.optJSONObject("coverArt"));
        String cover = big(img), thumb = pick(img, 300);
        JSONArray tracks = new JSONArray();
        String sub = str(e, "subtitle");
        if ("track".equals(kind)) {
            JSONArray as = e.optJSONArray("artists");
            StringBuilder b = new StringBuilder();
            for (int i = 0; as != null && i < as.length(); i++) {
                String n = str(as.optJSONObject(i), "name");
                if (!n.isEmpty()) b.append(b.length() > 0 ? ", " : "").append(n);
            }
            sub = b.toString();
            tracks.put(track(idOf(str(e, "uri")), title, sub, "", cover, thumb, ms(e.optLong("duration")), e.optBoolean("isExplicit")));
        } else {
            JSONArray list = e.optJSONArray("trackList");
            for (int i = 0; list != null && i < list.length(); i++) {
                JSONObject r = list.optJSONObject(i);
                if (r == null || str(r, "uri").isEmpty() || (r.has("entityType") && !"track".equals(str(r, "entityType")))) continue;
                boolean own = "album".equals(kind);
                tracks.put(track(idOf(str(r, "uri")), str(r, "title"), str(r, "subtitle"), own ? title : "",
                        own ? cover : "", own ? thumb : "", ms(r.optLong("duration")), r.optBoolean("isExplicit")));
            }
        }
        String label = "album".equals(kind) ? "Альбом" : "playlist".equals(kind) ? "Плейлист" : "artist".equals(kind) ? "Популярні треки" : "Трек";
        if ("artist".equals(kind)) sub = "Виконавець";
        return new JSONObject().put("kind", kind).put("id", idOf(str(e, "uri"))).put("title", title).put("subtitle", sub)
                .put("label", label).put("cover", cover).put("tracks", tracks).put("releases", new JSONArray())
                .put("total", tracks.length()).put("limited", !"track".equals(kind) && !"album".equals(kind));
    }

    // ---------------------------------------------------------------- helpers

    private static JSONObject gqlTrack(JSONObject d, String albumName, String[] albumCover) throws JSONException {
        JSONObject album = d.optJSONObject("albumOfTrack");
        JSONArray src = sources(album == null ? null : album.optJSONObject("coverArt"));
        String cover = albumCover != null ? albumCover[0] : big(src);
        String thumb = albumCover != null ? albumCover[1] : pick(src, 300);
        String an = albumName != null ? albumName : album == null ? "" : str(album, "name");
        JSONObject dur = d.optJSONObject("duration");
        if (dur == null) dur = d.optJSONObject("trackDuration");
        String artists = names(d.optJSONObject("artists"));
        if (artists.isEmpty()) {
            String a = names(d.optJSONObject("firstArtist")), o = names(d.optJSONObject("otherArtists"));
            artists = a + (!a.isEmpty() && !o.isEmpty() ? ", " : "") + o;
        }
        JSONObject rating = d.optJSONObject("contentRating");
        return track(idOf(str(d, "uri")), str(d, "name"), artists, an, cover, thumb,
                ms(dur == null ? 0 : dur.optLong("totalMilliseconds")), rating != null && "EXPLICIT".equals(str(rating, "label")));
    }

    private static JSONObject track(String id, String title, String artists, String album, String cover, String thumb,
                                    long seconds, boolean explicit) throws JSONException {
        return new JSONObject().put("id", id).put("title", title).put("artists", artists).put("album", album)
                .put("cover", cover).put("thumb", thumb.isEmpty() ? cover : thumb).put("seconds", seconds).put("explicit", explicit);
    }

    private static JSONObject card(String kind, JSONObject d, String subtitle, JSONArray images) throws JSONException {
        return new JSONObject().put("kind", kind).put("id", idOf(str(d, "uri"))).put("title", str(d, "name"))
                .put("subtitle", subtitle).put("cover", pick(images, 300));
    }

    private static long ms(long millis) { return (millis + 500) / 1000; }

    static String idOf(String uri) {
        int i = uri.lastIndexOf(':');
        return i >= 0 ? uri.substring(i + 1) : uri;
    }

    /** Artist names of an {items:[{profile:{name}}]} node, joined. */
    private static String names(JSONObject node) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (JSONObject a : items(node)) {
            JSONObject p = a.optJSONObject("profile");
            String n = p == null ? "" : str(p, "name");
            if (!n.isEmpty() && !out.contains(n)) out.add(n);
        }
        return String.join(", ", out);
    }

    private static String year(JSONObject date) {
        if (date == null) return "";
        if (date.optInt("year") > 0) return String.valueOf(date.optInt("year"));
        String iso = str(date, "isoString");
        return iso.length() >= 4 ? iso.substring(0, 4) : "";
    }

    private static JSONArray sources(JSONObject node) {
        JSONArray a = node == null ? null : node.optJSONArray("sources");
        return a == null ? new JSONArray() : a;
    }

    private static JSONArray playlistImages(JSONObject d) {
        java.util.List<JSONObject> list = items(d.optJSONObject("images"));
        return list.isEmpty() ? new JSONArray() : sources(list.get(0));
    }

    private static int width(JSONObject s) {
        int w = s.optInt("width", 0);
        return w > 0 ? w : s.optInt("maxWidth", 0);
    }

    /** The smallest image at least `want` px wide (else the largest); images without a size count as fine. */
    static String pick(JSONArray src, int want) {
        String best = "";
        int bestW = -1;
        boolean bestFits = false;
        for (int i = 0; i < src.length(); i++) {
            JSONObject s = src.optJSONObject(i);
            if (s == null || str(s, "url").isEmpty()) continue;
            int w = width(s);
            if (w == 0) { if (best.isEmpty()) best = str(s, "url"); continue; }
            boolean fits = w >= want;
            if (bestW < 0 || (fits && (!bestFits || w < bestW)) || (!fits && !bestFits && w > bestW)) {
                best = str(s, "url"); bestW = w; bestFits = fits;
            }
        }
        return best;
    }

    /** The 640 px cover (Spotify keeps the size in the image id, so a smaller one can be asked for in full size). */
    static String big(JSONArray src) {
        return SpotifyMeta.bigCover(pick(src, 640));
    }

    private static java.util.List<JSONObject> items(JSONObject node) {
        java.util.List<JSONObject> out = new java.util.ArrayList<>();
        JSONArray a = node == null ? null : node.optJSONArray("items");
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) out.add(o);
        }
        return out;
    }

    private static JSONObject obj(JSONObject o, String... path) {
        for (String k : path) {
            if (o == null) return null;
            o = o.optJSONObject(k);
        }
        return o;
    }

    /** optString that keeps JSON null as "" (Android's returns "null"). */
    static String str(JSONObject o, String key) {
        if (o == null || o.isNull(key)) return "";
        Object v = o.opt(key);
        return v instanceof String ? (String) v : v == null ? "" : String.valueOf(v);
    }

    private static String plain(String html) {
        return SpotifyMeta.unescape(html.replaceAll("<[^>]+>", "")).trim();
    }

    /** A short Ukrainian explanation for the screen. */
    static String explain(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof UnavailableException) return "Spotify не показує цю сторінку: її видалили, зробили приватною або вона недоступна в твоїй країні.";
            if (t instanceof UnknownHostException || t instanceof ConnectException || t instanceof SocketException
                    || t instanceof java.net.SocketTimeoutException || t instanceof javax.net.ssl.SSLException) {
                return "Немає з'єднання з інтернетом. Перевір мережу й спробуй ще раз.";
            }
            if (t instanceof FileNotFoundException) return "Spotify не знайшов цю сторінку.";
        }
        return "Spotify зараз не відповідає. Спробуй ще раз трохи пізніше.";
    }
}
