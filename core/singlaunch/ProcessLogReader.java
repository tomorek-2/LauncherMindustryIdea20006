package singlaunch;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class ProcessLogReader {
    private ProcessLogReader() {}

    public static void attach(Process process, String prefix) {
        Thread out = new Thread(() -> read(process.getInputStream(), prefix), "proc-out");
        Thread err = new Thread(() -> read(process.getErrorStream(), prefix + " err"), "proc-err");
        out.setDaemon(true);
        err.setDaemon(true);
        out.start();
        err.start();
    }

    private static void read(InputStream stream, String prefix) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LauncherLog.info("[" + prefix + "] " + line);
            }
        } catch (Exception ignored) {}
    }
}
