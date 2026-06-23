package singlaunch.android;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import singlaunch.HttpUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import singlaunch.LauncherPaths;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private WebAppBridge bridge;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        System.setProperty("singularity.launcher.dir", getFilesDir().getAbsolutePath());
        LauncherPaths.ensureDirs();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);

        bridge = new WebAppBridge(this, webView);
        webView.addJavascriptInterface(bridge, "javaApp");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                bridge.onPageReady();
            }
        });

        String html = loadWebHtml();
        if (html != null) {
            webView.loadDataWithBaseURL("file:///android_asset/web/", html, "text/html", "UTF-8", null);
        } else {
            webView.loadDataWithBaseURL(null,
                    "<html><body style='background:#1a1a1a;color:#ffd379;padding:24px'>"
                    + "Ошибка загрузки UI: web/index.html</body></html>",
                    "text/html", "UTF-8", null);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private String loadWebHtml() {
        String[] paths = {"web/index.html", "index.html"};
        for (String path : paths) {
            try (InputStream in = getAssets().open(path)) {
                return new String(HttpUtil.readBytes(in));
            } catch (IOException ignored) {}
        }
        return null;
    }

    void openApkInstall(File apk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(settingsIntent);
                android.widget.Toast.makeText(this,
                        "Разрешите установку из этого приложения", android.widget.Toast.LENGTH_LONG).show();
                return;
            }
        }
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
