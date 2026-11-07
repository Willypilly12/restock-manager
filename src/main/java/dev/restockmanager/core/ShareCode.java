package dev.restockmanager.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Turns a settings JSON string into a short copy-paste code and back. Decoding is size-limited. */
public final class ShareCode {
    public static final String PREFIX = "RSM1:";
    private static final int MAX_CODE = 100_000, MAX_JSON = 400_000;

    private ShareCode() { }

    public static String encode(String json) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(bos)) { gz.write(json.getBytes(StandardCharsets.UTF_8)); }
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bos.toByteArray());
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    /** @throws IllegalArgumentException if the text is not a valid code */
    public static String decode(String code) {
        if (code == null) throw new IllegalArgumentException("empty");
        String c = code.replaceAll("\\s+", "");
        if (!c.startsWith(PREFIX)) throw new IllegalArgumentException("not a Restock Manager code");
        if (c.length() > MAX_CODE) throw new IllegalArgumentException("code too long");
        try {
            byte[] gz = Base64.getUrlDecoder().decode(c.substring(PREFIX.length()));
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096]; int n, total = 0;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_JSON) throw new IllegalArgumentException("code too large");
                    out.write(buf, 0, n);
                }
                return out.toString(StandardCharsets.UTF_8);
            }
        } catch (IOException e) { throw new IllegalArgumentException("corrupt code"); }
    }
}
