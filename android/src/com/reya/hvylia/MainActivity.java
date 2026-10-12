package com.reya.hvylia;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Full-screen WebView around the web player in assets/www.
 * The page is served from https://appassets.androidplatform.net so storage (IndexedDB, localStorage)
 * behaves like a normal secure origin; Google Fonts requests are answered from bundled files,
 * so the app works offline.
 */
public class MainActivity extends Activity {
    static final String HOST = "appassets.androidplatform.net";
    private static final int PICK_AUDIO = 41;
    static MainActivity instance;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        instance = this;
        Window w = getWindow();
        w.setStatusBarColor(0xFF101211);
        w.setNavigationBarColor(0xFF050606);

        web = new WebView(this);
        web.setBackgroundColor(0xFF101211);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setTextZoom(100);
        web.addJavascriptInterface(new Bridge(), "HvyliaAndroid");
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        setContentView(web);

        if (state != null) web.restoreState(state);
        else web.loadUrl("https://" + HOST + "/index.html");
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        // The page keeps one history entry while a layer (sheet, player, page) is open.
        if (web.canGoBack()) web.goBack();
        else moveTaskToBack(true); // keep the music playing, like other players
    }

    // Intentionally no web.onPause(): audio must keep playing while the app is in the background.

    @Override
    protected void onResume() {
        super.onResume();
        // Pick up MP3s downloaded from the YouTube tab while the page was not looking.
        js("window.__hvyliaCheckImports&&__hvyliaCheckImports()");
    }

    @Override
    protected void onDestroy() {
        if (instance == this) instance = null;
        stopService(new Intent(this, PlayerService.class));
        web.destroy();
        super.onDestroy();
    }

    /** Runs a snippet in the page (from any thread). */
    static void js(final String code) {
        final MainActivity a = instance;
        if (a == null) return;
        a.runOnUiThread(new Runnable() {
            @Override public void run() { if (a.web != null) a.web.evaluateJavascript(code, null); }
        });
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (request != PICK_AUDIO || fileCallback == null) { super.onActivityResult(request, result, data); return; }
        Uri[] picked = null;
        if (result == RESULT_OK && data != null) {
            ClipData clip = data.getClipData();
            if (clip != null && clip.getItemCount() > 0) {
                picked = new Uri[clip.getItemCount()];
                for (int i = 0; i < picked.length; i++) picked[i] = clip.getItemAt(i).getUri();
            } else if (data.getData() != null) {
                picked = new Uri[] { data.getData() };
            }
        }
        fileCallback.onReceiveValue(picked);
        fileCallback = null;
    }

    private class Chrome extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = callback;
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("audio/*");
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            try {
                startActivityForResult(Intent.createChooser(i, "Обери музику"), PICK_AUDIO);
            } catch (ActivityNotFoundException e) {
                fileCallback = null;
                return false;
            }
            return true;
        }
    }

    private class Client extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
            Uri u = req.getUrl();
            String host = u.getHost();
            if (HOST.equals(host)) {
                String path = u.getPath();
                if ("/__dl".equals(path)) return downloaded(u.getQueryParameter("u"));
                if (path == null || path.equals("/") || path.isEmpty()) path = "/index.html";
                return asset("www" + path);
            }
            if ("fonts.googleapis.com".equals(host)) return asset("www/fonts/fonts.css");
            return null;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            Uri u = req.getUrl();
            if (HOST.equals(u.getHost())) return false;
            try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (ActivityNotFoundException ignored) { }
            return true;
        }
    }

    private WebResourceResponse asset(String path) {
        if (path.contains("..")) return notFound();
        try {
            InputStream in = getAssets().open(path);
            Map<String, String> headers = new HashMap<>();
            headers.put("Cache-Control", "no-cache");
            headers.put("Access-Control-Allow-Origin", "*");
            String mime = mime(path);
            String enc = mime.startsWith("text/") || mime.endsWith("javascript") || mime.endsWith("json") ? "utf-8" : null;
            return new WebResourceResponse(mime, enc, 200, "OK", headers, in);
        } catch (IOException e) {
            return notFound();
        }
    }

    /** Streams an MP3 the YouTube tab saved, so the page can add it to its library. */
    private WebResourceResponse downloaded(String uri) {
        if (uri == null || !Store.isAllowed(this, uri)) return notFound();
        try {
            InputStream in = getContentResolver().openInputStream(Uri.parse(uri));
            if (in == null) return notFound();
            Map<String, String> headers = new HashMap<>();
            headers.put("Cache-Control", "no-store");
            return new WebResourceResponse("audio/mpeg", null, 200, "OK", headers, in);
        } catch (IOException | SecurityException e) {
            return notFound();
        }
    }

    private static WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", new HashMap<String, String>(), null);
    }

    private static String mime(String p) {
        String l = p.toLowerCase();
        if (l.endsWith(".html")) return "text/html";
        if (l.endsWith(".css")) return "text/css";
        if (l.endsWith(".js")) return "application/javascript";
        if (l.endsWith(".json") || l.endsWith(".webmanifest")) return "application/json";
        if (l.endsWith(".svg")) return "image/svg+xml";
        if (l.endsWith(".png")) return "image/png";
        if (l.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }

    /** Called from the page as window.HvyliaAndroid.* (on a background thread). */
    private class Bridge {
        @JavascriptInterface
        public void update(String id, String title, String artist, String album, boolean playing, double durationMs, double positionMs, double speed) {
            PlayerService.update(MainActivity.this, id, title, artist, album, playing, (long) durationMs, (long) positionMs, (float) speed);
        }

        @JavascriptInterface
        public void art(String id, String dataUrl) {
            PlayerService.art(id, dataUrl);
        }

        @JavascriptInterface
        public void clear() {
            PlayerService.clear(MainActivity.this);
        }

        @JavascriptInterface
        public void bar(final String hex) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    try { getWindow().setStatusBarColor(Color.parseColor(hex)); } catch (IllegalArgumentException ignored) { }
                }
            });
        }

        @JavascriptInterface
        public void openYouTube() {
            runOnUiThread(new Runnable() {
                @Override public void run() { startActivity(new Intent(MainActivity.this, YouTubeActivity.class)); }
            });
        }

        /** JSON array of {uri, name} for MP3s waiting to be added to the library; clears the queue. */
        @JavascriptInterface
        public String takePendingImports() {
            return Store.takePending(MainActivity.this);
        }

        @JavascriptInterface
        public String version() {
            return Build.VERSION.RELEASE;
        }
    }
}
