package com.example.edgedashanalytics.advanced.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class WorkerResult implements Serializable {
    public boolean success;
    public long processTime; // EDA analysis time
    public long totalTime; // from receive to send result, for calculating network time
    public int frameNum;
    public String msg;
    public long queueSize;

    public boolean isDistracted; // Used only for inner
    public List<String> hazards; // Used only for outer

    public WorkerResult() {

    }

    public static WorkerResult createResult(int frameNum, long processTime, long totalTime, String msg,
                                            long queueSize, boolean isDistracted, List<String> hazards) {
        WorkerResult res = new WorkerResult();
        res.success = true;
        res.frameNum = frameNum;
        res.processTime = processTime;
        res.totalTime = totalTime;
        res.msg = msg;
        res.queueSize = queueSize;
        res.isDistracted = isDistracted;
        res.hazards = hazards;
        return res;
    }

    public static WorkerResult createFailedResult(int frameNum, long queueSize, long processTime, long totalTime) {
        WorkerResult res = new WorkerResult();
        res.frameNum = frameNum;
        res.processTime = processTime;
        res.totalTime = totalTime;
        res.success = false;
        res.queueSize = queueSize;
        return res;
    }
}
