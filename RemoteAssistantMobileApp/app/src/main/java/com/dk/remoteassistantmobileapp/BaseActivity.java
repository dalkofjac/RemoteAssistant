package com.dk.remoteassistantmobileapp;

import android.content.Context;

import androidx.fragment.app.FragmentActivity;

import com.iristick.smartglass.support.app.IristickApp;

public class BaseActivity extends FragmentActivity {
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(IristickApp.wrapContext(newBase));
    }
}
