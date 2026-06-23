package singlaunch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
            applyCacheFlag(version);
        }
        versions.sort(Comparator.comparingInt(VersionDownloader::versionSortKey).reversed());
        return versions;
    }

    private void updateCacheFlags(List<GameVersion> versions) {
        for (GameVersion version : versions) {
            applyCacheFlag(version);
        }
    }

    private void applyCacheFlag(GameVersion version) {
        version.cached = isCached(version);
        if (version.cached) {
            try {
                Path jar = jarPath(version);
                if (Files.exists(jar)) version.sizeBytes = Files.size(jar);
            } catch (IOException ignored) {}
        }
    }

    private boolean isCached(GameVersion version) {
        try {
            Path apk = apkPath(version);
            if (Files.exists(apk) && Files.size(apk) > 0) return true;
            Path jar = jarPath(version);
            return Files.exists(jar) && Files.size(jar) > 0;
        } catch (IOException e) {
            return false;
        }
    }

    public Path apkPath(GameVersion version) {
        if ("local".equals(version.source)) {
            return Paths.get(version.downloadUrl);
        }
        return LauncherPaths.versionsCacheDir().resolve(version.id).resolve("Mindustry.apk");
    }

    public Path ensureApkDownloaded(GameVersion version, DoubleConsumer progress) throws IOException {
        ensureApkUrl(version);
        if (version.apkUrl == null || version.apkUrl.isBlank()) {
            throw new IOException("Нет APK для версии " + version.name + " — на Android доступны только релизы с APK");
        }
        Path target = apkPath(version);
        if (Files.exists(target) && Files.size(target) > 0) {
            if (progress != null) progress.accept(1.0);
            LauncherLog.info("APK уже в кэше: " + target);
            return target;
        }
        LauncherLog.info("Скачивание APK " + version.name + " → " + target);
        LauncherLog.info("URL: " + version.apkUrl);
        HttpUtil.download(version.apkUrl, target, progress);
        LauncherLog.info("APK загружен: " + Files.size(target) + " байт");
        return target;
    }

    public void ensureApkUrl(GameVersion version) throws IOException {
        if (version.hasApk()) return;
        resolveReleaseAssets(version);
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
        LauncherLog.info("Скачивание JAR " + version.name + " → " + target);
        LauncherLog.info("URL: " + version.downloadUrl);
        HttpUtil.download(version.downloadUrl, target, progress);
        LauncherLog.info("JAR загружен: " + Files.size(target) + " байт");
        return target;
    }

    public Path jarPath(GameVersion version) {
        if ("local".equals(version.source)) {
            return Paths.get(version.downloadUrl);
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
                        String assetUrl = asset.get("browser_download_url").getAsString();
                        String lower = assetName.toLowerCase();
                        if ("mindustry.jar".equalsIgnoreCase(assetName)) {
                            jarUrl = assetUrl;
                            jarSize = asset.get("size").getAsLong();
                        } else if (lower.endsWith(".apk") && !lower.contains("sources") && !lower.contains("debug")) {
                            if (apkUrl == null || lower.contains("mindustry")) {
                                apkUrl = assetUrl;
                            }
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
        List<GameVersion> merged = new ArrayList<>();
        for (GameVersion version : versions) {
            GameVersion match = null;
            for (GameVersion kept : merged) {
                if (sameVersion(kept, version)) {
                    match = kept;
                    break;
                }
            }
            if (match == null) {
                merged.add(version);
            } else {
                mergeInto(match, version);
            }
        }
        versions.clear();
        versions.addAll(merged);
    }

    private static boolean sameVersion(GameVersion a, GameVersion b) {
        if (a.id != null && a.id.equals(b.id)) return true;
        if (a.name != null && a.name.equals(b.name)) return true;
        return normalizeTag(a.id).equals(normalizeTag(b.id));
    }

    private static void mergeInto(GameVersion keep, GameVersion other) {
        if ((keep.downloadUrl == null || keep.downloadUrl.isBlank()) && other.downloadUrl != null && !other.downloadUrl.isBlank()) {
            keep.downloadUrl = other.downloadUrl;
        }
        if (!keep.hasApk() && other.hasApk()) {
            keep.apkUrl = other.apkUrl;
        }
        if ("remote".equals(other.source) && "local".equals(keep.source)) {
            if (other.downloadUrl != null && !other.downloadUrl.isBlank()) keep.downloadUrl = other.downloadUrl;
            if (other.hasApk()) keep.apkUrl = other.apkUrl;
        }
        if (keep.sizeBytes <= 0 && other.sizeBytes > 0) keep.sizeBytes = other.sizeBytes;
    }

    private static String normalizeTag(String id) {
        return GameVersionUtil.normalizeTag(id);
    }

    public void resolveReleaseAssets(GameVersion version) throws IOException {
        for (String tag : releaseTagCandidates(version.id)) {
            try {
                String url = RELEASES_API + "/tags/" + tag;
                String body = HttpUtil.getString(url, GH_HEADERS);
                JsonObject release = JsonParser.parseString(body).getAsJsonObject();
                parseAssetsInto(version, release.getAsJsonArray("assets"));
                if (version.hasApk() || (version.downloadUrl != null && !version.downloadUrl.isBlank())) {
                    LauncherLog.info("Метаданные релиза " + tag + ": jar=" + (version.downloadUrl != null) + ", apk=" + version.hasApk());
                    return;
                }
            } catch (IOException ignored) {}
        }
    }

    private static List<String> releaseTagCandidates(String id) {
        List<String> tags = new ArrayList<>();
        if (id == null || id.isBlank()) return tags;
        String raw = id.startsWith("local-") ? id.substring(6) : id;
        tags.add(raw);
        if (!raw.startsWith("v")) tags.add("v" + raw);
        if (raw.startsWith("v")) tags.add(raw.substring(1));
        return tags;
    }

    private static void parseAssetsInto(GameVersion version, JsonArray assets) {
        String jarUrl = version.downloadUrl;
        String apkUrl = version.apkUrl;
        long jarSize = version.sizeBytes;
        for (JsonElement assetEl : assets) {
            JsonObject asset = assetEl.getAsJsonObject();
            String assetName = asset.get("name").getAsString();
            String assetUrl = asset.get("browser_download_url").getAsString();
            String lower = assetName.toLowerCase();
            if ("mindustry.jar".equalsIgnoreCase(assetName)) {
                jarUrl = assetUrl;
                jarSize = asset.get("size").getAsLong();
            } else if (lower.endsWith(".apk") && !lower.contains("sources") && !lower.contains("debug")) {
                if (apkUrl == null || lower.contains("mindustry")) apkUrl = assetUrl;
            }
        }
        if (jarUrl != null && !jarUrl.isBlank()) version.downloadUrl = jarUrl;
        if (apkUrl != null && !apkUrl.isBlank()) version.apkUrl = apkUrl;
        if (jarSize > 0) version.sizeBytes = jarSize;
    }
}
