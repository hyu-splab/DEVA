package com.example.edgedashanalytics.util.worker;

import android.graphics.Bitmap;

import com.example.edgedashanalytics.util.video.analysis.Frame;

abstract public class FrameProcessor {
    Bitmap frame;

    public void setFrame(Bitmap frame) {
        this.frame = frame;
    }

    abstract public String run();
}
