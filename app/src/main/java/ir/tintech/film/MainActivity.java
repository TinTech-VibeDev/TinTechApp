package ir.tintech.film;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Only explicitly configured HTTPS sites stay inside the app's WebView. No JavaScript bridge. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private ProgressBar progress;
    private View errorPanel;
    private SiteConfig sites;
    private LocalWebAssets localAssets;
    private ValueCallback<Uri[]> fileCallback;
    private static final int REQ_FILE = 1001;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile String activeOrigin = "";
    private String loadingUrl = "", tvScript = "";
    private int attempt;
    private boolean failed, destroyed;
    private Runnable timeout, pendingRetry;

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { handleBack(); }
        });
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        sites = new SiteConfig(this);
        localAssets = new LocalWebAssets(this);
        webView = findViewById(R.id.webView);
        progress = findViewById(R.id.progress);
        errorPanel = findViewById(R.id.connectionError);
        findViewById(R.id.homeButton).setOnClickListener(v -> loadHome());
        findViewById(R.id.refreshButton).setOnClickListener(v -> retryCurrent());
        findViewById(R.id.connectionButton).setOnClickListener(v -> showConnectionSettings());
        findViewById(R.id.retryButton).setOnClickListener(v -> retryCurrent());
        findViewById(R.id.errorSettingsButton).setOnClickListener(v -> showConnectionSettings());
        if (BuildConfig.IS_TV) {
            try (InputStream in = getAssets().open("tv-navigation.js")) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] b = new byte[4096]; int n;
                while ((n = in.read(b)) != -1) out.write(b, 0, n);
                tvScript = new String(out.toByteArray(), StandardCharsets.UTF_8);
            } catch (Exception ignored) {}
            for (int id : new int[]{R.id.homeButton, R.id.refreshButton, R.id.connectionButton}) {
                findViewById(id).setOnKeyListener((v, key, e) -> {
                    if (key == KeyEvent.KEYCODE_DPAD_DOWN && e.getAction() == KeyEvent.ACTION_DOWN && !failed) {
                        webView.requestFocus();
                        webView.evaluateJavascript("window.FilmBuffTV && FilmBuffTV.focusFirst()", null);
                        return true;
                    }
                    return false;
                });
            }
        }
        setupWebView();
        if (state == null || webView.restoreState(state) == null) loadIntent(getIntent());
        else {
            loadingUrl = webView.getUrl() == null ? sites.addresses().get(0) : webView.getUrl();
            activeOrigin = UrlPolicy.origin(loadingUrl);
        }
    }

    private void loadHome() { attempt = 0; load(sites.addresses().get(0)); }
    private void retryCurrent() {
        attempt = 0;
        load(sites.trusted(loadingUrl) ? loadingUrl : sites.addresses().get(0));
    }
    private void load(String url) {
        cancelTimeout();
        if (pendingRetry != null) handler.removeCallbacks(pendingRetry);
        pendingRetry = null;
        failed = false;
        errorPanel.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        loadingUrl = url;
        activeOrigin = UrlPolicy.origin(url);
        webView.loadUrl(url);
    }
    private void loadIntent(Intent intent) {
        String url = intent != null && intent.getData() != null ? intent.getData().toString() : "";
        if (!sites.trusted(url)) { loadHome(); return; }
        if ("/play".equals(Uri.parse(url).getPath())) { loadHome(); openInternalPlayer(Uri.parse(url)); }
        else { attempt = 0; load(url); }
    }
    private void cancelTimeout() { if (timeout != null) handler.removeCallbacks(timeout); timeout = null; }
    private void pageFailed(String url) {
        if (destroyed || failed || !url.equals(loadingUrl)) return;
        failed = true;
        cancelTimeout();
        webView.stopLoading();
        List<String> addresses = sites.addresses();
        if (++attempt < addresses.size()) {
            // Keep the route and signed query when switching to another address of the same service.
            Uri current = Uri.parse(loadingUrl), next = Uri.parse(addresses.get(attempt));
            String target = next.buildUpon().encodedPath(current.getEncodedPath())
                    .encodedQuery(current.getEncodedQuery()).encodedFragment(current.getEncodedFragment()).build().toString();
            pendingRetry = () -> { if (!destroyed && failed && loadingUrl.equals(url)) load(target); };
            handler.postDelayed(pendingRetry, 400);
        } else {
            progress.setVisibility(View.GONE);
            webView.setVisibility(View.INVISIBLE);
            errorPanel.setVisibility(View.VISIBLE);
            findViewById(R.id.retryButton).requestFocus();
        }
    }

    @SuppressLint("SetJavaScriptEnabled") private void setupWebView() {
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " FilmBuff/" + BuildConfig.VERSION_NAME + (BuildConfig.IS_TV ? " FilmBuffTV" : ""));
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) WebSettingsCompat.setSafeBrowsingEnabled(s, true);
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                if (!r.isForMainFrame()) return !UrlPolicy.isHttp(r.getUrl().toString());
                return handleUrl(r.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) { return handleUrl(Uri.parse(url)); }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return "GET".equals(r.getMethod()) ? localAssets.intercept(r.getUrl(), sites.trusted(r.getUrl().toString()), activeOrigin) : null;
            }
            @Override public void onPageStarted(WebView v, String url, Bitmap icon) {
                if (!sites.trusted(url)) { v.stopLoading(); openExternal(Uri.parse(url)); return; }
                cancelTimeout();
                loadingUrl = url; activeOrigin = UrlPolicy.origin(url); failed = false;
                progress.setVisibility(View.VISIBLE);
                timeout = () -> pageFailed(url);
                handler.postDelayed(timeout, 20000);
            }
            @Override public void onPageCommitVisible(WebView v, String url) {
                if (url.equals(loadingUrl) && !failed) cancelTimeout();
            }
            @Override public void onPageFinished(WebView v, String url) {
                if (!url.equals(loadingUrl) || failed || destroyed) return;
                cancelTimeout();
                progress.setVisibility(View.GONE);
                if (BuildConfig.IS_TV && sites.trusted(url)) v.evaluateJavascript(tvScript, null);
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) pageFailed(r.getUrl().toString());
            }
            @Override public void onReceivedError(WebView v, int code, String description, String url) {
                // Legacy callback is main-frame only; the modern callback above never retries images/scripts.
                pageFailed(url);
            }
            @Override public void onReceivedHttpError(WebView v, WebResourceRequest r, WebResourceResponse response) {
                if (r.isForMainFrame() && response.getStatusCode() >= 500) pageFailed(r.getUrl().toString());
            }
            @Override public void onReceivedSslError(WebView v, SslErrorHandler h, SslError e) {
                h.cancel();
                if (e.getUrl().equals(loadingUrl)) pageFailed(loadingUrl);
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                try { startActivityForResult(params.createIntent(), REQ_FILE); }
                catch (Exception e) { fileCallback = null; return false; }
                return true;
            }
        });
        webView.setDownloadListener((url, ua, cd, mime, len) -> openExternal(Uri.parse(url)));
    }

    private boolean handleUrl(Uri uri) {
        String url = uri.toString();
        if (UrlPolicy.isHttp(url) && ("1".equals(uri.getQueryParameter("download")) || "download".equals(uri.getQueryParameter("action")))) return openExternal(uri);
        if (sites.trusted(url)) {
            if ("/play".equals(uri.getPath())) return openInternalPlayer(uri);
            return false;
        }
        if (sites.trusted(webView.getUrl()) && UrlPolicy.isMedia(url)) return openInternalPlayer(uri);
        return openExternal(uri);
    }
    private boolean openInternalPlayer(Uri uri) {
        boolean route = sites.trusted(uri.toString()) && "/play".equals(uri.getPath());
        String video = route ? first(uri, "u", "url") : uri.toString();
        if (!UrlPolicy.isHttp(video)) { toast("لینک پخش معتبر نیست"); return true; }
        if (sites.trusted(video)) video = UrlPolicy.directVideo(video, UrlPolicy.origin(video));
        Intent intent = new Intent(this, PlayerActivity.class)
                .putExtra(PlayerActivity.EXTRA_VIDEO, video)
                .putExtra(PlayerActivity.EXTRA_ORIGIN, activeOrigin)
                .putExtra(PlayerActivity.EXTRA_TITLE, route ? uri.getQueryParameter("title") : "FilmBuff")
                .putExtra(PlayerActivity.EXTRA_SUB, route ? first(uri, "sub", "vtt", "srt") : null)
                .putExtra(PlayerActivity.EXTRA_NET, route ? uri.getQueryParameter("net") : null);
        startActivity(intent);
        return true;
    }
    private static String first(Uri uri, String... keys) {
        for (String key : keys) { String value = uri.getQueryParameter(key); if (value != null && !value.isEmpty()) return value; }
        return null;
    }
    private boolean openExternal(Uri uri) {
        try {
            Uri target = uri; String packageName = null;
            if ("intent".equalsIgnoreCase(uri.getScheme())) {
                Intent parsed = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
                target = parsed.getData(); packageName = parsed.getPackage();
            }
            if (target == null) return true;
            String scheme = target.getScheme() == null ? "" : target.getScheme().toLowerCase(java.util.Locale.ROOT);
            if (!java.util.Arrays.asList("https", "http", "vlc", "potplayer", "market", "tg", "telegram", "mailto", "tel", "sms").contains(scheme)) return true;
            Intent clean = new Intent(Intent.ACTION_VIEW, target).addCategory(Intent.CATEGORY_BROWSABLE);
            // Preserve the chosen external player, but never inherit components, selectors,
            // actions, extras or URI grants from an intent supplied by a web page.
            if (packageName != null && packageName.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+") && !packageName.equals(getPackageName())) clean.setPackage(packageName);
            startActivity(clean);
        } catch (Exception e) { toast("برنامهٔ لازم برای باز کردن این لینک پیدا نشد"); }
        return true;
    }
    private void showConnectionSettings() {
        EditText input = new EditText(this);
        input.setText(sites.customSites());
        input.setHint(BuildConfig.APP_BASE_URL);
        input.setTextDirection(View.TEXT_DIRECTION_LTR);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(3); input.setMaxLines(5);
        int p = (int)(20 * getResources().getDisplayMetrics().density);
        input.setPadding(p, p, p, p);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("آدرس‌های اتصال")
                .setMessage("آدرس اصلی و آدرس‌های جایگزین همین سایت را با https، هر کدام در یک خط وارد کنید. حداکثر ۳ آدرس؛ خالی = تنظیم پیش‌فرض.")
                .setView(input).setNegativeButton("انصراف", null).setPositiveButton("ذخیره و اتصال", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            String raw = input.getText().toString().trim();
            String[] entries = raw.isEmpty() ? new String[0] : raw.split("[\\n,]");
            if (entries.length > 3) { input.setError("حداکثر ۳ آدرس"); return; }
            for (String entry : entries) if (UrlPolicy.website(entry).isEmpty()) { input.setError("آدرس HTTPS معتبر وارد کنید"); return; }
            sites.save(raw); dialog.dismiss(); loadHome();
        }));
        dialog.show();
    }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); loadIntent(intent); }
    @Override protected void onSaveInstanceState(Bundle out) { webView.saveState(out); super.onSaveInstanceState(out); }
    @Override protected void onActivityResult(int request, int result, @Nullable Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQ_FILE && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data)); fileCallback = null;
        }
    }
    @Override public boolean onKeyDown(int key, KeyEvent event) {
        if (BuildConfig.IS_TV && key == KeyEvent.KEYCODE_MENU) { showConnectionSettings(); return true; }
        return super.onKeyDown(key, event);
    }
    private void handleBack() {
        if (BuildConfig.IS_TV && sites.trusted(webView.getUrl())) {
            webView.evaluateJavascript("Boolean(window.FilmBuffTV && FilmBuffTV.closeModal())", result -> {
                if (!"true".equals(result)) navigateBack();
            });
        } else navigateBack();
    }
    private void navigateBack() { if (webView.canGoBack()) webView.goBack(); else finish(); }
    @Override protected void onPause() { webView.onPause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if (webView != null) webView.onResume(); }
    @Override protected void onDestroy() {
        destroyed = true; handler.removeCallbacksAndMessages(null);
        if (fileCallback != null) { fileCallback.onReceiveValue(null); fileCallback = null; }
        if (webView != null) { ((ViewGroup)webView.getParent()).removeView(webView); webView.stopLoading(); webView.destroy(); }
        super.onDestroy();
    }
}
