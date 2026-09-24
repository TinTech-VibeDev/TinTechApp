package ir.tintech.film;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceResponse;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/** Fixed, bundled files only: no network proxy and no file access from untrusted paths. */
final class LocalWebAssets {
    private final Context context;
    LocalWebAssets(Context context) { this.context = context; }

    WebResourceResponse intercept(Uri uri, boolean trustedSite, String activeOrigin) {
        String host = uri.getHost(), path = uri.getPath();
        if (path == null || host == null || !"https".equals(uri.getScheme())) return null;
        String asset = null, mime = "application/javascript";
        boolean cdn = "cdn.jsdelivr.net".equalsIgnoreCase(host);
        if ((host.equalsIgnoreCase("telegram.org") && path.equals("/js/telegram-web-app.js")) || (trustedSite && path.equals("/assets/v273/telegram-web-app.js"))) asset = "telegram-web-app.js";
        if ((cdn && path.equals("/npm/hls.js@1.5.7/dist/hls.min.js")) || (trustedSite && path.equals("/assets/v273/hls-1.5.7.js"))) asset = "hls-1.5.7.js";
        if ((cdn && path.equals("/npm/hls.js@1.6.17/dist/hls.min.js")) || (trustedSite && path.equals("/assets/v273/hls-1.6.17.js"))) asset = "hls-1.6.17.js";
        if ((trustedSite && (path.equals("/__filmbuff_assets__/vazirmatn.woff2") || path.equals("/assets/v273/vazirmatn.woff2")))) { asset = "vazirmatn.woff2"; mime = "font/woff2"; }
        boolean fontCss = (cdn && path.equals("/gh/rastikerdar/vazirmatn@v33.003/Vazirmatn-font-face.css")) || (trustedSite && path.equals("/assets/v273/font.css"));
        try {
            if (fontCss && UrlPolicy.isHttp(activeOrigin)) {
                String css = "@font-face{font-family:Vazirmatn;src:url('" + activeOrigin + "/__filmbuff_assets__/vazirmatn.woff2') format('woff2');font-weight:100 900;font-style:normal;font-display:swap}";
                return response("text/css", new ByteArrayInputStream(css.getBytes(StandardCharsets.UTF_8)));
            }
            if (asset != null) return response(mime, context.getAssets().open("browser/" + asset));
        } catch (Exception ignored) {}
        return null;
    }

    private WebResourceResponse response(String mime, java.io.InputStream stream) {
        return new WebResourceResponse(mime, mime.startsWith("font/") ? null : "UTF-8", 200, "OK",
                Collections.singletonMap("Cache-Control", "public, max-age=31536000, immutable"), stream);
    }
}
