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
 * Keeps playback alive in the background and mirrors the page's player in a media notification,
 * on the lock screen and for headset / Bluetooth buttons. Every button is forwarded to the page.
 */
public class PlayerService extends Service {
    private static final String CHANNEL = "playback";
    private static final int NOTE_ID = 1;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static PlayerService inst;
    private static String id = "", title = "", artist = "", album = "", artId = "";
    private static boolean playing;
    private static long duration, position;
    private static float speed = 1f;
    private static Bitmap art;

    private MediaSession session;
    private NotificationManager nm;
    private boolean foreground;

    // ---------- called from the page bridge (background thread) ----------

    static void update(final Context c, String i, String t, String ar, String al, boolean p, long dur, long pos, float sp) {
        synchronized (PlayerService.class) {
            id = i == null ? "" : i; title = t == null ? "" : t; artist = ar == null ? "" : ar; album = al == null ? "" : al;
            playing = p; duration = Math.max(0, dur); position = Math.max(0, pos); speed = sp > 0 ? sp : 1f;
            if (!id.equals(artId)) art = null;
        }
        final Context app = c.getApplicationContext();
        MAIN.post(new Runnable() {
            @Override public void run() {
                if (inst != null) inst.refresh();
                else if (playing) start(app);
            }
        });
    }

    static void art(String forId, String dataUrl) {
        Bitmap b = null;
        try {
            int comma = dataUrl.indexOf(',');
            byte[] bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT);
            b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (RuntimeException ignored) { }
        synchronized (PlayerService.class) {
            artId = forId == null ? "" : forId;
            art = b;
        }
        MAIN.post(new Runnable() { @Override public void run() { if (inst != null) inst.refresh(); } });
    }

    static void clear(final Context c) {
        MAIN.post(new Runnable() { @Override public void run() { c.stopService(new Intent(c, PlayerService.class)); } });
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
            @Override public void onPlay() { MainActivity.js("window.__hvylia&&__hvylia.play()"); }
            @Override public void onPause() { MainActivity.js("window.__hvylia&&__hvylia.pause()"); }
            @Override public void onStop() { MainActivity.js("window.__hvylia&&__hvylia.pause()"); }
            @Override public void onSkipToNext() { MainActivity.js("window.__hvylia&&__hvylia.next()"); }
            @Override public void onSkipToPrevious() { MainActivity.js("window.__hvylia&&__hvylia.prev()"); }
            @Override public void onSeekTo(long pos) { MainActivity.js("window.__hvylia&&__hvylia.seek(" + pos + ")"); }
        });
        session.setSessionActivity(openApp());
        session.setActive(true);
        refresh();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent != null ? intent.getAction() : null;
        if ("prev".equals(a)) MainActivity.js("window.__hvylia&&__hvylia.prev()");
        else if ("next".equals(a)) MainActivity.js("window.__hvylia&&__hvylia.next()");
        else if ("toggle".equals(a)) MainActivity.js("window.__hvylia&&__hvylia.toggle()");
        refresh();
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // Swiping the app away stops the music, like closing it.
        MainActivity a = MainActivity.instance;
        if (a != null) a.finish();
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
        String t, ar, al; boolean p; long dur, pos; float sp; Bitmap b;
        synchronized (PlayerService.class) { t = title; ar = artist; al = album; p = playing; dur = duration; pos = position; sp = speed; b = art; }

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
                .setContentIntent(openApp())
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

    private PendingIntent openApp() {
        Intent i = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
