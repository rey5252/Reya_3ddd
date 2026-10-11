package ua.reya.gallery;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ContentUris;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Size;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FilterInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Hosts the web gallery (assets/web) in a WebView served from a private https origin,
 * and adds what a browser can't do well on Android: picking a whole folder through the
 * system picker, sharing, and saving into the phone's own gallery.
 */
public class MainActivity extends Activity {
    static final String HOST = "appassets.androidplatform.net";
    static final String START = "https://" + HOST + "/web/index.html";
    static final String SAVE_DIR = "Галерея Reya";
    private static final int REQ_FILES = 1, REQ_FOLDER = 2, REQ_WRITE = 3, REQ_READ = 4, REQ_PICK = 5;
    private static final int MAX_FOLDER_FILES = 20000;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Map<String, OutFile> outs = new ConcurrentHashMap<>();
    private List<OutFile> pendingSave;
    private Runnable pendingRead, pendingReadDenied;

    static final class OutFile {
        File file;
        String name, mime;
        FileOutputStream os;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        applySystemBars();
        clearOutDir();

        web = new WebView(this);
        web.setBackgroundColor(isNight() ? Color.BLACK : Color.WHITE);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setTextZoom(100);

        web.addJavascriptInterface(new Bridge(), "AndroidGallery");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                return serve(req);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                try {
                    startActivityForResult(i, REQ_FILES);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
            }
        });
        web.loadUrl(START);
    }

    // ---------- serving the app, thumbnails and phone files ----------
    private WebResourceResponse serve(WebResourceRequest req) {
        Uri u = req.getUrl();
        if (!HOST.equals(u.getHost())) return null;
        String path = u.getPath() == null ? "/" : u.getPath();
        try {
            if (path.equals("/saf")) return fileResponse(Uri.parse(u.getQueryParameter("u")), header(req, "Range"));
            if (path.equals("/thumb")) {
                int size = 400;
                try { size = Math.max(64, Math.min(1024, Integer.parseInt(u.getQueryParameter("s")))); } catch (Exception ignored) { }
                return thumbResponse(Uri.parse(u.getQueryParameter("u")), u.getQueryParameter("m"), size);
            }
            if (path.startsWith("/web/")) {
                String asset = path.substring(1);
                if (asset.endsWith("/")) asset += "index.html";
                return response(mimeOf(asset), getAssets().open(asset), "no-cache");
            }
        } catch (Exception ignored) { }
        return notFound();
    }

    private static String header(WebResourceRequest req, String name) {
        Map<String, String> h = req.getRequestHeaders();
        if (h == null) return null;
        for (Map.Entry<String, String> e : h.entrySet()) if (name.equalsIgnoreCase(e.getKey())) return e.getValue();
        return null;
    }

    private static WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", new HashMap<>(), null);
    }

    private static WebResourceResponse response(String mime, InputStream in, String cache) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Cache-Control", cache);
        String enc = mime.startsWith("text/") || mime.contains("javascript") || mime.contains("json") ? "utf-8" : null;
        return new WebResourceResponse(mime, enc, 200, "OK", headers, in);
    }

    // Serves a photo or video straight from the phone; supports byte ranges so videos can seek.
    private WebResourceResponse fileResponse(Uri src, String range) throws IOException {
        ContentResolver cr = getContentResolver();
        String mime = cr.getType(src);
        if (mime == null) mime = "application/octet-stream";
        if (range == null || !range.startsWith("bytes=")) {
            WebResourceResponse r = response(mime, cr.openInputStream(src), "max-age=86400");
            Map<String, String> h = new HashMap<>(r.getResponseHeaders());
            h.put("Accept-Ranges", "bytes");
            r.setResponseHeaders(h);
            return r;
        }
        ParcelFileDescriptor pfd = cr.openFileDescriptor(src, "r");
        if (pfd == null) return notFound();
        long total = pfd.getStatSize();
        if (total <= 0) { pfd.close(); return response(mime, cr.openInputStream(src), "no-cache"); }
        String[] se = range.substring(6).split(",")[0].trim().split("-", -1);
        long start, end;
        if (se[0].isEmpty()) { start = Math.max(0, total - Long.parseLong(se[1])); end = total - 1; }
        else { start = Long.parseLong(se[0]); end = se.length > 1 && !se[1].isEmpty() ? Math.min(Long.parseLong(se[1]), total - 1) : total - 1; }
        if (start >= total || start > end) {
            pfd.close();
            Map<String, String> h = new HashMap<>();
            h.put("Content-Range", "bytes */" + total);
            return new WebResourceResponse(mime, null, 416, "Range Not Satisfiable", h, null);
        }
        FileInputStream in = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
        in.getChannel().position(start);
        Map<String, String> h = new HashMap<>();
        h.put("Content-Range", "bytes " + start + "-" + end + "/" + total);
        h.put("Content-Length", String.valueOf(end - start + 1));
        h.put("Accept-Ranges", "bytes");
        return new WebResourceResponse(mime, null, 206, "Partial Content", h, new Limited(in, end - start + 1));
    }

    static final class Limited extends FilterInputStream {
        private long left;
        Limited(InputStream in, long n) { super(in); left = n; }
        @Override public int read() throws IOException { if (left <= 0) return -1; int b = super.read(); if (b >= 0) left--; return b; }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            if (left <= 0) return -1;
            int n = super.read(b, off, (int) Math.min(len, left));
            if (n > 0) left -= n;
            return n;
        }
        @Override public int available() throws IOException { return (int) Math.min(super.available(), left); }
    }

    // Thumbnails come from the system's own thumbnail cache when possible, and are kept on disk.
    private WebResourceResponse thumbResponse(Uri src, String mime, int size) throws IOException {
        File dir = new File(getCacheDir(), "thumbs");
        dir.mkdirs();
        File f = new File(dir, sha1(src.toString()) + "_" + size + ".jpg");
        if (!f.exists()) {
            Bitmap b = makeThumb(src, mime, size);
            if (b == null) return notFound();
            File tmp = new File(dir, f.getName() + "." + Thread.currentThread().getId() + ".tmp");
            try (FileOutputStream os = new FileOutputStream(tmp)) { b.compress(Bitmap.CompressFormat.JPEG, 82, os); }
            b.recycle();
            if (!tmp.renameTo(f)) tmp.delete();
        }
        if (!f.exists()) return notFound();
        return response("image/jpeg", new FileInputStream(f), "max-age=604800");
    }

    @SuppressWarnings("deprecation")
    private Bitmap makeThumb(Uri u, String mime, int size) {
        boolean video = mime != null && mime.startsWith("video/");
        if (Build.VERSION.SDK_INT >= 29) {
            try { return getContentResolver().loadThumbnail(u, new Size(size, size), null); } catch (Exception ignored) { }
        }
        if (video) {
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            try {
                r.setDataSource(this, u);
                Bitmap b = r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (b == null) b = r.getFrameAtTime();
                return scaleDown(b, size);
            } catch (Exception e) {
                return null;
            } finally {
                try { r.release(); } catch (Exception ignored) { }
            }
        }
        try {
            ContentResolver cr = getContentResolver();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = cr.openInputStream(u)) { BitmapFactory.decodeStream(in, null, o); }
            if (o.outWidth <= 0) return null;
            int sample = 1;
            while (Math.min(o.outWidth, o.outHeight) / (sample * 2) >= size) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap b;
            try (InputStream in = cr.openInputStream(u)) { b = BitmapFactory.decodeStream(in, null, o2); }
            if (b == null) return null;
            int rot = 0;
            try (InputStream in = cr.openInputStream(u)) {
                int ori = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                rot = ori == ExifInterface.ORIENTATION_ROTATE_90 ? 90 : ori == ExifInterface.ORIENTATION_ROTATE_180 ? 180 : ori == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
            } catch (Exception ignored) { }
            if (rot != 0) {
                Matrix m = new Matrix();
                m.postRotate(rot);
                Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
                if (r != b) b.recycle();
                b = r;
            }
            return scaleDown(b, size);
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap scaleDown(Bitmap b, int size) {
        if (b == null) return null;
        int s = Math.min(b.getWidth(), b.getHeight());
        if (s <= size * 1.3) return b;
        float k = (float) size / s;
        Bitmap r = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * k), Math.round(b.getHeight() * k), true);
        if (r != b) b.recycle();
        return r;
    }

    private static String sha1(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private static String mimeOf(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".html")) return "text/html";
        if (n.endsWith(".css")) return "text/css";
        if (n.endsWith(".js")) return "application/javascript";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".wasm")) return "application/wasm";
        if (n.endsWith(".webmanifest") || n.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    // ---------- JS bridge ----------
    final class Bridge {
        @JavascriptInterface
        public void pickFolder() {
            runOnUiThread(() -> {
                try {
                    startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_FOLDER);
                } catch (Exception e) {
                    callJs("window.__onNativeFolder && __onNativeFolder({error:'unsupported'})");
                }
            });
        }

        @JavascriptInterface
        public void pickDownloads() {
            withReadPerm(() -> startMediaScan("Download/", "Download"), null);
        }

        @JavascriptInterface
        public void pickAllMedia() {
            withReadPerm(() -> startMediaScan("", null), null);
        }

        @JavascriptInterface
        public void pickFiles() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try { startActivityForResult(i, REQ_PICK); } catch (Exception e) { toastJs("Не вдалося відкрити вибір файлів"); }
            });
        }

        @JavascriptInterface
        public void shareAll(String idsJson, String refsJson) {
            List<OutFile> files = take(idsJson);
            io.execute(() -> {
                try {
                    JSONArray refs = new JSONArray(refsJson);
                    for (int i = 0; i < refs.length(); i++) {
                        JSONObject r = refs.getJSONObject(i);
                        OutFile o = copyToOut(Uri.parse(r.getString("u")), r.optString("name", "file"), r.optString("mime", ""));
                        if (o != null) files.add(o);
                    }
                } catch (JSONException ignored) { }
                if (files.isEmpty()) { toastJs("Не вдалося підготувати файли"); return; }
                runOnUiThread(() -> shareFiles(files));
            });
        }

        @JavascriptInterface
        public String beginFile(String name, String mime) {
            try {
                File dir = new File(getCacheDir(), "out");
                dir.mkdirs();
                String id = UUID.randomUUID().toString().substring(0, 8);
                String safe = name.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1f]", "_");
                OutFile o = new OutFile();
                o.file = new File(dir, id + "_" + safe);
                o.name = safe;
                o.mime = mime == null || mime.isEmpty() ? guessMime(safe) : mime;
                o.os = new FileOutputStream(o.file);
                outs.put(id, o);
                FilesProvider.MIMES.put(o.file.getName(), o.mime);
                return id;
            } catch (IOException e) {
                return "";
            }
        }

        @JavascriptInterface
        public void appendChunk(String id, String b64) {
            OutFile o = outs.get(id);
            if (o == null) return;
            try { o.os.write(Base64.decode(b64, Base64.DEFAULT)); } catch (Exception ignored) { }
        }

        @JavascriptInterface
        public void endFile(String id) {
            OutFile o = outs.get(id);
            if (o == null) return;
            try { o.os.close(); } catch (IOException ignored) { }
        }

        @JavascriptInterface
        public void share(String idsJson) {
            List<OutFile> files = take(idsJson);
            if (files.isEmpty()) return;
            runOnUiThread(() -> shareFiles(files));
        }

        @JavascriptInterface
        public void save(String idsJson) {
            List<OutFile> files = take(idsJson);
            if (files.isEmpty()) return;
            if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                pendingSave = files;
                runOnUiThread(() -> requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_WRITE));
                return;
            }
            io.execute(() -> saveFiles(files));
        }
    }

    private List<OutFile> take(String idsJson) {
        List<OutFile> list = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(idsJson);
            for (int i = 0; i < a.length(); i++) {
                OutFile o = outs.remove(a.getString(i));
                if (o != null) list.add(o);
            }
        } catch (JSONException ignored) { }
        return list;
    }

    private void shareFiles(List<OutFile> files) {
        ArrayList<Uri> uris = new ArrayList<>();
        String type = null;
        for (OutFile o : files) {
            uris.add(Uri.parse("content://" + FilesProvider.AUTHORITY + "/" + Uri.encode(o.file.getName())));
            String t = o.mime.contains("/") ? o.mime.substring(0, o.mime.indexOf('/')) + "/*" : "*/*";
            type = type == null ? (files.size() == 1 ? o.mime : t) : (type.equals(t) ? t : "*/*");
        }
        Intent i;
        if (uris.size() == 1) {
            i = new Intent(Intent.ACTION_SEND);
            i.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        } else {
            i = new Intent(Intent.ACTION_SEND_MULTIPLE);
            i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }
        ClipData clip = ClipData.newRawUri("", uris.get(0));
        for (int k = 1; k < uris.size(); k++) clip.addItem(new ClipData.Item(uris.get(k)));
        i.setClipData(clip);
        i.setType(type);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(i, "Поділитися"));
        } catch (Exception e) {
            toastJs("Немає застосунку, щоб поділитися");
        }
    }

    private void saveFiles(List<OutFile> files) {
        int ok = 0;
        for (OutFile o : files) {
            try {
                saveOne(o);
                ok++;
            } catch (Exception ignored) { }
            o.file.delete();
        }
        toastJs(ok == files.size() ? "Збережено в галерею телефона: " + ok : "Збережено: " + ok + " з " + files.size());
    }

    private void saveOne(OutFile o) throws IOException {
        boolean video = o.mime.startsWith("video/");
        String base = video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES;
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver cr = getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, o.name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, o.mime);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, base + "/" + SAVE_DIR);
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri coll = video ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    : MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri item = cr.insert(coll, v);
            if (item == null) throw new IOException("insert failed");
            try (OutputStream os = cr.openOutputStream(item); InputStream in = new FileInputStream(o.file)) {
                if (os == null) throw new IOException("no stream");
                copy(in, os);
            }
            v.clear();
            v.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(item, v, null, null);
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(base), SAVE_DIR);
            dir.mkdirs();
            File dst = new File(dir, o.name);
            for (int n = 1; dst.exists(); n++) {
                int dot = o.name.lastIndexOf('.');
                dst = new File(dir, dot > 0 ? o.name.substring(0, dot) + " (" + n + ")" + o.name.substring(dot) : o.name + " (" + n + ")");
            }
            try (InputStream in = new FileInputStream(o.file); OutputStream os = new FileOutputStream(dst)) {
                copy(in, os);
            }
            MediaScannerConnection.scanFile(this, new String[]{dst.getPath()}, null, null);
        }
    }

    private OutFile copyToOut(Uri src, String name, String mime) {
        try {
            File dir = new File(getCacheDir(), "out");
            dir.mkdirs();
            OutFile o = new OutFile();
            o.name = name.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1f]", "_");
            o.mime = mime == null || mime.isEmpty() ? guessMime(o.name) : mime;
            o.file = new File(dir, UUID.randomUUID().toString().substring(0, 8) + "_" + o.name);
            try (InputStream in = getContentResolver().openInputStream(src); OutputStream os = new FileOutputStream(o.file)) {
                if (in == null) return null;
                copy(in, os);
            }
            FilesProvider.MIMES.put(o.file.getName(), o.mime);
            return o;
        } catch (Exception e) {
            return null;
        }
    }

    private static void copy(InputStream in, OutputStream os) throws IOException {
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
    }

    // ---------- phone media through MediaStore (fast: names, sizes, dates, dimensions in one query) ----------
    private static String[] readPerms() {
        return Build.VERSION.SDK_INT >= 33
                ? new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO}
                : new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    private boolean hasReadPerm() {
        for (String p : readPerms()) if (checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED) return true;
        return false;
    }

    private void withReadPerm(Runnable granted, Runnable denied) {
        runOnUiThread(() -> {
            if (hasReadPerm()) { granted.run(); return; }
            pendingRead = granted;
            pendingReadDenied = denied;
            requestPermissions(readPerms(), REQ_READ);
        });
    }

    /** prefix: relative folder like "Download/" ("" = whole phone); rootName replaces it in the paths. */
    private void startMediaScan(String prefix, String rootName) {
        callJs("window.__onNativeFolderStart && __onNativeFolderStart()");
        io.execute(() -> {
            try {
                JSONArray files = new JSONArray();
                scanMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, prefix, rootName, files);
                scanMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, false, prefix, rootName, files);
                JSONObject out = new JSONObject();
                out.put("root", rootName != null ? rootName : "Телефон");
                out.put("files", files);
                callJs("window.__onNativeFolder && __onNativeFolder(" + out + ")");
            } catch (Exception e) {
                callJs("window.__onNativeFolder && __onNativeFolder({error:" + JSONObject.quote(String.valueOf(e.getMessage())) + "})");
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void scanMedia(Uri collection, boolean images, String prefix, String rootName, JSONArray out) throws JSONException {
        boolean q = Build.VERSION.SDK_INT >= 29;
        String pathCol = q ? MediaStore.MediaColumns.RELATIVE_PATH : MediaStore.MediaColumns.DATA;
        String[] cols = {"_id", "_display_name", "mime_type", "_size", "date_modified", "datetaken", "width", "height", images ? "orientation" : "duration", pathCol};
        String sel = null;
        String[] args = null;
        if (!prefix.isEmpty()) {
            sel = pathCol + " LIKE ?";
            args = new String[]{q ? prefix + "%" : "%/" + prefix + "%"};
        }
        String extRoot = Environment.getExternalStorageDirectory().getPath() + "/";
        try (Cursor c = getContentResolver().query(collection, cols, sel, args, "date_modified ASC")) {
            if (c == null) return;
            while (c.moveToNext() && out.length() < MAX_FOLDER_FILES) {
                String name = c.getString(1), p = c.getString(9);
                if (name == null || name.startsWith(".")) continue;
                String dir;
                if (q) dir = p == null ? "" : (p.endsWith("/") ? p.substring(0, p.length() - 1) : p);
                else {
                    if (p == null) continue;
                    String rel = p.startsWith(extRoot) ? p.substring(extRoot.length()) : p.substring(p.lastIndexOf('/', Math.max(0, p.lastIndexOf('/') - 1)) + 1);
                    dir = rel.contains("/") ? rel.substring(0, rel.lastIndexOf('/')) : "";
                }
                String outDir;
                if (prefix.isEmpty()) outDir = dir.isEmpty() ? "Телефон" : dir;
                else {
                    String d2 = dir + "/";
                    if (!d2.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) continue;
                    String rest = d2.substring(prefix.length());
                    outDir = rootName + (rest.isEmpty() ? "" : "/" + rest.substring(0, rest.length() - 1));
                }
                if (outDir.startsWith(".") || outDir.contains("/.")) continue;
                String mime = c.getString(2);
                if (mime == null || mime.isEmpty()) mime = guessMime(name);
                long w = c.isNull(6) ? 0 : c.getLong(6), h = c.isNull(7) ? 0 : c.getLong(7);
                if (images && !c.isNull(8) && (c.getInt(8) == 90 || c.getInt(8) == 270)) { long t = w; w = h; h = t; }
                JSONObject f = new JSONObject();
                f.put("u", ContentUris.withAppendedId(collection, c.getLong(0)).toString());
                f.put("rel", outDir + "/" + name);
                f.put("name", name);
                f.put("size", c.isNull(3) ? 0 : c.getLong(3));
                f.put("mime", mime);
                f.put("mod", c.isNull(4) ? 0 : c.getLong(4) * 1000);
                f.put("taken", c.isNull(5) ? 0 : c.getLong(5));
                f.put("w", w);
                f.put("h", h);
                if (!images && !c.isNull(8)) f.put("dur", c.getLong(8));
                out.put(f);
            }
        }
    }

    // ---------- folder picking ----------
    private void scanTree(Uri tree) {
        try {
            String rootId = DocumentsContract.getTreeDocumentId(tree);
            String rootName = "Папка";
            try (Cursor c = getContentResolver().query(DocumentsContract.buildDocumentUriUsingTree(tree, rootId),
                    new String[]{Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst() && c.getString(0) != null) rootName = c.getString(0);
            }
            JSONArray files = new JSONArray();
            walk(tree, rootId, rootName, files, 0);
            JSONObject out = new JSONObject();
            out.put("root", rootName);
            out.put("files", files);
            callJs("window.__onNativeFolder && __onNativeFolder(" + out + ")");
        } catch (Exception e) {
            callJs("window.__onNativeFolder && __onNativeFolder({error:" + JSONObject.quote(String.valueOf(e.getMessage())) + "})");
        }
    }

    private void walk(Uri tree, String docId, String path, JSONArray out, int depth) throws JSONException {
        if (depth > 12 || out.length() >= MAX_FOLDER_FILES) return;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        String[] cols = {Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED};
        List<String[]> dirs = new ArrayList<>();
        try (Cursor c = getContentResolver().query(children, cols, null, null, null)) {
            if (c == null) return;
            while (c.moveToNext() && out.length() < MAX_FOLDER_FILES) {
                String id = c.getString(0), name = c.getString(1), mime = c.getString(2);
                if (name == null || name.startsWith(".")) continue;
                if (Document.MIME_TYPE_DIR.equals(mime)) { dirs.add(new String[]{id, name}); continue; }
                if (mime == null || mime.isEmpty() || mime.equals("application/octet-stream")) mime = guessMime(name);
                if (!mime.startsWith("image/") && !mime.startsWith("video/")) continue;
                JSONObject f = new JSONObject();
                f.put("u", DocumentsContract.buildDocumentUriUsingTree(tree, id).toString());
                f.put("rel", path + "/" + name);
                f.put("name", name);
                f.put("size", c.isNull(3) ? 0 : c.getLong(3));
                f.put("mime", mime);
                f.put("mod", c.isNull(4) ? 0 : c.getLong(4));
                out.put(f);
            }
        }
        for (String[] d : dirs) walk(tree, d[0], path + "/" + d[1], out, depth + 1);
    }

    private void describeFiles(List<Uri> uris) {
        try {
            JSONArray files = new JSONArray();
            ContentResolver cr = getContentResolver();
            for (Uri u : uris) {
                try { cr.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
                String name = u.getLastPathSegment(), mime = cr.getType(u);
                long size = 0, mod = 0;
                try (Cursor c = cr.query(u, new String[]{Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED}, null, null, null)) {
                    if (c != null && c.moveToFirst()) {
                        if (c.getString(0) != null) name = c.getString(0);
                        size = c.isNull(1) ? 0 : c.getLong(1);
                        mod = c.isNull(2) ? 0 : c.getLong(2);
                    }
                } catch (Exception ignored) { }
                if (name == null) name = "file";
                if (mime == null || mime.isEmpty()) mime = guessMime(name);
                JSONObject f = new JSONObject();
                f.put("u", u.toString());
                f.put("name", name);
                f.put("size", size);
                f.put("mime", mime);
                f.put("mod", mod);
                files.put(f);
            }
            JSONObject out = new JSONObject();
            out.put("files", files);
            callJs("window.__onNativeFiles && __onNativeFiles(" + out + ")");
        } catch (JSONException ignored) { }
    }

    private static String guessMime(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        int dot = n.lastIndexOf('.');
        String e = dot >= 0 ? n.substring(dot + 1) : "";
        switch (e) {
            case "jpg": case "jpeg": return "image/jpeg";
            case "png": return "image/png";
            case "gif": return "image/gif";
            case "webp": return "image/webp";
            case "heic": return "image/heic";
            case "heif": return "image/heif";
            case "avif": return "image/avif";
            case "bmp": return "image/bmp";
            case "mp4": case "m4v": return "video/mp4";
            case "mov": return "video/quicktime";
            case "webm": return "video/webm";
            case "3gp": return "video/3gpp";
            case "mkv": return "video/x-matroska";
            default: return "application/octet-stream";
        }
    }

    // ---------- activity plumbing ----------
    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FILES) {
            Uri[] result = null;
            if (res == RESULT_OK && data != null) {
                ClipData clip = data.getClipData();
                if (clip != null) {
                    result = new Uri[clip.getItemCount()];
                    for (int i = 0; i < result.length; i++) result[i] = clip.getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    result = new Uri[]{data.getData()};
                }
            }
            if (fileCallback != null) fileCallback.onReceiveValue(result);
            fileCallback = null;
        } else if (req == REQ_FOLDER) {
            if (res == RESULT_OK && data != null && data.getData() != null) {
                Uri tree = data.getData();
                try { getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
                Runnable saf = () -> {
                    callJs("window.__onNativeFolderStart && __onNativeFolderStart()");
                    io.execute(() -> scanTree(tree));
                };
                // A folder on the phone's main storage is read through MediaStore: much faster, with real dates.
                String docId = DocumentsContract.getTreeDocumentId(tree);
                if (Build.VERSION.SDK_INT >= 29 && docId != null && docId.startsWith("primary:")) {
                    String rel = docId.substring("primary:".length());
                    while (rel.endsWith("/")) rel = rel.substring(0, rel.length() - 1);
                    String prefix = rel.isEmpty() ? "" : rel + "/";
                    String root = rel.isEmpty() ? null : rel.substring(rel.lastIndexOf('/') + 1);
                    withReadPerm(() -> startMediaScan(prefix, root), saf);
                } else saf.run();
            }
        } else if (req == REQ_PICK) {
            if (res != RESULT_OK || data == null) return;
            List<Uri> uris = new ArrayList<>();
            ClipData clip = data.getClipData();
            if (clip != null) for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
            else if (data.getData() != null) uris.add(data.getData());
            io.execute(() -> describeFiles(uris));
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
        if (req == REQ_READ) {
            boolean any = false;
            for (int g : grants) any |= g == PackageManager.PERMISSION_GRANTED;
            Runnable ok = pendingRead, no = pendingReadDenied;
            pendingRead = null;
            pendingReadDenied = null;
            if (any && ok != null) ok.run();
            else if (no != null) no.run();
            else toastJs("Дозвольте доступ до фото й відео: Налаштування → Застосунки → Галерея → Дозволи");
            return;
        }
        if (req == REQ_WRITE && pendingSave != null) {
            List<OutFile> files = pendingSave;
            pendingSave = null;
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) io.execute(() -> saveFiles(files));
            else toastJs("Без дозволу не вийде зберегти в галерею телефона");
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        io.shutdown();
        super.onDestroy();
    }

    private void callJs(String code) {
        runOnUiThread(() -> { if (web != null) web.evaluateJavascript(code, null); });
    }

    private void toastJs(String msg) {
        callJs("window.__nativeToast && __nativeToast(" + JSONObject.quote(msg) + ")");
    }

    private boolean isNight() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    @SuppressWarnings("deprecation")
    private void applySystemBars() {
        boolean night = isNight();
        int bg = night ? Color.BLACK : Color.WHITE;
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        int flags = 0;
        if (!night) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private void clearOutDir() {
        File[] old = new File(getCacheDir(), "out").listFiles();
        if (old != null) for (File f : old) f.delete();
    }
}
