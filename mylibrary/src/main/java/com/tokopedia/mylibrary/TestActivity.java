package com.tokopedia.mylibrary;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

import com.google.android.play.core.splitcompat.SplitCompat;

/**
 * Created by hendry on 2019-05-07.
 */
public class TestActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_2);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        SplitCompat.install(this);
    }

}
