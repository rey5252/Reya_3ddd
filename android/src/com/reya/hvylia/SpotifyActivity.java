package com.reya.hvylia;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The «Spotify» tab, without a Spotify account: the app's own screen (assets/www/spotify.html) for search,
 * albums, playlists and artists, fed by {@link SpotifyApi} (Spotify's public, sign-in-free catalogue access).
 * Spotify's own audio is DRM-protected and is never touched: tracks are found on YouTube by DownloadService.
 * Spotify links shared to Хвиля (or opened with it) open here directly.
 */
public class SpotifyActivity extends BrowserActivity {
    static final String PAGE = "https://" + Assets.HOST + "/spotify.html";
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private volatile String pendingLink;

    @Override
    protected void onCreate(Bundle state) {
        pendingLink = linkText(getIntent());
        super.onCreate(state);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String l = linkText(intent);
        if (l == null) return;
        pendingLink = l;
        web.evaluateJavascript("window.__sp&&__sp.checkLink()", null);
    }

    @Override
    protected void onDestroy() {
        pool.shutdownNow();
        super.onDestroy();
    }

    @Override protected String startUrl() { return PAGE; }
    @Override protected int barColor() { return 0xFF121212; }
    @Override protected boolean canDownload(String url) { return false; } // the screen has its own buttons
    @Override protected void onDownloadTapped(String url) { }
    @Override protected boolean keepsInside(Uri u) { return Assets.HOST.equals(u.getHost()); }
    @Override protected WebResourceResponse intercept(Uri u) { return Assets.serve(this, u); }
    @Override protected void setUpWeb(WebView w) { w.addJavascriptInterface(new Api(), "HvyliaSpotify"); }

    /** Text that may hold a Spotify link: a share («Поділитися» → Хвиля) or an opened open.spotify.com link. */
    static String linkText(Intent i) {
        if (i == null) return null;
        if (Intent.ACTION_SEND.equals(i.getAction())) {
            CharSequence t = i.getCharSequenceExtra(Intent.EXTRA_TEXT);
            return t == null ? null : t.toString();
        }
        if (Intent.ACTION_VIEW.equals(i.getAction())) return i.getDataString();
        return null;
    }

    private void reply(final int cb, final boolean ok, final String payload) {
        runOnUiThread(() -> {
            if (isDestroyed()) return;
            web.evaluateJavascript("window.__sp&&__sp." + (ok ? "done" : "fail") + "(" + cb + "," + JSONObject.quote(payload) + ")", null);
        });
    }

    private interface Call { String run() throws Exception; }

    private void async(final int cb, final Call call) {
        try {
            pool.execute(() -> {
                try {
                    reply(cb, true, call.run());
                } catch (Throwable e) {
                    reply(cb, false, SpotifyApi.explain(e));
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) { } // closing
    }

    /** Opens the download sheet for tracks the screen sent (one track, or a whole album / playlist / top list). */
    private void openDownload(String tracksJson, String metaJson) {
        final List<SpotifyMeta.Track> list = new ArrayList<>();
        final List<String> yt = new ArrayList<>();
        final JSONArray keys = new JSONArray();
        JSONObject meta;
        try {
            JSONArray a = new JSONArray(tracksJson);
            meta = new JSONObject(metaJson);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                SpotifyMeta.Track t = new SpotifyMeta.Track();
                t.id = SpotifyApi.str(o, "id");
                t.title = SpotifyApi.str(o, "title");
                t.artists = SpotifyApi.str(o, "artists");
                t.album = SpotifyApi.str(o, "album");
                t.cover = SpotifyApi.str(o, "cover");
                t.seconds = o.optLong("seconds");
                if (t.title.isEmpty()) continue;
                list.add(t);
                yt.add(SpotifyApi.str(o, "yt"));
                keys.put(t.id.isEmpty() ? SpotifyApi.str(o, "yt") : t.id);
            }
        } catch (Exception e) {
            return;
        }
        if (list.isEmpty()) return;
        final DownloadSheet.Model m = new DownloadSheet.Model();
        m.note = "звук з YouTube";
        if (list.size() == 1) {
            final SpotifyMeta.Track t = list.get(0);
            final String url = yt.get(0);
            m.cover = !t.cover.isEmpty() ? t.cover : SpotifyApi.str(meta, "cover");
            m.title = t.title;
            m.subtitle = t.artists;
            m.seconds = t.seconds;
            if (url.isEmpty()) {
                m.loader = mm -> {
                    SpotifyMeta.complete(t);
                    mm.title = t.title;
                    mm.subtitle = t.artists;
                    mm.seconds = t.seconds;
                    if (!t.cover.isEmpty()) mm.cover = t.cover;
                };
            }
        } else {
            long total = 0;
            for (SpotifyMeta.Track t : list) total += t.seconds;
            m.cover = SpotifyApi.str(meta, "cover");
            if (m.cover.isEmpty()) m.cover = list.get(0).cover;
            m.title = SpotifyApi.str(meta, "title");
            m.subtitle = SpotifyApi.str(meta, "subtitle");
            m.seconds = total;
            m.count = list.size();
        }
        m.starter = (kbps, tree) -> {
            for (int i = 0; i < list.size(); i++) {
                if (yt.get(i).isEmpty()) DownloadService.startTrack(this, list.get(i), kbps, tree);
                else DownloadService.startYouTube(this, yt.get(i), list.get(i).title, kbps, tree);
            }
            web.evaluateJavascript("window.__sp&&__sp.queued(" + keys + ")", null);
        };
        openSheet(m);
    }

    /** Called from the screen as window.HvyliaSpotify.* (on a background thread). Answers arrive via __sp.done / __sp.fail. */
    private final class Api {
        @JavascriptInterface
        public void search(int cb, String q) {
            async(cb, () -> SpotifyApi.search(q).toString());
        }

        @JavascriptInterface
        public void page(int cb, String kind, String id) {
            async(cb, () -> SpotifyApi.page(kind, id).toString());
        }

        /** {kind, id} for a Spotify link (also short spotify.link ones) in the text, or null. */
        @JavascriptInterface
        public void resolve(int cb, String text) {
            async(cb, () -> {
                SpotifyApi.Link l = SpotifyApi.resolveLink(text);
                return l == null ? "null" : new JSONObject().put("kind", l.kind).put("id", l.id).toString();
            });
        }

        /** A link shared to the app since the last call, or "". */
        @JavascriptInterface
        public String takeLink() {
            String l = pendingLink;
            pendingLink = null;
            return l == null ? "" : l;
        }

        @JavascriptInterface
        public String clipboard() {
            final String[] out = { "" };
            final CountDownLatch done = new CountDownLatch(1);
            runOnUiThread(() -> {
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    ClipData clip = cm == null ? null : cm.getPrimaryClip();
                    if (clip != null && clip.getItemCount() > 0) {
                        CharSequence t = clip.getItemAt(0).coerceToText(SpotifyActivity.this);
                        if (t != null) out[0] = t.toString();
                    }
                } catch (RuntimeException ignored) {
                } finally {
                    done.countDown();
                }
            });
            try { done.await(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
            return out[0];
        }

        @JavascriptInterface
        public void download(final String tracksJson, final String metaJson) {
            runOnUiThread(() -> { if (!isDestroyed()) openDownload(tracksJson, metaJson); });
        }
    }
}
