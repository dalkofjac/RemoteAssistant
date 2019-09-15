package com.dk.remoteassistantmobileapp;

import android.app.Application;

import com.iristick.smartglass.support.app.IristickApp;
import com.iristick.smartglass.support.app.IristickConfiguration;

public class RemoteAssistantMobileApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        IristickApp.init(this, new IristickConfiguration());
    }
}
