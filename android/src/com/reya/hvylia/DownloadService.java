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
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.widget.Toast;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException;
import org.schabi.newpipe.extractor.exceptions.PaidContentException;
import org.schabi.newpipe.extractor.exceptions.PrivateContentException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException;
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException;
import org.schabi.newpipe.extractor.services.youtube.ItagItem;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads the audio of a YouTube video in the background and saves it as a tagged MP3:
 * NewPipeExtractor finds the best audio stream → chunked download → MediaCodec decode → MP3 (jump3r)
 * → ID3 title / artist / cover → the folder the user picked → added to Hvylia's library.
 */
public class DownloadService extends Service {
    static final String ACTION_DOWNLOAD = "com.reya.hvylia.DOWNLOAD";
    static final String ACTION_CANCEL = "com.reya.hvylia.CANCEL";
    private static final String CH_PROGRESS = "downloads";
    private static final String CH_DONE = "downloads_done";
    private static final int ID_PROGRESS = 10;
    private static final AtomicInteger NEXT_ID = new AtomicInteger(100);
    private static final int CHUNK = 2 * 1024 * 1024;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private NotificationManager nm;
    private volatile boolean cancel;
    private int queued; // main thread only
    private long lastUpdate;

    static void start(Context c, String videoUrl, String title, int kbps, Uri tree) {
        Intent i = new Intent(c, DownloadService.class).setAction(ACTION_DOWNLOAD)
                .putExtra("url", videoUrl).putExtra("title", title).putExtra("kbps", kbps).putExtra("tree", tree.toString());
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel p = new NotificationChannel(CH_PROGRESS, "Скачування", NotificationManager.IMPORTANCE_LOW);
            p.setShowBadge(false);
            nm.createNotificationChannel(p);
            nm.createNotificationChannel(new NotificationChannel(CH_DONE, "Готові скачування", NotificationManager.IMPORTANCE_DEFAULT));
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_CANCEL.equals(action)) {
            cancel = true;
            if (queued <= 0) stopSelfNow();
            return START_NOT_STICKY;
        }
        if (!ACTION_DOWNLOAD.equals(action)) {
            if (queued <= 0) stopSelfNow();
            return START_NOT_STICKY;
        }
        final String url = intent.getStringExtra("url");
        final String title = intent.getStringExtra("title");
        final int kbps = intent.getIntExtra("kbps", 192);
        final Uri tree = Uri.parse(intent.getStringExtra("tree"));
        queued++;
        Notification n = progress(title, queued > 1 ? "У черзі…" : "Готую скачування…", 0, true);
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID_PROGRESS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(ID_PROGRESS, n);
        worker.execute(() -> run(url, title == null ? "" : title, kbps, tree));
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        cancel = true;
        worker.shutdownNow();
        super.onDestroy();
    }

    private void stopSelfNow() {
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    // ---------------------------------------------------------------- the job

    private void run(String url, String fallbackTitle, int kbps, Uri tree) {
        cancel = false;
        String title = fallbackTitle;
        File src = null;
        File mp3 = null;
        try {
            YtDownloader.ensureInit();
            update(title, "Отримую дані про відео…", 0, true, true);
            StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, url);
            if (info.getName() != null && !info.getName().trim().isEmpty()) title = info.getName().trim();
            String artist = channel(info.getUploaderName());
            AudioStream audio = pickAudio(info.getAudioStreams());
            if (audio == null) throw new NoAudioException();
            checkCancel();

            String ext = audio.getFormat() != null ? audio.getFormat().getSuffix() : "m4a";
            src = new File(getCacheDir(), "yt-" + info.getId() + "." + ext);
            download(audio, src, title);

            update(title, "Готую обкладинку…", 56, false, true);
            byte[] cover = cover(info);
            checkCancel();

            mp3 = new File(getCacheDir(), "yt-" + info.getId() + ".mp3");
            final String t = title;
            OutputStream os = new BufferedOutputStream(new FileOutputStream(mp3), 1 << 16);
            try {
                os.write(Id3.tag(title, artist, "YouTube", cover, "image/jpeg"));
                AudioTranscoder.toMp3(src.getPath(), os, kbps, new AudioTranscoder.Progress() {
                    @Override public void on(float f) { update(t, "Перекодування в MP3…", 56 + Math.round(f * 42), false, false); }
                    @Override public boolean cancelled() { return cancel; }
                });
            } catch (AudioTranscoder.CancelledException e) {
                throw e;
            } catch (IOException | RuntimeException e) {
                if (cancel) throw new AudioTranscoder.CancelledException();
                throw new TranscodeException(e);
            } finally {
                os.close();
            }
            checkCancel();

            update(title, "Зберігаю у папку…", 99, false, true);
            String fileName = fileName(title) + ".mp3";
            Uri doc = save(tree, fileName, mp3);
            Store.addPending(this, doc, fileName);
            MainActivity.js("window.__hvyliaCheckImports&&__hvyliaCheckImports()");
            String folder = Store.treeName(this, tree);
            finished(title, "MP3 " + kbps + " кбіт/с" + (folder != null ? " у папці «" + folder + "»" : "") + " і в бібліотеці Хвилі.", false);
        } catch (AudioTranscoder.CancelledException e) {
            finished(title, "Скачування скасовано.", true);
        } catch (Throwable e) {
            if (cancel) finished(title, "Скачування скасовано.", true);
            else finished(title, explain(e), true);
        } finally {
            if (src != null) src.delete();
            if (mp3 != null) mp3.delete();
            main.post(() -> {
                queued--;
                if (queued <= 0) stopSelfNow();
            });
        }
    }

    private void checkCancel() throws AudioTranscoder.CancelledException {
        if (cancel) throw new AudioTranscoder.CancelledException();
    }

    /** The best-sounding progressive stream in the original language. */
    static AudioStream pickAudio(List<AudioStream> streams) {
        AudioStream best = null;
        int bestScore = Integer.MIN_VALUE;
        for (AudioStream s : streams) {
            if (s.getDeliveryMethod() != DeliveryMethod.PROGRESSIVE_HTTP || !s.isUrl()) continue;
            int bitrate = s.getAverageBitrate() > 0 ? s.getAverageBitrate() : s.getBitrate() / 1000;
            int score = bitrate * 10;
            AudioTrackType type = s.getAudioTrackType();
            if (type != null && type != AudioTrackType.ORIGINAL) score -= 100000; // dubbed / descriptive tracks last
            if (s.getFormat() == org.schabi.newpipe.extractor.MediaFormat.M4A) score += 5; // ties: AAC decodes everywhere
            if (score > bestScore) { bestScore = score; best = s; }
        }
        return best;
    }

    private void download(AudioStream audio, File out, String title) throws IOException {
        String url = audio.getContent();
        ItagItem itag = audio.getItagItem();
        long total = itag != null && itag.getContentLength() > 0 ? itag.getContentLength() : -1;
        long pos = 0;
        byte[] buf = new byte[64 * 1024];
        OutputStream os = new BufferedOutputStream(new FileOutputStream(out), 1 << 16);
        try {
            int failures = 0;
            while (total < 0 || pos < total) {
                checkCancel();
                long end = pos + CHUNK - 1;
                if (total > 0) end = Math.min(end, total - 1);
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                try {
                    c.setConnectTimeout(20000);
                    c.setReadTimeout(30000);
                    YtDownloader.streamHeaders(c, url);
                    c.setRequestProperty("Range", "bytes=" + pos + "-" + end);
                    int code = c.getResponseCode();
                    if (code == 416 && total < 0) break;
                    if (code == 403) throw new ForbiddenException();
                    if (code != 200 && code != 206) throw new IOException("HTTP " + code);
                    if (total < 0) total = totalSize(c, code);
                    InputStream in = c.getInputStream();
                    try {
                        for (int n; (n = in.read(buf)) > 0; ) {
                            checkCancel();
                            os.write(buf, 0, n);
                            pos += n;
                            if (total > 0) update(title, "Завантаження звуку…", (int) (pos * 55 / total), false, false);
                        }
                    } finally { in.close(); }
                    failures = 0;
                    if (code == 200) break; // the server sent the whole file at once
                    if (total < 0) break;
                } catch (ForbiddenException | AudioTranscoder.CancelledException e) {
                    throw e;
                } catch (IOException e) {
                    if (++failures > 4) throw e;
                    SystemClock.sleep(1000L * failures);
                } finally {
                    c.disconnect();
                }
            }
        } finally {
            os.close();
        }
        if (pos == 0) throw new IOException("empty download");
    }

    private static long totalSize(HttpURLConnection c, int code) {
        String range = c.getHeaderField("Content-Range");
        if (range != null) {
            Matcher m = Pattern.compile("/(\\d+)").matcher(range);
            if (m.find()) return Long.parseLong(m.group(1));
        }
        return code == 200 ? c.getContentLengthLong() : -1;
    }

    /** A square JPEG cover from the video thumbnail, or null. */
    private static byte[] cover(StreamInfo info) {
        String url = null;
        int bestW = -1;
        for (Image im : info.getThumbnails()) {
            int w = im.getWidth() > 0 ? im.getWidth() : 480;
            if (w > bestW && w <= 1280) { bestW = w; url = im.getUrl(); }
        }
        if (url == null) url = "https://i.ytimg.com/vi/" + info.getId() + "/hqdefault.jpg";
        Bitmap b = fetchBitmap(url);
        if (b == null) b = fetchBitmap("https://i.ytimg.com/vi/" + info.getId() + "/hqdefault.jpg");
        if (b == null) return null;
        Bitmap sq = squareCrop(b, 600);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        sq.compress(Bitmap.CompressFormat.JPEG, 90, out);
        return out.toByteArray();
    }

    static Bitmap fetchBitmap(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            try {
                c.setConnectTimeout(15000);
                c.setReadTimeout(20000);
                c.setRequestProperty("User-Agent", YtDownloader.USER_AGENT);
                if (c.getResponseCode() != 200) return null;
                InputStream in = c.getInputStream();
                try { return BitmapFactory.decodeStream(in); } finally { in.close(); }
            } finally { c.disconnect(); }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Centre-crops to a square (YouTube thumbnails are 16:9, often with black bars) and scales to size. */
    static Bitmap squareCrop(Bitmap b, int size) {
        int w = b.getWidth(), h = b.getHeight();
        int top = 0, bottom = h;
        // drop letterbox bars from 4:3 "hqdefault" thumbnails
        if (w * 3 == h * 4) { int bar = h / 8; top = bar; bottom = h - bar; }
        int ch = bottom - top, side = Math.min(w, ch);
        Bitmap cut = Bitmap.createBitmap(b, (w - side) / 2, top + (ch - side) / 2, side, side);
        return Bitmap.createScaledBitmap(cut, size, size, true);
    }

    private Uri save(Uri tree, String name, File file) throws IOException {
        try {
            Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
            Uri doc = DocumentsContract.createDocument(getContentResolver(), parent, "audio/mpeg", name);
            if (doc == null) throw new FolderException(null);
            OutputStream os = getContentResolver().openOutputStream(doc);
            if (os == null) throw new FolderException(null);
            InputStream in = new FileInputStream(file);
            try {
                byte[] buf = new byte[64 * 1024];
                for (int n; (n = in.read(buf)) > 0; ) os.write(buf, 0, n);
            } finally {
                in.close();
                os.close();
            }
            return doc;
        } catch (SecurityException | IllegalArgumentException | FileNotFoundException e) {
            throw new FolderException(e);
        }
    }

    static String channel(String name) {
        if (name == null) return "YouTube";
        String n = name.trim();
        if (n.endsWith(" - Topic")) n = n.substring(0, n.length() - 8).trim();
        return n.isEmpty() ? "YouTube" : n;
    }

    static String fileName(String title) {
        String s = title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", " ").replaceAll("\\s+", " ").trim();
        if (s.length() > 120) s = s.substring(0, 120).trim();
        return s.isEmpty() ? "YouTube" : s;
    }

    // ---------------------------------------------------------------- errors

    static final class NoAudioException extends IOException { }
    static final class ForbiddenException extends IOException { }
    static final class FolderException extends IOException {
        FolderException(Throwable cause) { super("folder", cause); }
    }
    static final class TranscodeException extends IOException {
        TranscodeException(Throwable cause) { super("transcode", cause); }
    }

    /** A clear Ukrainian explanation for the user. */
    static String explain(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof FolderException) return "Немає доступу до папки. Обери папку ще раз у панелі скачування.";
            if (t instanceof TranscodeException) return "Не вдалося перекодувати звук у MP3. Спробуй іншу якість або інше відео.";
            if (t instanceof NoAudioException) return "У цього відео немає звукової доріжки, яку можна скачати.";
            if (t instanceof ForbiddenException) return "YouTube не дозволив скачати звук (помилка 403). Спробуй ще раз трохи пізніше.";
            if (t instanceof UnknownHostException || t instanceof ConnectException
                    || t instanceof InterruptedIOException || t instanceof SocketException
                    || t instanceof javax.net.ssl.SSLException) {
                return "Немає з'єднання з інтернетом. Перевір мережу й спробуй ще раз.";
            }
            if (t instanceof AgeRestrictedContentException) return "Відео має вікове обмеження, тому його не можна скачати.";
            if (t instanceof GeographicRestrictionException) return "Це відео недоступне у твоєму регіоні.";
            if (t instanceof PaidContentException || t instanceof YoutubeMusicPremiumContentException) return "Це платний контент, його не можна скачати.";
            if (t instanceof PrivateContentException) return "Це приватне відео.";
            if (t instanceof SignInConfirmNotBotException || t instanceof ReCaptchaException) return "YouTube тимчасово обмежив запити з цього пристрою. Спробуй пізніше.";
            if (t instanceof ContentNotAvailableException) return "Відео недоступне: його видалили або обмежили доступ.";
            if (t instanceof ExtractionException) return "YouTube змінив сторінку, і отримати звук не вийшло. Спробуй пізніше.";
        }
        String m = e.getMessage();
        return "Не вдалося скачати" + (m != null && !m.isEmpty() ? ": " + m : ".");
    }

    // ---------------------------------------------------------------- notifications

    private void update(String title, String text, int percent, boolean indeterminate, boolean force) {
        long now = SystemClock.uptimeMillis();
        if (!force && now - lastUpdate < 500) return;
        lastUpdate = now;
        nm.notify(ID_PROGRESS, progress(title, text + (indeterminate ? "" : " " + percent + "%"), percent, indeterminate));
    }

    private Notification progress(String title, String text, int percent, boolean indeterminate) {
        Notification.Builder b = builder(CH_PROGRESS)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(title == null || title.isEmpty() ? "Скачування з YouTube" : title)
                .setContentText(text)
                .setProgress(100, percent, indeterminate)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setColor(0xFF1ED760)
                .setContentIntent(openApp());
        Intent cancelIntent = new Intent(this, DownloadService.class).setAction(ACTION_CANCEL);
        PendingIntent pi = PendingIntent.getService(this, 1, cancelIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        b.addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_close), "Скасувати", pi).build());
        return b.build();
    }

    private void finished(final String title, final String text, boolean failed) {
        Notification n = builder(CH_DONE)
                .setSmallIcon(failed ? R.drawable.ic_close : R.drawable.ic_check)
                .setContentTitle(failed ? "Не вдалося скачати" + (title.isEmpty() ? "" : " «" + title + "»") : "Готово: " + title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setColor(failed ? 0xFFFF5C6C : 0xFF1ED760)
                .setAutoCancel(true)
                .setContentIntent(openApp())
                .build();
        nm.notify(NEXT_ID.incrementAndGet(), n);
        main.post(() -> Toast.makeText(getApplicationContext(),
                failed ? text : "Готово: " + title, Toast.LENGTH_LONG).show());
    }

    private Notification.Builder builder(String channel) {
        return Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, channel) : new Notification.Builder(this);
    }

    private PendingIntent openApp() {
        Intent i = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return PendingIntent.getActivity(this, 2, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
