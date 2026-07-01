#!/bin/bash
cd ~/billiards-aim

cat > app/src/main/java/com/billiards/aim/FloatingWindowService.java << 'ENDOFFILE'
package com.billiards.aim;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class FloatingWindowService extends Service {
    private static final String TAG = "FloatingWindowService";
    private static final String CHANNEL_ID = "BilliardsAimChannel";
    private static final int NOTIFICATION_ID = 1001;
    private static volatile boolean isRunning = false;
    private WindowManager windowManager;
    private View floatingView;
    private WebView webView;
    private LinearLayout controlPanel;
    private ImageButton btnClose, btnMinimize, btnSettings;
    private SeekBar opacitySeekBar;
    private WindowManager.LayoutParams params;
    private int initialX, initialY;
    private float initialTouchX, initialTouchY;
    private boolean isDragging = false;
    private boolean isMinimized = false;
    private SharedPreferences prefs;
    private Handler mainHandler;
    private int screenWidth, screenHeight;
    private static final int MIN_WIDTH_DP = 200;
    private static final int MIN_HEIGHT_DP = 280;
    private static final int MINIMIZED_SIZE_DP = 56;

    public static boolean isRunning() { return isRunning; }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("billiards_prefs", Context.MODE_PRIVATE);
        mainHandler = new Handler(Looper.getMainLooper());
        initScreenMetrics();
    }

    private void initScreenMetrics() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenWidth = dm.widthPixels;
        screenHeight = dm.heightPixels;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "ACTION_STOP_SERVICE".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (isRunning && floatingView != null) return START_STICKY;

        try {
            createNotificationChannel();
            Notification notification = createNotification();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "startForeground failed", e);
            stopSelf();
            return START_NOT_STICKY;
        }

        isRunning = true;
        mainHandler.post(() -> {
            try { createFloatingWindow(); }
            catch (Exception e) { Log.e(TAG, "createFloatingWindow failed", e); stopSelf(); }
        });
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "台球瞄准辅助", NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("悬浮窗服务运行中");
                channel.setSound(null, null);
                channel.enableVibration(false);
                channel.setShowBadge(false);
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent stopIntent = new Intent(this, FloatingWindowService.class);
        stopIntent.setAction("ACTION_STOP_SERVICE");
        PendingIntent stopPending = PendingIntent.getService(this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPending = PendingIntent.getActivity(this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("台球瞄准辅助").setContentText("悬浮窗运行中")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW).setOngoing(true)
            .setSilent(true).setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "打开", openPending).addAction(0, "关闭", stopPending).build();
    }

    private void createFloatingWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) throw new IllegalStateException("WindowManager is null");
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        if (inflater == null) throw new IllegalStateException("LayoutInflater is null");
        floatingView = inflater.inflate(R.layout.floating_window, null);
        webView = floatingView.findViewById(R.id.web_view);
        controlPanel = floatingView.findViewById(R.id.control_panel);
        btnClose = floatingView.findViewById(R.id.btn_close);
        btnMinimize = floatingView.findViewById(R.id.btn_minimize);
        btnSettings = floatingView.findViewById(R.id.btn_settings);
        opacitySeekBar = floatingView.findViewById(R.id.opacity_seekbar);
        if (webView == null || controlPanel == null) throw new IllegalStateException("Required views not found");
        setupWebView(); setupButtons(); setupOpacityControl();
        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONT
    private void setupButtons() {
        btnClose.setOnClickListener(v -> stopSelf());
        btnMinimize.setOnClickListener(v -> toggleMinimize());
        btnSettings.setOnClickListener(v -> {
            if (isMinimized) { toggleMinimize(); return; }
            controlPanel.setVisibility(controlPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        });
    }

    private void setupOpacityControl() {
        opacitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                try { float alpha = progress / 100f; webView.setAlpha(alpha); prefs.edit().putFloat("opacity", alpha).apply(); }
                catch (Exception ignored) {}
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void applySavedOpacity() {
        float savedOpacity = prefs.getFloat("opacity", 0.5f);
        webView.setAlpha(savedOpacity);
        opacitySeekBar.setProgress((int) (savedOpacity * 100));
    }

    private void setupDragListener() {
        View dragBar = floatingView.findViewById(R.id.control_bar);
        if (dragBar == null) return;
        dragBar.setOnTouchListener((v, event) -> {
            if (isMinimized) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = params.x; initialY = params.y;
                    initialTouchX = event.getRawX(); initialTouchY = event.getRawY(); isDragging = false; return true;
                case MotionEvent.ACTION_MOVE:
                    int deltaX = (int) (event.getRawX() - initialTouchX);
                    int deltaY = (int) (event.getRawY() - initialTouchY);
                    if (!isDragging && (Math.abs(deltaX) > dpToPx(6) || Math.abs(deltaY) > dpToPx(6))) isDragging = true;
                    if (isDragging) { params.x = initialX + deltaX; params.y = initialY + deltaY;
                        try { windowManager.updateViewLayout(floatingView, params); } catch (Exception ignored) {} }
                    return true;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                    if (isDragging) prefs.edit().putInt("last_x", params.x).putInt("last_y", params.y).apply();
                    isDragging = false; return true;
            } return false;
        });
    }

    private void toggleMinimize() {
        if (isMinimized) {
            webView.setVisibility(View.VISIBLE); controlPanel.setVisibility(View.VISIBLE);
            int savedWidth = prefs.getInt("last_w", dpToPx(320));
            int savedHeight = prefs.getInt("last_h", dpToPx(420));
            params.width = Math.max(dpToPx(MIN_WIDTH_DP), savedWidth);
            params.height = Math.max(dpToPx(MIN_HEIGHT_DP), savedHeight);
            btnMinimize.setImageResource(android.R.drawable.ic_menu_close_clear_cancel); isMinimized = false;
        } else {
            webView.setVisibility(View.GONE); controlPanel.setVisibility(View.GONE);
            params.width = dpToPx(MINIMIZED_SIZE_DP); params.height = dpToPx(MINIMIZED_SIZE_DP);
            btnMinimize.setImageResource(android.R.drawable.ic_menu_add); isMinimized = true;
        }
        try { windowManager.updateViewLayout(floatingView, params); } catch (Exception ignored) {}
        prefs.edit().putBoolean("minimized", isMinimized).apply();
    }

    @Override public void onDestroy() {
        super.onDestroy(); isRunning = false;
        if (floatingView != null && windowManager != null) {
            try { windowManager.removeView(floatingView); } catch (Exception e) { Log.w(TAG, "removeView failed", e); }
        }
        if (webView != null) webView.destroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
    private int dpToPx(int dp) { return (int) (dp * getResources().getDisplayMetrics().density + 0.5f); }
}
ENDOFFILE

git add app/src/main/java/com/billiards/aim/FloatingWindowService.java
git commit -m "fix: Android 16 compatibility - enhance startForeground"
git push origin main
echo "=== 修复完成，已推送到 GitHub ==="
