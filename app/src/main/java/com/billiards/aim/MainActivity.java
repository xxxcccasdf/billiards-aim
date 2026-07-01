package com.billiards.aim;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("Hello World - 台球瞄准辅助");
        tv.setTextSize(24);
        setContentView(tv);
    }
}
