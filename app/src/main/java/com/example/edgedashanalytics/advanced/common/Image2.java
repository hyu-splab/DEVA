package com.example.edgedashanalytics.advanced.common;

import java.io.Serializable;

public class Image2 implements Serializable {
    public boolean isInner;
    public int frameNum;
    public int cameraFrameNum;
    public long coordinatorStartTime;
    public long workerStartTime;
    public byte[] data;
    public long dataSize;

    public Image2(boolean isInner, int frameNum, int cameraFrameNum, byte[] data) {
        this.isInner = isInner;
        this.frameNum = frameNum;
        this.cameraFrameNum = cameraFrameNum;
        this.data = data;
        this.dataSize = data.length;
    }
}
