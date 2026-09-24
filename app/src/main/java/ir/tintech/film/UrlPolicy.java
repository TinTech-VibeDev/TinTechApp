package ir.tintech.film;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** URL identity stays independent of Android and can be tested on the JVM. */
final class UrlPolicy {
    private UrlPolicy() {}

    static boolean isHttp(String value) {
        if (value == null || value.length() > 65536) return false;
        try {
            URI u = new URI(value);
            return ("https".equalsIgnoreCase(u.getScheme()) || "http".equalsIgnoreCase(u.getScheme()))
                    && u.getHost() != null && u.getRawUserInfo() == null;
        } catch (Exception e) { return false; }
    }

    static String origin(String value) {
        if (!isHttp(value)) return "";
        URI u = URI.create(value);
        String scheme = u.getScheme().toLowerCase(Locale.ROOT);
        int port = u.getPort();
        String suffix = port < 0 || (port == 443 && scheme.equals("https")) || (port == 80 && scheme.equals("http")) ? "" : ":" + port;
        return scheme + "://" + u.getHost().toLowerCase(Locale.ROOT) + suffix;
    }

    static String website(String value) {
        if (value == null) return "";
        value = value.trim();
        if (!isHttp(value) || !"https".equalsIgnoreCase(URI.create(value).getScheme()) || URI.create(value).getRawFragment() != null) return "";
        URI u = URI.create(value);
        if (u.getRawPath() == null || u.getRawPath().isEmpty() || u.getRawPath().equals("/")) return origin(value) + "/menu";
        return value;
    }

    static String query(String value, String name) {
        try {
            String q = URI.create(value).getRawQuery();
            if (q == null) return null;
            for (String pair : q.split("&")) {
                String[] kv = pair.split("=", 2);
                if (URLDecoder.decode(kv[0], "UTF-8").equals(name)) return kv.length == 2 ? URLDecoder.decode(kv[1], "UTF-8") : "";
            }
        } catch (Exception ignored) {}
        return null;
    }

    static String mediaPath(String value) {
        try { return URI.create(value).getPath().toLowerCase(Locale.ROOT); } catch (Exception e) { return ""; }
    }

    static boolean isMedia(String value) {
        return isHttp(value) && mediaPath(value).matches(".*\\.(?:mp4|mkv|m3u8|webm|m4v|mov|avi)$");
    }

    static String directVideo(String value, String appOrigin) {
        if (!isHttp(value)) return "";
        if (origin(value).equals(appOrigin) && mediaPath(value).equals("/api/proxy-dl")) {
            String inner = query(value, "u");
            if (inner == null) inner = query(value, "url");
            if (isHttp(inner)) return inner;
        }
        return value;
    }

    static String relay(String video, String appOrigin) {
        if (!isHttp(video) || !isHttp(appOrigin) || !appOrigin.startsWith("https://")) return "";
        try { return origin(appOrigin) + "/api/proxy-dl?u=" + URLEncoder.encode(video, "UTF-8") + "&force=1"; }
        catch (Exception e) { return ""; }
    }

    static String routeMode(String value) {
        return "direct".equals(value) || "relay".equals(value) ? value : "auto";
    }

    static String subtitleMime(String url) {
        String p = mediaPath(url);
        if (p.endsWith(".vtt") || p.equals("/api/source-subtitle") || p.equals("/api/player-subtitle")
                || "vtt".equalsIgnoreCase(query(url, "format"))) return "text/vtt";
        if (p.endsWith(".ass") || p.endsWith(".ssa")) return "text/x-ssa";
        return "application/x-subrip";
    }
}
