package com.bekh.frosttest;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

// a loud, sharp background that stays put under the keyboard (adjustNothing), to judge the frost
public class Main extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        // --ez black true: pure black, like the sheath launcher
        if (getIntent().getBooleanExtra("black", false)) root.setBackgroundColor(0xFF000000);
        else root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[] {0xFF14142B, 0xFFE0245E, 0xFFF5A623, 0xFF17BF63, 0xFF1DA1F2, 0xFF6A0DAD}));
        TextView t = new TextView(this);
        t.setText("FROST\nTEST\nsheath ▮▮▮▮\nkeys ▮▮▮▮▮");
        t.setTextSize(78);
        t.setTextColor(0xFFFFFFFF);
        root.addView(t, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        EditText e = new EditText(this);
        e.setHint("type here");
        e.setTextColor(0xFFFFFFFF);
        e.setHintTextColor(0x99FFFFFF);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0x55000000);
        pill.setCornerRadius(40);
        e.setBackground(pill);
        e.setPadding(40, 30, 40, 30);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        lp.setMargins(40, 200, 40, 0);
        root.addView(e, lp);
        setContentView(root);
    }
}
