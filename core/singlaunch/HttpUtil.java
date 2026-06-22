package singlaunch;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.function.DoubleConsumer;

public final class HttpUtil {
    private HttpUtil() {}

    public static String getString(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection connection = open(url, headers);
        connection.setRequestMethod("GET");
        int code = connection.getResponseCode();
        if (code >= 400) {
            throw new IOException("HTTP " + code);
        }
        try (InputStream in = connection.getInputStream()) {
            return new String(readBytes(in), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    public static HttpResult getWithHeaders(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection connection = open(url, headers);
        connection.setRequestMethod("GET");
        int code = connection.getResponseCode();
        if (code >= 400) {
            throw new IOException("HTTP " + code);
        }
        String body;
        try (InputStream in = connection.getInputStream()) {
            body = new String(readBytes(in), StandardCharsets.UTF_8);
        }
        String link = connection.getHeaderField("Link");
        connection.disconnect();
        return new HttpResult(body, link);
    }

    public static final class HttpResult {
        public final String body;
        public final String linkHeader;

        public HttpResult(String body, String linkHeader) {
            this.body = body;
            this.linkHeader = linkHeader;
        }
    }

    public static void download(String url, Path target, DoubleConsumer progress) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".part");
        HttpURLConnection connection = open(url, Map.of("User-Agent", "SingularityLauncher"));
        connection.setRequestMethod("GET");
        int code = connection.getResponseCode();
        if (code >= 400) {
            throw new IOException("HTTP " + code);
        }
        long total = connection.getContentLengthLong();
        try (InputStream in = connection.getInputStream(); OutputStream out = Files.newOutputStream(temp)) {
            byte[] buffer = new byte[8192];
            long done = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                done += read;
                if (progress != null && total > 0) progress.accept((double) done / total);
            }
        } finally {
            connection.disconnect();
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        if (progress != null) progress.accept(1.0);
    }

    public static byte[] readBytes(InputStream in) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static HttpURLConnection open(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(60000);
        if (headers != null) {
            for (var entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        return connection;
    }
}
