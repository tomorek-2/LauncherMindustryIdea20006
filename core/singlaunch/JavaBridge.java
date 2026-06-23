package singlaunch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class JavaBridge {
    private static final Gson GSON = new GsonBuilder().create();

    private final ConfigManager configManager;
    private final InstanceManager instanceManager;
    private final VersionDownloader versionDownloader;
    private final GameLauncher gameLauncher;
    private final ModBrowserService modBrowserService;
    private final ModInstaller modInstaller;
    private final LauncherService service;
    private final WebEngine webEngine;
    private final WebView webView;
    private final Consumer<String> status;
    private final LauncherWindow launcherWindow;

    public JavaBridge(ConfigManager configManager, InstanceManager instanceManager,
                      VersionDownloader versionDownloader, GameLauncher gameLauncher,
                      WebEngine webEngine, WebView webView, Consumer<String> status, LauncherWindow launcherWindow) {
        this.configManager = configManager;
        this.instanceManager = instanceManager;
        this.versionDownloader = versionDownloader;
        this.gameLauncher = gameLauncher;
        this.service = new LauncherService(configManager, instanceManager, versionDownloader);
        this.modBrowserService = new ModBrowserService();
        this.modInstaller = new ModInstaller();
        this.webEngine = webEngine;
        this.webView = webView;
        this.status = status;
        this.launcherWindow = launcherWindow;
        LauncherLog.setSink(line -> runJs("appendLog(" + jsQuote(line) + ")"));
        LauncherLog.info("Singularity Launcher (desktop)");
    }

    public String getBootstrapData() {
        return service.getBootstrapData();
    }

    public String getBootstrapDataFast() {
        return service.getBootstrapData(versionDownloader.peekCachedOrLocal());
    }

    public void refreshVersionsAsync() {
        versionDownloader.refreshAsync(() -> Platform.runLater(() ->
                runJs("applyVersions(" + GSON.toJson(versionDownloader.listAvailable()) + ")")));
    }

    public void saveSettings(String json) {
        LauncherSettings next = GSON.fromJson(json, LauncherSettings.class);
        if (next == null) return;
        configManager.update(next);
        runJs("onSettingsSaved()");
        status.accept("Настройки сохранены");
    }

    public void selectInstance(String id) {
        LauncherSettings settings = configManager.getSettings();
        settings.selectedInstanceId = id;
        InstanceInfo instance = instanceManager.get(id);
        if (instance.versionId != null && !instance.versionId.isBlank()) {
            settings.selectedVersionId = instance.versionId;
        }
        configManager.save();
        runJs("onInstanceSelected(" + jsQuote(instance.name) + "," + jsQuote(settings.selectedVersionId) + ")");
        refreshInstances();
    }

    public void selectVersion(String id) {
        setInstanceVersion(configManager.getSettings().selectedInstanceId, id);
    }

    public void setInstanceVersion(String instanceId, String versionId) {
        if (instanceId == null || instanceId.isBlank() || versionId == null || versionId.isBlank()) return;
        InstanceInfo instance = instanceManager.get(instanceId);
        instance.versionId = versionId;
        instanceManager.save(instance);
        LauncherSettings settings = configManager.getSettings();
        if (instanceId.equals(settings.selectedInstanceId)) {
            settings.selectedVersionId = versionId;
        }
        configManager.save();
        LauncherLog.info("Версия инстанса " + instance.name + " → " + versionId);
        runJs("onVersionSelected(" + jsQuote(versionId) + ")");
        refreshInstances();
    }

    public void createInstance(String name, String versionId) {
        if (name == null || name.isBlank()) name = "Инстанс";
        final String finalName = name.trim();
        final String finalVersionId = versionId;
        Thread thread = new Thread(() -> {
            InstanceInfo created = instanceManager.create(finalName, finalVersionId);
            LauncherSettings settings = configManager.getSettings();
            settings.selectedInstanceId = created.id;
            configManager.save();
            Platform.runLater(() -> {
                refreshInstances();
                runJs("showToast('Создан инстанс: " + escapeJs(created.name) + "')");
            });
        }, "instance-create");
        thread.setDaemon(true);
        thread.start();
    }

    public void deleteInstance(String id) {
        Thread thread = new Thread(() -> {
            instanceManager.delete(id);
            LauncherSettings settings = configManager.getSettings();
            if (id.equals(settings.selectedInstanceId)) {
                settings.selectedInstanceId = instanceManager.list().get(0).id;
                configManager.save();
            }
            Platform.runLater(this::refreshInstances);
        }, "instance-delete");
        thread.setDaemon(true);
        thread.start();
    }

    public void openPicker(String itemsJson, String selectedId, double x, double y, String callback) {
        OverlayDropdown.showAt(webView, x, y, itemsJson, selectedId, value ->
                runJs(callback + "(" + jsQuote(value) + ")"));
    }

    public void play() {
        LauncherSettings settings = configManager.getSettings();
        InstanceInfo instance = instanceManager.get(settings.selectedInstanceId);
        String versionId = service.resolveVersionId(instance);
        GameVersion version = service.findVersion(versionId);
        if (version == null) {
            LauncherLog.error("Версия не найдена: " + versionId);
            status.accept("Версия не выбрана");
            return;
        }

        LauncherLog.info("Играть: инстанс=" + instance.name + ", версия=" + version.name + " (" + version.id + ")");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Загрузка " + version.name + "...");
                Platform.runLater(() -> runJs("setDownloadProgress(0, 'Загрузка...')"));

                versionDownloader.ensureDownloaded(version, progress ->
                        Platform.runLater(() -> runJs("setDownloadProgress(" + progress + ", 'Загрузка...')")));
                var gameJar = versionDownloader.jarPath(version);

                updateMessage("Запуск...");
                Platform.runLater(() -> runJs("setDownloadProgress(1, 'Запуск...')"));
                Process process = gameLauncher.launch(instance, version, settings, gameJar);

                boolean keepOpen = configManager.getSettings().keepLauncherOpen;
                if (!keepOpen) {
                    Platform.runLater(() -> launcherWindow.hideForGame());
                }

                process.waitFor();

                if (!keepOpen) {
                    Platform.runLater(() -> {
                        launcherWindow.showAfterGame();
                        refreshInstances();
                        status.accept("Игра закрыта — лаунчер снова активен");
                    });
                }
                return null;
            }

            @Override
            protected void succeeded() {
                runJs("setDownloadProgress(-1, '')");
                if (configManager.getSettings().keepLauncherOpen) {
                    status.accept("Запущено: " + instance.name);
                }
            }

            @Override
            protected void failed() {
                runJs("setDownloadProgress(-1, '')");
                Throwable error = getException();
                String message = error != null && error.getMessage() != null ? error.getMessage() : "Ошибка запуска";
                LauncherLog.error("Ошибка запуска", error);
                status.accept(message);
                runJs("showToast(" + jsQuote(message) + ")");
            }
        };

        task.setOnRunning(e -> status.accept(task.getMessage()));
        Thread thread = new Thread(task, "game-launch");
        thread.setDaemon(true);
        thread.start();
    }

    public void downloadVersion(String id) {
        GameVersion version = findVersion(id);
        if (version == null) {
            LauncherLog.error("Версия не найдена для загрузки: " + id);
            return;
        }

        setInstanceVersion(configManager.getSettings().selectedInstanceId, id);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                versionDownloader.ensureDownloaded(version, progress ->
                        Platform.runLater(() -> runJs("setDownloadProgress(" + progress + ", 'Загрузка " + escapeJs(version.name) + "')")));
                return null;
            }

            @Override
            protected void succeeded() {
                runJs("setDownloadProgress(-1, '')");
                versionDownloader.invalidateCache();
                runJs("onVersionDownloaded(" + jsQuote(id) + "," + GSON.toJson(versionDownloader.listAvailable()) + ")");
            }

            @Override
            protected void failed() {
                runJs("setDownloadProgress(-1, '')");
                Throwable error = getException();
                LauncherLog.error("Ошибка загрузки версии", error);
                runJs("showToast(" + jsQuote(error != null ? error.getMessage() : "Ошибка загрузки") + ")");
            }
        };
        new Thread(task, "version-download").start();
    }

    public void openInstanceFolder() {
        InstanceInfo instance = instanceManager.get(configManager.getSettings().selectedInstanceId);
        openFolder(InstanceManager.dataDir(instance).toFile());
    }

    public void openModsFolder() {
        InstanceInfo instance = resolveModsInstance(null);
        File mods = InstanceManager.dataDir(instance).resolve("mods").toFile();
        openFolder(mods);
    }

    public void settings() {
        runJs("openPanel('settings')");
    }

    public void instances() {
        runJs("openPanel('instances')");
    }

    public void mods() {
        runJs("openPanel('mods')");
        loadMods("");
    }

    public void loadMods(String query) {
        loadMods(query, null);
    }

    public void loadMods(String query, String instanceId) {
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                List<ModListing> mods = modBrowserService.search(query);
                InstanceInfo instance = resolveModsInstance(instanceId);
                GameVersion version = service.findVersion(service.resolveVersionId(instance));
                int[] parsed = version != null
                        ? GameVersionUtil.parse(version.id, version.name)
                        : new int[]{0, 0};
                Set<String> installed = service.listInstalledModKeys(instance);

                List<Map<String, Object>> rows = new ArrayList<>();
                for (ModListing mod : mods) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("repo", mod.repo);
                    row.put("name", mod.name);
                    row.put("author", mod.author);
                    row.put("description", mod.description);
                    row.put("stars", mod.stars);
                    row.put("hasJava", mod.hasJava);
                    row.put("minGameVersion", mod.minGameVersion);
                    row.put("compatible", GameVersionUtil.isAtLeast(parsed[0], parsed[1], mod.minGameVersion));
                    row.put("installed", installed.contains(repoKey(mod.repo)));
                    rows.add(row);
                }

                Map<String, Object> payload = new HashMap<>();
                payload.put("mods", rows);
                payload.put("instanceId", instance.id);
                payload.put("instanceName", instance.name);
                payload.put("gameVersionLabel", GameVersionUtil.format(parsed[0], parsed[1]));
                payload.put("gameBuild", parsed[0]);
                payload.put("gameRevision", parsed[1]);
                return GSON.toJson(payload);
            }

            @Override
            protected void succeeded() {
                runJs("onModsLoaded(" + getValue() + ")");
            }

            @Override
            protected void failed() {
                Throwable error = getException();
                runJs("showToast(" + jsQuote(error != null ? error.getMessage() : "Ошибка загрузки модов") + ")");
            }
        };
        new Thread(task, "mod-list").start();
    }

    public void installMod(String repo, boolean hasJava, String instanceId) {
        InstanceInfo instance = resolveModsInstance(instanceId);
        ModListing resolvedListing = null;
        try {
            resolvedListing = modBrowserService.findByRepo(repo);
        } catch (Exception ignored) {}

        if (resolvedListing != null && !isCompatible(resolvedListing, instance)) {
            runJs("showToast('Мод несовместим с версией инстанса')");
            return;
        }

        final ModListing installListing = resolvedListing;
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                Platform.runLater(() -> runJs("setDownloadProgress(0, 'Установка мода...')"));
                if (installListing != null) {
                    modInstaller.installFromListing(installListing, instance, progress ->
                            Platform.runLater(() -> runJs("setDownloadProgress(" + progress + ", 'Установка мода...')")));
                } else {
                    modInstaller.installFromGithub(repo, hasJava, instance, progress ->
                            Platform.runLater(() -> runJs("setDownloadProgress(" + progress + ", 'Установка мода...')")));
                }
                return null;
            }

            @Override
            protected void succeeded() {
                runJs("setDownloadProgress(-1, '')");
                runJs("showToast('Мод установлен')");
                refreshInstances();
                loadMods("", instance.id);
            }

            @Override
            protected void failed() {
                runJs("setDownloadProgress(-1, '')");
                Throwable error = getException();
                runJs("showToast(" + jsQuote(error != null ? error.getMessage() : "Ошибка установки") + ")");
            }
        };
        new Thread(task, "mod-install").start();
    }

    public void importGithubMod(String input, String instanceId) {
        if (input == null || input.isBlank()) return;
        String repo = input.trim();
        if (repo.startsWith("https://github.com/")) repo = repo.substring("https://github.com/".length());
        if (repo.endsWith("/")) repo = repo.substring(0, repo.length() - 1);

        boolean hasJava = false;
        try {
            ModListing listing = modBrowserService.findByRepo(repo);
            if (listing != null) {
                if (!isCompatible(listing, resolveModsInstance(instanceId))) {
                    runJs("showToast('Мод несовместим с версией инстанса')");
                    return;
                }
                hasJava = listing.hasJava;
            }
        } catch (Exception ignored) {}

        installMod(repo, hasJava, instanceId);
    }

    public void logs() {
        runJs("openPanel('logs')");
    }

    public String getLogs() {
        return LauncherLog.dump();
    }

    public void clearLogs() {
        LauncherLog.clear();
        runJs("clearLogView()");
    }

    public void exit() {
        Platform.runLater(() -> {
            Platform.exit();
            System.exit(0);
        });
    }

    private boolean isCompatible(ModListing mod, InstanceInfo instance) {
        GameVersion version = service.findVersion(service.resolveVersionId(instance));
        int[] parsed = version != null
                ? GameVersionUtil.parse(version.id, version.name)
                : new int[]{0, 0};
        return GameVersionUtil.isAtLeast(parsed[0], parsed[1], mod.minGameVersion);
    }

    private InstanceInfo resolveModsInstance(String instanceId) {
        if (instanceId != null && !instanceId.isBlank()) {
            return instanceManager.get(instanceId);
        }
        return instanceManager.get(configManager.getSettings().selectedInstanceId);
    }

    private static String repoKey(String repo) {
        return repo.replace("/", "");
    }

    private GameVersion findVersion(String id) {
        return service.findVersion(id);
    }

    private void refreshInstances() {
        runJs("applyInstances(" + service.getInstancesPayload() + ")");
    }

    private void refreshData() {
        runJs("applyBootstrap(" + getBootstrapData() + ")");
    }

    private void openFolder(File folder) {
        DesktopUtil.openFolderAsync(folder, message -> runJs("showToast(" + jsQuote(message) + ")"));
    }

    private void runJs(String script) {
        Platform.runLater(() -> webEngine.executeScript(script));
    }

    private static String jsQuote(String value) {
        if (value == null) return "''";
        return "'" + escapeJs(value) + "'";
    }

    private static String escapeJs(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }
}
