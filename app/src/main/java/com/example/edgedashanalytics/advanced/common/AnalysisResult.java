package com.example.edgedashanalytics.advanced.common;

public class AnalysisResult {
    private static final String TAG = "FrameResult";
    public long timestamp;
    public int frameNum;
    public int workerNum;
    public boolean isInner;
    public long processTime;
    public long networkTime;
    public long workerTime;
    public long responseTime;
    public long queueSize;

    public AnalysisResult(long timestamp, int frameNum, int workerNum, boolean isInner,
                          long workerTime, long processTime, long networkTime, long responseTime, long queueSize) {
        this.timestamp = timestamp;
        this.frameNum = frameNum;
        this.workerNum = workerNum;
        this.isInner = isInner;
        this.workerTime = workerTime;
        this.processTime = processTime;
        this.networkTime = networkTime;
        this.responseTime = responseTime;
        this.queueSize = queueSize;
    }
}
