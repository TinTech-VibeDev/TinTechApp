package ir.tintech.film;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

final class SubtitleSupport {
    static final int MAX_BYTES = 4 * 1024 * 1024;
    static final class Document {
        final String text, mime, extension;
        Document(String text, String mime, String extension) { this.text = text; this.mime = mime; this.extension = extension; }
    }
    private SubtitleSupport() {}

    static Document read(InputStream input) throws IOException {
        if (input == null) throw new IOException("فایل قابل خواندن نیست");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] block = new byte[8192];
        int count;
        while ((count = input.read(block)) != -1) {
            if (out.size() + count > MAX_BYTES) throw new IOException("حجم زیرنویس باید کمتر از ۴ مگابایت باشد");
            out.write(block, 0, count);
        }
        byte[] bytes = out.toByteArray();
        if (bytes.length == 0) throw new IOException("فایل خالی است");
        String text;
        if (bytes.length > 1 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) {
            text = new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        } else if (bytes.length > 1 && bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
            text = new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        } else {
            try { text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
            catch (CharacterCodingException e) { text = new String(bytes, Charset.forName("windows-1256")); }
        }
        text = text.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n');
        if (text.indexOf('\0') >= 0) throw new IOException("فایل متنی زیرنویس معتبر نیست");
        String trim = text.trim();
        if (trim.startsWith("WEBVTT") && trim.contains("-->")) return new Document(text, "text/vtt", ".vtt");
        if (Pattern.compile("(?m)^\\s*(?:\\d{1,3}:)?\\d{2}:\\d{2}[,.]\\d{1,3}\\s*-->\\s*").matcher(text).find()) {
            return new Document(text, "application/x-subrip", ".srt");
        }
        if (trim.toLowerCase(Locale.ROOT).contains("[events]") && Pattern.compile("(?im)^Dialogue\\s*:").matcher(text).find()) {
            return new Document(text, "text/x-ssa", ".ass");
        }
        throw new IOException("یک زیرنویس SRT، VTT، ASS یا SSA انتخاب کنید");
    }
}
