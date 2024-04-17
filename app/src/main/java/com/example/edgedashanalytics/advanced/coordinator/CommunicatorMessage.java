package com.example.edgedashanalytics.advanced.coordinator;

public class CommunicatorMessage {
    public int type;
    public boolean isInner;
    public int frameNum;
    public byte[] data;
    public long receivedTime;

    public CommunicatorMessage(int type, boolean isInner, int frameNum, byte[] data, long receivedTime) {
        this.type = type;
        this.isInner = isInner;
        this.frameNum = frameNum;
        this.data = data;
        this.receivedTime = receivedTime;
    }

    public CommunicatorMessage(int type) {
        this.type = type;
    }
}
