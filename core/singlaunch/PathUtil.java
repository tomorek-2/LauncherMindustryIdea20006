package singlaunch;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

final class PathUtil {
    private PathUtil() {}

    static String defaultJava() {
        String home = System.getProperty("java.home");
        Path unix = Paths.get(home, "bin", "java");
        if (Files.isExecutable(unix)) return unix.toAbsolutePath().toString();
        Path win = Paths.get(home, "bin", "java.exe");
        if (Files.isExecutable(win)) return win.toAbsolutePath().toString();
        return "java";
    }
}
