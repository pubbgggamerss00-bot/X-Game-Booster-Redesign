package com.bytecycle.xgamebooster.ghost;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Toast;

import com.bytecycle.xgamebooster.ui.IntroActivity;
import com.bytecycle.xgamebooster.util.Prefs;

public class GhostReceiver extends BroadcastReceiver {

    public static final String ACTION_RESTORE = "com.bytecycle.xgamebooster.GHOST_RESTORE";

    public static void hideIcon(Context c) {
        c.getPackageManager().setComponentEnabledSetting(
                new ComponentName(c, IntroActivity.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        Prefs.putBool(c, "ghost_on", true);
    }

    public static void restoreIcon(Context c) {
        c.getPackageManager().setComponentEnabledSetting(
                new ComponentName(c, IntroActivity.class),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
        Prefs.putBool(c, "ghost_on", false);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        if (i != null && ACTION_RESTORE.equals(i.getAction())) {
            restoreIcon(c);
            try {
                Toast.makeText(c, "آیکون اپ برگشت", Toast.LENGTH_SHORT).show();
            } catch (Throwable t) { }
        }
    }
}
