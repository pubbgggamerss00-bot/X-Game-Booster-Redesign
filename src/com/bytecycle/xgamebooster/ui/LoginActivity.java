package com.bytecycle.xgamebooster.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import com.bytecycle.xgamebooster.R;
import com.bytecycle.xgamebooster.util.Prefs;

import java.util.Random;

public class LoginActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_login);

        final EditText etUser = findViewById(R.id.etUser);
        Button btnLogin = findViewById(R.id.btnLogin);
        Button btnGuest = findViewById(R.id.btnGuest);

        btnGuest.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                Prefs.putStr(LoginActivity.this, "user",
                        "مهمان-" + (1000 + new Random().nextInt(9000)));
                Prefs.putBool(LoginActivity.this, "logged", true);
                go();
            }
        });

        btnLogin.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                String u = etUser.getText().toString().trim();
                if (u.length() < 3) {
                    etUser.setError("حداقل ۳ کاراکتر");
                    return;
                }
                Prefs.putStr(LoginActivity.this, "user", u);
                Prefs.putBool(LoginActivity.this, "logged", true);
                go();
            }
        });
    }

    void go() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
