package com.reya.hvylia;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;

/**
 * Keeps playback alive in the background and shows the active player in a media notification, on the lock screen
 * and for headset / Bluetooth buttons. Two players can own it: the library player (the page in MainActivity) and
 * the Spotify tab's {@link StreamPlayer}. The one that starts playing takes over and the other one pauses;
 * buttons go to the owner.
 */
public class PlayerService extends Service {
    /** What the notification buttons and media keys drive. */
    interface Controller {
        void play();
        void pause();
        void toggle();
        void next();
        void prev();
        void seek(long ms);
    }

    /** The library player: forwards to the page. */
    static final Controller WEB = new Controller() {
        @Override public void play() { MainActivity.js("window.__hvylia&&__hvylia.play()"); }
        @Override public void pause() { MainActivity.js("window.__hvylia&&__hvylia.pause()"); }
        @Override public void toggle() { MainActivity.js("window.__hvylia&&__hvylia.toggle()"); }
        @Override public void next() { MainActivity.js("window.__hvylia&&__hvylia.next()"); }
        @Override public void prev() { MainActivity.js("window.__hvylia&&__hvylia.prev()"); }
        @Override public void seek(long ms) { MainActivity.js("window.__hvylia&&__hvylia.seek(" + ms + ")"); }
    };

    private static final class State {
        String id = "", title = "", artist = "", album = "", artId = "";
        boolean playing;
        long duration, position;
        float speed = 1f;
        Bitmap art;
    }

    private static final String CHANNEL = "playback";
    private static final int NOTE_ID = 1;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static PlayerService inst;
    private static final State web = new State(), stream = new State();
    private static Controller streamCtl;
    private static boolean streamOwns; // guarded by PlayerService.class
    private static long streamSince;

    private MediaSession session;
    private NotificationManager nm;
    private boolean foreground;

    // ---------- called from the page bridge (background thread) ----------

    static void update(final Context c, String i, String t, String ar, String al, final boolean p, long dur, long pos, float sp) {
        boolean takeOver = false;
        synchronized (PlayerService.class) {
            web.id = i == null ? "" : i; web.title = t == null ? "" : t; web.artist = ar == null ? "" : ar; web.album = al == null ? "" : al;
            web.playing = p; web.duration = Math.max(0, dur); web.position = Math.max(0, pos); web.speed = sp > 0 ? sp : 1f;
            if (!web.id.equals(web.artId)) web.art = null;
            // (a "playing" report already on its way when the Spotify tab took over must not take it back)
            if (p && streamOwns && SystemClock.uptimeMillis() - streamSince > 1500) { streamOwns = false; takeOver = true; }
        }
        final Context app = c.getApplicationContext();
        final boolean pauseStream = takeOver;
        MAIN.post(new Runnable() {
            @Override public void run() {
                StreamPlayer sp = StreamPlayer.peek();
                if (pauseStream && sp != null) sp.pause(); // the library player started: the Spotify tab stops
                if (inst != null) inst.refresh();
                else if (p) start(app);
            }
        });
    }

    /** The Spotify tab's player changed (main thread). `wants` = it is playing or about to (still loading). */
    static void stream(Context c, Controller ctl, String key, String t, String ar, String al, boolean playing, boolean wants,
                       long dur, long pos, Bitmap art) {
        boolean takeOver = false;
        synchronized (PlayerService.class) {
            streamCtl = ctl;
            stream.id = key; stream.title = t; stream.artist = ar; stream.album = al;
            stream.playing = playing; stream.duration = Math.max(0, dur); stream.position = Math.max(0, pos); stream.speed = 1f;
            stream.art = art;
            if (wants && !streamOwns) { streamOwns = true; streamSince = SystemClock.uptimeMillis(); takeOver = true; }
        }
        if (takeOver) WEB.pause(); // and the library player stops
        if (inst != null) inst.refresh();
        else if (wants) start(c.getApplicationContext());
    }

    /** The Spotify tab's player was closed: show the library player again, or go away. */
    static void streamStopped(Context c) {
        boolean webHasTrack;
        synchronized (PlayerService.class) {
            stream.id = "";
            streamOwns = false;
            webHasTrack = !web.id.isEmpty();
        }
        if (inst == null) return;
        if (webHasTrack) inst.refresh();
        else c.stopService(new Intent(c, PlayerService.class));
    }

    private static Controller owner() {
        synchronized (PlayerService.class) { return streamOwns && streamCtl != null ? streamCtl : WEB; }
    }

    static void art(String forId, String dataUrl) {
        Bitmap b = null;
        try {
            int comma = dataUrl.indexOf(',');
            byte[] bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT);
            b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (RuntimeException ignored) { }
        synchronized (PlayerService.class) {
            web.artId = forId == null ? "" : forId;
            web.art = b;
        }
        MAIN.post(new Runnable() { @Override public void run() { if (inst != null) inst.refresh(); } });
    }

    /** The library player has nothing to show. */
    static void clear(final Context c) {
        final boolean keep;
        synchronized (PlayerService.class) {
            web.id = "";
            keep = streamOwns;
        }
        MAIN.post(new Runnable() {
            @Override public void run() {
                if (!keep) c.stopService(new Intent(c, PlayerService.class));
                else if (inst != null) inst.refresh();
            }
        });
    }

    private static void start(Context c) {
        Intent i = new Intent(c, PlayerService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (RuntimeException ignored) {
            // Starting from the background can be refused on Android 12+; the next tap in the app retries.
        }
    }

    // ---------- service ----------

    @Override
    public void onCreate() {
        super.onCreate();
        inst = this;
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
        }
        session = new MediaSession(this, "Hvylia");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { owner().play(); }
            @Override public void onPause() { owner().pause(); }
            @Override public void onStop() { owner().pause(); }
            @Override public void onSkipToNext() { owner().next(); }
            @Override public void onSkipToPrevious() { owner().prev(); }
            @Override public void onSeekTo(long pos) { owner().seek(pos); }
        });
        session.setActive(true);
        refresh();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent != null ? intent.getAction() : null;
        if ("prev".equals(a)) owner().prev();
        else if ("next".equals(a)) owner().next();
        else if ("toggle".equals(a)) owner().toggle();
        refresh();
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Swiping the app away stops the music, like closing it.
        MainActivity a = MainActivity.instance;
        if (a != null) a.finish();
        StreamPlayer sp = StreamPlayer.peek();
        if (sp != null) sp.stop();
        stopSelf();
    }

    @Override
    public void onDestroy() {
        inst = null;
        if (session != null) { session.setActive(false); session.release(); }
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void refresh() {
        String t, ar, al; boolean p, fromStream; long dur, pos; float sp; Bitmap b;
        synchronized (PlayerService.class) {
            fromStream = streamOwns;
            State s = fromStream ? stream : web;
            t = s.title; ar = s.artist; al = s.album; p = s.playing; dur = s.duration; pos = s.position; sp = s.speed; b = s.art;
        }
        PendingIntent open = fromStream ? openSpotify() : openApp();
        session.setSessionActivity(open);

        MediaMetadata.Builder md = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, t)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, ar)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, al)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, dur);
        if (b != null) md.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, b);
        session.setMetadata(md.build());
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO
                        | PlaybackState.ACTION_STOP)
                .setState(p ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, pos, p ? sp : 0f, SystemClock.elapsedRealtime())
                .build());

        Notification.Builder nb = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        nb.setSmallIcon(R.drawable.ic_stat_wave)
                .setContentTitle(t)
                .setContentText(ar)
                .setSubText(al)
                .setLargeIcon(b)
                .setContentIntent(open)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setShowWhen(false)
                .setOngoing(p)
                .setColor(0xFF1ED760)
                .addAction(action(R.drawable.ic_prev, getString(R.string.prev), "prev", 1))
                .addAction(action(p ? R.drawable.ic_pause : R.drawable.ic_play, getString(p ? R.string.pause : R.string.play), "toggle", 2))
                .addAction(action(R.drawable.ic_next, getString(R.string.next), "next", 3))
                .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1, 2));
        Notification n = nb.build();

        if (!foreground) {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(NOTE_ID, n);
            foreground = true;
        } else {
            nm.notify(NOTE_ID, n);
        }
    }

    private Notification.Action action(int icon, String label, String act, int code) {
        Intent i = new Intent(this, PlayerService.class).setAction(act);
        PendingIntent pi = PendingIntent.getService(this, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Action.Builder(Icon.createWithResource(this, icon), label, pi).build();
    }

    private PendingIntent openSpotify() {
        Intent i = new Intent(this, SpotifyActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return PendingIntent.getActivity(this, 4, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent openApp() {
        Intent i = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
