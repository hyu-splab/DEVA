package com.example.edgedashanalytics.util.worker;

import android.graphics.Bitmap;

import com.example.edgedashanalytics.util.video.analysis.Frame;

abstract public class FrameProcessor {
    Bitmap frame;
    int cameraFrameNum;

    public void setFrame(Bitmap frame) {
        this.frame = frame;
    }

    public void setCameraFrameNum(int cameraFrameNum) {
        this.cameraFrameNum = cameraFrameNum;
    }

    abstract public String run();
}
