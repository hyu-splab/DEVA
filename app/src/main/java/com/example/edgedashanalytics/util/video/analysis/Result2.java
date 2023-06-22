package com.example.edgedashanalytics.util.video.analysis;

import java.io.Serializable;

public class Result2 implements Serializable {
    public boolean isInner;
    public long frameNumber;
    public String msg;

    public Result2(boolean isInner, long frameNumber, String msg) {
        this.isInner = isInner;
        this.frameNumber = frameNumber;
        this.msg = msg;
    }
}
