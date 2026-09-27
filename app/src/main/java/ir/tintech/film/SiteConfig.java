package ir.tintech.film;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

final class SiteConfig {
    private final SharedPreferences prefs;
    SiteConfig(Context context) { prefs = context.getSharedPreferences("connection", Context.MODE_PRIVATE); }

    List<String> addresses() {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        String custom = prefs.getString("sites", "").trim();
        String configured = custom.isEmpty() ? BuildConfig.APP_BASE_URL + "," + BuildConfig.APP_FALLBACK_URLS : custom;
        for (String raw : configured.split("[\\n,]")) {
            String normalized = UrlPolicy.website(raw);
            if (!normalized.isEmpty() && result.size() < 3) result.add(normalized);
        }
        if (result.isEmpty()) result.add(UrlPolicy.website(BuildConfig.APP_BASE_URL));
        return new ArrayList<>(result);
    }

    boolean trusted(String url) {
        String origin = UrlPolicy.origin(url);
        if (origin.isEmpty()) return false;
        for (String site : addresses()) if (origin.equals(UrlPolicy.origin(site))) return true;
        return false;
    }

    String customSites() { return prefs.getString("sites", ""); }
    void save(String list) { prefs.edit().putString("sites", list).apply(); }
}
