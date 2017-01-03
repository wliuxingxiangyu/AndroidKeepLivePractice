package com.silence.keeplive.foreground;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import com.silence.keeplive.onepx.ScreenReceiver;
import com.silence.keeplive.timer.ScheduleService;

public class DaemonService extends Service {
    public static final int SERVICE_ID = 9510;
    private static final String TAG = "DaemonService";
    /**
     * 定时唤醒的时间间隔，15 分钟
     */
    private final static long WAKE_INTERVAL = 15 * 60 * 1000;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate()");
        BroadcastReceiver receiver = new ScreenReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.SCREEN_OFF");
        intentFilter.addAction("android.intent.action.SCREEN_ON");
        intentFilter.addAction("android.intent.action.USER_PRESENT");
        registerReceiver(receiver, intentFilter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand(): intent = [" + intent.toUri(0) + "], flags = [" + flags + "], startId = [" + startId + "]");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            startForeground(SERVICE_ID, new Notification());
        } else {
            startForeground(SERVICE_ID, new Notification());
            Intent sendIntend = new Intent(this, ChannelService.class);
            startService(sendIntend);
        }

        try {
            //定时检查 WorkService 是否在运行，如果不在运行就把它拉起来
            //Android 5.0+ 使用 JobScheduler，效果比 AlarmManager 好
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Log.i(TAG, "开启 JobService 定时");
                JobScheduler jobScheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
                jobScheduler.cancelAll();
                JobInfo.Builder builder = new JobInfo.Builder(1024, new ComponentName(getPackageName(), ScheduleService.class.getName()));
                builder.setPeriodic(WAKE_INTERVAL);
//                builder.setPersisted(true);
                builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
                int schedule = jobScheduler.schedule(builder.build());
                if (schedule <= 0) {
                    Log.w(TAG, "schedule error！");
                }
            } else {
                //Android 4.4- 使用 AlarmManager
                Log.i(TAG, "开启 AlarmManager 定时");
                AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
                Intent i = new Intent(getApplication(), DaemonService.class);
                PendingIntent pendingIntent = PendingIntent.getService(this, 1024, i, PendingIntent.FLAG_UPDATE_CURRENT);
                am.cancel(pendingIntent);
                am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + WAKE_INTERVAL, WAKE_INTERVAL, pendingIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "e:" + e);
        }

        //简单守护开机广播
        getPackageManager().setComponentEnabledSetting(
                new ComponentName(getPackageName(), DaemonService.class.getName()),
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
        Intent intent = new Intent(this, DaemonService.class);
        startService(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}