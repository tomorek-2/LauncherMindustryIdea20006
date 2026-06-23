package singlaunch;

public class GameVersion {
    public String id;
    public String name;
    public String downloadUrl;
    public String source;
    public boolean cached;
    public long sizeBytes;
    public String apkUrl;

    public GameVersion() {}

    public GameVersion(String id, String name, String downloadUrl, String source) {
        this.id = id;
        this.name = name;
        this.downloadUrl = downloadUrl;
        this.source = source;
    }

    public boolean hasApk() {
        return apkUrl != null && !apkUrl.isBlank();
    }
}
