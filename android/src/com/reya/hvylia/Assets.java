package com.reya.hvylia;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Serves the bundled web pages (assets/www) at https://appassets.androidplatform.net, so storage behaves like a
 * normal secure origin, and answers Google Fonts requests with the bundled fonts, so the pages work offline.
 */
final class Assets {
    static final String HOST = "appassets.androidplatform.net";

    private Assets() { }

    /** A response for requests the pages make to the app host or Google Fonts; null for everything else. */
    static WebResourceResponse serve(Context c, Uri u) {
        String host = u.getHost();
        if (HOST.equals(host)) {
            String path = u.getPath();
            if (path == null || path.equals("/") || path.isEmpty()) path = "/index.html";
            return file(c, "www" + path);
        }
        if ("fonts.googleapis.com".equals(host)) return file(c, "www/fonts/fonts.css");
        return null;
    }

    static WebResourceResponse file(Context c, String path) {
        if (path.contains("..")) return notFound();
        try {
            InputStream in = c.getAssets().open(path);
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

    static WebResourceResponse notFound() {
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
}
