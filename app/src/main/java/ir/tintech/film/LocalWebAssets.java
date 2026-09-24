package ir.tintech.film;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceResponse;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

final class LocalWebAssets {
    private final Context context;
    LocalWebAssets(Context context) { this.context = context; }

    WebResourceResponse intercept(Uri url) {
        if (!"https".equalsIgnoreCase(url.getScheme())) return null;
        String host = url.getHost(), path = url.getPath();
        if (host == null || path == null) return null;
        String file = null, mime = "application/javascript";
        if (host.equalsIgnoreCase("telegram.org") && path.equals("/js/telegram-web-app.js"))
            file = "telegram-web-app.js";
        if (host.equalsIgnoreCase("cdn.jsdelivr.net") && path.equals("/npm/hls.js@1.5.7/dist/hls.min.js"))
            file = "hls-1.5.7.js";
        if (host.equalsIgnoreCase("cdn.jsdelivr.net") && path.equals("/gh/rastikerdar/vazirmatn@v33.003/Vazirmatn-font-face.css")) {
            String css = "@font-face{font-family:Vazirmatn;src:url('https://cdn.jsdelivr.net/__filmbuff__/vazirmatn.woff2') format('woff2');font-weight:100 900;font-style:normal;font-display:swap}";
            return response("text/css", new ByteArrayInputStream(css.getBytes(StandardCharsets.UTF_8)));
        }
        if (host.equalsIgnoreCase("cdn.jsdelivr.net") && path.equals("/__filmbuff__/vazirmatn.woff2")) {
            file = "vazirmatn.woff2"; mime = "font/woff2";
        }
        if (file == null) return null;
        try { return response(mime, context.getAssets().open("browser/" + file)); }
        catch (Exception ignored) { return null; }
    }

    private WebResourceResponse response(String mime, InputStream body) {
        return new WebResourceResponse(mime, mime.startsWith("font/") ? null : "UTF-8", body);
    }
}
