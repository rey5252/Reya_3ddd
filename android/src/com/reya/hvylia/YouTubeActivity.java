package com.reya.hvylia;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.List;
import java.util.regex.Pattern;

/** The «YouTube» tab: m.youtube.com in-app, «Скачати MP3» on /watch and /shorts pages. */
public class YouTubeActivity extends BrowserActivity {
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private final Handler main = new Handler(Looper.getMainLooper());
    private Runnable prefetch;

    @Override protected String startUrl() { return "https://m.youtube.com/"; }
    @Override protected int barColor() { return 0xFF0F0F0F; }
    @Override protected boolean canDownload(String url) { return videoIdOf(url) != null; }

    /** Starts looking the video up as soon as its page opens, so «Скачати» has the details ready. */
    @Override
    protected void onPageChanged(String url) {
        if (prefetch != null) main.removeCallbacks(prefetch);
        final String id = videoIdOf(url);
        if (id == null) return;
        prefetch = () -> StreamCache.prefetch(StreamCache.canonical(id));
        main.postDelayed(prefetch, 700);
    }

    @Override
    protected void onDownloadTapped(String url) {
        final String id = videoIdOf(url);
        if (id == null) return;
        // Title, channel and length straight from the page's player, so the sheet opens with them at once.
        String js = "(function(){var p=document.querySelector('#movie_player');var d=p&&p.getVideoData?p.getVideoData():{};"
                + "if(d.video_id!=='" + id + "')d={};var v=document.querySelector('video');"
                + "return JSON.stringify({title:d.title||document.title.replace(/ - YouTube$/,''),author:d.author||'',"
                + "seconds:v&&isFinite(v.duration)?Math.round(v.duration):0})})()";
        web.evaluateJavascript(js, value -> {
            final String canon = StreamCache.canonical(id);
            final DownloadSheet.Model m = new DownloadSheet.Model();
            m.cover = "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg";
            try {
                Object v = new JSONTokener(value).nextValue();
                JSONObject o = new JSONObject(v instanceof String ? (String) v : "{}");
                m.title = o.optString("title");
                m.subtitle = o.optString("author");
                m.seconds = o.optLong("seconds");
            } catch (Exception ignored) { }
            m.loader = mm -> {
                StreamInfo info = StreamCache.get(canon);
                if (info.getName() != null && !info.getName().isEmpty()) mm.title = info.getName();
                mm.subtitle = DownloadService.channel(info.getUploaderName());
                if (info.getDuration() > 0) mm.seconds = info.getDuration();
            };
            m.starter = (kbps, tree) -> DownloadService.startYouTube(this, canon, m.title, kbps, tree);
            openSheet(m);
        });
    }

    static String videoIdOf(String url) {
        try {
            Uri u = Uri.parse(url);
            String host = u.getHost();
            if (host == null) return null;
            host = host.toLowerCase(java.util.Locale.ROOT);
            String id = null;
            List<String> seg = u.getPathSegments();
            if (host.equals("youtu.be")) {
                if (!seg.isEmpty()) id = seg.get(0);
            } else if (host.equals("youtube.com") || host.endsWith(".youtube.com")) {
                if ("/watch".equals(u.getPath())) id = u.getQueryParameter("v");
                else if (seg.size() >= 2 && ("shorts".equals(seg.get(0)) || "live".equals(seg.get(0)))) id = seg.get(1);
            }
            return id != null && ID.matcher(id).matches() ? id : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
