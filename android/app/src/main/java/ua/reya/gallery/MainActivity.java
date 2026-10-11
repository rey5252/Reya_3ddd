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
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.MediaStore;
import android.util.Base64;
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
    private static final int REQ_FILES = 1, REQ_FOLDER = 2, REQ_WRITE = 3, REQ_READ = 4;
    private static final int MAX_FOLDER_FILES = 20000;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Map<String, OutFile> outs = new ConcurrentHashMap<>();
    private List<OutFile> pendingSave;

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
                return serve(req.getUrl());
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

    // ---------- serving the app and picked files ----------
    private WebResourceResponse serve(Uri u) {
        if (!HOST.equals(u.getHost())) return null;
        String path = u.getPath() == null ? "/" : u.getPath();
        try {
            if (path.equals("/saf")) {
                Uri src = Uri.parse(u.getQueryParameter("u"));
                ContentResolver cr = getContentResolver();
                String mime = cr.getType(src);
                return response(mime != null ? mime : "application/octet-stream", cr.openInputStream(src));
            }
            if (path.startsWith("/web/")) {
                String asset = path.substring(1);
                if (asset.endsWith("/")) asset += "index.html";
                return response(mimeOf(asset), getAssets().open(asset));
            }
        } catch (Exception ignored) { }
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", new HashMap<>(), null);
    }

    private static WebResourceResponse response(String mime, InputStream in) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Cache-Control", "no-cache");
        String enc = mime.startsWith("text/") || mime.contains("javascript") || mime.contains("json") ? "utf-8" : null;
        return new WebResourceResponse(mime, enc, 200, "OK", headers, in);
    }

    private static String mimeOf(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".html")) return "text/html";
        if (n.endsWith(".css")) return "text/css";
        if (n.endsWith(".js")) return "application/javascript";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".png")) return "image/png";
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
            runOnUiThread(() -> {
                String[] perms = readPerms();
                boolean granted = false;
                for (String p : perms) granted |= checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
                if (granted) startDownloadsScan();
                else requestPermissions(perms, REQ_READ);
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

    private static void copy(InputStream in, OutputStream os) throws IOException {
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
    }

    // ---------- Download folder (read through MediaStore: the folder picker refuses it on Android 11+) ----------
    private static String[] readPerms() {
        return Build.VERSION.SDK_INT >= 33
                ? new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO}
                : new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    private void startDownloadsScan() {
        callJs("window.__onNativeFolderStart && __onNativeFolderStart()");
        io.execute(() -> {
            try {
                JSONArray files = new JSONArray();
                scanMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, files);
                scanMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, files);
                JSONObject out = new JSONObject();
                out.put("root", "Download");
                out.put("files", files);
                callJs("window.__onNativeFolder && __onNativeFolder(" + out + ")");
            } catch (Exception e) {
                callJs("window.__onNativeFolder && __onNativeFolder({error:" + JSONObject.quote(String.valueOf(e.getMessage())) + "})");
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void scanMedia(Uri collection, JSONArray out) throws JSONException {
        boolean q = Build.VERSION.SDK_INT >= 29;
        String pathCol = q ? MediaStore.MediaColumns.RELATIVE_PATH : MediaStore.MediaColumns.DATA;
        String[] cols = {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED, pathCol};
        String sel = pathCol + " LIKE ?";
        String[] args = {q ? "Download/%" : "%/Download/%"};
        try (Cursor c = getContentResolver().query(collection, cols, sel, args, MediaStore.MediaColumns.DATE_MODIFIED + " ASC")) {
            if (c == null) return;
            while (c.moveToNext() && out.length() < MAX_FOLDER_FILES) {
                String name = c.getString(1), p = c.getString(5);
                if (name == null || p == null || name.startsWith(".")) continue;
                String dir;
                if (q) dir = p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
                else {
                    int i = p.indexOf("/Download/");
                    if (i < 0) continue;
                    dir = p.substring(i + 1, p.lastIndexOf('/'));
                }
                if (!dir.equals("Download") && !dir.startsWith("Download/")) {
                    // MediaStore may report "download/" in another case; keep the folder name as "Download"
                    int slash = dir.indexOf('/');
                    dir = "Download" + (slash >= 0 ? dir.substring(slash) : "");
                }
                if (dir.contains("/.")) continue;
                String mime = c.getString(2);
                if (mime == null || mime.isEmpty()) mime = guessMime(name);
                JSONObject f = new JSONObject();
                f.put("u", ContentUris.withAppendedId(collection, c.getLong(0)).toString());
                f.put("rel", dir + "/" + name);
                f.put("name", name);
                f.put("size", c.isNull(3) ? 0 : c.getLong(3));
                f.put("mime", mime);
                f.put("mod", c.isNull(4) ? 0 : c.getLong(4) * 1000);
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
                callJs("window.__onNativeFolderStart && __onNativeFolderStart()");
                io.execute(() -> scanTree(tree));
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
        if (req == REQ_READ) {
            boolean any = false;
            for (int g : grants) any |= g == PackageManager.PERMISSION_GRANTED;
            if (any) startDownloadsScan();
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
