package com.example.edgedashanalytics.advanced.common;

import java.io.Serializable;
import java.util.List;

public class WorkerResult implements Serializable {
    public boolean success;
    public boolean isInner;
    public long coordinatorStartTime; // before coordinator's send
    public long processTime; // EDA analysis time
    public long totalTime; // from receive to send result, for calculating network time
    public int frameNum;
    public int cameraFrameNum;
    public String msg;
    public long dataSize;
    public long queueSize;

    public boolean isDistracted; // Used only for inner
    public List<String> hazards; // Used only for outer
    public long energyConsumed;

    public WorkerResult(boolean isInner, long coordinatorStartTime, int frameNum, int cameraFrameNum,
                        long processTime, long totalTime, String msg, long dataSize, long queueSize,
                        boolean isDistracted, List<String> hazards, long energyConsumed) {
        this.success = true;
        this.isInner = isInner;
        this.coordinatorStartTime = coordinatorStartTime;
        this.frameNum = frameNum;
        this.cameraFrameNum = cameraFrameNum;
        this.processTime = processTime;
        this.totalTime = totalTime;
        this.msg = msg;
        this.dataSize = dataSize;
        this.queueSize = queueSize;
        this.isDistracted = isDistracted;
        this.hazards = hazards;
        this.energyConsumed = energyConsumed;
    }

    public WorkerResult(boolean isInner, long dataSize, long energyConsumed) {
        this.success = false;
        this.isInner = isInner;
        this.dataSize = dataSize;
        this.energyConsumed = energyConsumed;
    }
}
