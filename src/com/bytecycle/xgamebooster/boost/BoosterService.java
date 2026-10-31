package com.bytecycle.xgamebooster.boost;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import com.bytecycle.xgamebooster.R;

public class BoosterService extends Service {

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public int onStartCommand(Intent it, int flags, int id) {
        goForeground("در حال بهینه‌سازی حافظه…");
        new Thread() {
            @Override
            public void run() {
                final long freed = MemoryUtils.boost(BoosterService.this);
                SystemClock.sleep(700);
                long avail = MemoryUtils.availMem(BoosterService.this);
                long availTotal = MemoryUtils.totalMem(BoosterService.this);
                String msg = freed / 1048576 + " مگابایت آزاد شد — رم آزاد: "
                        + (avail / 1048576) + " از " + (availTotal / 1048576) + " مگابایت";
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                nm.notify(11, notif(msg));
                SystemClock.sleep(2400);
                stopFg();
                stopSelf();
            }
        }.start();
        return START_NOT_STICKY;
    }

    void stopFg() {
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }

    void goForeground(String txt) {
        Notification n = notif(txt);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(11, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(11, n);
        }
    }

    Notification notif(String txt) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "boost")
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_bolt)
                .setContentTitle("X Game Booster")
                .setContentText(txt)
                .setOngoing(true);
        if (Build.VERSION.SDK_INT < 26) {
            b.setPriority(Notification.PRIORITY_LOW);
        }
        return b.build();
    }
}
