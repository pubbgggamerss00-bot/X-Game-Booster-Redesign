package com.bytecycle.xgamebooster.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.bytecycle.xgamebooster.R;
import com.bytecycle.xgamebooster.overlay.HudService;
import com.bytecycle.xgamebooster.util.Prefs;
import com.bytecycle.xgamebooster.vpn.PingVpnService;

public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);

        TextView tvAbout = findViewById(R.id.tvAbout);
        tvAbout.setText("X Game Booster — نسخه‌ی بازطراحی‌شده ۴.۱.۰\n"
                + "پکیج: com.bytecycle.xgamebooster\n"
                + "موتورها: تونل پینگ DNS، بوستر رم، حالت شبح، HUD/کراس‌هیر، ضبط صفحه\n"
                + "ساخته‌شده بدون هیچ وابستگی خارجی (اندروید خالص)");

        Button btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // DNS selection
        final RadioGroup rgDns = findViewById(R.id.rgDns);
        final String cur = Prefs.getStr(this, "dns", "auto");
        if ("8.8.8.8".equals(cur)) rgDns.check(R.id.rbGoogle);
        else if ("1.1.1.1".equals(cur)) rgDns.check(R.id.rbCloudflare);
        else if ("178.22.122.100".equals(cur)) rgDns.check(R.id.rbShecan);
        else if ("185.51.200.2".equals(cur)) rgDns.check(R.id.rbShecan2);
        else if ("10.202.10.10".equals(cur)) rgDns.check(R.id.rbRadar);
        else if ("10.202.10.202".equals(cur)) rgDns.check(R.id.rbRadarGame);
        else rgDns.check(R.id.rbAuto);
        final String before = cur;
        rgDns.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup g, int cid) {
                String v = "auto";
                if (cid == R.id.rbGoogle) v = "8.8.8.8";
                else if (cid == R.id.rbCloudflare) v = "1.1.1.1";
                else if (cid == R.id.rbShecan) v = "178.22.122.100";
                else if (cid == R.id.rbShecan2) v = "185.51.200.2";
                else if (cid == R.id.rbRadar) v = "10.202.10.10";
                else if (cid == R.id.rbRadarGame) v = "10.202.10.202";
                Prefs.putStr(SettingsActivity.this, "dns", v);
                if (PingVpnService.running && !v.equals(before)) {
                    Toast.makeText(SettingsActivity.this,
                            "تونل برای اعمال DNS جدید ری‌استارت می‌شود", Toast.LENGTH_SHORT).show();
                    Intent s = new Intent(SettingsActivity.this, PingVpnService.class);
                    s.setAction(PingVpnService.ACTION_STOP);
                    startService(s);
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            Intent s2 = new Intent(SettingsActivity.this, PingVpnService.class);
                            s2.setAction(PingVpnService.ACTION_START);
                            if (Build.VERSION.SDK_INT >= 26) {
                                startForegroundService(s2);
                            } else {
                                startService(s2);
                            }
                        }
                    }, 800);
                }
            }
        });

        // Booster behaviour
        Switch swAuto = findViewById(R.id.swAuto);
        swAuto.setChecked(Prefs.getBool(this, "auto_boost", true));
        swAuto.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton btn, boolean on) {
                Prefs.putBool(SettingsActivity.this, "auto_boost", on);
            }
        });
        Switch swMic = findViewById(R.id.swMic);
        swMic.setChecked(Prefs.getBool(this, "rec_mic", true));
        swMic.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton btn, boolean on) {
                Prefs.putBool(SettingsActivity.this, "rec_mic", on);
            }
        });

        // Crosshair style
        RadioGroup rgStyle = findViewById(R.id.rgStyle);
        int st = Prefs.getInt(this, "hud_style", 1);
        if (st == 0) rgStyle.check(R.id.rbDot);
        else if (st == 1) rgStyle.check(R.id.rbCross);
        else if (st == 2) rgStyle.check(R.id.rbCircle);
        else rgStyle.check(R.id.rbCombo);
        rgStyle.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup g, int cid) {
                int v = 1;
                if (cid == R.id.rbDot) v = 0;
                else if (cid == R.id.rbCross) v = 1;
                else if (cid == R.id.rbCircle) v = 2;
                else v = 3;
                Prefs.putInt(SettingsActivity.this, "hud_style", v);
                reloadHud();
            }
        });

        // Crosshair size
        SeekBar sbSize = findViewById(R.id.sbSize);
        sbSize.setMax(40);
        sbSize.setProgress(Prefs.getInt(this, "hud_size", 22) - 8);
        sbSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int pr, boolean fr) { }

            @Override
            public void onStartTrackingTouch(SeekBar s) { }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                Prefs.putInt(SettingsActivity.this, "hud_size", s.getProgress() + 8);
                reloadHud();
            }
        });

        // Crosshair color
        RadioGroup rgColor = findViewById(R.id.rgColor);
        int col = Prefs.getInt(this, "hud_color", 0xFF22C55E);
        if (col == 0xFFEF4444) rgColor.check(R.id.rbRed);
        else if (col == 0xFF38BDF8) rgColor.check(R.id.rbBlue);
        else if (col == 0xFFFFFFFF) rgColor.check(R.id.rbWhite);
        else rgColor.check(R.id.rbGreen);
        rgColor.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup g, int cid) {
                int v;
                if (cid == R.id.rbRed) v = 0xFFEF4444;
                else if (cid == R.id.rbBlue) v = 0xFF38BDF8;
                else if (cid == R.id.rbWhite) v = 0xFFFFFFFF;
                else v = 0xFF22C55E;
                Prefs.putInt(SettingsActivity.this, "hud_color", v);
                reloadHud();
            }
        });
    }

    void reloadHud() {
        if (HudService.running) {
            Intent i = new Intent(this, HudService.class);
            i.setAction(HudService.ACTION_RELOAD);
            startService(i);
        }
    }
}
