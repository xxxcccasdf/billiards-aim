import android.app.AlarmManager;
package com.billiards.aim;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_NOTIFICATION_PERMISSION = 1002;
    private static final int REQUEST_EXACT_ALARM_PERMISSION = 1003;

    private Button btnStartFloat;
    private Button btnStopFloat;
    private TextView tvStatus;
    private TextView tvTip;

    private final ActivityResultLauncher<Intent> overlayLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (canDrawOverlays()) {
                    Toast.makeText(this, "悬浮窗权限已开启", Toast.LENGTH_SHORT).show();
                    checkAllPermissions();
                } else {
                    Toast.makeText(this, "未开启悬浮窗权限", Toast.LENGTH_LONG).show();
                    tvStatus.setText("未开启悬浮窗权限");
                    tvTip.setText("请先开启「显示在其他应用上层」权限");
                    btnStartFloat.setEnabled(false);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        checkAllPermissions();
    }

    private void initViews() {
        btnStartFloat = findViewById(R.id.btn_start_float);
        btnStopFloat = findViewById(R.id.btn_stop_float);
        tvStatus = findViewById(R.id.tv_status);
        tvTip = findViewById(R.id.tv_tip);

        btnStartFloat.setOnClickListener(v -> startFloatingWindow());
        btnStopFloat.setOnClickListener(v -> stopFloatingWindow());
    }

    private void checkAllPermissions() {
        // 1. 检查悬浮窗权限
        if (!canDrawOverlays()) {
            tvStatus.setText("未开启悬浮窗权限");
            tvTip.setText("请先开启「显示在其他应用上层」权限");
            btnStartFloat.setEnabled(false);
            requestOverlayPermission();
            return;
        }

        // 2. 检查通知权限 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATION_PERMISSION);
                return;
            }
        }

        // 3. 检查精确闹钟权限 (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_EXACT_ALARM_PERMISSION);
                return;
            }
        }

        updateStatus();
    }

    private boolean canDrawOverlays() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            overlayLauncher.launch(intent);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "通知权限已开启", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "未开启通知权限，悬浮窗可能无法正常运行", Toast.LENGTH_LONG).show();
            }
            checkAllPermissions();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EXACT_ALARM_PERMISSION) {
            checkAllPermissions();
        }
    }

    private void startFloatingWindow() {
        if (!canDrawOverlays()) {
            Toast.makeText(this, "请先开启悬浮窗权限", Toast.LENGTH_SHORT).show();
            requestOverlayPermission();
            return;
        }

        Intent serviceIntent = new Intent(this, FloatingWindowService.class);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }

            Toast.makeText(this, "悬浮窗已启动", Toast.LENGTH_SHORT).show();
            updateStatus();

            // 延迟返回桌面
            new android.os.Handler(Looper.getMainLooper()).postDelayed(() -> {
                Intent homeIntent = new Intent(Intent.ACTION_MAIN);
                homeIntent.addCategory(Intent.CATEGORY_HOME);
                homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(homeIntent);
            }, 1500);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "无法启动悬浮窗服务: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopFloatingWindow() {
        Intent serviceIntent = new Intent(this, FloatingWindowService.class);
        try {
            stopService(serviceIntent);
            Toast.makeText(this, "悬浮窗已关闭", Toast.LENGTH_SHORT).show();
            updateStatus();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "停止悬浮窗失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void updateStatus() {
        boolean running = FloatingWindowService.isRunning();

        if (running) {
            tvStatus.setText("悬浮窗运行中");
            tvStatus.setTextColor(0xFF4CAF50);
            btnStartFloat.setEnabled(false);
            btnStopFloat.setEnabled(true);
            tvTip.setText("悬浮窗正在运行，切换到游戏即可使用");
        } else {
            tvStatus.setText("悬浮窗未启动");
            tvStatus.setTextColor(0xFFFF9800);
            btnStartFloat.setEnabled(true);
            btnStopFloat.setEnabled(false);
            tvTip.setText("点击启动悬浮窗，然后切换到台球游戏");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }
}
