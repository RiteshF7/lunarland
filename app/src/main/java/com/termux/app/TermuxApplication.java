package com.termux.app;

import android.app.Application;

import androidx.annotation.MainThread;

public class TermuxApplication extends Application {

    @Override
    @MainThread
    public void onCreate() {
        super.onCreate();

        TermuxCore.initialize(this);
    }
}
