package com.example.edgedashanalytics.util.video.analysis;

import android.graphics.Bitmap;

import java.io.Serializable;

public class Image2 implements Serializable {
    public boolean isInner;
    public long frameNumber;
    public byte[] data;

    public Image2(boolean isInner, long frameNumber, byte[] data) {
        this.isInner = isInner;
        this.frameNumber = frameNumber;
        this.data = data;
    }
}
