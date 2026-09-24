package ir.tintech.film;

import org.junit.Test;
import static org.junit.Assert.*;

public class UrlPolicyTest {
    @Test public void signedVideoSurvivesOuterEncodingExactly() {
        String media = "https://cdn.example/show/ep%201.m3u8?token=a%2Bb%26c&expires=123";
        String relay = UrlPolicy.relay(media, "https://film.example");
        assertEquals(media, UrlPolicy.query(relay, "u"));
        assertEquals(media, UrlPolicy.directVideo(relay, "https://film.example"));
        assertTrue(relay.endsWith("&force=1"));
    }
    @Test public void onlySameOriginProxyIsUnwrapped() {
        String url = "https://evil.example/api/proxy-dl?u=https%3A%2F%2Fcdn.example%2Fx.mp4";
        assertEquals(url, UrlPolicy.directVideo(url, "https://film.example"));
        assertNotEquals(UrlPolicy.origin("https://film.example.attacker.example"), UrlPolicy.origin("https://film.example"));
        assertEquals("https://film.example", UrlPolicy.origin("https://FILM.example:443/menu"));
    }
    @Test public void websiteSettingsRequireHttpsWithoutCredentials() {
        assertEquals("https://film.example/menu", UrlPolicy.website("https://film.example/"));
        for (String value : new String[]{"http://film.example", "javascript:alert(1)", "file:///sdcard/movie", "https://user:pass@film.example", "https://film.example/#x"}) assertEquals("", UrlPolicy.website(value));
    }
    @Test public void resolverSubtitlesAreVttAndMediaDetectionUsesPath() {
        assertEquals("text/vtt", UrlPolicy.subtitleMime("https://film.example/api/source-subtitle?u=x"));
        assertEquals("text/vtt", UrlPolicy.subtitleMime("https://film.example/api/player-subtitle?u=x"));
        assertEquals("text/x-ssa", UrlPolicy.subtitleMime("https://cdn.example/Farsi.ASS?key=x"));
        assertTrue(UrlPolicy.isMedia("https://cdn.example/show.MKV?token=1"));
        assertFalse(UrlPolicy.isMedia("https://evil.example/article?video=x.mp4"));
        assertFalse(UrlPolicy.isHttp("https://good.example@bad.example/a.mp4"));
    }
}
