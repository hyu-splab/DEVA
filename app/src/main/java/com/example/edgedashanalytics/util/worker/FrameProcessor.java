package com.example.edgedashanalytics.util.worker;

import android.graphics.Bitmap;

import com.example.edgedashanalytics.util.video.analysis.Frame;

abstract public class FrameProcessor {
    Bitmap frame;
    public FrameProcessor(Bitmap bitmap) {
        this.frame = bitmap;
    }

    abstract public String run();
}
