package com.example.edgedashanalytics.advanced.coordinator;

import android.util.Size;

public class EDACamera {
    public boolean isInner;
    public Size resolution;
    public int quality;
    public int frameRate;

    public EDACamera(boolean isInner, Size resolution, int quality, int frameRate) {
        this.isInner = isInner;
        this.resolution = resolution;
        this.quality = quality;
        this.frameRate = frameRate;
    }
}
