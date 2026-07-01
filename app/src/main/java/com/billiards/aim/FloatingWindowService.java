package com.billiards.aim;

import android.app.AlarmManager;
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
import android.os.SystemClock;
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
import androidx.webkit.WebViewAssetLoader;

public class FloatingWindowService extends Service {

    private static final String TAG = "FloatingWindowService";
    private static final String CHANNEL_ID = "BilliardsAimChannel";
    private static final String ERR_CHANNEL_ID = "BilliardsAimErrChannel";
    private static final int NOTIFICATION_ID = 1001;
    private static volatile boolean isRunning = false;

    private WindowManager windowManager;
    private View floatingView;
    private View resizeHandle;
    private WebView webView;
    private LinearLayout controlPanel;
    private ImageButton btnClose;
    private ImageButton btnMinimize;
    private ImageButton btnSettings;
    private SeekBar opacitySeekBar;
    private WindowManager.LayoutParams params;

    private int initialX, initialY;
    private float initialTouchX, initialTouchY;
    private boolean isDragging = false;
    private boolean isMinimized = false;
    private boolean isResizing = false;
    private int initialWidth, initialHeight;
    private float resizeStartX, resizeStartY;

    private SharedPreferences prefs;
    private Handler mainHandler;
    private WebViewAssetLoader assetLoader;

    // 屏幕尺寸
    private int screenWidth, screenHeight;
    private static final int MIN_WIDTH_DP = 200;
    private static final int MIN_HEIGHT_DP = 280;
    private static final int MINIMIZED_SIZE_DP = 56;
    private static final int EDGE_MARGIN_DP = 8;
    private static final int SNAP_THRESHOLD_DP = 32;

    public static boolean isRunning() {
        return isRunning;
    }

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

        // 如果已经在运行，避免重复创建
        if (isRunning && floatingView != null) {
            return START_STICKY;
        }

        createNotificationChannel();
        Notification notification = createNotification();

        try {
            startForeground(NOTIFICATION_ID, notification);
        } catch (Exception e) {
            Log.e(TAG, "startForeground failed", e);
            stopSelf();
            return START_NOT_STICKY;
        }

        isRunning = true;

        mainHandler.post(() -> {
            try {
                createFloatingWindow();
            } catch (Exception e) {
                Log.e(TAG, "createFloatingWindow failed", e);
                notifyError("悬浮窗启动失败", e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage()));
                stopSelf();
            }
        });

        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 主通知通道
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "台球瞄准辅助",
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("悬浮窗辅助服务正在运行");
            serviceChannel.setSound(null, null);
            serviceChannel.enableVibration(false);
            serviceChannel.setShowBadge(false);

            // 错误通知通道
            NotificationChannel errChannel = new NotificationChannel(
                    ERR_CHANNEL_ID,
                    "悬浮窗错误",
                    NotificationManager.IMPORTANCE_HIGH
            );
            errChannel.setDescription("悬浮窗错误通知");
            errChannel.setSound(null, null);
            errChannel.enableVibration(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
                manager.createNotificationChannel(errChannel);
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
                .setContentTitle("台球瞄准辅助")
                .setContentText("悬浮窗正在运行")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .addAction(new NotificationCompat.Action(0, "打开", openPending))
                .addAction(new NotificationCompat.Action(0, "停止", stopPending))
                .build();
    }

    private void createFloatingWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            throw new IllegalStateException("WindowManager is null");
        }

        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        if (inflater == null) {
            throw new IllegalStateException("LayoutInflater is null");
        }

        floatingView = inflater.inflate(R.layout.floating_window, null);

        webView = floatingView.findViewById(R.id.web_view);
        controlPanel = floatingView.findViewById(R.id.control_panel);
        btnClose = floatingView.findViewById(R.id.btn_close);
        btnMinimize = floatingView.findViewById(R.id.btn_minimize);
        btnSettings = floatingView.findViewById(R.id.btn_settings);
        opacitySeekBar = floatingView.findViewById(R.id.opacity_seekbar);
        resizeHandle = floatingView.findViewById(R.id.resize_handle);

        if (webView == null || controlPanel == null) {
            throw new IllegalStateException("Required views not found in layout");
        }

        setupWebView();
        setupButtons();
        setupOpacityControl();

        int layoutType = getLayoutType();

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = prefs.getInt("last_x", dpToPx(16));
        params.y = prefs.getInt("last_y", dpToPx(100));

        int savedWidth = prefs.getInt("last_w", dpToPx(320));
        int savedHeight = prefs.getInt("last_h", dpToPx(420));
        if (savedWidth > dpToPx(MIN_WIDTH_DP)) params.width = savedWidth;
        if (savedHeight > dpToPx(MIN_HEIGHT_DP)) params.height = savedHeight;

        try {
            windowManager.addView(floatingView, params);
        } catch (Exception e) {
            Log.e(TAG, "addView failed", e);
            String message = e.getClass().getSimpleName() + ": " + (e.getMessage() != null ? e.getMessage() : "");
            notifyError("无法显示悬浮窗", message);
            stopSelf();
            return;
        }

        updateNotificationSuccess();
        setupDragListener();
        setupResizeListener();
        applySavedOpacity();

        // 恢复最小化状态
        if (prefs.getBoolean("minimized", false)) {
            toggleMinimize();
        }
    }

    private int getLayoutType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            return WindowManager.LayoutParams.TYPE_PHONE;
        }
    }

    private void setupWebView() {
        try {
            // 使用 WebViewAssetLoader 安全加载本地资源（Android 11+ 兼容）
            assetLoader = new WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                    .build();

            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setAllowFileAccess(false);  // 禁用 file:// 访问
            settings.setAllowContentAccess(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);

            // 禁用缩放
            settings.setSupportZoom(false);
            settings.setBuiltInZoomControls(false);
            settings.setDisplayZoomControls(false);

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return false;
                }

                @Override
                public android.webkit.WebResourceResponse shouldInterceptRequest(
                        WebView view, WebResourceRequest request) {
                    if (assetLoader != null) {
                        return assetLoader.shouldInterceptRequest(request.getUrl());
                    }
                    return super.shouldInterceptRequest(view, request);
                }
            });

            webView.setWebChromeClient(new WebChromeClient());
            webView.setBackgroundColor(0x00000000);
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

            // 使用 https://appassets.androidplatform.net/assets/ 协议加载
            webView.loadUrl("https://appassets.androidplatform.net/assets/index.html");
        } catch (Exception e) {
            Log.e(TAG, "setupWebView failed", e);
        }
    }

    private void setupButtons() {
        btnClose.setOnClickListener(v -> stopSelf());
        btnMinimize.setOnClickListener(v -> toggleMinimize());
        btnSettings.setOnClickListener(v -> {
            if (isMinimized) {
                toggleMinimize(); // 最小化时点击设置先恢复
                return;
            }
            controlPanel.setVisibility(
                    controlPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE
            );
        });
    }

    private void setupOpacityControl() {
        opacitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                try {
                    float alpha = progress / 100f;
                    webView.setAlpha(alpha);
                    prefs.edit().putFloat("opacity", alpha).apply();
                } catch (Exception ignored) {
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
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
            if (isResizing) return false;

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = params.x;
                    initialY = params.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    isDragging = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    int deltaX = (int) (event.getRawX() - initialTouchX);
                    int deltaY = (int) (event.getRawY() - initialTouchY);
                    if (!isDragging && (Math.abs(deltaX) > dpToPx(6) || Math.abs(deltaY) > dpToPx(6))) {
                        isDragging = true;
                    }
                    if (isDragging) {
                        params.x = initialX + deltaX;
                        params.y = initialY + deltaY;
                        try {
                            windowManager.updateViewLayout(floatingView, params);
                        } catch (Exception ignored) {
                        }
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isDragging) {
                        snapToEdge();
                        prefs.edit()
                                .putInt("last_x", params.x)
                                .putInt("last_y", params.y)
                                .apply();
                    }
                    isDragging = false;
                    return true;
            }
            return false;
        });
    }

    private void setupResizeListener() {
        if (resizeHandle == null) return;

        resizeHandle.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    isResizing = true;
                    initialWidth = floatingView.getWidth();
                    initialHeight = floatingView.getHeight();
                    resizeStartX = event.getRawX();
                    resizeStartY = event.getRawY();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (isResizing) {
                        int newWidth = initialWidth + (int) (event.getRawX() - resizeStartX);
                        int newHeight = initialHeight + (int) (event.getRawY() - resizeStartY);
                        newWidth = Math.max(dpToPx(MIN_WIDTH_DP), newWidth);
                        newHeight = Math.max(dpToPx(MIN_HEIGHT_DP), newHeight);
                        // 限制最大尺寸为屏幕的90%
                        newWidth = Math.min(newWidth, (int) (screenWidth * 0.9));
                        newHeight = Math.min(newHeight, (int) (screenHeight * 0.9));
                        params.width = newWidth;
                        params.height = newHeight;
                        try {
                            windowManager.updateViewLayout(floatingView, params);
                        } catch (Exception ignored) {
                        }
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isResizing = false;
                    prefs.edit()
                            .putInt("last_w", params.width)
                            .putInt("last_h", params.height)
                            .apply();
                    return true;
            }
            return false;
        });
    }

    private void snapToEdge() {
        int snapThreshold = dpToPx(SNAP_THRESHOLD_DP);
        int margin = dpToPx(EDGE_MARGIN_DP);
        int viewWidth = floatingView.getWidth();
        int viewHeight = floatingView.getHeight();

        // 水平吸附
        if (params.x < snapThreshold) {
            params.x = margin;
        } else if (params.x + viewWidth > screenWidth - snapThreshold) {
            params.x = screenWidth - viewWidth - margin;
        }

        // 垂直吸附（状态栏和导航栏避让）
        int statusBarHeight = getStatusBarHeight();
        int navBarHeight = getNavigationBarHeight();

        if (params.y < statusBarHeight + snapThreshold) {
            params.y = statusBarHeight + margin;
        } else if (params.y + viewHeight > screenHeight - navBarHeight - snapThreshold) {
            params.y = screenHeight - viewHeight - navBarHeight - margin;
        }

        try {
            windowManager.updateViewLayout(floatingView, params);
        } catch (Exception ignored) {
        }
    }

    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    private int getNavigationBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    private void toggleMinimize() {
        if (isMinimized) {
            // 恢复
            webView.setVisibility(View.VISIBLE);
            controlPanel.setVisibility(View.VISIBLE);
            int savedWidth = prefs.getInt("last_w", dpToPx(320));
            int savedHeight = prefs.getInt("last_h", dpToPx(420));
            params.width = Math.max(dpToPx(MIN_WIDTH_DP), savedWidth);
            params.height = Math.max(dpToPx(MIN_HEIGHT_DP), savedHeight);
            btnMinimize.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            isMinimized = false;
        } else {
            // 最小化
            webView.setVisibility(View.GONE);
            controlPanel.setVisibility(View.GONE);
            params.width = dpToPx(MINIMIZED_SIZE_DP);
            params.height = dpToPx(MINIMIZED_SIZE_DP);
            btnMinimize.setImageResource(android.R.drawable.ic_menu_add);
            isMinimized = true;
        }
        try {
            windowManager.updateViewLayout(floatingView, params);
        } catch (Exception ignored) {
        }
        prefs.edit().putBoolean("minimized", isMinimized).apply();
    }

    private void updateNotificationSuccess() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                Notification updated = new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("台球瞄准辅助")
                        .setContentText("悬浮窗已显示")
                        .setSmallIcon(android.R.drawable.ic_menu_compass)
                        .setOngoing(true)
                        .setSilent(true)
                        .setCategory(NotificationCompat.CATEGORY_SERVICE)
                        .build();
                nm.notify(NOTIFICATION_ID, updated);
            }
        } catch (Exception ignored) {
        }
    }

    private void notifyError(String title, String message) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            Intent openIntent = new Intent(this, MainActivity.class);
            openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent openPending = PendingIntent.getActivity(this, 0, openIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            Notification n = new NotificationCompat.Builder(this, ERR_CHANNEL_ID)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentIntent(openPending)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build();

            if (nm != null) {
                nm.notify((int) (System.currentTimeMillis() & 0x7fffffff), n);
            }
        } catch (Throwable ignored) {
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (floatingView != null && windowManager != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception e) {
                Log.w(TAG, "removeView failed", e);
            }
        }
        if (webView != null) {
            webView.destroy();
        }
        assetLoader = null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        // Android 14+ 无法通过 AlarmManager 启动前台服务，使用 JobScheduler 备用
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+: 使用 JobScheduler
                KeepAliveJobService.schedule(this);
            } else {
                // Android 14 以下: 使用 AlarmManager
                scheduleAlarmRestart();
            }
        } catch (Exception e) {
            Log.e(TAG, "onTaskRemoved restart failed", e);
        }
    }

    private void scheduleAlarmRestart() {
        try {
            Intent restartIntent = new Intent(getApplicationContext(), ServiceRestarter.class);
            restartIntent.setAction("ACTION_RESTART_SERVICE");
            PendingIntent restartPending = PendingIntent.getBroadcast(getApplicationContext(), 1, restartIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                long triggerAt = SystemClock.elapsedRealtime() + 2000;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android 12+: 检查是否有 SCHEDULE_EXACT_ALARM 权限
                    if (am.canScheduleExactAlarms()) {
                        am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, restartPending);
                    } else {
                        am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, restartPending);
                    }
                } else {
                    am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, restartPending);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "scheduleAlarmRestart failed", e);
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 屏幕旋转/折叠时更新屏幕尺寸
        initScreenMetrics();
        snapToEdge();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
