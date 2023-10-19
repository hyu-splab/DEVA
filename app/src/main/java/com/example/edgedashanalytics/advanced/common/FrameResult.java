package com.example.edgedashanalytics.advanced.common;

public class FrameResult {
    public boolean isInner;
    public int processTime;
    public int networkTime;

    public FrameResult(boolean isInner, int procTime, int networkTime) {
        this.isInner = isInner;
        this.processTime = procTime;
        this.networkTime = networkTime;
    }
}
