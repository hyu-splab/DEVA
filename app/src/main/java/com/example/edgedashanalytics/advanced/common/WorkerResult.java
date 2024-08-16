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

    public ArrayList<Integer> temperatures;
    public ArrayList<Integer> frequencies;

    public WorkerResult() {

    }

    public static WorkerResult createResult(int frameNum, long processTime, long totalTime, String msg,
                                            long queueSize, boolean isDistracted, List<String> hazards,
                                            ArrayList<Integer> temperatures, ArrayList<Integer> frequencies) {
        WorkerResult res = new WorkerResult();
        res.success = true;
        res.frameNum = frameNum;
        res.processTime = processTime;
        res.totalTime = totalTime;
        res.msg = msg;
        res.queueSize = queueSize;
        res.isDistracted = isDistracted;
        res.hazards = hazards;
        res.temperatures = temperatures;
        res.frequencies = frequencies;
        return res;
    }

    public static WorkerResult createFailedResult(int frameNum, long queueSize, long processTime, long totalTime, ArrayList<Integer> temperatures, ArrayList<Integer> frequencies) {
        WorkerResult res = new WorkerResult();
        res.frameNum = frameNum;
        res.processTime = processTime;
        res.totalTime = totalTime;
        res.success = false;
        res.queueSize = queueSize;
        res.temperatures = temperatures;
        res.frequencies = frequencies;
        return res;
    }
}
