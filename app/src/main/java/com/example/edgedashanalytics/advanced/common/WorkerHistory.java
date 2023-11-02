package com.example.edgedashanalytics.advanced.common;

import java.util.ArrayDeque;
import java.util.Deque;

public class WorkerHistory {
    public Deque<FrameResult> history;
    public static final long HISTORY_DURATION = 2000;

    public double processTime;

    public int totalProcessTime;
    public int totalNetworkTime;

    public WorkerHistory() {
        history = new ArrayDeque<>();
        processTime = 0;
        totalProcessTime = 0;
    }

    public WorkerHistory(WorkerHistory org) {
        history = new ArrayDeque<>();
        processTime = org.processTime;
        totalProcessTime = org.totalProcessTime;
        totalNetworkTime = org.totalNetworkTime;
    }

    public synchronized void addResult(FrameResult result) {
        history.add(result);
        totalProcessTime += result.processTime;
        totalNetworkTime += result.networkTime;

        calcProcessTime();
    }

    public synchronized void removeOldResults() {
        long curTime = System.currentTimeMillis();
        while (!history.isEmpty() && curTime - history.peek().timestamp > HISTORY_DURATION) {
            FrameResult old = history.pop();
            totalProcessTime -= old.processTime;
            totalNetworkTime -= old.networkTime;
        }

        calcProcessTime();
    }

    private void calcProcessTime() {
        if (history.isEmpty())
            processTime = 0;
        else
            processTime = (double) totalProcessTime / history.size();
    }
}
