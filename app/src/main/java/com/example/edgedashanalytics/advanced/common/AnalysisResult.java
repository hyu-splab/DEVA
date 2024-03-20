package com.example.edgedashanalytics.advanced.common;

public class AnalysisResult {
    private static final String TAG = "FrameResult";
    public long timestamp;
    public int frameNum;
    public int cameraFrameNum;
    public int workerNum;
    public boolean isInner;
    public long processTime;
    public long networkTime;
    public long workerTime;
    public long turnaround;
    public long queueSize;
    public int powerConsumed;

    public AnalysisResult(long timestamp, int frameNum, int cameraFrameNum, int workerNum, boolean isInner,
                          long workerTime, long processTime, long networkTime, long turnaround, long queueSize, int powerConsumed) {
        this.timestamp = timestamp;
        this.frameNum = frameNum;
        this.cameraFrameNum = cameraFrameNum;
        this.workerNum = workerNum;
        this.isInner = isInner;
        this.workerTime = workerTime;
        this.processTime = processTime;
        this.networkTime = networkTime;
        this.turnaround = turnaround;
        this.queueSize = queueSize;
        this.powerConsumed = powerConsumed;
    }
}
