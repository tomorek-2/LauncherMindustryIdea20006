package singlaunch.android;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

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

        try (InputStream in = getAssets().open("web/index.html")) {
            String html = new String(in.readAllBytes());
            webView.loadDataWithBaseURL("file:///android_asset/web/", html, "text/html", "UTF-8", null);
        } catch (IOException e) {
            webView.loadData("<html><body style='background:#1a1a1a;color:#ffd379;padding:24px'>"
                    + "Ошибка загрузки UI: " + e.getMessage() + "</body></html>", "text/html", "UTF-8", null);
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

    void openApkInstall(File apk) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
