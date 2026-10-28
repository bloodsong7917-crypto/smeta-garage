package ru.garage.smeta;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Оболочка над опубликованной сметой: страница и её база живут в Claude, вход один раз. */
public class MainActivity extends Activity {
    static final String HOME = "https://claude.ai/artifact/7X81EGdbRck1tEHcKKoE29";

    WebView web;
    ProgressBar bar;
    View errorView;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        FrameLayout root = new FrameLayout(this);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        // Без пометки «wv» страницы входа не отказывают встроенному браузеру
        s.setUserAgentString(s.getUserAgentString().replace("; wv)", ")").replaceAll("Version/\\S+ ", ""));

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return openOutside(r.getUrl());
            }

            @Override
            public void onPageStarted(WebView v, String url, Bitmap f) {
                errorView.setVisibility(View.GONE);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                CookieManager.getInstance().flush();
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) errorView.setVisibility(View.VISIBLE);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                bar.setProgress(p);
                bar.setVisibility(p < 100 ? View.VISIBLE : View.GONE);
            }

            // Ссылки «в новом окне» открываем здесь же
            @Override
            public boolean onCreateWindow(WebView v, boolean dialog, boolean user, Message msg) {
                WebView tmp = new WebView(MainActivity.this);
                tmp.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView t, WebResourceRequest r) {
                        if (!openOutside(r.getUrl())) web.loadUrl(r.getUrl().toString());
                        t.destroy();
                        return true;
                    }
                });
                ((WebView.WebViewTransport) msg.obj).setWebView(tmp);
                msg.sendToTarget();
                return true;
            }
        });

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.getProgressDrawable().setTint(0xFFC4521C);

        errorView = buildErrorView();
        errorView.setVisibility(View.GONE);

        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        root.addView(errorView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(bar, new FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP));
        root.setFitsSystemWindows(true);
        setContentView(root);

        if (saved != null) web.restoreState(saved);
        else web.loadUrl(startUrl(getIntent()));
    }

    String startUrl(Intent i) {
        Uri u = i != null ? i.getData() : null;
        return u != null && "claude.ai".equals(u.getHost()) ? u.toString() : HOME;
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (i.getData() != null) web.loadUrl(startUrl(i));
    }

    /** Claude, вход и сама страница остаются в приложении; остальное (телефон, почта, чужие сайты) уходит наружу. */
    boolean openOutside(Uri u) {
        String scheme = u.getScheme() == null ? "" : u.getScheme();
        String host = u.getHost() == null ? "" : u.getHost();
        if (scheme.equals("https") && (host.equals("claude.ai") || host.endsWith(".claude.ai")
                || host.endsWith("anthropic.com") || host.endsWith("claudeusercontent.com")
                || host.endsWith("google.com") || host.endsWith("apple.com"))) {
            return false;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (Exception ignored) {
        }
        return true;
    }

    View buildErrorView() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Color.parseColor("#EEF0EF"));
        box.setPadding(dp(24), dp(24), dp(24), dp(24));
        TextView t = new TextView(this);
        t.setText("Нет связи с базой.\nПроверьте интернет и попробуйте ещё раз.");
        t.setTextSize(16);
        t.setTextColor(Color.parseColor("#1C2226"));
        t.setGravity(Gravity.CENTER);
        Button b = new Button(this);
        b.setText("Повторить");
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                errorView.setVisibility(View.GONE);
                String cur = web.getUrl();
                if (cur == null || cur.startsWith("data:") || cur.startsWith("about:")) web.loadUrl(HOME);
                else web.reload();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        box.addView(t);
        box.addView(b, lp);
        return box;
    }

    int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }
}
