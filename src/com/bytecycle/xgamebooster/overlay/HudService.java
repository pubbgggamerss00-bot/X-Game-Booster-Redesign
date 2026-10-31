package com.bytecycle.xgamebooster.overlay;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.bytecycle.xgamebooster.R;
import com.bytecycle.xgamebooster.boost.MemoryUtils;
import com.bytecycle.xgamebooster.ui.MainActivity;
import com.bytecycle.xgamebooster.util.Prefs;
import com.bytecycle.xgamebooster.vpn.PingVpnService;

/**
 * HUD engine: system overlay with a game crosshair (4 styles,
 * adjustable size/color) plus a small status bar showing RAM and DNS ping.
 */
public class HudService extends Service {

    public static final String ACTION_SHOW = "com.bytecycle.xgamebooster.HUD_SHOW";
    public static final String ACTION_STOP = "com.bytecycle.xgamebooster.HUD_STOP";
    public static final String ACTION_RELOAD = "com.bytecycle.xgamebooster.HUD_RELOAD";

    public static volatile boolean running = false;

    WindowManager wm;
    View cross, stats;
    Handler h;
    Runnable tick;

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        h = new Handler();
    }

    @Override
    public int onStartCommand(Intent it, int f, int id) {
        if (it != null && ACTION_STOP.equals(it.getAction())) {
            stopHud();
            return START_NOT_STICKY;
        }
        goForeground();
        boolean reload = it != null && ACTION_RELOAD.equals(it.getAction());
        if (reload) {
            removeViews();
        }
        if (cross == null && stats == null) {
            addViews();
        }
        return START_STICKY;
    }

    int otype() {
        return Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    void addViews() {
        try {
            wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            float d = getResources().getDisplayMetrics().density;
            int size = Prefs.getInt(this, "hud_size", 22);
            int px = (int) (d * (size * 2 + 20));

            CrosshairView cv = new CrosshairView(this);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    px, px, otype(),
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.CENTER;
            wm.addView(cv, lp);
            cross = cv;

            TextView tv = new TextView(this);
            tv.setTextColor(0xFFFFFFFF);
            tv.setTextSize(11);
            tv.setBackgroundColor(0x99000000);
            tv.setPadding((int) (10 * d), (int) (5 * d), (int) (10 * d), (int) (5 * d));
            WindowManager.LayoutParams lp2 = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT, otype(),
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            lp2.gravity = Gravity.TOP | Gravity.START;
            lp2.x = (int) (14 * d);
            lp2.y = (int) (40 * d);
            wm.addView(tv, lp2);
            stats = tv;

            tick = new Runnable() {
                @Override
                public void run() {
                    try {
                        if (stats != null) {
                            long a = MemoryUtils.availMem(HudService.this);
                            long t = MemoryUtils.totalMem(HudService.this);
                            int pct = (int) (100 * (t - a) / Math.max(1, t));
                            String s = "RAM " + pct + "% (" + (a / 1048576) + "MB آزاد)";
                            if (PingVpnService.running && PingVpnService.bestRtt >= 0) {
                                s += "  •  DNS " + PingVpnService.bestRtt + "ms";
                            }
                            ((TextView) stats).setText(s);
                        }
                    } catch (Throwable t) { }
                    h.postDelayed(this, 1000);
                }
            };
            h.post(tick);
        } catch (Throwable t) { }
    }

    void removeViews() {
        try {
            if (tick != null) h.removeCallbacks(tick);
        } catch (Throwable t) { }
        try {
            if (cross != null && wm != null) wm.removeView(cross);
        } catch (Throwable t) { }
        try {
            if (stats != null && wm != null) wm.removeView(stats);
        } catch (Throwable t) { }
        cross = null;
        stats = null;
        tick = null;
    }

    void stopHud() {
        removeViews();
        Prefs.putBool(this, "hud_on", false);
        running = false;
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }

    void goForeground() {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "hud")
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_cross)
                .setContentTitle("X HUD")
                .setContentText("کراس‌هیر و HUD فعال است")
                .setOngoing(true);
        Intent st = new Intent(this, HudService.class);
        st.setAction(ACTION_STOP);
        b.addAction(0, "خاموش کردن HUD", PendingIntent.getService(this, 3, st,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        b.setContentIntent(PendingIntent.getActivity(this, 4,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(22, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(22, n);
        }
    }

    /** The floating crosshair drawn in the center of the screen. */
    public static class CrosshairView extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int style, size, color;

        public CrosshairView(Context c) {
            super(c);
            style = Prefs.getInt(c, "hud_style", 1);
            size = Prefs.getInt(c, "hud_size", 22);
            color = Prefs.getInt(c, "hud_color", 0xFF22C55E);
        }

        @Override
        protected void onDraw(Canvas g) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, s = size;
            p.setColor(color);
            p.setStrokeWidth(2.4f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStyle(Paint.Style.STROKE);
            if (style == 0) {
                p.setStyle(Paint.Style.FILL);
                g.drawCircle(cx, cy, Math.max(2f, s / 5f), p);
            } else if (style == 1) {
                g.drawLine(cx - s, cy, cx + s, cy, p);
                g.drawLine(cx, cy - s, cx, cy + s, p);
            } else if (style == 2) {
                g.drawCircle(cx, cy, s, p);
                p.setStyle(Paint.Style.FILL);
                g.drawCircle(cx, cy, 1.8f, p);
            } else {
                g.drawCircle(cx, cy, s, p);
                g.drawLine(cx - s * 1.7f, cy, cx - s * 0.55f, cy, p);
                g.drawLine(cx + s * 0.55f, cy, cx + s * 1.7f, cy, p);
                g.drawLine(cx, cy - s * 1.7f, cx, cy - s * 0.55f, p);
                g.drawLine(cx, cy + s * 0.55f, cx, cy + s * 1.7f, p);
                p.setStyle(Paint.Style.FILL);
                g.drawCircle(cx, cy, 1.8f, p);
            }
        }
    }
}
