package singlaunch;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class LauncherLog {
    private static final int MAX_LINES = 2000;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final List<String> lines = new ArrayList<>();
    private static volatile Consumer<String> sink;

    private LauncherLog() {}

    public static void setSink(Consumer<String> listener) {
        sink = listener;
    }

    public static void info(String message) {
        append("INFO", message);
    }

    public static void warn(String message) {
        append("WARN", message);
    }

    public static void error(String message) {
        append("ERROR", message);
    }

    public static void error(String message, Throwable error) {
        append("ERROR", message + (error != null && error.getMessage() != null ? ": " + error.getMessage() : ""));
        if (error != null) {
            for (StackTraceElement frame : error.getStackTrace()) {
                append("ERROR", "  at " + frame);
                if (frame.toString().contains("singlaunch")) break;
            }
        }
    }

    public static synchronized String dump() {
        return String.join("\n", lines);
    }

    public static synchronized void clear() {
        lines.clear();
    }

    private static void append(String level, String message) {
        String line = LocalTime.now().format(TIME) + " [" + level + "] " + message;
        synchronized (lines) {
            lines.add(line);
            while (lines.size() > MAX_LINES) {
                lines.remove(0);
            }
        }
        Consumer<String> listener = sink;
        if (listener != null) {
            try {
                listener.accept(line);
            } catch (Exception ignored) {}
        }
    }
}
