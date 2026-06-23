package singlaunch;

import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public final class JarLauncher {
    private static final String[] MAIN_CLASS_CANDIDATES = {
            "mindustry.desktop.DesktopLauncher",
            "io.anuke.mindustry.desktop.DesktopLauncher",
            "io.anuke.mindustry.Mindustry",
            "mindustry.core.Mindustry"
    };

    private JarLauncher() {}

    public static String detectMainClass(Path jar) throws IOException {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            Manifest manifest = jarFile.getManifest();
            if (manifest != null) {
                Attributes attributes = manifest.getMainAttributes();
                String mainClass = attributes.getValue(Attributes.Name.MAIN_CLASS);
                if (mainClass != null && !mainClass.isBlank() && hasClass(jarFile, mainClass)) {
                    return mainClass.trim();
                }
            }
            for (String candidate : MAIN_CLASS_CANDIDATES) {
                if (hasClass(jarFile, candidate)) return candidate;
            }
        }
        throw new IOException("Не найден главный класс в " + jar.getFileName());
    }

    private static boolean hasClass(JarFile jarFile, String className) {
        return jarFile.getEntry(className.replace('.', '/') + ".class") != null;
    }
}
