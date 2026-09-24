package ir.tintech.film;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.pm.ResolveInfo;
import android.webkit.WebResourceResponse;
import java.util.List;
import java.util.ArrayList;
import android.webkit.WebResourceError;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

/** WebView روی دامنه ورکر — بدون آپدیت خودکار APK */
public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progress;
    private ValueCallback<Uri[]> fileCallback;
    private static final int REQ_FILE = 1001;
    private final Handler retryHandler = new Handler(Looper.getMainLooper());
    private final List<String> sites = new ArrayList<>();
    private int siteIndex = 0;
    private boolean mainPageFailed = false;
    private final Runnable pageTimeout = () -> {
        if (webView != null && progress.getVisibility() == View.VISIBLE) { mainPageFailed = true; tryNextSite(); }
    };
    private final LocalWebAssets localAssets = new LocalWebAssets(this);
    private static final String DOWNLOAD_HOOK = "(function(){var t=window.Telegram&&window.Telegram.WebApp;if(!t||t.__fbDl)return;t.__fbDl=true;var o=t.openLink;t.openLink=function(u,opt){try{var p=new URL(u,location.href);if(p.origin===location.origin&&p.pathname==='/go'&&p.searchParams.get('mode')==='browser'){location.href=p.href;return;}}catch(e){}return o.call(this,u,opt);};})();";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        progress = findViewById(R.id.progress);

        View updateHint = findViewById(R.id.updateHint);
        if (updateHint != null) updateHint.setVisibility(View.GONE);

        configureSites();
        setupWebView();
        loadHome();
    }

    private void configureSites() {
        sites.add(BuildConfig.APP_BASE_URL.trim());
        for (String site : BuildConfig.APP_FALLBACK_URLS.split(",")) {
            site = site.trim();
            if (!site.isEmpty() && !sites.contains(site)) sites.add(site);
        }
    }

    private void loadHome() {
        siteIndex = 0;
        Intent in = getIntent();
        String start = in != null && Intent.ACTION_VIEW.equals(in.getAction()) && in.getData() != null
                ? in.getData().toString() : sites.get(0);
        if (isBrowserDownload(Uri.parse(start))) {
            webView.loadUrl(sites.get(0));
            openDownloadInBrowser(Uri.parse(start));
        } else webView.loadUrl(start);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getData() != null && !handleUrl(intent.getData())) webView.loadUrl(intent.getData().toString());
    }

    private void tryNextSite() {
        retryHandler.removeCallbacks(pageTimeout);
        if (!mainPageFailed || webView == null) return;
        if (siteIndex + 1 < sites.size()) {
            siteIndex++;
            mainPageFailed = false;
            webView.stopLoading();
            webView.loadUrl(sites.get(siteIndex));
        } else {
            progress.setVisibility(View.GONE);
            Toast.makeText(this, "سایت در دسترس نیست؛ اتصال یا آدرس جایگزین را بررسی کن", Toast.LENGTH_LONG).show();
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        // FilmBuff only needs content:// access for the system file picker.
        // File URL access and mixed HTTP content are intentionally disabled.
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " FilmBuff/" + BuildConfig.VERSION_NAME);

        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(s, true);
        }

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return localAssets.intercept(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(Uri.parse(url));
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                mainPageFailed = false;
                retryHandler.removeCallbacks(pageTimeout);
                retryHandler.postDelayed(pageTimeout, 15000);
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                if (url.equals(view.getUrl())) retryHandler.removeCallbacks(pageTimeout);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!url.equals(view.getUrl())) return;
                Uri loaded = Uri.parse(url);
                if (isSiteHost(loaded.getHost() == null ? "" : loaded.getHost())) view.evaluateJavascript(DOWNLOAD_HOOK, null);
                retryHandler.removeCallbacks(pageTimeout);
                if (!mainPageFailed) progress.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (failingUrl != null && failingUrl.equals(view.getUrl())) {
                    mainPageFailed = true;
                    tryNextSite();
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame() && request.getUrl().toString().equals(view.getUrl())) {
                    mainPageFailed = true;
                    tryNextSite();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = filePathCallback;
                try {
                    startActivityForResult(fileChooserParams.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                openDownloadInBrowser(Uri.parse(url));
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "باز کردن لینک ممکن نشد", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean handleUrl(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "";
        String url = uri.toString();

        if (scheme.equals("intent")) {
            try {
                Intent parsed = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                String fallback = parsed.getStringExtra("browser_fallback_url");
                Uri target = fallback != null ? Uri.parse(fallback) : parsed.getData();
                if (isBrowserDownload(target)) return openDownloadInBrowser(target);
            } catch (Exception ignored) {}
        }
        if (isBrowserDownload(uri)) return openDownloadInBrowser(uri);

        if (scheme.equals("intent") || scheme.equals("vlc") || scheme.equals("potplayer")
                || scheme.equals("market") || scheme.equals("tg") || scheme.equals("telegram")
                || scheme.equals("mailto") || scheme.equals("tel") || scheme.equals("sms")
                || url.startsWith("intent:")) {
            return openExternal(uri);
        }

        if (scheme.equals("http") || scheme.equals("https")) {
            String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
            String path = uri.getPath() != null ? uri.getPath() : "";

            if (isDownloadRequest(uri)) {
                return openDownloadInBrowser(uri);
            }

            if (isSiteHost(host)) {
                if ("/play".equals(path) || path.startsWith("/play")) {
                    return openInternalPlayer(uri);
                }
                return false;
            }

            if (PlayerActivity.isDirectMedia(url)
                    || host.contains("abrtech")
                    || (host.contains("cdn") && (url.contains(".mp4") || url.contains(".mkv") || url.contains("m3u8")))) {
                return openInternalPlayer(uri);
            }

            if (host.contains("sub-api")) {
                return openExternal(uri); // دانلود زیرنویس
            }

            // Do not keep arbitrary third-party pages inside FilmBuff's privileged WebView.
            // Top-level external navigation opens in the user's browser.
            return openExternal(uri);
        }
        return openExternal(uri);
    }


    private boolean isSiteHost(String host) {
        for (String site : sites) {
            String siteHost = Uri.parse(site).getHost();
            if (siteHost != null && siteHost.equalsIgnoreCase(host)) return true;
        }
        return "movie-search-bot.barmonn.workers.dev".equalsIgnoreCase(host);
    }

    private boolean isBrowserDownload(Uri uri) {
        return uri != null && "https".equalsIgnoreCase(uri.getScheme())
                && isSiteHost(uri.getHost() == null ? "" : uri.getHost())
                && "/go".equals(uri.getPath())
                && "browser".equalsIgnoreCase(uri.getQueryParameter("mode"));
    }

    private boolean openDownloadInBrowser(Uri link) {
        Intent browser = new Intent(Intent.ACTION_VIEW, link);
        browser.addCategory(Intent.CATEGORY_BROWSABLE);
        List<ResolveInfo> handlers = getPackageManager().queryIntentActivities(browser, android.content.pm.PackageManager.GET_RESOLVED_FILTER);
        String[] browsers = { "com.android.chrome", "org.mozilla.firefox", "com.sec.android.app.sbrowser", "com.brave.browser", "com.microsoft.emmx", "com.google.android.apps.chrome" };
        for (String preferred : browsers) {
            for (ResolveInfo info : handlers) {
                if (info.activityInfo == null || !preferred.equals(info.activityInfo.packageName)) continue;
                // Never send the website's download route back into FilmBuff or a video app.
                browser.setClassName(info.activityInfo.packageName, info.activityInfo.name);
                try { startActivity(browser); return true; } catch (Exception ignored) {}
            }
        }
        for (ResolveInfo info : handlers) {
            if (info.activityInfo == null || info.filter == null || info.filter.countDataAuthorities() != 0 || info.filter.countDataTypes() != 0
                    || getPackageName().equals(info.activityInfo.packageName)) continue;
            browser.setClassName(info.activityInfo.packageName, info.activityInfo.name);
            try { startActivity(browser); return true; } catch (Exception ignored) {}
        }
        Toast.makeText(this, "برای دانلود مستقیم مرورگر نصب کن", Toast.LENGTH_LONG).show();
        return true;
    }

    private boolean isDownloadRequest(Uri uri) {
        String full = uri.toString().toLowerCase();
        String path = uri.getPath() != null ? uri.getPath().toLowerCase() : "";
        String action = uri.getQueryParameter("action");
        return path.contains("download")
                || full.contains("download=1")
                || full.contains("direct_download")
                || full.contains("download_direct")
                || "download".equalsIgnoreCase(action);
    }

    /** پخش با پلیر داخلی — لینک CDN مستقیم (نه مرورگر) */
    private boolean openInternalPlayer(Uri uri) {
        try {
            String video = null;
            String sub = null;
            String title = null;
            String path = uri.getPath() != null ? uri.getPath() : "";

            if ("/play".equals(path) || path.startsWith("/play")) {
                video = uri.getQueryParameter("u");
                if (video == null) video = uri.getQueryParameter("url");
                sub = uri.getQueryParameter("sub");
                if (sub == null) sub = uri.getQueryParameter("vtt");
                if (sub == null) sub = uri.getQueryParameter("srt");
                title = uri.getQueryParameter("title");
            } else {
                video = uri.toString();
            }

            if (video == null || video.isEmpty()) {
                Toast.makeText(this, "لینک پخش پیدا نشد", Toast.LENGTH_SHORT).show();
                return true;
            }

            Intent i = new Intent(this, PlayerActivity.class);
            i.putExtra(PlayerActivity.EXTRA_VIDEO, video);
            if (sub != null && !sub.isEmpty()) i.putExtra(PlayerActivity.EXTRA_SUB, sub);
            if (title != null && !title.isEmpty()) i.putExtra(PlayerActivity.EXTRA_TITLE, title);
            startActivity(i);
            return true;
        } catch (Exception e) {
            Toast.makeText(this, "باز کردن پلیر ممکن نشد", Toast.LENGTH_SHORT).show();
            return true;
        }
    }

    private boolean openExternal(Uri uri) {
        try {
            Intent intent;
            if ("intent".equalsIgnoreCase(uri.getScheme())) {
                intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
            } else {
                intent = new Intent(Intent.ACTION_VIEW, uri);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "اپلیکیشن مورد نیاز نصب نیست", Toast.LENGTH_SHORT).show();
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQ_FILE) {
            if (fileCallback == null) return;
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        retryHandler.removeCallbacksAndMessages(null);
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
