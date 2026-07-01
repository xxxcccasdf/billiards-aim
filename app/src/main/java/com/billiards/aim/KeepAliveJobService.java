package com.billiards.aim;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class KeepAliveJobService extends JobService {

    private static final String TAG = "KeepAliveJobService";
    private static final int JOB_ID = 9001;

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.i(TAG, "onStartJob");
        try {
            if (!FloatingWindowService.isRunning()) {
                Intent serviceIntent = new Intent(this, FloatingWindowService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Restart failed", e);
        }
        // 重新调度下一次
        schedule(this);
        return false; // 任务已完成
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.w(TAG, "onStopJob");
        return false;
    }

    public static void schedule(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;

        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler == null) return;

            // 取消已有任务
            scheduler.cancel(JOB_ID);

            ComponentName component = new ComponentName(context, KeepAliveJobService.class);
            JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, component);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+: 使用最小间隔 15 分钟
                builder.setMinimumLatency(15 * 60 * 1000L);
                builder.setOverrideDeadline(30 * 60 * 1000L);
            } else {
                builder.setPeriodic(15 * 60 * 1000L);
            }

            builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE);
            builder.setRequiresCharging(false);
            builder.setRequiresDeviceIdle(false);
            builder.setPersisted(true);

            int result = scheduler.schedule(builder.build());
            Log.i(TAG, "Job scheduled: " + (result == JobScheduler.RESULT_SUCCESS ? "success" : "failure"));
        } catch (Exception e) {
            Log.e(TAG, "Schedule failed", e);
        }
    }

    public static void cancel(Context context) {
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler != null) {
                scheduler.cancel(JOB_ID);
            }
        } catch (Exception e) {
            Log.e(TAG, "Cancel failed", e);
        }
    }
}
