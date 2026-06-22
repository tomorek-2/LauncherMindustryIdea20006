package singlaunch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VersionDownloader {
    private static final String RELEASES_API = "https://api.github.com/repos/Anuken/Mindustry/releases";
    private static final Pattern NEXT_LINK = Pattern.compile("<([^>]+)>;\\s*rel=\"next\"");
    private static final Map<String, String> GH_HEADERS = Map.of(
            "User-Agent", "SingularityLauncher",
            "Accept", "application/vnd.github+json"
    );

    private volatile List<GameVersion> cachedVersions;
    private volatile long cachedAt;
    private static final long CACHE_TTL_MS = 10 * 60 * 1000;

    public List<GameVersion> listAvailable() {
        List<GameVersion> cached = cachedVersions;
        if (cached != null && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
            updateCacheFlags(cached);
            return new ArrayList<>(cached);
        }
        List<GameVersion> fresh = loadAvailable();
        cachedVersions = fresh;
        cachedAt = System.currentTimeMillis();
        return new ArrayList<>(fresh);
    }

    public void refreshAsync(Runnable onDone) {
        Thread thread = new Thread(() -> {
            List<GameVersion> fresh = loadAvailable();
            cachedVersions = fresh;
            cachedAt = System.currentTimeMillis();
            if (onDone != null) onDone.run();
        }, "version-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    public List<GameVersion> peekCachedOrLocal() {
        List<GameVersion> cached = cachedVersions;
        if (cached != null) {
            updateCacheFlags(cached);
            return new ArrayList<>(cached);
        }
        List<GameVersion> local = scanLegacyLocal();
        local.sort(Comparator.comparingInt(VersionDownloader::versionSortKey).reversed());
        return local;
    }

    public void invalidateCache() {
        cachedVersions = null;
        cachedAt = 0;
    }

    private List<GameVersion> loadAvailable() {
        List<GameVersion> versions = new ArrayList<>();
        versions.addAll(scanLegacyLocal());
        versions.addAll(fetchRemote());
        dedupe(versions);
        for (GameVersion version : versions) {
            version.cached = jarPath(version).toFile().exists();
            if (version.cached) version.sizeBytes = jarPath(version).toFile().length();
        }
        versions.sort(Comparator.comparingInt(VersionDownloader::versionSortKey).reversed());
        return versions;
    }

    private void updateCacheFlags(List<GameVersion> versions) {
        for (GameVersion version : versions) {
            version.cached = jarPath(version).toFile().exists();
            if (version.cached) version.sizeBytes = jarPath(version).toFile().length();
        }
    }

    public Path apkPath(GameVersion version) {
        if ("local".equals(version.source)) {
            return Path.of(version.downloadUrl);
        }
        return LauncherPaths.versionsCacheDir().resolve(version.id).resolve("Mindustry.apk");
    }

    public Path ensureApkDownloaded(GameVersion version, DoubleConsumer progress) throws IOException {
        if (version.apkUrl == null || version.apkUrl.isBlank()) {
            throw new IOException("Нет APK для версии " + version.id);
        }
        Path target = apkPath(version);
        if (Files.exists(target) && Files.size(target) > 0) {
            if (progress != null) progress.accept(1.0);
            return target;
        }
        HttpUtil.download(version.apkUrl, target, progress);
        return target;
    }

    public Path ensureDownloaded(GameVersion version, DoubleConsumer progress) throws IOException {
        Path target = jarPath(version);
        if (Files.exists(target) && Files.size(target) > 0) {
            if (progress != null) progress.accept(1.0);
            return target;
        }
        if (version.downloadUrl == null || version.downloadUrl.isBlank()) {
            throw new IOException("Нет URL для версии " + version.id);
        }
        HttpUtil.download(version.downloadUrl, target, progress);
        return target;
    }

    public Path jarPath(GameVersion version) {
        if ("local".equals(version.source)) {
            return Path.of(version.downloadUrl);
        }
        return LauncherPaths.versionsCacheDir().resolve(version.id).resolve("Mindustry.jar");
    }

    private List<GameVersion> scanLegacyLocal() {
        List<GameVersion> local = new ArrayList<>();
        Path legacy = LauncherPaths.legacyVersionsDir();
        if (!Files.isDirectory(legacy)) return local;

        try (var stream = Files.list(legacy)) {
            stream.filter(p -> p.toString().endsWith(".jar")).forEach(path -> {
                String fileName = path.getFileName().toString();
                String id = fileName.replaceFirst("(?i)mindustry", "").replaceFirst("\\.jar$", "");
                if (id.isBlank() || id.startsWith("V")) id = fileName.replace(".jar", "");
                local.add(new GameVersion("local-" + id, fileName.replace(".jar", ""), path.toAbsolutePath().toString(), "local"));
            });
        } catch (IOException ignored) {}
        return local;
    }

    private List<GameVersion> fetchRemote() {
        List<GameVersion> remote = new ArrayList<>();
        String nextUrl = RELEASES_API + "?per_page=100";

        while (nextUrl != null) {
            try {
                HttpUtil.HttpResult result = HttpUtil.getWithHeaders(nextUrl, GH_HEADERS);
                String body = result.body;
                JsonArray releases = JsonParser.parseString(body).getAsJsonArray();
                for (JsonElement element : releases) {
                    JsonObject release = element.getAsJsonObject();
                    String tag = release.get("tag_name").getAsString();
                    String name = release.has("name") ? release.get("name").getAsString() : tag;
                    JsonArray assets = release.getAsJsonArray("assets");
                    String jarUrl = null;
                    String apkUrl = null;
                    long jarSize = 0;
                    for (JsonElement assetEl : assets) {
                        JsonObject asset = assetEl.getAsJsonObject();
                        String assetName = asset.get("name").getAsString();
                        if ("Mindustry.jar".equals(assetName)) {
                            jarUrl = asset.get("browser_download_url").getAsString();
                            jarSize = asset.get("size").getAsLong();
                        } else if (assetName.endsWith(".apk")) {
                            apkUrl = asset.get("browser_download_url").getAsString();
                        }
                    }
                    if (jarUrl != null) {
                        GameVersion version = new GameVersion(tag, name, jarUrl, "remote");
                        version.sizeBytes = jarSize;
                        version.apkUrl = apkUrl;
                        remote.add(version);
                    } else if (apkUrl != null) {
                        GameVersion version = new GameVersion(tag, name, "", "remote");
                        version.apkUrl = apkUrl;
                        remote.add(version);
                    }
                }
                nextUrl = parseNextLink(result.linkHeader);
            } catch (Exception e) {
                break;
            }
        }
        return remote;
    }

    private static String parseNextLink(String linkHeader) {
        if (linkHeader == null || linkHeader.isBlank()) return null;
        Matcher matcher = NEXT_LINK.matcher(linkHeader);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static int versionSortKey(GameVersion version) {
        int[] parsed = GameVersionUtil.parse(version.id, version.name);
        return parsed[0] * 1000 + parsed[1];
    }

    private void dedupe(List<GameVersion> versions) {
        List<GameVersion> filtered = new ArrayList<>();
        for (GameVersion version : versions) {
            boolean exists = false;
            for (GameVersion kept : filtered) {
                if (kept.id.equals(version.id) || kept.name.equals(version.name)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) filtered.add(version);
        }
        versions.clear();
        versions.addAll(filtered);
    }
}
