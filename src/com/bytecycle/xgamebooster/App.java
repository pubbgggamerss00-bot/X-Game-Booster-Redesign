package com.bytecycle.xgamebooster;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel("boost", "بوستر", NotificationManager.IMPORTANCE_LOW));
            nm.createNotificationChannel(new NotificationChannel("vpn", "تونل پینگ", NotificationManager.IMPORTANCE_LOW));
            nm.createNotificationChannel(new NotificationChannel("hud", "HUD", NotificationManager.IMPORTANCE_LOW));
            nm.createNotificationChannel(new NotificationChannel("rec", "ضبط صفحه", NotificationManager.IMPORTANCE_LOW));
        }
    }
}
