package com.familytree.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Base64;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONObject;

public class MentionService extends Service {
    private static final String CHANNEL_ID = "mentions";
    private static final int FOREGROUND_ID = 2001;
    private static final String RAW_URL = "https://api.github.com/repos/zhushisanxiangfangfamily/family-tree/contents/data/mentions.json";
    private static final String SESSION_URL = "https://api.github.com/repos/zhushisanxiangfangfamily/family-tree/contents/data/messages.json";
    private static final long POLL_INTERVAL = 60000;
    private static final String GH_TOKEN = com.familytree.app.BuildConfig.GH_TOKEN;

    private Handler _handler;
    private Runnable _pollRunnable;
    private String _memberId;
    private String _memberName;
    private String _etag;
    private String _cachedData;
    private long _lastNotifiedId;
    private boolean _running;
    private String _sessionId;
    private String _sessionEtag;
    private boolean _sessionFirstPoll = true;
    private PowerManager.WakeLock _wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        _handler = new Handler(Looper.getMainLooper());
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        _wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FamilyTree:Mention");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, getString(R.string.app_name) + "通知", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            _memberId = intent.getStringExtra("memberId");
            _memberName = intent.getStringExtra("memberName");
            _sessionId = intent.getStringExtra("sessionId");
        }
        if (_memberId == null) {
            SharedPreferences prefs = getSharedPreferences("ft_prefs", MODE_PRIVATE);
            _memberId = prefs.getString("currentMemberId", null);
            _memberName = prefs.getString("currentMemberName", null);
            _sessionId = prefs.getString("currentSessionId", null);
        }
        if (_memberId == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        SharedPreferences prefs = getSharedPreferences("ft_prefs", MODE_PRIVATE);
        _etag = prefs.getString("mentionEtag", null);
        _cachedData = prefs.getString("mentionData", null);
        _lastNotifiedId = prefs.getLong("lastMentionId", 0);

        Notification fgNotify = buildForegroundNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FOREGROUND_ID, fgNotify, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(FOREGROUND_ID, fgNotify);
        }
        startPolling();
        _running = true;
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        _running = false;
        stopPolling();
        if (_wakeLock != null && _wakeLock.isHeld()) _wakeLock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildForegroundNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
            Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("家族群聊")
            .setContentText("@提醒通知已开启")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pi)
            .build();
    }

    private void startPolling() {
        if (_pollRunnable != null) return;
        _pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!_running) return;
                // Keep CPU awake for the duration of HTTP requests (15s max)
                if (_wakeLock != null && !_wakeLock.isHeld()) {
                    _wakeLock.acquire(15000);
                }
                pollMentions();
                checkSession();
                if (_running) _handler.postDelayed(this, POLL_INTERVAL);
            }
        };
        _handler.post(_pollRunnable);
    }

    private void stopPolling() {
        if (_pollRunnable != null) {
            _handler.removeCallbacks(_pollRunnable);
            _pollRunnable = null;
        }
    }


    private void pollMentions() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(RAW_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Authorization", "token " + GH_TOKEN);
                    conn.setRequestProperty("Connection", "close");
                    if (_etag != null) conn.setRequestProperty("If-None-Match", _etag);

                    int code = conn.getResponseCode();
                    if (code == 304) {
                        conn.disconnect();
                        return; // No new data
                    }
                    if (code == 404) {
                        conn.disconnect();
                        return; // File doesn't exist yet
                    }
                    if (code != 200) {
                        Log.w("MentionSvc", "poll HTTP " + code);
                        conn.disconnect();
                        return;
                    }

                    String newEtag = conn.getHeaderField("ETag");
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    conn.disconnect();

                    // GitHub API wraps content in JSON with base64 encoding
                    JSONObject apiResp = new JSONObject(sb.toString());
                    String b64Content = apiResp.optString("content", "");
                    String raw;
                    if (b64Content.length() > 0) {
                        raw = new String(Base64.decode(b64Content.replaceAll("\\s", ""), Base64.DEFAULT), "UTF-8");
                    } else {
                        raw = sb.toString(); // fallback: raw content
                    }

                    // Cache ETag + response
                    if (newEtag != null) {
                        _etag = newEtag;
                        _cachedData = raw;
                        SharedPreferences prefs = getSharedPreferences("ft_prefs", MODE_PRIVATE);
                        prefs.edit()
                            .putString("mentionEtag", _etag)
                            .putString("mentionData", _cachedData)
                            .apply();
                    }

                    // Parse mentions and check for new ones targeting current user
                    JSONArray mentions = new JSONArray(raw);
                    long maxId = _lastNotifiedId;
                    int matchCount = 0;
                    int notifyCount = 0;
                    boolean firstRun = (_lastNotifiedId == 0);
                    for (int i = 0; i < mentions.length(); i++) {
                        JSONObject m = mentions.getJSONObject(i);
                        String toId = m.optString("toMemberId", "");
                        if (!toId.equals(_memberId)) continue;
                        matchCount++;
                        long id = m.optLong("id", 0);
                        if (id > maxId) maxId = id;
                        if (id <= _lastNotifiedId) continue;
                        // First run: silently catch up, don't notify old mentions
                        if (firstRun) continue;

                        String fromName = m.optString("fromMemberName", "有人");
                        String text = m.optString("text", "");
                        notifyCount++;
                        showMentionNotification(fromName, text, id);
                    }

                    if (firstRun) {
                        Log.d("MentionSvc", "First sync: " + matchCount + " mentions, waiting for new");
                    } else {
                        Log.d("MentionSvc", "Matched " + matchCount + " notified " + notifyCount);
                    }

                    if (maxId > _lastNotifiedId) {
                        _lastNotifiedId = maxId;
                        SharedPreferences prefs = getSharedPreferences("ft_prefs", MODE_PRIVATE);
                        prefs.edit().putLong("lastMentionId", maxId).apply();
                    }
                } catch (Exception e) {
                    Log.e("MentionSvc", "Poll error: " + e.getMessage(), e);
                }
            }
        }).start();
    }

    private void checkSession() {
        if (_sessionId == null || _memberId == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(SESSION_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Authorization", "token " + GH_TOKEN);
                    conn.setRequestProperty("Connection", "close");
                    if (_sessionEtag != null) conn.setRequestProperty("If-None-Match", _sessionEtag);

                    int code = conn.getResponseCode();
                    if (code == 304) { conn.disconnect(); return; }
                    if (code != 200) { conn.disconnect(); return; }

                    String newEtag = conn.getHeaderField("ETag");
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    conn.disconnect();

                    if (newEtag != null) _sessionEtag = newEtag;

                    // Parse messages.json: {"_sessions":{"memberId":{"sessionId":...}},"messages":[...]}
                    JSONObject apiResp = new JSONObject(sb.toString());
                    String b64Content = apiResp.optString("content", "");
                    String raw;
                    if (b64Content.length() > 0) {
                        raw = new String(Base64.decode(b64Content.replaceAll("\\s", ""), Base64.DEFAULT), "UTF-8");
                    } else {
                        raw = sb.toString();
                    }
                    JSONObject msgFile = new JSONObject(raw);
                    JSONObject sessions = msgFile.optJSONObject("_sessions");
                    String remoteSid = null;
                    if (sessions != null) {
                        JSONObject entry = sessions.optJSONObject(_memberId);
                        if (entry != null) {
                            remoteSid = entry.optString("sessionId", null);
                        }
                    }

                    // First poll: store baseline, don't check
                    if (_sessionFirstPoll) {
                        _sessionFirstPoll = false;
                        if (remoteSid != null && !remoteSid.isEmpty()) {
                            _sessionId = remoteSid;
                            SharedPreferences prefs = getSharedPreferences("ft_prefs", MODE_PRIVATE);
                            prefs.edit().putString("currentSessionId", _sessionId).apply();
                        }
                        return;
                    }

                    // Check if session was taken over by another device
                    if (remoteSid != null && !remoteSid.equals(_sessionId)) {
                        Log.w("MentionSvc", "Session kicked: local=" + _sessionId + " remote=" + remoteSid);
                        SharedPreferences prefs = getSharedPreferences("ft_prefs", MODE_PRIVATE);
                        prefs.edit().putBoolean("sessionKicked", true).apply();
                        _handler.post(new Runnable() {
                            @Override
                            public void run() {
                                showKickedNotification();
                            }
                        });
                        // Don't stop service — let the user see the notification
                    }
                } catch (Exception e) {
                    Log.e("MentionSvc", "Session check error: " + e.getMessage(), e);
                }
            }
        }).start();
    }

    private void showKickedNotification() {
        Intent intent = new Intent(MentionService.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("sessionKicked", true);
        PendingIntent pi = PendingIntent.getActivity(MentionService.this, 3001,
            intent, Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(MentionService.this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("账号在另一设备登录")
            .setContentText("当前会话已退出，请重新登录")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .build();

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(3001, notification);
    }

    private void showMentionNotification(final String fromName, final String text, final long id) {
        _handler.post(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(MentionService.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                PendingIntent pi = PendingIntent.getActivity(MentionService.this, (int) id,
                    intent, Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT);

                Notification notification = new NotificationCompat.Builder(MentionService.this, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("💬 " + fromName + " @了你")
                    .setContentText(text)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setSilent(true)
                    .setContentIntent(pi)
                    .build();

                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                nm.notify((int) id, notification);
            }
        });
    }
}
