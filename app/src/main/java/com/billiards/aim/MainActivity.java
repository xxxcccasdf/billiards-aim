package com.billiards.aim;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.BLACK);
        layout.setPadding(50, 50, 50, 50);
        
        TextView tv = new TextView(this);
        tv.setText("Hello World - 台球瞄准辅助");
        tv.setTextSize(24);
        tv.setTextColor(Color.WHITE);
        
        layout.addView(tv);
        setContentView(layout);
    }
}
