package singlaunch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InstanceManager {
    private static final Gson GSON = new GsonBuilder().create();

    public List<InstanceInfo> list() {
        List<InstanceInfo> result = new ArrayList<>();
        Path dir = LauncherPaths.instancesDir();
        if (!Files.isDirectory(dir)) return result;

        try (var stream = Files.list(dir)) {
            stream.filter(Files::isDirectory).forEach(instanceDir -> {
                String folderName = instanceDir.getFileName().toString();
                Path meta = instanceDir.resolve("instance.json");
                InstanceInfo info = null;
                if (Files.exists(meta)) {
                    try (Reader reader = Files.newBufferedReader(meta, StandardCharsets.UTF_8)) {
                        info = GSON.fromJson(reader, InstanceInfo.class);
                    } catch (IOException ignored) {}
                }
                if (info == null) info = new InstanceInfo(folderName, folderName, null);
                info.id = folderName;
                if (info.name == null || info.name.isBlank()) info.name = folderName;
                result.add(info);
            });
        } catch (IOException ignored) {}

        result.sort((a, b) -> Long.compare(a.createdAt, b.createdAt));
        if (result.isEmpty()) {
            result.add(create("Основной", null));
        }
        return result;
    }

    public InstanceInfo create(String name, String versionId) {
        if (name == null || name.isBlank()) name = "Инстанс";
        name = name.trim();
        String folderName = allocateFolderName(name);
        InstanceInfo info = new InstanceInfo(folderName, name, versionId);
        save(info);
        dataDir(info).toFile().mkdirs();
        dataDir(info).resolve("mods").toFile().mkdirs();
        dataDir(info).resolve("saves").toFile().mkdirs();
        return info;
    }

    public void delete(String id) {
        if (countInstances() <= 1) return;
        Path dir = LauncherPaths.instancesDir().resolve(id);
        if (!Files.isDirectory(dir)) return;
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException exc) throws IOException {
                    if (exc != null) throw exc;
                    Files.delete(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {}
    }

    public InstanceInfo get(String id) {
        for (InstanceInfo info : list()) {
            if (info.id.equals(id)) return info;
        }
        return list().get(0);
    }

    public void save(InstanceInfo info) {
        Path dir = LauncherPaths.instancesDir().resolve(info.id);
        dir.toFile().mkdirs();
        Path meta = dir.resolve("instance.json");
        try (Writer writer = Files.newBufferedWriter(meta, StandardCharsets.UTF_8)) {
            GSON.toJson(info, writer);
        } catch (IOException ignored) {}
    }

    public static Path dataDir(InstanceInfo info) {
        return LauncherPaths.instancesDir().resolve(info.id).resolve("data");
    }

    public static String dataPathLabel(InstanceInfo info) {
        return LauncherPaths.instancesDir().resolve(info.id).resolve("data").toString();
    }

    private String allocateFolderName(String displayName) {
        Set<String> used = new HashSet<>();
        Path dir = LauncherPaths.instancesDir();
        if (Files.isDirectory(dir)) {
            try (var stream = Files.list(dir)) {
                stream.filter(Files::isDirectory).forEach(p -> used.add(p.getFileName().toString()));
            } catch (IOException ignored) {}
        }

        String base = sanitizeFolderName(displayName);
        if (base.isEmpty()) base = "Инстанс";
        String candidate = base;
        int suffix = 2;
        while (used.contains(candidate)) {
            candidate = base + " (" + suffix + ")";
            suffix++;
        }
        return candidate;
    }

    private static String sanitizeFolderName(String name) {
        String cleaned = name.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", " ")
                .strip();
        while (cleaned.endsWith(".") || cleaned.endsWith(" ")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).strip();
        }
        return cleaned;
    }

    private int countInstances() {
        Path dir = LauncherPaths.instancesDir();
        if (!Files.isDirectory(dir)) return 0;
        try (var stream = Files.list(dir)) {
            return (int) stream.filter(Files::isDirectory).count();
        } catch (IOException e) {
            return 0;
        }
    }
}
