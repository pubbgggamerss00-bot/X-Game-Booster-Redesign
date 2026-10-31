package com.bytecycle.xgamebooster.ui;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.bytecycle.xgamebooster.R;

public class AppsAdapter extends BaseAdapter {

    public static class AppInfo {
        public String label, pkg;
        public Drawable icon;
    }

    Context c;
    List<AppInfo> items;
    LayoutInflater inf;

    public AppsAdapter(Context c, List<AppInfo> items) {
        this.c = c;
        this.items = items;
        inf = LayoutInflater.from(c);
    }

    public static List<AppInfo> collect(Context c) {
        List<AppInfo> out = new ArrayList<>();
        try {
            PackageManager pm = c.getPackageManager();
            List<ApplicationInfo> all = pm.getInstalledApplications(0);
            if (all != null) {
                for (ApplicationInfo ai : all) {
                    if (pm.getLaunchIntentForPackage(ai.packageName) == null) continue;
                    if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                    AppInfo x = new AppInfo();
                    x.pkg = ai.packageName;
                    x.label = ai.loadLabel(pm).toString();
                    x.icon = ai.loadIcon(pm);
                    out.add(x);
                }
            }
            Collections.sort(out, new Comparator<AppInfo>() {
                @Override
                public int compare(AppInfo a, AppInfo b) {
                    return a.label.compareToIgnoreCase(b.label);
                }
            });
        } catch (Throwable t) { }
        return out;
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int p) {
        return items.get(p);
    }

    @Override
    public long getItemId(int p) {
        return p;
    }

    @Override
    public View getView(int p, View cv, ViewGroup parent) {
        if (cv == null) {
            cv = inf.inflate(R.layout.item_app, parent, false);
        }
        AppInfo a = items.get(p);
        ImageView iv = (ImageView) cv.findViewById(R.id.ivIcon);
        TextView tv = (TextView) cv.findViewById(R.id.tvName);
        TextView tv2 = (TextView) cv.findViewById(R.id.tvPkg);
        iv.setImageDrawable(a.icon);
        tv.setText(a.label);
        tv2.setText(a.pkg);
        return cv;
    }
}
