package singlaunch;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class LauncherPaths {
    private static final Path ROOT = Paths.get(
            System.getProperty("singularity.launcher.dir",
                    System.getenv().getOrDefault("SINGULARITY_LAUNCHER_DIR",
                            Paths.get(System.getProperty("user.home"), ".singularity-launcher").toString())));

    private LauncherPaths() {}

    public static Path root() {
        return ROOT;
    }

    public static Path configFile() {
        return ROOT.resolve("config.json");
    }

    public static Path instancesDir() {
        return ROOT.resolve("instances");
    }

    public static Path versionsCacheDir() {
        return ROOT.resolve("cache").resolve("versions");
    }

    public static Path legacyVersionsDir() {
        return Paths.get("versions");
    }

    public static void ensureDirs() {
        ROOT.toFile().mkdirs();
        instancesDir().toFile().mkdirs();
        versionsCacheDir().toFile().mkdirs();
    }

    public static boolean isDevLayout() {
        return legacyVersionsDir().toFile().isDirectory()
                && legacyVersionsDir().resolve("MindustryV158.1.jar").toFile().exists()
                || new File("index.html").exists();
    }
}
