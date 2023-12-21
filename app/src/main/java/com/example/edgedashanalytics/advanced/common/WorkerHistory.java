package com.example.edgedashanalytics.advanced.common;

import android.util.Log;

import java.util.ArrayDeque;
import java.util.Deque;

public class WorkerHistory {
    private static final String TAG = "WorkerHistory";
    public Deque<FrameResult> history;
    public static final long HISTORY_DURATION = 2000;
    public static final double DEFAULT_PROCESS_TIME = 100;

    public double processTime;

    public int totalProcessTime;
    public int totalNetworkTime;

    public WorkerHistory() {
        history = new ArrayDeque<>();
        processTime = DEFAULT_PROCESS_TIME;
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
            processTime = DEFAULT_PROCESS_TIME;
        else
            processTime = (double) totalProcessTime / history.size();
        //Log.v(TAG, "processTime = " + processTime);
    }
}
