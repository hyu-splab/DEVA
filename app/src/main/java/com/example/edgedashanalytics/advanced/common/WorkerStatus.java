package com.example.edgedashanalytics.advanced.common;

import java.util.ArrayList;

public class WorkerStatus {
    private static final String TAG = "WorkerStatus";
    // 20 for lightning, 50 for thunder
    public static final double DEFAULT_INNER_PROCESS_TIME = 40;
    // 20 for mobilenet_v1, 30, 50, 70, 110, 230 for efficientdet-lite0-4
    public static final double DEFAULT_OUTER_PROCESS_TIME = 100;
    public WorkerHistory innerHistory, outerHistory;
    public int innerWaiting, outerWaiting;
    public long latestQueueSize;
    public double networkTime;
    public boolean isConnected;
    public ArrayList<Integer> temperatures;
    public ArrayList<Integer> frequencies;
    public long lastUpdated;
    public long lastChecked;

    public WorkerStatus() {
        innerHistory = new WorkerHistory((int)DEFAULT_INNER_PROCESS_TIME);
        outerHistory = new WorkerHistory((int)DEFAULT_OUTER_PROCESS_TIME);
        innerWaiting = outerWaiting = 0;
        networkTime = 0;
        latestQueueSize = 0;
    }

    public WorkerStatus(WorkerStatus org) {
        temperatures = org.temperatures;
        frequencies = org.frequencies;
        innerHistory = new WorkerHistory(org.innerHistory);
        outerHistory = new WorkerHistory(org.outerHistory);
        innerWaiting = org.innerWaiting;
        outerWaiting = org.outerWaiting;
        networkTime = org.networkTime;
        isConnected = org.isConnected;
        latestQueueSize = org.latestQueueSize;
    }

    public synchronized void addResult(AnalysisResult result) {
        WorkerHistory history = result.isInner ? innerHistory : outerHistory;
        history.addResult(result);
        calcNetworkTime();
        lastUpdated = result.timestamp;
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
        innerHistory.removeOldResults();
        if (innerHistory.history.isEmpty())
            return DEFAULT_INNER_PROCESS_TIME;
        return innerHistory.processTime;
    }

    public double getAverageOuterProcessTime() {
        outerHistory.removeOldResults();
        if (outerHistory.history.isEmpty())
            return DEFAULT_OUTER_PROCESS_TIME;
        return outerHistory.processTime;
    }

    public double getAverage() {
        return getAverageOuterProcessTime();
        //return (getAverageInnerProcessTime() + getAverageOuterProcessTime()) / 2;
    }

    public double getAverageForReal() {
        return (getAverageInnerProcessTime() + getAverageOuterProcessTime()) / 2;
    }

    public double getWeight() {
        return getWeightByAverageQueueSize();
    }

    public double getLatencyWithQueueSize(double queueSize) {
        return getAverage() * (queueSize + 1);
    }

    public double getAverageQueueSize() {
        innerHistory.removeOldResults();
        outerHistory.removeOldResults();
        long totalQueueSize = innerHistory.getSumQueueSize() + outerHistory.getSumQueueSize();
        long totalHistoryCount = innerHistory.history.size() + outerHistory.history.size();
        return (totalHistoryCount == 0 ? 0.0 : (double)totalQueueSize / totalHistoryCount);
    }

    private double getWeightByAverageQueueSize() {
        double averageQueueSize = getAverageQueueSize();
        return 1.0 / (getAverage() * (averageQueueSize + 1.0));
    }

    public double getWeightByLatestQueueSize() {
        return 1.0 / (getAverage() * (latestQueueSize + 1));
    }
}
