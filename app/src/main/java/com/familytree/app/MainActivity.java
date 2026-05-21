package com.familytree.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.ValueCallback;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private WebView webView;
    private ProgressBar progressBar;
    private TextView backBtn;
    private boolean unlocked = false;
    private long lastBackTime = 0;
    private ValueCallback<Uri[]> fileChooserCallback;
    private static final int VERSION_CODE = 19;
    private static final String HOME_URL = "https://zhushisanxiangfangfamily.github.io/family-tree-test/";
    private static final String VERSION_URL = "https://raw.githubusercontent.com/zhushisanxiangfangfamily/family-tree-app/master/version.txt";
    private static final String UPDATE_APK_URL = "https://github.com/zhushisanxiangfangfamily/family-tree-app/releases/latest/download/app-debug.apk";

    private static final String EXPORT_JS =
        "(function(){" +
        "var _orig=downloadExportedFile;" +
        "downloadExportedFile=function(html){" +
        "if(window.Android){" +
        "Android.exportHTML(html);" +
        "}else{_orig(html);}" +
        "};" +
        "})();";

    private static final String HASH_HISTORY_JS =
        "(function(){" +
        "var _openMemberDetail=openMemberDetail,_openModal=openModal;" +
        "var _busy=false,_depth=0;" +
        "function enter(){_depth++;}" +
        "function leave(){_depth--;}" +
        "function top(){return _depth===1;}" +
        "function pushHash(h){" +
        "if(!_busy&&location.hash!==h){" +
        "var cur=location.hash.replace('#','');" +
        "if(cur.indexOf('member-')===0||cur.indexOf('modal-')===0){" +
        "history.replaceState(null,'',h);" +
        "}else{history.pushState(null,'',h);}" +
        "}" +
        "}" +
        // Wrap openMemberDetail - only outermost call pushes hash
        "openMemberDetail=function(id){" +
        "enter();" +
        "_openMemberDetail(id);" +
        "if(top()&&!_busy)pushHash('#member-'+id);" +
        "leave();" +
        "};" +
        // Wrap openModal - only push if called directly (not from openMemberDetail)
        "openModal=function(id){" +
        "enter();" +
        "_openModal(id);" +
        "if(top()&&!_busy)pushHash('#modal-'+id);" +
        "leave();" +
        "};" +
        // Replace closeModal with history.back
        "closeModal=function(id){history.back();};" +
        // Handle hash change
        "window.addEventListener('hashchange',function(){" +
        "_busy=true;" +
        // Close all open modals
        "document.querySelectorAll('.modal-overlay.open').forEach(function(m){" +
        "m.classList.remove('open');" +
        "});" +
        "var h=location.hash.replace('#','');" +
        "if(h.indexOf('member-')===0){" +
        "_openMemberDetail(parseInt(h.replace('member-','')));" +
        "}else if(h.indexOf('modal-')===0){" +
        "_openModal(h.replace('modal-',''));" +
        "}" +
        "setTimeout(function(){_busy=false;},50);" +
        "});" +
        // Set initial hash
        "if(!location.hash){" +
        "var el=document.querySelector('.page.active');" +
        "if(el&&el.id)history.replaceState(null,'','#'+el.id.replace('page-',''));" +
        "}" +
        "})();";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        float density = getResources().getDisplayMetrics().density;

        RelativeLayout layout = new RelativeLayout(this);
        layout.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT));

        webView = new WebView(this);
        RelativeLayout.LayoutParams wvParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT);
        layout.addView(webView, wvParams);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setVisibility(View.GONE);
        RelativeLayout.LayoutParams pbParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, 6);
        pbParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        layout.addView(progressBar, pbParams);

        // Circular back button, positioned above the site's bottom tab bar
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xCC1A1A1A);

        backBtn = new TextView(this);
        backBtn.setText("←");
        backBtn.setTextColor(Color.WHITE);
        backBtn.setTextSize(20);
        backBtn.setGravity(Gravity.CENTER);
        backBtn.setBackground(circle);
        backBtn.setVisibility(View.GONE);
        int btnSize = (int)(48 * density);
        RelativeLayout.LayoutParams btnParams = new RelativeLayout.LayoutParams(btnSize, btnSize);
        btnParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        btnParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        btnParams.setMargins(0, 0, (int)(16 * density), (int)(72 * density));
        layout.addView(backBtn, btnParams);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (webView.canGoBack()) webView.goBack();
            }
        });

        setContentView(layout);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " FamilyTreeApp/1.0");

        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.clearCache(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                String errorHtml = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                    + "<body style='background:#F5F0E8;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;font-family:system-ui,sans-serif;text-align:center'>"
                    + "<div><div style='font-size:48px;margin-bottom:16px'>&#128225;</div>"
                    + "<h2 style='color:#4a3000'>网络连接失败</h2>"
                    + "<p style='color:#8B7355'>请检查网络后重新打开</p></div></body></html>";
                view.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null);
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                backBtn.setVisibility(view.canGoBack() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!unlocked) {
                    unlocked = true;
                    view.evaluateJavascript(
                        "sessionStorage.setItem('ft_unlocked','1');location.reload();", null);
                } else {
                    view.evaluateJavascript(HASH_HISTORY_JS, null);
                    view.evaluateJavascript(EXPORT_JS, null);
                    view.evaluateJavascript(
                        "(function(){var s=document.createElement('style');s.textContent='html,body{overscroll-behavior:none;}';document.head.appendChild(s);})();", null);
                    checkUpdate();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                if (progress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(progress);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                fileChooserCallback = callback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                startActivityForResult(Intent.createChooser(intent, "选择图片"), 1);
                return true;
            }
        });

        webView.addJavascriptInterface(new WebAppInterface(), "Android");

        webView.loadUrl(HOME_URL);
    }

    private void checkUpdate() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(VERSION_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                    String line = reader.readLine();
                    reader.close();
                    conn.disconnect();
                    final int remoteVersion = Integer.parseInt(line.trim());
                    if (remoteVersion > VERSION_CODE) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showUpdateDialog();
                            }
                        });
                    }
                } catch (Exception e) {
                    // Network error, silently skip
                }
            }
        }).start();
    }

    private void showUpdateDialog() {
        new AlertDialog.Builder(this)
            .setTitle("发现新版本（V" + VERSION_CODE + " → 最新版）")
            .setMessage("家族族谱 App 有新版本可用，更新后才能继续使用。\n\n点击「立即更新」将自动下载并安装。")
            .setCancelable(false)
            .setPositiveButton("立即更新", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    downloadAndInstall();
                }
            })
            .show();
    }

    private void downloadAndInstall() {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(UPDATE_APK_URL));
            request.setTitle("家族族谱更新");
            request.setDescription("正在下载新版本...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "家族族谱.apk");
            request.setMimeType("application/vnd.android.package-archive");
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            dm.enqueue(request);
            Toast.makeText(this, "正在下载，请在通知栏查看进度，下载完成后点击安装", Toast.LENGTH_LONG).show();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "下载失败，请检查网络后重试", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            long now = System.currentTimeMillis();
            if (now - lastBackTime < 2000) {
                super.onBackPressed();
            } else {
                lastBackTime = now;
                Toast.makeText(this, "再按一次退出", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            } else {
                long now = System.currentTimeMillis();
                if (now - lastBackTime < 2000) {
                    return super.onKeyDown(keyCode, event);
                } else {
                    lastBackTime = now;
                    Toast.makeText(this, "再按一次退出", Toast.LENGTH_SHORT).show();
                    return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && fileChooserCallback != null) {
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                results = new Uri[]{data.getData()};
            }
            fileChooserCallback.onReceiveValue(results);
            fileChooserCallback = null;
        }
    }

    private class WebAppInterface {
        @JavascriptInterface
        public void exportHTML(String html) {
            try {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                String filename = "族谱_" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) + ".html";
                File file = new File(dir, filename);
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(html.getBytes("UTF-8"));
                fos.close();
                final String path = file.getAbsolutePath();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "族谱已导出到 " + path, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "导出失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }
}
