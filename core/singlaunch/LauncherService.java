package singlaunch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class LauncherService {
    private static final Gson GSON = new GsonBuilder().create();

    private final ConfigManager configManager;
    private final InstanceManager instanceManager;
    private final VersionDownloader versionDownloader;

    public LauncherService(ConfigManager configManager, InstanceManager instanceManager, VersionDownloader versionDownloader) {
        this.configManager = configManager;
        this.instanceManager = instanceManager;
        this.versionDownloader = versionDownloader;
    }

    public ConfigManager configManager() { return configManager; }
    public InstanceManager instanceManager() { return instanceManager; }
    public VersionDownloader versionDownloader() { return versionDownloader; }

    public String getBootstrapData() {
        return getBootstrapData(versionDownloader.listAvailable());
    }

    public String getBootstrapData(List<GameVersion> versions) {
        LauncherSettings settings = configManager.getSettings();
        List<InstanceInfo> instances = instanceManager.list();

        if (settings.selectedInstanceId == null || settings.selectedInstanceId.isBlank()) {
            settings.selectedInstanceId = instances.get(0).id;
        }
        if ((settings.selectedVersionId == null || settings.selectedVersionId.isBlank()) && !versions.isEmpty()) {
            settings.selectedVersionId = versions.get(0).id;
        }

        InstanceInfo instance = instanceManager.get(settings.selectedInstanceId);
        GameVersion version = findVersion(versions, resolveVersionId(instance, settings));
        int[] parsed = version != null
                ? GameVersionUtil.parse(version.id, version.name)
                : new int[]{0, 0};

        Map<String, Object> payload = new HashMap<>();
        payload.put("settings", settings);
        payload.put("instances", instanceRows(instances));
        payload.put("versions", versions);
        payload.put("launcherDir", LauncherPaths.root().toAbsolutePath().toString());
        payload.put("systemRamMb", (int) (Runtime.getRuntime().maxMemory() / (1024 * 1024)));
        payload.put("gameBuild", parsed[0]);
        payload.put("gameRevision", parsed[1]);
        payload.put("gameVersionLabel", GameVersionUtil.format(parsed[0], parsed[1]));
        payload.put("installedModKeys", listInstalledModKeys(instance));
        return GSON.toJson(payload);
    }

    public String getInstancesPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("instances", instanceRows(instanceManager.list()));
        payload.put("settings", configManager.getSettings());
        return GSON.toJson(payload);
    }

    public GameVersion findVersion(String id) {
        return findVersion(versionDownloader.listAvailable(), id);
    }

    public String resolveVersionId(InstanceInfo instance) {
        return resolveVersionId(instance, configManager.getSettings());
    }

    public Set<String> listInstalledModKeys(InstanceInfo instance) {
        Set<String> keys = new HashSet<>();
        Path modsDir = InstanceManager.dataDir(instance).resolve("mods");
        if (!Files.isDirectory(modsDir)) return keys;
        try (var stream = Files.list(modsDir)) {
            stream.filter(path -> {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                return name.endsWith(".zip") || name.endsWith(".jar");
            }).forEach(path -> keys.add(path.getFileName().toString().replaceFirst("(?i)\\.(zip|jar)$", "")));
        } catch (Exception ignored) {}
        return keys;
    }

    private List<Map<String, Object>> instanceRows(List<InstanceInfo> instances) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (InstanceInfo inst : instances) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", inst.id);
            row.put("name", inst.name);
            row.put("versionId", inst.versionId);
            row.put("createdAt", inst.createdAt);
            row.put("dataPath", InstanceManager.dataPathLabel(inst));
            rows.add(row);
        }
        return rows;
    }

    private GameVersion findVersion(List<GameVersion> versions, String id) {
        if (id == null) return null;
        for (GameVersion version : versions) {
            if (version.id.equals(id)) return version;
        }
        return null;
    }

    private String resolveVersionId(InstanceInfo instance, LauncherSettings settings) {
        if (instance.versionId != null && !instance.versionId.isBlank()) return instance.versionId;
        return settings.selectedVersionId;
    }
}
