package ir.tintech.film;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

/** WebView روی دامنه ورکر — بدون آپدیت خودکار APK */
public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private ValueCallback<Uri[]> fileCallback;
    private static final int REQ_FILE = 1001;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        swipe = findViewById(R.id.swipe);
        progress = findViewById(R.id.progress);

        View updateHint = findViewById(R.id.updateHint);
        if (updateHint != null) updateHint.setVisibility(View.GONE);

        setupWebView();
        setupSwipe();
        webView.loadUrl(resolveStartUrl());
    }

    private String resolveStartUrl() {
        Intent in = getIntent();
        if (in != null && Intent.ACTION_VIEW.equals(in.getAction()) && in.getData() != null) {
            return in.getData().toString();
        }
        String base = BuildConfig.APP_BASE_URL;
        if (base == null || base.trim().isEmpty()) {
            return "https://movie-search-bot.barmonn.workers.dev/menu";
        }
        return base.trim();
    }

    private void setupSwipe() {
        swipe.setColorSchemeColors(0xFFFF7A18, 0xFF36A9FF);
        swipe.setOnRefreshListener(() -> {
            webView.reload();
            new Handler(Looper.getMainLooper()).postDelayed(() -> swipe.setRefreshing(false), 1200);
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
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
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                swipe.setRefreshing(false);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                progress.setVisibility(View.GONE);
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
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "باز کردن لینک ممکن نشد", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean handleUrl(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "";
        String url = uri.toString();

        if (scheme.equals("intent") || scheme.equals("vlc") || scheme.equals("potplayer")
                || scheme.equals("market") || scheme.equals("tg") || scheme.equals("telegram")
                || scheme.equals("mailto") || scheme.equals("tel") || scheme.equals("sms")
                || url.startsWith("intent:")) {
            return openExternal(uri);
        }

        if (scheme.equals("http") || scheme.equals("https")) {
            String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
            String path = uri.getPath() != null ? uri.getPath() : "";

            // پلیر داخلی اپ — /play یا لینک مستقیم مدیا (CDN اصلی)
            if (host.equals("movie-search-bot.barmonn.workers.dev")
                    || host.endsWith(".barmonn.workers.dev")) {
                if ("/play".equals(path) || path.startsWith("/play")) {
                    return openInternalPlayer(uri);
                }
                return false; // بقیه صفحات ورکر داخل WebView
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
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
