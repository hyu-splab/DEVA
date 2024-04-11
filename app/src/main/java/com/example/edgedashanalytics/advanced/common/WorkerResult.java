package com.example.edgedashanalytics.advanced.common;

import java.io.Serializable;
import java.util.ArrayList;
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
    public int energyConsumed;
    public ArrayList<Integer> temperatures;
    public ArrayList<Integer> frequencies;

    public WorkerResult() {

    }

    public static WorkerResult createResult(boolean isInner, long coordinatorStartTime, int frameNum, int cameraFrameNum,
                                            long processTime, long totalTime, String msg, long dataSize, long queueSize,
                                            boolean isDistracted, List<String> hazards, int energyConsumed, ArrayList<Integer> temperatures, ArrayList<Integer> frequencies) {
        WorkerResult res = new WorkerResult();
        res.success = true;
        res.isInner = isInner;
        res.coordinatorStartTime = coordinatorStartTime;
        res.frameNum = frameNum;
        res.cameraFrameNum = cameraFrameNum;
        res.processTime = processTime;
        res.totalTime = totalTime;
        res.msg = msg;
        res.dataSize = dataSize;
        res.queueSize = queueSize;
        res.isDistracted = isDistracted;
        res.hazards = hazards;
        res.energyConsumed = energyConsumed;
        res.temperatures = temperatures;
        res.frequencies = frequencies;
        return res;
    }

    public static WorkerResult createFailedResult(boolean isInner, long dataSize, long queueSize, int energyConsumed, ArrayList<Integer> temperatures, ArrayList<Integer> frequencies) {
        WorkerResult res = new WorkerResult();
        res.success = false;
        res.isInner = isInner;
        res.dataSize = dataSize;
        res.queueSize = queueSize;
        res.energyConsumed = energyConsumed;
        res.temperatures = temperatures;
        res.frequencies = frequencies;
        return res;
    }
}
