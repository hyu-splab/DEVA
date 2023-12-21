package com.example.edgedashanalytics.advanced.common;

import java.io.Serializable;

public class WorkerResult implements Serializable {
    public boolean isInner;
    public long coordinatorStartTime; // before coordinator's send
    public long processTime; // EDA analysis time
    public long totalTime; // from receive to send result, for calculating network time
    public int frameNum;
    public String msg;
    public long dataSize;

    public WorkerResult(boolean isInner, long coordinatorStartTime, int frameNum, long processTime, long totalTime, String msg, long dataSize) {
        this.isInner = isInner;
        this.coordinatorStartTime = coordinatorStartTime;
        this.frameNum = frameNum;
        this.processTime = processTime;
        this.totalTime = totalTime;
        this.msg = msg;
        this.dataSize = dataSize;
    }
}
