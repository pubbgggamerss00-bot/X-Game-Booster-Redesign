package com.bytecycle.xgamebooster.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import com.bytecycle.xgamebooster.R;
import com.bytecycle.xgamebooster.boost.BoosterService;
import com.bytecycle.xgamebooster.boost.MemoryUtils;
import com.bytecycle.xgamebooster.ghost.GhostReceiver;
import com.bytecycle.xgamebooster.overlay.HudService;
import com.bytecycle.xgamebooster.record.RecordRequestActivity;
import com.bytecycle.xgamebooster.record.RecordService;
import com.bytecycle.xgamebooster.util.Prefs;
import com.bytecycle.xgamebooster.util.Ui;
import com.bytecycle.xgamebooster.vpn.PingVpnService;

public class MainActivity extends Activity {

    static final int REQ_VPN = 41;
    static final int REQ_OVERLAY = 42;

    Handler h = new Handler();
    ProgressBar barRam;
    TextView tvRamPct, tvRamDetail, tvVpnState, tvPing, tvUser;
    Switch swVpn, swGhost, swHud;
    ListView lvApps;
    Button btnRecord;
    boolean syncing = false;

    Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshRam();
            refreshVpn();
            h.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Prefs.getBool(this, "logged", false)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);

        tvUser = findViewById(R.id.tvUser);
        barRam = findViewById(R.id.barRam);
        tvRamPct = findViewById(R.id.tvRamPct);
        tvRamDetail = findViewById(R.id.tvRamDetail);
        tvVpnState = findViewById(R.id.tvVpnState);
        tvPing = findViewById(R.id.tvPing);
        swVpn = findViewById(R.id.swVpn);
        swGhost = findViewById(R.id.swGhost);
        swHud = findViewById(R.id.swHud);
        lvApps = findViewById(R.id.lvApps);
        btnRecord = findViewById(R.id.btnRecord);
        ImageButton btnSettings = findViewById(R.id.btnSettings);
        Button btnBoost = findViewById(R.id.btnBoost);
        Button btnCross = findViewById(R.id.btnCross);
        View cardPremium = findViewById(R.id.cardPremium);

        tvUser.setText(Prefs.getStr(this, "user", "مهمان"));

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        btnCross.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        cardPremium.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, PremiumActivity.class));
            }
        });
        btnBoost.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doBoost();
            }
        });
        btnRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (RecordService.running) {
                    Intent i = new Intent(MainActivity.this, RecordService.class);
                    i.setAction(RecordService.ACTION_STOP);
                    startService(i);
                    Toast.makeText(MainActivity.this, "ضبط متوقف شد", Toast.LENGTH_SHORT).show();
                } else {
                    startActivity(new Intent(MainActivity.this, RecordRequestActivity.class));
                }
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        refreshRecordLabel();
                    }
                }, 500);
            }
        });

        swVpn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton btn, boolean on) {
                if (syncing) return;
                if (on) {
                    Intent pre = VpnService.prepare(MainActivity.this);
                    if (pre != null) {
                        startActivityForResult(pre, REQ_VPN);
                    } else {
                        startVpn(true);
                    }
                } else {
                    startVpn(false);
                }
            }
        });
        swGhost.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton btn, boolean on) {
                if (syncing) return;
                applyGhost(on);
            }
        });
        swHud.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton btn, boolean on) {
                if (syncing) return;
                toggleHud(on);
            }
        });

        lvApps.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                AppsAdapter.AppInfo ai = (AppsAdapter.AppInfo) p.getItemAtPosition(pos);
                if (ai != null) {
                    boostAndLaunch(ai.pkg);
                }
            }
        });

        loadApps();
    }

    void startVpn(boolean on) {
        Prefs.putBool(this, "vpn_on", on);
        Intent i = new Intent(this, PingVpnService.class);
        i.setAction(on ? PingVpnService.ACTION_START : PingVpnService.ACTION_STOP);
        if (on && Build.VERSION.SDK_INT >= 26) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }

    void toggleHud(boolean on) {
        Prefs.putBool(this, "hud_on", on);
        if (on && !Settings.canDrawOverlays(this)) {
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())), REQ_OVERLAY);
            return;
        }
        Intent i = new Intent(this, HudService.class);
        i.setAction(on ? HudService.ACTION_SHOW : HudService.ACTION_STOP);
        if (on && Build.VERSION.SDK_INT >= 26) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_VPN) {
            if (res == RESULT_OK) {
                startVpn(true);
            } else {
                sync();
            }
        } else if (req == REQ_OVERLAY) {
            sync();
        }
    }

    void doBoost() {
        try {
            Vibrator vb = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vb != null) {
                if (Build.VERSION.SDK_INT >= 26) {
                    vb.vibrate(VibrationEffect.createOneShot(60,
                            VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vb.vibrate(60);
                }
            }
        } catch (Throwable t) { }
        Intent i = new Intent(this, BoosterService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(i);
        } else {
            startService(i);
        }
        Toast.makeText(this, "در حال بهینه‌سازی…", Toast.LENGTH_SHORT).show();
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                refreshRam();
            }
        }, 1600);
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                refreshRam();
            }
        }, 3200);
    }

    void boostAndLaunch(final String pkg) {
        if (Prefs.getBool(this, "auto_boost", true)) {
            Intent i = new Intent(this, BoosterService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(i);
            } else {
                startService(i);
            }
        }
        Toast.makeText(this, "بوست و اجرا…", Toast.LENGTH_SHORT).show();
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent l = getPackageManager().getLaunchIntentForPackage(pkg);
                    if (l != null) {
                        l.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(l);
                    }
                } catch (Throwable t) { }
            }
        }, 700);
    }

    void applyGhost(final boolean on) {
        if (on) {
            new AlertDialog.Builder(this)
                    .setTitle("حالت شبح")
                    .setMessage("آیکون اپ از منوی برنامه‌ها مخفی می‌شود. برای بازگشت، از نوتیفیکیشن «بازگرداندن آیکون» استفاده کن. ادامه می‌دهی؟")
                    .setPositiveButton("بله", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            ghostNotification();
                            GhostReceiver.hideIcon(MainActivity.this);
                            Toast.makeText(MainActivity.this,
                                    "حالت شبح فعال شد", Toast.LENGTH_LONG).show();
                            h.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    finish();
                                }
                            }, 900);
                        }
                    })
                    .setNegativeButton("انصراف", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            sync();
                        }
                    })
                    .show();
        } else {
            Prefs.putBool(this, "ghost_on", false);
            GhostReceiver.restoreIcon(this);
        }
    }

    void ghostNotification() {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "boost")
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_ghost)
                .setContentTitle("X Game Booster — حالت شبح")
                .setContentText("برای بازگرداندن آیکون اپ، دکمه‌ی زیر را لمس کن")
                .setOngoing(true);
        Intent ri = new Intent(this, GhostReceiver.class);
        ri.setAction(GhostReceiver.ACTION_RESTORE);
        b.addAction(0, "بازگرداندن آیکون", PendingIntent.getBroadcast(this, 5, ri,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        b.setContentIntent(PendingIntent.getActivity(this, 8,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(31, b.build());
    }

    void refreshRam() {
        try {
            long a = MemoryUtils.availMem(this), t = MemoryUtils.totalMem(this);
            int pct = (int) (100 * (t - a) / Math.max(1, t));
            barRam.setProgress(pct);
            tvRamPct.setText(pct + "%");
            tvRamDetail.setText("رم آزاد: " + (a / 1048576) + " مگابایت از "
                    + (t / 1048576) + " مگابایت");
        } catch (Throwable t) { }
    }

    void refreshVpn() {
        boolean on = PingVpnService.running;
        if (on != swVpn.isChecked()) {
            syncing = true;
            swVpn.setChecked(on);
            syncing = false;
        }
        tvVpnState.setText(on
                ? "تونل فعال است — ترافیک DNS از سریع‌ترین سرور عبور می‌کند"
                : "تونل غیرفعال است");
        tvPing.setText(on
                ? ("DNS: " + (PingVpnService.bestDns == null ? "…" : PingVpnService.bestDns)
                        + "  |  پینگ: " + (PingVpnService.bestRtt < 0 ? "…" : PingVpnService.bestRtt + "ms")
                        + "  |  پرس‌وجو: " + PingVpnService.relayedCount)
                : "");
    }

    void refreshRecordLabel() {
        btnRecord.setText(RecordService.running ? "توقف ضبط" : "شروع ضبط");
    }

    void loadApps() {
        new Thread() {
            @Override
            public void run() {
                final List<AppsAdapter.AppInfo> list = AppsAdapter.collect(MainActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        lvApps.setAdapter(new AppsAdapter(MainActivity.this, list));
                        Ui.fit(lvApps);
                    }
                });
            }
        }.start();
    }

    void sync() {
        syncing = true;
        swVpn.setChecked(PingVpnService.running);
        swGhost.setChecked(Prefs.getBool(this, "ghost_on", false));
        swHud.setChecked(HudService.running);
        syncing = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        sync();
        refreshRam();
        refreshVpn();
        refreshRecordLabel();
        h.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(tick);
    }
}
