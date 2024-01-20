package com.example.edgedashanalytics.advanced.worker;

import android.graphics.Bitmap;

import java.util.List;

abstract public class FrameProcessor {
    Bitmap frame;
    int cameraFrameNum;

    public void setFrame(Bitmap frame) {
        this.frame = frame;
    }

    public void setCameraFrameNum(int cameraFrameNum) {
        this.cameraFrameNum = cameraFrameNum;
    }

    abstract public ProcessResult run();

    public static class ProcessResult {
        public String msg;
        public boolean isDistracted;
        public List<String> hazards;

        public ProcessResult(String msg, boolean isDistracted, List<String> hazards) {
            this.msg = msg;
            this.isDistracted = isDistracted;
            this.hazards = hazards;
        }
    }
}
