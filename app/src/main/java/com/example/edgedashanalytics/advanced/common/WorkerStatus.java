package com.example.edgedashanalytics.advanced.common;

import android.util.Log;

public class WorkerStatus {
    private static final String TAG = "WorkerStatus";
    private static final double DEFAULT_PERFORMANCE = 1.0;
    private static final double DEFAULT_INNER_PROCESS_TIME = 50;
    private static final double DEFAULT_OUTER_PROCESS_TIME = 100;
    public WorkerHistory innerHistory, outerHistory;
    public int innerWaiting, outerWaiting;
    public double networkTime;

    public WorkerStatus() {
        innerHistory = new WorkerHistory(50);
        outerHistory = new WorkerHistory(100);
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
        return innerHistory.totalProcessTime;
    }

    public double outerProcessTime() {
        return outerHistory.totalProcessTime;
    }

    public double getPerformance() {
        return 1.0 / (getAverageInnerProcessTime() + getAverageOuterProcessTime());
        /*int totalWaiting = innerWaiting + outerWaiting;
        if (totalWaiting == 0)
            return DEFAULT_PERFORMANCE;

        return totalWaiting / (((innerWaiting * getAverageInnerProcessTime())
                + (outerWaiting * getAverageOuterProcessTime())) / 2.0);*/
    }

    public double getAverageInnerProcessTime() {
        if (innerHistory.history.isEmpty())
            return DEFAULT_INNER_PROCESS_TIME;
        return innerHistory.processTime;
    }

    public double getAverageOuterProcessTime() {
        if (outerHistory.history.isEmpty())
            return DEFAULT_OUTER_PROCESS_TIME;
        return outerHistory.processTime;
    }
}
