package ir.tintech.film;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class SubtitleSupportTest {
    private static final String SRT = "1\r\n00:00:01,000 --> 00:00:04,000\r\nسلام دنیا\r\n";
    private SubtitleSupport.Document read(byte[] bytes) throws IOException { return SubtitleSupport.read(new ByteArrayInputStream(bytes)); }
    @Test public void utf8SrtIsNormalizedAndPersianSurvives() throws Exception {
        SubtitleSupport.Document d = read(("\uFEFF" + SRT).getBytes(StandardCharsets.UTF_8));
        assertEquals("application/x-subrip", d.mime); assertTrue(d.text.contains("سلام دنیا")); assertFalse(d.text.contains("\r")); assertFalse(d.text.contains("\uFEFF"));
    }
    @Test public void olderPersianAndUtf16EncodingsAreSupported() throws Exception {
        // Legacy CP1256 contains Arabic yeh; Persian yeh cannot be encoded in that charset.
        String legacy = SRT.replace('ی', 'ي');
        assertTrue(read(legacy.getBytes(Charset.forName("windows-1256"))).text.contains("سلام دنيا"));
        assertTrue(read(SRT.getBytes(StandardCharsets.UTF_16)).text.contains("سلام دنیا"));
    }
    @Test public void vttAndAssHaveCorrectMimeTypes() throws Exception {
        assertEquals("text/vtt", read("WEBVTT\n\n00:01.000 --> 00:04.000\nسلام\n".getBytes(StandardCharsets.UTF_8)).mime);
        assertEquals("text/x-ssa", read("[Script Info]\nScriptType: v4.00+\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\nDialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hello".getBytes(StandardCharsets.UTF_8)).mime);
    }
    @Test public void unrelatedOrOversizedFilesAreRejected() throws Exception {
        for (byte[] bytes : new byte[][]{new byte[0], "<html>Error 502</html>".getBytes(StandardCharsets.UTF_8), new byte[SubtitleSupport.MAX_BYTES + 1], (SRT + '\0').getBytes(StandardCharsets.UTF_8)}) {
            try { read(bytes); fail("Invalid subtitle accepted"); } catch (IOException expected) { assertNotNull(expected.getMessage()); }
        }
    }
}
