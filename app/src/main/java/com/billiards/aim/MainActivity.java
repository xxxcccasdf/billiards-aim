package com.billiards.aim;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnStart = findViewById(R.id.btn_start_float);
        Button btnStop = findViewById(R.id.btn_stop_float);

        btnStart.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
                return;
            }
            try {
                Intent intent = new Intent(this, FloatingWindowService.class);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
                Toast.makeText(this, "悬浮窗已启动", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "启动失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
        });

        btnStop.setOnClickListener(v -> {
            try {
                stopService(new Intent(this, FloatingWindowService.class));
                Toast.makeText(this, "悬浮窗已关闭", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "关闭失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
}
