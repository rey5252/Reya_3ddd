package com.reya.hvylia;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Listening without downloading: plays tracks from the Spotify screen straight away. Spotify's own audio is
 * DRM-protected and never touched; each track is matched on YouTube (as for downloads) and its audio streamed
 * through {@link StreamSource}. Keeps a queue (the list the track was started from), moves on by itself, prepares
 * the next track while the current one plays, and shows in the media notification via {@link PlayerService}.
 * All methods run on the main thread.
 */
final class StreamPlayer implements PlayerService.Controller {
    interface Listener { void onState(String json); }

    static final class Item {
        String key = "", id = "", title = "", artists = "", album = "", cover = "", yt = "";
        long seconds;
        JSONObject json;
    }

    private static StreamPlayer inst;

    static StreamPlayer get(Context c) {
        if (inst == null) inst = new StreamPlayer(c.getApplicationContext());
        return inst;
    }

    /** The player if it was ever used, else null (nothing to stop). */
    static StreamPlayer peek() { return inst; }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService work = Executors.newFixedThreadPool(2);
    private final AudioManager am;
    private final AudioAttributes attrs = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
    private final AudioFocusRequest focus;
    /** Track key → its YouTube match, so replays and the next track skip the search. */
    private static final Map<String, String> MATCH = new LinkedHashMap<String, String>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> e) { return size() > 300; }
    };

    private List<Item> queue = new ArrayList<>();
    private int index = -1;
    private MediaPlayer mp;
    private StreamSource src;
    private int generation;
    private boolean prepared, wantPlay, loading, buffering, completed, pausedByFocus;
    private String error = "";
    private long durHint; // ms, from Spotify / YouTube; used while the player doesn't know the length
    private int skips;
    private Bitmap art;
    private String artKey = "";
    private Listener listener;
    private boolean noisyOn;

    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { pause(); } // headphones unplugged
    };

    private StreamPlayer(Context app) {
        this.app = app;
        am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(this::onFocus, main).build();
        File[] old = app.getCacheDir().listFiles((d, n) -> n.startsWith("play-") && n.endsWith(".tmp"));
        if (old != null) for (File f : old) f.delete();
    }

    void setListener(Listener l) { listener = l; }

    void removeListener(Listener l) { if (listener == l) listener = null; }

    // ---------------------------------------------------------------- controls

    /** Plays `items` from position `start` (the list becomes the queue). */
    void playList(List<Item> items, int start) {
        if (items.isEmpty()) return;
        queue = new ArrayList<>(items);
        skips = 0;
        load(Math.max(0, Math.min(start, items.size() - 1)), true);
    }

    @Override public void play() {
        if (index < 0) return;
        if (mp == null || !error.isEmpty()) { load(index, true); return; }
        wantPlay = true;
        if (!prepared) { publish(); return; }
        if (completed) { mp.seekTo(0); completed = false; }
        startPlayback();
    }

    @Override public void pause() {
        wantPlay = false;
        pausedByFocus = false;
        if (prepared && mp.isPlaying()) mp.pause();
        noisy(false);
        publish();
    }

    @Override public void toggle() { if (wantPlay) pause(); else play(); }

    @Override public void next() {
        if (index + 1 < queue.size()) { skips = 0; load(index + 1, true); }
    }

    @Override public void prev() {
        if (prepared && mp.getCurrentPosition() > 3000 || index <= 0) seek(0);
        else { skips = 0; load(index - 1, true); }
    }

    @Override public void seek(long ms) {
        if (!prepared) return;
        completed = false;
        int d = mp.getDuration();
        mp.seekTo((int) Math.max(0, d > 0 ? Math.min(ms, d) : ms));
        publish();
    }

    /** Stops and forgets everything (closing the app). */
    void stop() {
        generation++;
        release();
        queue = new ArrayList<>();
        index = -1;
        wantPlay = false;
        loading = false;
        error = "";
        noisy(false);
        am.abandonAudioFocusRequest(focus);
        PlayerService.streamStopped(app);
        publish();
    }

    // ---------------------------------------------------------------- loading

    private void load(final int i, boolean play) {
        release();
        index = i;
        wantPlay = play;
        loading = true;
        prepared = false;
        completed = false;
        buffering = false;
        error = "";
        final int gen = ++generation;
        final Item it = queue.get(i);
        durHint = it.seconds * 1000L;
        loadArt(it);
        publish();
        work.execute(() -> {
            StreamSource s = null;
            try {
                String url = match(it);
                StreamInfo info = StreamCache.get(url);
                try {
                    s = open(info, gen);
                } catch (StreamSource.ForbiddenException e) { // stale stream addresses: look the video up again once
                    StreamCache.forget(url);
                    info = StreamCache.get(url);
                    s = open(info, gen);
                }
                final StreamSource ready = s;
                final long dur = info.getDuration() * 1000L;
                main.post(() -> {
                    if (gen != generation) { ready.close(); return; }
                    begin(ready, dur, gen);
                });
            } catch (final Throwable e) {
                if (s != null) s.close();
                main.post(() -> { if (gen == generation) fail(e); });
            }
        });
    }

    private StreamSource open(StreamInfo info, int gen) throws IOException {
        AudioStream a = pickStream(info.getAudioStreams());
        if (a == null) throw new DownloadService.NoAudioException();
        long known = a.getItagItem() != null && a.getItagItem().getContentLength() > 0 ? a.getItagItem().getContentLength() : -1;
        return new StreamSource(a.getContent(), known, new File(app.getCacheDir(), "play-" + gen + ".tmp"));
    }

    /** The YouTube video for a track: given (YouTube results), remembered, or searched like for downloads. */
    private static String match(Item it) throws Exception {
        if (!it.yt.isEmpty()) return it.yt;
        synchronized (MATCH) {
            String known = MATCH.get(it.key);
            if (known != null) return known;
        }
        SpotifyMeta.Track t = new SpotifyMeta.Track();
        t.id = it.id; t.title = it.title; t.artists = it.artists; t.album = it.album; t.cover = it.cover; t.seconds = it.seconds;
        if (t.artists.isEmpty() || t.seconds <= 0) SpotifyMeta.complete(t);
        String url = YtSearch.find(t.title, t.artists, t.seconds);
        synchronized (MATCH) { MATCH.put(it.key, url); }
        return url;
    }

    /** AAC (M4A) first: every Android device plays it; then whatever sounds best. */
    static AudioStream pickStream(List<AudioStream> streams) {
        AudioStream best = null;
        int bestScore = Integer.MIN_VALUE;
        for (AudioStream s : streams) {
            if (s.getDeliveryMethod() != DeliveryMethod.PROGRESSIVE_HTTP || !s.isUrl()) continue;
            int bitrate = s.getAverageBitrate() > 0 ? s.getAverageBitrate() : s.getBitrate() / 1000;
            int score = Math.min(bitrate, 320);
            if (s.getFormat() == MediaFormat.M4A) score += 1000;
            AudioTrackType type = s.getAudioTrackType();
            if (type != null && type != AudioTrackType.ORIGINAL) score -= 100000;
            if (score > bestScore) { bestScore = score; best = s; }
        }
        return best;
    }

    private void begin(StreamSource s, long ytDuration, final int gen) {
        src = s;
        if (ytDuration > 0) durHint = ytDuration;
        mp = new MediaPlayer();
        mp.setAudioAttributes(attrs);
        mp.setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK);
        mp.setOnPreparedListener(p -> {
            if (gen != generation) return;
            prepared = true;
            loading = false;
            skips = 0;
            if (wantPlay) startPlayback(); else publish();
            prepareNext();
        });
        mp.setOnCompletionListener(p -> {
            if (gen != generation) return;
            if (index + 1 < queue.size()) { load(index + 1, true); return; }
            wantPlay = false;
            completed = true;
            noisy(false);
            publish();
        });
        mp.setOnErrorListener((p, what, extra) -> {
            if (gen == generation) fail(new IOException("player " + what + "/" + extra));
            return true;
        });
        mp.setOnInfoListener((p, what, extra) -> {
            if (gen != generation) return false;
            if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) { buffering = true; publish(); }
            else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) { buffering = false; publish(); }
            return false;
        });
        try {
            mp.setDataSource(s);
            mp.prepareAsync();
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    /** Looks the next track up while this one plays, so moving on starts quickly. */
    private void prepareNext() {
        if (index + 1 >= queue.size()) return;
        final Item next = queue.get(index + 1);
        work.execute(() -> {
            try { StreamCache.prefetch(match(next)); } catch (Exception ignored) { }
        });
    }

    private void startPlayback() {
        if (am.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { wantPlay = false; publish(); return; }
        wantPlay = true;
        mp.setVolume(1f, 1f);
        mp.start();
        noisy(true);
        publish();
    }

    private void fail(Throwable e) {
        release();
        loading = false;
        wantPlay = false;
        error = explain(e);
        publish();
        boolean missing = false;
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof YtSearch.NotFoundException || t instanceof DownloadService.NoAudioException) missing = true;
        }
        // A track YouTube doesn't have: move on, like a player skipping a gap (but don't race through the whole list).
        if (missing && index + 1 < queue.size() && skips < 3) {
            final int gen = generation;
            main.postDelayed(() -> { if (gen == generation) { skips++; load(index + 1, true); } }, 1800);
        }
    }

    private void release() {
        if (mp != null) {
            try { mp.reset(); } catch (RuntimeException ignored) { }
            mp.release();
            mp = null;
        }
        if (src != null) {
            final StreamSource s = src;
            src = null;
            work.execute(s::close);
        }
        prepared = false;
    }

    private void onFocus(int change) {
        if (change == AudioManager.AUDIOFOCUS_LOSS) {
            pause();
        } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            if (wantPlay) { pause(); pausedByFocus = true; }
        } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            if (mp != null) mp.setVolume(.25f, .25f);
        } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
            if (mp != null) mp.setVolume(1f, 1f);
            if (pausedByFocus) { pausedByFocus = false; play(); }
        }
    }

    private void noisy(boolean on) {
        if (on == noisyOn) return;
        noisyOn = on;
        try {
            if (on) app.registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)); // a system broadcast
            else app.unregisterReceiver(noisy);
        } catch (RuntimeException e) {
            noisyOn = false; // without it only the unplug pause is lost
        }
    }

    private void loadArt(final Item it) {
        if (it.key.equals(artKey)) return;
        artKey = it.key;
        art = null;
        if (it.cover.isEmpty()) return;
        work.execute(() -> {
            Bitmap b = DownloadService.fetchBitmap(it.cover);
            if (b != null && (b.getWidth() > 512 || b.getHeight() > 512)) b = Bitmap.createScaledBitmap(b, 512, 512, true);
            final Bitmap fb = b;
            main.post(() -> { if (it.key.equals(artKey)) { art = fb; publish(); } });
        });
    }

    static String explain(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof YtSearch.NotFoundException) return "Не знайшов цей трек на YouTube.";
            if (t instanceof DownloadService.NoAudioException) return "У цього треку на YouTube немає звуку.";
            if (t instanceof StreamSource.ForbiddenException) return "YouTube не віддав звук цього треку. Спробуй ще раз трохи пізніше.";
            if (t instanceof UnknownHostException || t instanceof ConnectException || t instanceof SocketException
                    || t instanceof InterruptedIOException || t instanceof javax.net.ssl.SSLException) {
                return "Немає з'єднання з інтернетом. Перевір мережу й спробуй ще раз.";
            }
        }
        String m = e.getMessage();
        if (m != null && m.startsWith("player ")) return "Не вдалося відтворити цей трек. Спробуй ще раз.";
        return DownloadService.explain(e).replace("Не вдалося скачати", "Не вдалося увімкнути");
    }

    // ---------------------------------------------------------------- state

    /** What the Spotify screen shows (also sent on every change). */
    String state() {
        JSONObject o = new JSONObject();
        try {
            Item it = index >= 0 && index < queue.size() ? queue.get(index) : null;
            o.put("active", it != null);
            if (it != null) {
                long dur = length();
                long pos = prepared ? (completed ? dur : mp.getCurrentPosition()) : 0;
                o.put("key", it.key).put("index", index).put("count", queue.size()).put("track", it.json)
                        .put("playing", wantPlay).put("loading", loading || buffering).put("error", error)
                        .put("pos", pos).put("dur", Math.max(0, dur)).put("at", System.currentTimeMillis())
                        .put("hasNext", index + 1 < queue.size()).put("hasPrev", index > 0);
            }
        } catch (JSONException ignored) { }
        return o.toString();
    }

    private void publish() {
        if (listener != null) listener.onState(state());
        Item it = index >= 0 && index < queue.size() ? queue.get(index) : null;
        if (it == null) return;
        boolean playing = prepared && wantPlay && mp != null && mp.isPlaying();
        long pos = prepared ? mp.getCurrentPosition() : 0;
        long dur = length();
        PlayerService.stream(app, this, it.key, it.title, it.artists, it.album, playing, wantPlay, dur, pos, art);
    }

    private long length() {
        int d = prepared ? mp.getDuration() : -1;
        return d > 0 ? d : Math.max(0, durHint);
    }

    /** Queue items from the screen's track JSON. */
    static List<Item> items(JSONArray a) {
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            Item it = new Item();
            it.id = SpotifyApi.str(o, "id");
            it.yt = SpotifyApi.str(o, "yt");
            it.key = it.id.isEmpty() ? it.yt : it.id;
            it.title = SpotifyApi.str(o, "title");
            it.artists = SpotifyApi.str(o, "artists");
            it.album = SpotifyApi.str(o, "album");
            it.cover = SpotifyApi.str(o, "cover");
            if (it.cover.isEmpty()) it.cover = SpotifyApi.str(o, "thumb");
            it.seconds = o.optLong("seconds");
            it.json = o;
            if (!it.title.isEmpty() && !it.key.isEmpty()) out.add(it);
        }
        return out;
    }
}
