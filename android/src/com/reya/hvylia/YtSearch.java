package com.reya.hvylia;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Finds the same recording on YouTube for a track known by title, artists and length (like spotDL does). */
final class YtSearch {
    static final class NotFoundException extends IOException {
        NotFoundException() { super("not found"); }
    }

    private static final String[] VERSION_WORDS = { "live", "cover", "remix", "karaoke", "instrumental", "acoustic",
            "slowed", "sped", "nightcore", "8d", "reverb", "lyrics", "concert", "концерт", "кавер", "мінус", "ремікс", "наживо" };

    private YtSearch() { }

    /** URL of the best match. YouTube Music songs first (clean studio audio), then regular videos. */
    static String find(String title, String artist, long seconds) throws Exception {
        YtDownloader.ensureInit();
        String query = ((artist == null ? "" : artist) + " " + title).trim();
        String bestUrl = null;
        double best = Double.NEGATIVE_INFINITY;
        Exception last = null;
        for (String filter : new String[] { YoutubeSearchQueryHandlerFactory.MUSIC_SONGS, YoutubeSearchQueryHandlerFactory.VIDEOS }) {
            try {
                SearchInfo info = SearchInfo.getInfo(ServiceList.YouTube,
                        ServiceList.YouTube.getSearchQHFactory().fromQuery(query, Collections.singletonList(filter), ""));
                boolean music = filter.equals(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS);
                int n = 0;
                for (InfoItem item : info.getRelatedItems()) {
                    if (!(item instanceof StreamInfoItem) || n++ >= 10) continue;
                    StreamInfoItem s = (StreamInfoItem) item;
                    double sc = score(s.getName(), s.getUploaderName(), s.getDuration(), title, artist, seconds, music);
                    if (sc > best) { best = sc; bestUrl = s.getUrl(); }
                }
            } catch (Exception e) {
                last = e;
            }
            if (bestUrl != null && best >= 5) break; // a confident YouTube Music match: no need for the second search
        }
        if (bestUrl == null || best < 1) {
            if (last != null && bestUrl == null) throw last;
            throw new NotFoundException();
        }
        return bestUrl;
    }

    /** Higher is better: title and artist words present, length within a few seconds, no "live"/"cover"/… surprises. */
    static double score(String name, String uploader, long duration, String title, String artist, long target, boolean music) {
        String hay = norm(name) + " " + norm(uploader);
        Set<String> nameWords = words(name + " " + uploader);
        double s = 0;
        s += 4 * overlap(words(title), nameWords);
        if (artist != null && !artist.isEmpty()) s += 2 * overlap(words(artist), nameWords);
        if (target > 0 && duration > 0) {
            long d = Math.abs(duration - target);
            s += d <= 3 ? 4 : d <= 8 ? 2.5 : d <= 20 ? 0.5 : d <= 60 ? -2 : -5;
        }
        String wanted = norm(title);
        for (String w : VERSION_WORDS) {
            if (hay.contains(w) && !wanted.contains(w)) s -= 2.5;
        }
        if (music) s += 1;
        return s;
    }

    private static double overlap(Set<String> want, Set<String> have) {
        if (want.isEmpty()) return 0;
        int hit = 0;
        for (String w : want) if (have.contains(w)) hit++;
        return hit / (double) want.size();
    }

    static String norm(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) b.append(Character.isLetterOrDigit(c) ? c : ' ');
        return b.toString().replaceAll("\\s+", " ").trim();
    }

    static Set<String> words(String s) {
        Set<String> out = new HashSet<>();
        for (String w : norm(s).split(" ")) {
            if (w.isEmpty() || w.equals("feat") || w.equals("ft") || w.equals("the") || w.equals("topic")) continue;
            out.add(w);
        }
        return out;
    }

    /** Joins artist names the way tags usually show them. */
    static String joinArtists(List<String> names) {
        List<String> clean = new ArrayList<>();
        for (String n : names) if (n != null && !n.trim().isEmpty() && !clean.contains(n.trim())) clean.add(n.trim());
        return String.join(", ", clean);
    }
}
