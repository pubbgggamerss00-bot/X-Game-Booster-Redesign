package com.bytecycle.xgamebooster.util;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ListView;

public class Ui {

    /** Makes a ListView placed inside a ScrollView fully expanded. */
    public static void fit(final ListView lv) {
        ListAdapter a = lv.getAdapter();
        if (a == null) return;
        int total = 0;
        int w = lv.getResources().getDisplayMetrics().widthPixels;
        for (int i = 0; i < a.getCount(); i++) {
            View v = a.getView(i, null, lv);
            v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.UNSPECIFIED);
            total += v.getMeasuredHeight();
        }
        int extra = a.getCount() > 0 ? (a.getCount() - 1) * Math.max(0, lv.getDividerHeight()) : 0;
        ViewGroup.LayoutParams p = lv.getLayoutParams();
        p.height = total + extra + 8;
        lv.setLayoutParams(p);
        lv.requestLayout();
    }
}
