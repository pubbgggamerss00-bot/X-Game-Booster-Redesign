package com.bytecycle.xgamebooster.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.bytecycle.xgamebooster.R;
import com.bytecycle.xgamebooster.util.Prefs;

public class PremiumActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_premium);

        RadioGroup rg = findViewById(R.id.rgPlan);
        Button btn = findViewById(R.id.btnActivate);
        TextView tvState = findViewById(R.id.tvState);

        boolean pre = Prefs.getBool(this, "premium", false);
        tvState.setText(pre ? "وضعیت: پریمیوم فعال است" : "وضعیت: نسخه رایگان");

        rg.check(R.id.rbYearly);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.putBool(PremiumActivity.this, "premium", true);
                Toast.makeText(PremiumActivity.this, "پریمیوم فعال شد",
                        Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }
}
