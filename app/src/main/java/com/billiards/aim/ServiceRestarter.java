package com.billiards.aim;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class ServiceRestarter extends BroadcastReceiver {

    private static final String TAG = "ServiceRestarter";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        Log.i(TAG, "onReceive: " + action);

        try {
            // Android 14+ 限制从广播启动前台服务，使用 JobScheduler 替代
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                KeepAliveJobService.schedule(context);
                return;
            }

            Intent serviceIntent = new Intent(context, FloatingWindowService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Restart failed", e);
        }
    }
}
