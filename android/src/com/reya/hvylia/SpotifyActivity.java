package com.reya.hvylia;

import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * The «Spotify» tab: open.spotify.com in-app, «Скачати MP3» on track, album and playlist pages.
 * Spotify's own audio is DRM-protected and is never touched: the tab takes the track details from the page
 * (title, artists, album, length, cover) and the download service finds the same recording on YouTube.
 */
public class SpotifyActivity extends BrowserActivity {
    @Override protected String startUrl() { return "https://open.spotify.com/"; }
    @Override protected int barColor() { return 0xFF121212; }
    @Override protected boolean canDownload(String url) { return SpotifyMeta.kindOf(url) != null; }

    @Override
    protected void onDownloadTapped(String url) {
        final String kind = SpotifyMeta.kindOf(url);
        if (kind == null) return;
        web.evaluateJavascript(SpotifyMeta.PAGE_JS, value -> {
            SpotifyMeta.Page p = SpotifyMeta.fromPageJs(value);
            if (p.tracks.isEmpty()) {
                Toast.makeText(this, "Не бачу треків на сторінці. Дочекайся, поки вона завантажиться, і натисни ще раз.", Toast.LENGTH_LONG).show();
                return;
            }
            final DownloadSheet.Model m = new DownloadSheet.Model();
            m.note = "звук з YouTube";
            if ("track".equals(p.kind)) {
                final SpotifyMeta.Track t = p.tracks.get(0);
                m.cover = t.cover;
                m.title = t.title;
                m.subtitle = t.artists;
                m.seconds = t.seconds;
                m.loader = mm -> {
                    SpotifyMeta.complete(t);
                    mm.title = t.title;
                    mm.subtitle = t.artists;
                    mm.seconds = t.seconds;
                    if (!t.cover.isEmpty()) mm.cover = t.cover;
                };
                m.starter = (kbps, tree) -> DownloadService.startTrack(this, t, kbps, tree);
            } else {
                final List<SpotifyMeta.Track> list = new ArrayList<>(p.tracks);
                long total = 0;
                for (SpotifyMeta.Track t : list) total += t.seconds;
                m.cover = !p.cover.isEmpty() ? p.cover : list.get(0).cover;
                m.title = p.title;
                m.subtitle = !p.subtitle.isEmpty() ? p.subtitle : "album".equals(p.kind) ? "Альбом" : "Плейлист";
                m.seconds = total;
                m.count = list.size();
                m.starter = (kbps, tree) -> {
                    for (SpotifyMeta.Track t : list) DownloadService.startTrack(this, t, kbps, tree);
                };
            }
            openSheet(m);
        });
    }
}
