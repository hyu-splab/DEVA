package com.example.edgedashanalytics.advanced.common;

import java.util.Deque;

public class WorkerHistory {
    public Deque<FrameResult> history;
    public static final int MAX_HISTORY = 10;

    public int waiting;
    public double processTime;

    private int totalProcessTime;
    public int totalNetworkTime;

    public WorkerHistory() {
        waiting = 0;
        processTime = 0;
        totalProcessTime = 0;
    }

    public void addResult(FrameResult result) {
        history.add(result);
        totalProcessTime += result.processTime;
        totalNetworkTime += result.networkTime;

        if (history.size() > MAX_HISTORY) {
            FrameResult old = history.pop();
            totalProcessTime -= old.processTime;
            totalNetworkTime -= old.networkTime;
        }

        processTime = (double) totalProcessTime / history.size();
    }
}
