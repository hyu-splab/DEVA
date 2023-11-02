package com.example.edgedashanalytics.advanced.common;

import android.util.Log;

public class WorkerStatus {
    private static final String TAG = "WorkerStatus";

    public WorkerHistory innerHistory, outerHistory;
    public int innerWaiting, outerWaiting;
    public double networkTime;

    public WorkerStatus() {
        innerHistory = new WorkerHistory();
        outerHistory = new WorkerHistory();
        innerWaiting = outerWaiting = 0;
        networkTime = 0;
    }

    public WorkerStatus(WorkerStatus org) {
        innerHistory = new WorkerHistory(org.innerHistory);
        outerHistory = new WorkerHistory(org.outerHistory);
        innerWaiting = org.innerWaiting;
        outerWaiting = org.outerWaiting;
        networkTime = org.networkTime;
    }

    public synchronized void addResult(FrameResult result) {
        WorkerHistory history = result.isInner ? innerHistory : outerHistory;
        history.addResult(result);
        calcNetworkTime();
    }

    public synchronized void calcNetworkTime() {
        //Log.d(TAG, "size = " + innerHistory.history.size() + ", " + outerHistory.history.size());
        if (innerHistory.history.size() + outerHistory.history.size() == 0)
            networkTime = 0;
        else
            networkTime = (double) (innerHistory.totalNetworkTime + outerHistory.totalNetworkTime)
                / (innerHistory.history.size() + outerHistory.history.size());
        //Log.d(TAG, "networkTime = " + networkTime);
    }

    public double innerProcessTime() {
        return innerHistory.processTime;
    }

    public double outerProcessTime() {
        return outerHistory.processTime;
    }
}
