package com.reya.hvylia;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

/**
 * An in-app browser tab (YouTube, Spotify) in the player's style: the site in a WebView with sign-in,
 * a round «Скачати MP3» button on pages that have something to download, and the download sheet.
 * Sites like these are single-page apps, so address changes are tracked via doUpdateVisitedHistory and a
 * small script that reports history.pushState / replaceState / popstate, not only full page loads.
 */
abstract class BrowserActivity extends Activity {
    private static final int PICK_FOLDER = 61;
    private static final int ASK_NOTIFICATIONS = 62;
    private static final String HOOK = "(function(){if(window.__hvyHook)return;window.__hvyHook=1;"
            + "var last='';function send(){var u=location.href;if(u===last)return;last=u;try{HvyliaNav.onUrl(u)}catch(e){}}"
            + "['pushState','replaceState'].forEach(function(n){var o=history[n];history[n]=function(){var r=o.apply(this,arguments);setTimeout(send,0);return r}});"
            + "addEventListener('popstate',send);document.addEventListener('yt-navigate-finish',send);"
            + "setInterval(send,1000);send()})()";

    protected FrameLayout root;
    protected WebView web;
    private ImageButton fab;
    private View progress;
    private String currentUrl = "";
    private boolean fabShown;
    private DownloadSheet sheet;
    private View fullscreen;
    private WebChromeClient.CustomViewCallback fullscreenCallback;

    /** Page to open first. */
    protected abstract String startUrl();
    /** Colour of the bars around the site. */
    protected abstract int barColor();
    /** Whether the page at this address has something to download. */
    protected abstract boolean canDownload(String url);
    /** Called when the address changes (also for in-page navigation). */
    protected void onPageChanged(String url) { }
    /** «Скачати MP3» was tapped on a downloadable page. */
    protected abstract void onDownloadTapped(String url);
    /** Whether a link stays inside this tab; others open in the system browser. */
    protected boolean keepsInside(Uri u) { return true; }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(barColor());
        w.setNavigationBarColor(barColor());

        root = new FrameLayout(this);
        root.setBackgroundColor(barColor());

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        // Without the WebView marker Google and Spotify let you sign in and serve their normal mobile sites.
        s.setUserAgentString(s.getUserAgentString().replace("; wv", "").replaceAll(" Version/\\d+(\\.\\d+)*", ""));
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.addJavascriptInterface(new Bridge(), "HvyliaNav");
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progress = new View(this);
        progress.setBackgroundColor(Ui.ACCENT);
        progress.setPivotX(0);
        progress.setScaleX(0);
        root.addView(progress, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 3), Gravity.TOP));

        fab = new ImageButton(this);
        fab.setImageResource(R.drawable.ic_download);
        fab.setColorFilter(Ui.ACCENT_INK);
        fab.setBackground(Ui.oval(Ui.ACCENT));
        fab.setElevation(Ui.dp(this, 8));
        fab.setContentDescription("Скачати MP3");
        fab.setPadding(Ui.dp(this, 17), Ui.dp(this, 17), Ui.dp(this, 17), Ui.dp(this, 17));
        fab.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        fab.setVisibility(View.GONE);
        fab.setOnClickListener(v -> onDownloadTapped(currentUrl));
        final FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(Ui.dp(this, 62), Ui.dp(this, 62), Gravity.BOTTOM | Gravity.END);
        fl.rightMargin = Ui.dp(this, 18);
        fl.bottomMargin = Ui.dp(this, 96); // above the site's own bottom bar / mini player
        root.addView(fab, fl);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            fl.bottomMargin = Ui.dp(this, 96) + insets.getSystemWindowInsetBottom();
            fab.setLayoutParams(fl);
            return insets;
        });

        setContentView(root);
        if (state != null) web.restoreState(state);
        else web.loadUrl(startUrl());
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause(); // the site's own playback stops when you leave the tab
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (sheet != null && sheet.isShowing()) sheet.dismiss();
        web.destroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (fullscreen != null) { exitFullscreen(); return; }
        if (web.canGoBack()) web.goBack();
        else finish();
    }

    private void onUrl(String url) {
        if (url == null || url.equals(currentUrl)) return;
        currentUrl = url;
        onPageChanged(url);
        boolean show = canDownload(url);
        if (show != fabShown) { fabShown = show; showFab(show); }
    }

    private void showFab(boolean show) {
        fab.animate().cancel();
        if (show && fullscreen == null) {
            if (fab.getVisibility() != View.VISIBLE) { fab.setScaleX(0.4f); fab.setScaleY(0.4f); fab.setAlpha(0f); }
            fab.setVisibility(View.VISIBLE);
            fab.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(320)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).start();
        } else if (fab.getVisibility() == View.VISIBLE) {
            fab.animate().scaleX(0.4f).scaleY(0.4f).alpha(0f).setDuration(180)
                    .withEndAction(() -> fab.setVisibility(View.GONE)).start();
        }
    }

    protected void openSheet(DownloadSheet.Model m) {
        if (sheet != null && sheet.isShowing()) sheet.dismiss();
        sheet = new DownloadSheet(this, m);
        sheet.show();
    }

    // ---------------------------------------------------------------- folder & permissions

    void pickFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try {
            startActivityForResult(i, PICK_FOLDER);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "На цьому пристрої немає вибору папок.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (request != PICK_FOLDER) { super.onActivityResult(request, result, data); return; }
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri tree = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(tree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException e) {
            Toast.makeText(this, "Не вдалося отримати доступ до цієї папки. Обери іншу.", Toast.LENGTH_LONG).show();
            return;
        }
        Store.setTree(this, tree);
        if (sheet != null && sheet.isShowing()) sheet.onFolderChosen();
    }

    void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, ASK_NOTIFICATIONS);
        }
    }

    // ---------------------------------------------------------------- web

    private final class Bridge {
        @JavascriptInterface
        public void onUrl(final String url) {
            runOnUiThread(() -> BrowserActivity.this.onUrl(url));
        }
    }

    private final class Client extends WebViewClient {
        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            onUrl(url);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            onUrl(url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            onUrl(url);
            view.evaluateJavascript(HOOK, null);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            Uri u = req.getUrl();
            String scheme = u.getScheme() == null ? "" : u.getScheme();
            if (scheme.equals("http") || scheme.equals("https")) {
                if (keepsInside(u)) return false;
            } else if (scheme.equals("intent") || scheme.equals("vnd.youtube") || scheme.equals("spotify") || scheme.equals("market")) {
                return true; // "open in the app" prompts: stay here
            }
            try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (ActivityNotFoundException ignored) { }
            return true;
        }
    }

    private final class Chrome extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int p) {
            progress.animate().cancel();
            progress.setAlpha(1f);
            progress.animate().scaleX(p / 100f).setDuration(150).start();
            if (p >= 100) progress.animate().alpha(0f).setStartDelay(250).setDuration(250).withEndAction(() -> progress.setScaleX(0)).start();
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (fullscreen != null) { callback.onCustomViewHidden(); return; }
            fullscreen = view;
            fullscreenCallback = callback;
            root.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            fab.setVisibility(View.GONE);
            if (Build.VERSION.SDK_INT >= 30) {
                getWindow().getInsetsController().hide(WindowInsets.Type.systemBars());
            } else {
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
        }

        @Override
        public void onHideCustomView() {
            exitFullscreen();
        }
    }

    private void exitFullscreen() {
        if (fullscreen == null) return;
        root.removeView(fullscreen);
        fullscreen = null;
        if (fullscreenCallback != null) fullscreenCallback.onCustomViewHidden();
        fullscreenCallback = null;
        if (Build.VERSION.SDK_INT >= 30) getWindow().getInsetsController().show(WindowInsets.Type.systemBars());
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        if (fabShown) showFab(true);
    }
}
