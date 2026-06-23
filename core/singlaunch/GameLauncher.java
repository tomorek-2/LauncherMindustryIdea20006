package singlaunch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GameLauncher {

    public Process launch(InstanceInfo instance, GameVersion version, LauncherSettings settings, Path gameJar) throws IOException {
        if (!Files.isRegularFile(gameJar)) {
            throw new IOException("JAR не найден: " + gameJar.toAbsolutePath());
        }

        Path dataDir = InstanceManager.dataDir(instance);
        Files.createDirectories(dataDir);
        Files.createDirectories(dataDir.resolve("mods"));
        Files.createDirectories(dataDir.resolve("saves"));

        String mainClass = JarLauncher.detectMainClass(gameJar);
        Path absoluteJar = gameJar.toAbsolutePath().normalize();

        List<String> cmd = new ArrayList<>();
        cmd.add(settings.resolveJavaPath());
        cmd.add("-Xms" + settings.minMemoryMb + "m");
        cmd.add("-Xmx" + settings.maxMemoryMb + "m");
        cmd.add("-Dmindustry.data.dir=" + dataDir.toAbsolutePath());
        cmd.add("-Dfile.encoding=UTF-8");

        if (settings.extraJvmArgs != null && !settings.extraJvmArgs.isBlank()) {
            for (String arg : settings.extraJvmArgs.trim().split("\\s+")) {
                if (!arg.isBlank()) cmd.add(arg);
            }
        }

        cmd.add("-cp");
        cmd.add(absoluteJar.toString());
        cmd.add(mainClass);

        LauncherLog.info("Запуск " + version.name + " (" + version.id + ")");
        LauncherLog.info("JAR: " + absoluteJar + " (" + Files.size(absoluteJar) + " байт)");
        LauncherLog.info("Main: " + mainClass);
        LauncherLog.info("Data: " + dataDir.toAbsolutePath());
        LauncherLog.info("Cmd: " + String.join(" ", cmd));

        ProcessBuilder builder = new ProcessBuilder(cmd);
        builder.directory(absoluteJar.getParent().toFile());
        Process process = builder.start();
        ProcessLogReader.attach(process, "game");
        return process;
    }
}
