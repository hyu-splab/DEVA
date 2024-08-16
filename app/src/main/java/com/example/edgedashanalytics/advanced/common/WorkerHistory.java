package com.example.edgedashanalytics.advanced.common;

import android.util.Log;

import java.util.ArrayDeque;
import java.util.Deque;

public class WorkerHistory {
    private static final String TAG = "WorkerHistory";
    public Deque<AnalysisResult> history;
    // Should be removed
    public static final long MAX_HISTORY_DURATION = 1000;

    public double processTime;

    public int totalProcessTime;
    public int totalNetworkTime;

    public WorkerHistory(long defaultProcessTime) {
        history = new ArrayDeque<>();
        processTime = defaultProcessTime;
        totalProcessTime = 0;
    }

    public WorkerHistory(WorkerHistory org) {
        history = new ArrayDeque<>();
        processTime = org.processTime;
        totalProcessTime = org.totalProcessTime;
        totalNetworkTime = org.totalNetworkTime;
    }

    public synchronized void addResult(AnalysisResult result) {
        history.add(result);
        totalProcessTime += result.processTime;
        totalNetworkTime += result.networkTime;

        calcProcessTime();
    }

    public synchronized long getSumQueueSize() {
        long sum = 0;
        for (AnalysisResult res : history) {
            sum += res.queueSize;
        }
        return sum;
    }

    public synchronized void removeOldResults() {
        long curTime = System.currentTimeMillis();
        while (!history.isEmpty() && curTime - history.peekFirst().timestamp > MAX_HISTORY_DURATION) {
            StringBuilder sb = new StringBuilder();
            //sb.append("Removing ").append(history.peekFirst().processTime).append(" ").append(history.peekFirst().timestamp).append(" ").append(curTime).append(" (").append(curTime - history.peekFirst().timestamp).append(")");
            //Log.w(TAG, sb.toString());
            AnalysisResult old = history.pop();
            totalProcessTime -= old.processTime;
            totalNetworkTime -= old.networkTime;
        }

        calcProcessTime();
    }

    private void calcProcessTime() {
        int sz = history.size();
        //StringBuilder sb = new StringBuilder();
        if (sz != 0) {
            /*for (AnalysisResult res : history) {
                sb.append(res.processTime).append(" ");
            }*/
            processTime = (double) totalProcessTime / sz;
        }
        //Log.w(TAG, sb.toString());
    }
}
