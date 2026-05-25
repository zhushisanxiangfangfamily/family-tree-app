package com.familytree.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.ValueCallback;
import android.webkit.PermissionRequest;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public class MainActivity extends Activity {
    private WebView webView;
    private ProgressBar progressBar;
    private TextView backBtn;
    private MediaPlayer mediaPlayer;
    private boolean musicPausedByLifecycle = false;
    private boolean unlocked = false;
    private long lastBackTime = 0;
    private ValueCallback<Uri[]> fileChooserCallback;
    private String _currentMemberId = null;
    private String _currentMemberName = null;
    private SharedPreferences _prefs;
    private static final String CHANNEL_ID = "mentions";
    private static final int VERSION_CODE = 39;
    private Handler _timeoutHandler;
    private Runnable _loadTimeoutRunnable;
    private int _loadRetryCount = 0;
    private static final int MAX_RETRIES = 2;
    private static final int LOAD_TIMEOUT_MS = 15000;
    private static final String HOME_URL = "https://zhushisanxiangfangfamily.github.io/family-tree/";
    private static final String VERSION_URL = "https://raw.githubusercontent.com/zhushisanxiangfangfamily/family-tree-app/master/version.txt";
    private static final String UPDATE_APK_URL = "https://zhushisanxiangfangfamily.github.io/family-tree/app-release.apk";

    private static final String EXPORT_JS =
        "(function(){" +
        "var _orig=downloadExportedFile;" +
        "downloadExportedFile=function(html){" +
        "if(window.Android){" +
        "Android.exportHTML(html);" +
        "}else{_orig(html);}" +
        "};" +
        "})();";

    private static final String MUSIC_SYNC_JS =
        "(function(){" +
        "if(typeof syncMusicState==='function')syncMusicState();" +
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
        getWindow().getDecorView().setBackgroundColor(Color.parseColor("#FFF8E7"));

        float density = getResources().getDisplayMetrics().density;

        RelativeLayout layout = new RelativeLayout(this);
        layout.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT));
        layout.setBackgroundColor(Color.parseColor("#FFF8E7"));

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
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setBackgroundColor(Color.parseColor("#FFF8E7"));
        webView.clearCache(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                // Reset load timeout on each page start
                cancelLoadTimeout();
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                showLoadError();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    showLoadError();
                }
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                backBtn.setVisibility(view.canGoBack() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                cancelLoadTimeout();
                _loadRetryCount = 0; // Success, reset retry counter
                if (!unlocked) {
                    unlocked = true;
                    view.evaluateJavascript(
                        "sessionStorage.setItem('ft_unlocked','1');localStorage.setItem('ft_unlocked','1');document.getElementById('lock-overlay').classList.add('hidden');", null);
                }
                {
                    view.evaluateJavascript(HASH_HISTORY_JS, null);
                    view.evaluateJavascript(EXPORT_JS, null);
                    view.evaluateJavascript(MUSIC_SYNC_JS, null);
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
            public void onPermissionRequest(PermissionRequest request) {
                // Grant microphone and other audio permissions for voice chat
                String[] resources = request.getResources();
                for (String r : resources) {
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) {
                        request.grant(request.getResources());
                        return;
                    }
                }
                request.grant(request.getResources());
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

        // Notification channel for @mention notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "家族群聊通知", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("@提醒通知");
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }

        _prefs = getSharedPreferences("ft_prefs", MODE_PRIVATE);

        // Restore user state (survives process death)
        _currentMemberId = _prefs.getString("currentMemberId", null);
        _currentMemberName = _prefs.getString("currentMemberName", null);

        webView.addJavascriptInterface(new WebAppInterface(), "Android");

        // Request runtime permissions BEFORE starting notification service
        // (Android 13+ requires POST_NOTIFICATIONS for foreground service)
        boolean needPerms = false;
        if (Build.VERSION.SDK_INT >= 33) {
            boolean needMic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED;
            boolean needNotif = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
            if (needMic || needNotif) {
                needPerms = true;
                java.util.ArrayList<String> perms = new java.util.ArrayList<>();
                if (needMic) perms.add(Manifest.permission.RECORD_AUDIO);
                if (needNotif) perms.add(Manifest.permission.POST_NOTIFICATIONS);
                requestPermissions(perms.toArray(new String[0]), 1001);
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                needPerms = true;
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1001);
            }
        }
        // Start service only if permissions are already granted (otherwise deferred to callback)
        if (!needPerms) {
            ensureMentionService();
            requestBatteryOptimization();
        }

        _timeoutHandler = new Handler(Looper.getMainLooper());
        webView.loadUrl(HOME_URL);
        startLoadTimeout();
    }

    private void startLoadTimeout() {
        cancelLoadTimeout();
        _loadTimeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (_loadRetryCount < MAX_RETRIES) {
                    _loadRetryCount++;
                    webView.reload();
                    startLoadTimeout();
                } else {
                    showLoadError();
                }
            }
        };
        _timeoutHandler.postDelayed(_loadTimeoutRunnable, LOAD_TIMEOUT_MS);
    }

    private void cancelLoadTimeout() {
        if (_loadTimeoutRunnable != null) {
            _timeoutHandler.removeCallbacks(_loadTimeoutRunnable);
            _loadTimeoutRunnable = null;
        }
    }

    private void showLoadError() {
        String errorHtml = "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<style>body{font-family:system-ui,sans-serif;background:#F5F0E8;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;text-align:center}"
            + "h2{color:#4a3000;margin:16px 0}p{color:#8B7355;margin:8px 0}"
            + ".btn{display:inline-block;margin-top:20px;padding:12px 32px;background:#4a3000;color:#fff;border:none;border-radius:8px;font-size:16px;cursor:pointer;text-decoration:none}"
            + "</style></head><body><div>"
            + "<div style='font-size:48px'>&#128225;</div>"
            + "<h2>加载失败</h2><p>请检查网络连接后重试</p>"
            + "<a class='btn' href='javascript:location.reload()'>重新加载</a>"
            + "</div></body></html>";
        webView.loadDataWithBaseURL(HOME_URL, errorHtml, "text/html", "UTF-8", null);
    }

    private void ensureMentionService() {
        if (_currentMemberId == null) return;
        String savedSessionId = _prefs.getString("currentSessionId", null);
        Intent si = new Intent(MainActivity.this, MentionService.class);
        si.putExtra("memberId", _currentMemberId);
        si.putExtra("memberName", _currentMemberName);
        si.putExtra("sessionId", savedSessionId != null ? savedSessionId : "");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(si);
        } else {
            startService(si);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            // Permissions granted — start notification service now if user is logged in
            ensureMentionService();
            // Mic permission granted — reload page so WebView can use it
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (webView != null) webView.reload();
            }
        }
        // Request battery optimization after permission dialogs are done
        requestBatteryOptimization();
    }

    private void requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent bi = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    bi.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(bi);
                } catch (Exception ignored) {}
            }
        }
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
        final Context appCtx = getApplicationContext();
        final String CH_ID = "apk_dl";
        final int NOTIF_ID = 5001;

        // Create notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CH_ID, "应用更新", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }

        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        final NotificationCompat.Builder nb = new NotificationCompat.Builder(this, CH_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("家族族谱更新")
            .setContentText("准备下载...")
            .setOngoing(true)
            .setProgress(100, 0, true);

        nm.notify(NOTIF_ID, nb.build());

        Toast.makeText(appCtx, "正在后台下载，请在通知栏查看进度", Toast.LENGTH_SHORT).show();
        finish();

        new Thread(new Runnable() {
            @Override
            public void run() {
                File outFile = null;
                try {
                    URL url = new URL(UPDATE_APK_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Connection", "close");
                    conn.connect();

                    int total = conn.getContentLength();
                    InputStream in = conn.getInputStream();

                    File dir = new File(appCtx.getExternalFilesDir(null), "Download");
                    dir.mkdirs();
                    outFile = new File(dir, "家族族谱.apk");
                    FileOutputStream out = new FileOutputStream(outFile);

                    byte[] buf = new byte[8192];
                    int downloaded = 0, len;
                    long lastUpdate = 0;

                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                        downloaded += len;
                        long now = System.currentTimeMillis();
                        if (now - lastUpdate > 300 && total > 0) {
                            lastUpdate = now;
                            int pct = (int) (downloaded * 100L / total);
                            String text = pct + "% (" + (downloaded / 1024) + "KB / " + (total / 1024) + "KB)";
                            nb.setProgress(100, pct, false).setContentText(text);
                            nm.notify(NOTIF_ID, nb.build());
                        }
                    }

                    in.close();
                    out.close();
                    conn.disconnect();

                    // Download complete — show install notification
                    nb.setContentTitle("下载完成").setContentText("点击安装新版本")
                        .setProgress(0, 0, false).setOngoing(false).setAutoCancel(true);

                    Intent install = new Intent(Intent.ACTION_VIEW);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        Uri apkUri = FileProvider.getUriForFile(appCtx, appCtx.getPackageName() + ".fileprovider", outFile);
                        install.setDataAndType(apkUri, "application/vnd.android.package-archive");
                        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } else {
                        install.setDataAndType(Uri.fromFile(outFile), "application/vnd.android.package-archive");
                    }
                    install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    int flags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT;
                    PendingIntent pi = PendingIntent.getActivity(appCtx, 0, install, flags);
                    nb.setContentIntent(pi);
                    nm.notify(NOTIF_ID, nb.build());

                } catch (Exception e) {
                    nb.setContentTitle("下载失败").setContentText("请检查网络后重试")
                        .setProgress(0, 0, false).setOngoing(false).setAutoCancel(true);
                    nm.notify(NOTIF_ID, nb.build());
                    if (outFile != null) outFile.delete();
                }
            }
        }).start();
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

    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            musicPausedByLifecycle = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mediaPlayer != null && musicPausedByLifecycle) {
            mediaPlayer.start();
            musicPausedByLifecycle = false;
        }
        // Check if session was kicked by another device
        SharedPreferences prefs = getSharedPreferences("ft_prefs", MODE_PRIVATE);
        if (prefs.getBoolean("sessionKicked", false)) {
            prefs.edit().putBoolean("sessionKicked", false).apply();
            // Clear login state and reload to show login screen
            _currentMemberId = null;
            _currentMemberName = null;
            prefs.edit()
                .remove("currentMemberId").remove("currentMemberName")
                .remove("currentSessionId").apply();
            stopService(new Intent(MainActivity.this, MentionService.class));
            Toast.makeText(MainActivity.this, "账号在另一设备登录，已退出", Toast.LENGTH_LONG).show();
            webView.reload();
        }
        // Handle notification click with sessionKicked
        Intent intent = getIntent();
        if (intent != null && intent.getBooleanExtra("sessionKicked", false)) {
            intent.removeExtra("sessionKicked");
            prefs.edit().putBoolean("sessionKicked", false).apply();
            _currentMemberId = null;
            _currentMemberName = null;
            prefs.edit()
                .remove("currentMemberId").remove("currentMemberName")
                .remove("currentSessionId").apply();
            stopService(new Intent(MainActivity.this, MentionService.class));
            Toast.makeText(MainActivity.this, "账号在另一设备登录，已退出", Toast.LENGTH_LONG).show();
            webView.reload();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void initMediaPlayer() {
        if (mediaPlayer != null) return;
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setLooping(true);
            AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.bg_music);
            mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception e) {
            mediaPlayer = null;
        }
    }

    private void playMusicUrl(String url) {
        try {
            if (mediaPlayer != null) {
                try { mediaPlayer.stop(); } catch (Exception ignored) {}
                try { mediaPlayer.reset(); } catch (Exception ignored) {}
                try { mediaPlayer.release(); } catch (Exception ignored) {}
            }
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setLooping(true);
            mediaPlayer.setDataSource(url);
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.start();
                }
            });
            mediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    try { mp.reset(); } catch (Exception ignored) {}
                    try {
                        AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.bg_music);
                        mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                        afd.close();
                        mp.prepare();
                        mp.start();
                    } catch (Exception e2) {
                        mediaPlayer = null;
                    }
                    return true;
                }
            });
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            mediaPlayer = null;
        }
    }

    private class WebAppInterface {
        private final ConcurrentHashMap<String, String> ghResults = new ConcurrentHashMap<>();
        private volatile String ghEtag = null;
        private volatile String ghCachedData = null;

        @JavascriptInterface
        public String ghGet(String path) {
            try {
                String urlStr = "https://api.github.com/repos/zhushisanxiangfangfamily/family-tree/contents/" + path + "?ref=master";
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "token " + BuildConfig.GH_TOKEN);
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("Connection", "close");
                String etag = ghEtag;
                if (etag != null) conn.setRequestProperty("If-None-Match", etag);
                int code = conn.getResponseCode();
                if (code == 304) {
                    conn.disconnect();
                    String cached = ghCachedData;
                    if (cached != null) return cached;
                    // No cache — retry without ETag
                    conn = (HttpURLConnection) new URL(urlStr).openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Authorization", "token " + BuildConfig.GH_TOKEN);
                    conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                    conn.setRequestProperty("Connection", "close");
                    code = conn.getResponseCode();
                }
                if (code == 404) return "{\"ok\":false,\"status\":404}";
                if (code != 200) return "{\"ok\":false,\"status\":" + code + "}";
                String newEtag = conn.getHeaderField("ETag");
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                conn.disconnect();
                String result = "{\"ok\":true,\"status\":200,\"data\":" + sb.toString() + "}";
                if (newEtag != null) {
                    ghEtag = newEtag;
                    ghCachedData = result;
                }
                return result;
            } catch (Exception e) {
                return "{\"ok\":false,\"status\":0,\"error\":\"" + e.getMessage() + "\"}";
            }
        }

        // Fetch raw file from raw.githubusercontent.com (CDN, no rate limit, no auth)
        private volatile String ghRawEtag = null;
        private volatile String ghRawCachedData = null;

        @JavascriptInterface
        public String ghRawGet(String path) {
            try {
                String urlStr = "https://raw.githubusercontent.com/zhushisanxiangfangfamily/family-tree/master/" + path;
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Connection", "close");
                String etag = ghRawEtag;
                if (etag != null) conn.setRequestProperty("If-None-Match", etag);
                int code = conn.getResponseCode();
                if (code == 304) {
                    conn.disconnect();
                    String cached = ghRawCachedData;
                    if (cached != null) return cached;
                    conn = (HttpURLConnection) new URL(urlStr).openConnection();
                    conn.setConnectTimeout(4000);
                    conn.setReadTimeout(4000);
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Connection", "close");
                    code = conn.getResponseCode();
                }
                if (code == 404) return "{\"ok\":false,\"status\":404}";
                if (code != 200) return "{\"ok\":false,\"status\":" + code + "}";
                String newEtag = conn.getHeaderField("ETag");
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                conn.disconnect();
                String result = "{\"ok\":true,\"status\":200,\"data\":\"" + sb.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
                if (newEtag != null) {
                    ghRawEtag = newEtag;
                    ghRawCachedData = result;
                }
                return result;
            } catch (Exception e) {
                return "{\"ok\":false,\"status\":0,\"error\":\"" + e.getMessage() + "\"}";
            }
        }

        @JavascriptInterface
        public String ghPut(String path, String content, String sha, String message) {
            try {
                String urlStr = "https://api.github.com/repos/zhushisanxiangfangfamily/family-tree/contents/" + path;
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Authorization", "token " + BuildConfig.GH_TOKEN);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("Connection", "close");
                conn.setDoOutput(true);
                StringBuilder body = new StringBuilder();
                body.append("{\"message\":\"").append(message.replace("\"", "\\\""))
                    .append("\",\"content\":\"").append(content)
                    .append("\",\"branch\":\"master\"");
                if (sha != null && !sha.isEmpty()) {
                    body.append(",\"sha\":\"").append(sha).append("\"");
                }
                body.append("}");
                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.close();
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    return "{\"ok\":false,\"status\":" + code + "}";
                }
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                conn.disconnect();
                return "{\"ok\":true,\"status\":" + code + ",\"data\":" + sb.toString() + "}";
            } catch (Exception e) {
                return "{\"ok\":false,\"status\":0,\"error\":\"" + e.getMessage() + "\"}";
            }
        }

        @JavascriptInterface
        public void ghRawGetAsync(final String path, final String callbackId) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final String result = ghRawGet(path);
                    ghResults.put(callbackId, result);
                    webView.post(new Runnable() {
                        @Override
                        public void run() {
                            webView.evaluateJavascript(
                                "if(window._ghCb)window._ghCb('" + callbackId + "')", null);
                        }
                    });
                }
            }).start();
        }

        @JavascriptInterface
        public void ghGetAsync(final String path, final String callbackId) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final String result = ghGet(path);
                    ghResults.put(callbackId, result);
                    webView.post(new Runnable() {
                        @Override
                        public void run() {
                            webView.evaluateJavascript(
                                "if(window._ghCb)window._ghCb('" + callbackId + "')", null);
                        }
                    });
                }
            }).start();
        }

        @JavascriptInterface
        public void ghPutAsync(final String path, final String content, final String sha, final String message, final String callbackId) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final String result = ghPut(path, content, sha, message);
                    ghResults.put(callbackId, result);
                    webView.post(new Runnable() {
                        @Override
                        public void run() {
                            webView.evaluateJavascript(
                                "if(window._ghCb)window._ghCb('" + callbackId + "')", null);
                        }
                    });
                }
            }).start();
        }

        @JavascriptInterface
        public String ghTakeResult(String callbackId) {
            return ghResults.remove(callbackId);
        }

        @JavascriptInterface
        public void playMusic(String url) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                            return;
                        }
                    } catch (Exception e) {
                        mediaPlayer = null;
                    }
                    if (mediaPlayer != null) {
                        try { mediaPlayer.stop(); } catch (Exception ignored) {}
                        try { mediaPlayer.reset(); } catch (Exception ignored) {}
                        try { mediaPlayer.release(); } catch (Exception ignored) {}
                        mediaPlayer = null;
                    }
                    if (url == null || url.isEmpty()) {
                        initMediaPlayer();
                    } else {
                        playMusicUrl(url);
                    }
                }
            });
        }

        @JavascriptInterface
        public void pauseMusic() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                            mediaPlayer.pause();
                        }
                    } catch (Exception e) { }
                }
            });
        }

        @JavascriptInterface
        public void resumeMusic() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (mediaPlayer == null) {
                            initMediaPlayer();
                        }
                        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                            mediaPlayer.start();
                        }
                    } catch (Exception e) { }
                }
            });
        }

        @JavascriptInterface
        public String isMusicPlaying() {
            try {
                return (mediaPlayer != null && mediaPlayer.isPlaying()) ? "true" : "false";
            } catch (Exception e) {
                return "false";
            }
        }

        @JavascriptInterface
        public void setCurrentUser(String memberId, String memberName, String sessionId) {
            _currentMemberId = memberId;
            _currentMemberName = memberName;
            SharedPreferences prefs = getSharedPreferences("ft_prefs", MODE_PRIVATE);
            prefs.edit()
                .putString("currentMemberId", memberId)
                .putString("currentMemberName", memberName)
                .putString("currentSessionId", sessionId != null ? sessionId : "")
                .putBoolean("sessionKicked", false)
                .apply();

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ensureMentionService();
                }
            });
        }

        @JavascriptInterface
        public void clearCurrentUser() {
            _currentMemberId = null;
            _currentMemberName = null;
            SharedPreferences prefs = getSharedPreferences("ft_prefs", MODE_PRIVATE);
            prefs.edit().remove("currentMemberId").remove("currentMemberName")
                .remove("currentSessionId").putBoolean("sessionKicked", false).apply();
            stopService(new Intent(MainActivity.this, MentionService.class));
        }

        @JavascriptInterface
        public boolean isSessionKicked() {
            SharedPreferences prefs = getSharedPreferences("ft_prefs", MODE_PRIVATE);
            return prefs.getBoolean("sessionKicked", false);
        }

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
