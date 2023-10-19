package com.example.edgedashanalytics.advanced.common;

import java.util.ArrayDeque;
import java.util.Deque;

public class WorkerStatus {
    private static final String TAG = "WorkerStatus";

    public WorkerHistory innerHistory, outerHistory;
    public double networkTime;

    public WorkerStatus() {
        innerHistory = new WorkerHistory();
        outerHistory = new WorkerHistory();
        networkTime = 0;
    }

    public void addHistory(FrameResult result) {
        WorkerHistory history = result.isInner ? innerHistory : outerHistory;
        synchronized (this) {
            history.addResult(result);
            networkTime = (double) (innerHistory.totalNetworkTime + outerHistory.totalNetworkTime)
                    / (innerHistory.history.size() + outerHistory.history.size());
        }
    }

    public int innerWaiting() {
        return innerHistory.waiting;
    }

    public double innerProcessTime() {
        return innerHistory.processTime;
    }

    public int outerWaiting() {
        return outerHistory.waiting;
    }

    public double outerProcessTime() {
        return outerHistory.processTime;
    }
}
