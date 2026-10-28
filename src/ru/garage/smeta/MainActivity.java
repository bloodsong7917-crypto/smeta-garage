package ru.garage.smeta;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Смета работает без интернета: страница лежит в приложении, данные — в файле на телефоне. */
public class MainActivity extends Activity {
    static final int REQ_EXPORT = 1, REQ_PICK = 2;

    WebView web;
    String pendingExport;
    ValueCallback<Uri[]> pendingPick;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        web.addJavascriptInterface(new Bridge(), "Android");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }
        });

        // Выбор файла резервной копии для «Загрузить из файла»
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (pendingPick != null) pendingPick.onReceiveValue(null);
                pendingPick = cb;
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try {
                    startActivityForResult(i, REQ_PICK);
                } catch (Exception e) {
                    pendingPick = null;
                    return false;
                }
                return true;
            }
        });

        web.setFitsSystemWindows(true);
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    File dataFile() {
        return new File(getFilesDir(), "data.json");
    }

    class Bridge {
        @JavascriptInterface
        public String load() {
            File f = dataFile();
            if (!f.exists()) return null;
            try (InputStream in = new FileInputStream(f)) {
                return readAll(in);
            } catch (Exception e) {
                return null;
            }
        }

        @JavascriptInterface
        public synchronized void save(String json) {
            // Пишем во временный файл и подменяем, чтобы не испортить данные при сбое
            File tmp = new File(getFilesDir(), "data.json.tmp");
            try (OutputStream out = new FileOutputStream(tmp)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                return;
            }
            tmp.renameTo(dataFile());
        }

        @JavascriptInterface
        public void exportFile(final String json, final String name) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    pendingExport = json;
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("application/json");
                    i.putExtra(Intent.EXTRA_TITLE, name);
                    startActivityForResult(i, REQ_EXPORT);
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        Uri u = res == RESULT_OK && data != null ? data.getData() : null;
        if (req == REQ_PICK && pendingPick != null) {
            pendingPick.onReceiveValue(u != null ? new Uri[]{u} : null);
            pendingPick = null;
        } else if (req == REQ_EXPORT && pendingExport != null) {
            boolean ok = false;
            if (u != null) {
                try (OutputStream out = getContentResolver().openOutputStream(u)) {
                    out.write(pendingExport.getBytes(StandardCharsets.UTF_8));
                    ok = true;
                } catch (Exception ignored) {
                }
                web.evaluateJavascript("window.onExported&&window.onExported(" + ok + ")", null);
            }
            pendingExport = null;
        }
    }

    static String readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return new String(b.toByteArray(), StandardCharsets.UTF_8);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
