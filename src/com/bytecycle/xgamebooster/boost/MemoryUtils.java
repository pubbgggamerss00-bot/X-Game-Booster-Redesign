package com.bytecycle.xgamebooster.boost;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.List;

public class MemoryUtils {

    static ActivityManager am(Context c) {
        return (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
    }

    public static long availMem(Context c) {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am(c).getMemoryInfo(mi);
        return mi.availMem;
    }

    public static long totalMem(Context c) {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am(c).getMemoryInfo(mi);
        return mi.totalMem;
    }

    public static int taskCount(Context c) {
        try {
            List<ActivityManager.RunningAppProcessInfo> l = am(c).getRunningAppProcesses();
            return l == null ? 0 : l.size();
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Kill background processes of user-installed apps (same mechanism
     * class the original booster uses) and return freed bytes.
     */
    public static long boost(Context c) {
        long before = availMem(c);
        try {
            ActivityManager a = am(c);
            PackageManager pm = c.getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(0);
            if (apps != null) {
                for (ApplicationInfo ai : apps) {
                    if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                        try {
                            a.killBackgroundProcesses(ai.packageName);
                        } catch (Throwable t) { }
                    }
                }
            }
            List<ActivityManager.RunningAppProcessInfo> procs = a.getRunningAppProcesses();
            if (procs != null) {
                for (ActivityManager.RunningAppProcessInfo p : procs) {
                    if (p.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND) {
                        try {
                            a.killBackgroundProcesses(p.processName);
                        } catch (Throwable t) { }
                    }
                }
            }
        } catch (Throwable t) { }
        long freed = availMem(c) - before;
        return Math.max(0, freed);
    }
}
