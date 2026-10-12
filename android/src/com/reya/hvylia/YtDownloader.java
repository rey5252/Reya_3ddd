package com.reya.hvylia;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** NewPipeExtractor's network layer, on plain HttpURLConnection. */
final class YtDownloader extends Downloader {
    /** Latest Firefox ESR, the same one NewPipeExtractor uses. */
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:153.0) Gecko/20100101 Firefox/153.0";

    private static boolean initialised;

    private YtDownloader() { }

    static synchronized void ensureInit() {
        if (initialised) return;
        NewPipe.init(new YtDownloader(), new Localization("uk", "UA"), new ContentCountry("UA"));
        initialised = true;
    }

    /** Headers a googlevideo.com stream URL expects, depending on which YouTube client produced it. */
    static void streamHeaders(HttpURLConnection c, String url) {
        if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) {
            c.setRequestProperty("User-Agent", YoutubeParsingHelper.getVisionOsUserAgent(null));
        } else {
            c.setRequestProperty("User-Agent", USER_AGENT);
            if (YoutubeParsingHelper.isWebStreamingUrl(url)) {
                c.setRequestProperty("Origin", "https://www.youtube.com");
                c.setRequestProperty("Referer", "https://www.youtube.com/");
            }
        }
    }

    @Override
    public Response execute(Request request) throws IOException, ReCaptchaException {
        HttpURLConnection c = (HttpURLConnection) new URL(request.url()).openConnection();
        try {
            c.setConnectTimeout(20000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(true);
            c.setRequestMethod(request.httpMethod());
            c.setRequestProperty("User-Agent", USER_AGENT);
            for (Map.Entry<String, List<String>> h : request.headers().entrySet()) {
                boolean first = true;
                for (String v : h.getValue()) {
                    if (first) c.setRequestProperty(h.getKey(), v);
                    else c.addRequestProperty(h.getKey(), v);
                    first = false;
                }
            }
            byte[] data = request.dataToSend();
            if (data != null) {
                c.setDoOutput(true);
                c.setFixedLengthStreamingMode(data.length);
                OutputStream os = c.getOutputStream();
                try { os.write(data); } finally { os.close(); }
            }
            int code = c.getResponseCode();
            if (code == 429) throw new ReCaptchaException("reCaptcha Challenge requested", request.url());
            String message = c.getResponseMessage();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String body = null;
            if (in != null) {
                try {
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    byte[] b = new byte[16384];
                    for (int n; (n = in.read(b)) > 0; ) buf.write(b, 0, n);
                    body = new String(buf.toByteArray(), StandardCharsets.UTF_8);
                } finally { in.close(); }
            }
            Map<String, List<String>> headers = new HashMap<>();
            for (Map.Entry<String, List<String>> h : c.getHeaderFields().entrySet()) {
                if (h.getKey() != null) headers.put(h.getKey(), h.getValue());
            }
            return new Response(code, message, headers, body, c.getURL().toString());
        } finally {
            c.disconnect();
        }
    }
}
