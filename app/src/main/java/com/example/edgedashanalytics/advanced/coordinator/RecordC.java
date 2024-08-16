package com.example.edgedashanalytics.advanced.coordinator;

public class RecordC {
    public boolean isInner;
    public long cameraFrameNum;
    public long receivedTime;
    public long dataSize;

    public RecordC(boolean isInner, long cameraFrameNum, long receivedTime, long dataSize) {
        this.isInner = isInner;
        this.cameraFrameNum = cameraFrameNum;
        this.receivedTime = receivedTime;
        this.dataSize = dataSize;
    }
}
