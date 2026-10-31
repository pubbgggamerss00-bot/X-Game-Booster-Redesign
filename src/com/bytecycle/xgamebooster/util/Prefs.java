package com.bytecycle.xgamebooster.util;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("xgb", Context.MODE_PRIVATE);
    }

    public static boolean getBool(Context c, String k, boolean d) {
        return sp(c).getBoolean(k, d);
    }

    public static void putBool(Context c, String k, boolean v) {
        sp(c).edit().putBoolean(k, v).apply();
    }

    public static String getStr(Context c, String k, String d) {
        return sp(c).getString(k, d);
    }

    public static void putStr(Context c, String k, String v) {
        sp(c).edit().putString(k, v).apply();
    }

    public static int getInt(Context c, String k, int d) {
        return sp(c).getInt(k, d);
    }

    public static void putInt(Context c, String k, int v) {
        sp(c).edit().putInt(k, v).apply();
    }
}
