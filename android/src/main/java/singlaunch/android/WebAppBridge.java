package singlaunch.android;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import singlaunch.ConfigManager;
import singlaunch.GameVersion;
import singlaunch.InstanceInfo;
import singlaunch.InstanceManager;
import singlaunch.LauncherService;
import singlaunch.LauncherSettings;
import singlaunch.ModBrowserService;
import singlaunch.ModInstaller;
import singlaunch.ModListing;
import singlaunch.VersionDownloader;

public class WebAppBridge {
    private static final Gson GSON = new Gson();
    private static final String[] MINDUSTRY_PACKAGES = {"io.anuke.mindustry", "mindustry"};

    private final MainActivity activity;
    private final WebView webView;
    private final ConfigManager configManager;
    private final InstanceManager instanceManager;
    private final VersionDownloader versionDownloader;
    private final LauncherService service;
    private final ModBrowserService modBrowserService;
    private final ModInstaller modInstaller;

    public WebAppBridge(MainActivity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        this.configManager = new ConfigManager();
        this.instanceManager = new InstanceManager();
        this.versionDownloader = new VersionDownloader();
        this.service = new LauncherService(configManager, instanceManager, versionDownloader);
        this.modBrowserService = new ModBrowserService();
        this.modInstaller = new ModInstaller();
    }

    void onPageReady() {
        runJs("document.body.classList.add('android','mobile')");
        runJs("applyBootstrap(" + getBootstrapDataFast() + ")");
        versionDownloader.refreshAsync(() -> runJs("applyVersions(" + GSON.toJson(versionDownloader.listAvailable()) + ")"));
    }

    @JavascriptInterface
    public String getBootstrapData() {
        return service.getBootstrapData();
    }

    @JavascriptInterface
    public String getBootstrapDataFast() {
        return service.getBootstrapData(versionDownloader.peekCachedOrLocal());
    }

    @JavascriptInterface
    public void saveSettings(String json) {
        LauncherSettings next = GSON.fromJson(json, LauncherSettings.class);
        if (next == null) return;
        configManager.update(next);
        runJs("onSettingsSaved()");
        toast("Настройки сохранены");
    }

    @JavascriptInterface
    public void selectInstance(String id) {
        LauncherSettings settings = configManager.getSettings();
        settings.selectedInstanceId = id;
        InstanceInfo instance = instanceManager.get(id);
        if (instance.versionId != null && !instance.versionId.isBlank()) {
            settings.selectedVersionId = instance.versionId;
        }
        configManager.save();
        runJs("onInstanceSelected('" + escapeJs(instance.name) + "','" + escapeJs(settings.selectedVersionId) + "')");
        refreshInstances();
    }

    @JavascriptInterface
    public void selectVersion(String id) {
        setInstanceVersion(configManager.getSettings().selectedInstanceId, id);
    }

    @JavascriptInterface
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
        runJs("onVersionSelected('" + escapeJs(versionId) + "')");
        refreshInstances();
    }

    @JavascriptInterface
    public void createInstance(String name, String versionId) {
        String instanceName = (name == null || name.isBlank()) ? "Инстанс" : name.trim();
        new Thread(() -> {
            InstanceInfo created = instanceManager.create(instanceName, versionId);
            LauncherSettings settings = configManager.getSettings();
            settings.selectedInstanceId = created.id;
            configManager.save();
            activity.runOnUiThread(() -> {
                refreshInstances();
                toast("Создан инстанс: " + created.name);
            });
        }, "instance-create").start();
    }

    @JavascriptInterface
    public void deleteInstance(String id) {
        new Thread(() -> {
            instanceManager.delete(id);
            LauncherSettings settings = configManager.getSettings();
            if (id.equals(settings.selectedInstanceId)) {
                settings.selectedInstanceId = instanceManager.list().get(0).id;
                configManager.save();
            }
            activity.runOnUiThread(this::refreshInstances);
        }, "instance-delete").start();
    }

    @JavascriptInterface
    public void openPicker(String itemsJson, String selectedId, double x, double y, String callback) {
        activity.runOnUiThread(() -> {
            JsonArray items = JsonParser.parseString(itemsJson).getAsJsonArray();
            List<String> labels = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            int selected = 0;
            for (int i = 0; i < items.size(); i++) {
                JsonObject item = items.get(i).getAsJsonObject();
                String id = item.get("id").getAsString();
                String label = item.has("label") ? item.get("label").getAsString() : id;
                ids.add(id);
                labels.add(label);
                if (id.equals(selectedId)) selected = i;
            }
            new AlertDialog.Builder(activity)
                    .setItems(labels.toArray(new String[0]), (dialog, which) ->
                            runJs(callback + "('" + escapeJs(ids.get(which)) + "')"))
                    .show();
        });
    }

    @JavascriptInterface
    public void play() {
        LauncherSettings settings = configManager.getSettings();
        InstanceInfo instance = instanceManager.get(settings.selectedInstanceId);
        GameVersion version = service.findVersion(service.resolveVersionId(instance));
        if (version == null) {
            toast("Версия не выбрана");
            return;
        }
        if (version.apkUrl == null || version.apkUrl.isBlank()) {
            toast("Нет APK для версии " + version.name);
            return;
        }

        new Thread(() -> {
            try {
                runJs("setDownloadProgress(0, 'Загрузка...')");
                versionDownloader.ensureApkDownloaded(version, p ->
                        runJs("setDownloadProgress(" + p + ", 'Загрузка APK...')"));
                runJs("setDownloadProgress(1, 'Запуск...')");
                activity.runOnUiThread(() -> launchMindustry(version));
                runJs("setDownloadProgress(-1, '')");
            } catch (Exception e) {
                runJs("setDownloadProgress(-1, '')");
                toast(e.getMessage() != null ? e.getMessage() : "Ошибка запуска");
            }
        }, "game-launch").start();
    }

    @JavascriptInterface
    public void downloadVersion(String id) {
        GameVersion version = service.findVersion(id);
        if (version == null) return;
        setInstanceVersion(configManager.getSettings().selectedInstanceId, id);
        new Thread(() -> {
            try {
                runJs("setDownloadProgress(0, 'Загрузка...')");
                if (version.apkUrl != null && !version.apkUrl.isBlank()) {
                    versionDownloader.ensureApkDownloaded(version, p ->
                            runJs("setDownloadProgress(" + p + ", 'Загрузка...')"));
                } else {
                    versionDownloader.ensureDownloaded(version, p ->
                            runJs("setDownloadProgress(" + p + ", 'Загрузка...')"));
                }
                runJs("setDownloadProgress(-1, '')");
                versionDownloader.invalidateCache();
                String versionsJson = GSON.toJson(versionDownloader.listAvailable());
                runJs("onVersionDownloaded('" + escapeJs(id) + "'," + versionsJson + ")");
            } catch (Exception e) {
                runJs("setDownloadProgress(-1, '')");
                toast("Ошибка загрузки");
            }
        }, "version-download").start();
    }

    @JavascriptInterface
    public void loadMods(String query, String instanceId) {
        new Thread(() -> {
            try {
                String payload = buildModsPayload(query, instanceId);
                runJs("onModsLoaded(" + payload + ")");
            } catch (Exception e) {
                toast("Ошибка загрузки модов");
            }
        }, "mod-list").start();
    }

    @JavascriptInterface
    public void loadMods(String query) {
        loadMods(query, null);
    }

    @JavascriptInterface
    public void installMod(String repo, boolean hasJava, String instanceId) {
        InstanceInfo instance = resolveModsInstance(instanceId);
        new Thread(() -> {
            try {
                runJs("setDownloadProgress(0, 'Установка мода...')");
                ModListing listing = modBrowserService.findByRepo(repo);
                if (listing != null) {
                    modInstaller.installFromListing(listing, instance, p ->
                            runJs("setDownloadProgress(" + p + ", 'Установка мода...')"));
                } else {
                    modInstaller.installFromGithub(repo, hasJava, instance, p ->
                            runJs("setDownloadProgress(" + p + ", 'Установка мода...')"));
                }
                runJs("setDownloadProgress(-1, '')");
                toast("Мод установлен");
                loadMods("", instance.id);
            } catch (Exception e) {
                runJs("setDownloadProgress(-1, '')");
                toast("Ошибка установки");
            }
        }, "mod-install").start();
    }

    @JavascriptInterface
    public void importGithubMod(String input, String instanceId) {
        if (input == null || input.isBlank()) return;
        String repo = input.trim();
        if (repo.startsWith("https://github.com/")) repo = repo.substring("https://github.com/".length());
        installMod(repo, false, instanceId);
    }

    @JavascriptInterface
    public void openModsFolder() {
        toast(InstanceManager.dataDir(resolveModsInstance(null)).resolve("mods").toString());
    }

    @JavascriptInterface
    public void openInstanceFolder() {
        toast(InstanceManager.dataDir(instanceManager.get(configManager.getSettings().selectedInstanceId)).toString());
    }

    @JavascriptInterface
    public void settings() { runJs("openPanel('settings')"); }
    @JavascriptInterface
    public void instances() { runJs("openPanel('instances')"); }
    @JavascriptInterface
    public void mods() { runJs("openPanel('mods')"); loadMods(""); }
    @JavascriptInterface
    public void exit() { activity.finish(); }

    private void launchMindustry(GameVersion version) {
        try {
            File apk = versionDownloader.apkPath(version).toFile();
            if (apk.exists() && apk.length() > 0) {
                activity.openApkInstall(apk);
                toast("Установка " + version.name);
                return;
            }
        } catch (Exception ignored) {}

        for (String pkg : MINDUSTRY_PACKAGES) {
            Intent launch = activity.getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(launch);
                toast("Запуск Mindustry");
                return;
            }
        }
        toast("APK не найден — скачайте версию ещё раз");
    }

    private String buildModsPayload(String query, String instanceId) throws Exception {
        InstanceInfo instance = resolveModsInstance(instanceId);
        GameVersion version = service.findVersion(service.resolveVersionId(instance));
        int[] parsed = version != null
                ? singlaunch.GameVersionUtil.parse(version.id, version.name)
                : new int[]{0, 0};
        var installed = service.listInstalledModKeys(instance);
        var rows = new ArrayList<>();
        for (ModListing mod : modBrowserService.search(query)) {
            var row = new java.util.HashMap<String, Object>();
            row.put("repo", mod.repo);
            row.put("name", mod.name);
            row.put("author", mod.author);
            row.put("description", mod.description);
            row.put("stars", mod.stars);
            row.put("hasJava", mod.hasJava);
            row.put("minGameVersion", mod.minGameVersion);
            row.put("compatible", singlaunch.GameVersionUtil.isAtLeast(parsed[0], parsed[1], mod.minGameVersion));
            row.put("installed", installed.contains(mod.repo.replace("/", "")));
            rows.add(row);
        }
        var payload = new java.util.HashMap<String, Object>();
        payload.put("mods", rows);
        payload.put("instanceId", instance.id);
        payload.put("instanceName", instance.name);
        payload.put("gameVersionLabel", singlaunch.GameVersionUtil.format(parsed[0], parsed[1]));
        return GSON.toJson(payload);
    }

    private InstanceInfo resolveModsInstance(String instanceId) {
        if (instanceId != null && !instanceId.isBlank()) return instanceManager.get(instanceId);
        return instanceManager.get(configManager.getSettings().selectedInstanceId);
    }

    private void refreshInstances() {
        runJs("applyInstances(" + service.getInstancesPayload() + ")");
    }

    private void runJs(String script) {
        activity.runOnUiThread(() -> webView.evaluateJavascript(script, null));
    }

    private void toast(String text) {
        activity.runOnUiThread(() -> Toast.makeText(activity, text, Toast.LENGTH_SHORT).show());
    }

    private static String escapeJs(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }
}
