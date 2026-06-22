package singlaunch;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ModBrowserService {
    private static final String[] MOD_JSON_URLS = {
            "https://raw.githubusercontent.com/Anuken/MindustryMods/master/mods.json",
            "https://cdn.jsdelivr.net/gh/anuken/mindustrymods/mods.json"
    };
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<ModListing>>() {}.getType();
    private static final Map<String, String> HEADERS = Map.of("User-Agent", "SingularityLauncher");

    private List<ModListing> cache;

    public synchronized List<ModListing> fetchList() throws IOException {
        if (cache != null) return cache;

        IOException lastError = null;
        for (String url : MOD_JSON_URLS) {
            try {
                String body = HttpUtil.getString(url, HEADERS);
                List<ModListing> list = GSON.fromJson(body, LIST_TYPE);
                if (list == null) list = new ArrayList<>();
                list.sort(Comparator.comparing((ModListing m) -> m.lastUpdated != null ? m.lastUpdated : "").reversed());
                cache = list;
                return list;
            } catch (IOException e) {
                lastError = e;
            }
        }
        throw lastError != null ? lastError : new IOException("Не удалось загрузить mods.json");
    }

    public List<ModListing> search(String query) throws IOException {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<ModListing> all = fetchList();
        if (q.isEmpty()) return all;

        List<ModListing> filtered = new ArrayList<>();
        for (ModListing mod : all) {
            if (contains(mod.name, q) || contains(mod.repo, q) || contains(mod.author, q) || contains(mod.internalName, q)) {
                filtered.add(mod);
            }
        }
        return filtered;
    }

    public ModListing findByRepo(String repo) throws IOException {
        for (ModListing mod : fetchList()) {
            if (mod.repo != null && mod.repo.equalsIgnoreCase(repo)) return mod;
        }
        return null;
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }
}
