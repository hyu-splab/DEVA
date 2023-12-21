package com.example.edgedashanalytics.advanced.coordinator;

import android.util.Log;
import android.util.Size;

import com.example.edgedashanalytics.advanced.common.WorkerStatus;

import java.util.List;

public class Controller_Archived {
    private static final String TAG = "Controller_Archived";
    private final EDACam innerCam = null;
    private final EDACam outerCam = null;
    private static final double NETWORK_SLOW = 300;
    private static final double WAIT_SLOW = 2.5;

    private static final double NETWORK_FAST = 100;
    private static final double WAIT_FAST = 1.5;
    static final int[] frameRateValues = {5, 10, 15, 20, 25, 30};
    static final Size[] resolutionValues = {
            new Size(640, 360),
            new Size(854, 480),
            new Size(960, 540),
            new Size(1280, 720)
    };
    static final int[] qualityValues = {30, 40, 50, 60, 70, 80, 90, 100};

    public void adjustV2(List<EDAWorker> workers, CamSettings_Archived innerCamSettings, CamSettings_Archived outerCamSettings) {
        // new algorithm will pick the best settings that don't violate two major constraints:
        // 1. network speed
        // 2. worker capacity

        double networkTime;
        double workerCapacity;

        // First, we calculate average network speed for all workers:
        double totalNetworkTime = 0;
        int numHistory = 0;
        for (EDAWorker worker : workers) {
            WorkerStatus status = worker.status;
            totalNetworkTime += status.innerHistory.totalNetworkTime;
            totalNetworkTime += status.outerHistory.totalNetworkTime;
            numHistory += status.innerHistory.history.size() + status.outerHistory.history.size();
        }

        networkTime = totalNetworkTime / numHistory;

        /*
        Second, total capacity of workers are calculated
        inner, outer weights are constants, calculated based on experimental results
        So far, it is deemed that outer analysis typically takes considerably more time
        so we assume that inner time should get more attention than its value

        It is needed to consider both values since both cameras may not always be available
        */
        final double innerWeight = 2;
        final double outerWeight = 1;

        double totalInnerProcessTime = 0, totalOuterProcessTime = 0;
        for (EDAWorker worker : workers) {
            WorkerStatus status = worker.status;
            totalInnerProcessTime += status.innerProcessTime();
            totalOuterProcessTime += status.outerProcessTime();
        }

        // capacity is "how many frames can be addressed within a second?"
        // so it's (# of workers) / (average time to process one frame)
        double avgProcessTime = (innerWeight * totalInnerProcessTime + outerWeight * totalOuterProcessTime) / numHistory;
        workerCapacity = workers.size() / avgProcessTime;

        int bestFrameRate = -1;
        Size bestResolution = null;
        int bestQuality = -1;
        int bestScore = -1;

        // iterate all values over all three arguments
        for (int frameRate : frameRateValues) {
            for (Size resolution : resolutionValues) {
                for (int quality : qualityValues) {
                    if (getConstraintNetwork(frameRate, resolution, quality) < networkTime
                            && getConstraintWorkerCapacity(frameRate) < workerCapacity) {
                        int score = getScore(frameRate, resolution, quality);
                        if (score > bestScore) {
                            bestScore = score;
                            bestFrameRate = frameRate;
                            bestResolution = resolution;
                            bestQuality = quality;
                        }
                    }
                }
            }
        }
    }

    private int getScore(int frameRate, Size resolution, int quality) {
        // TODO
        return 0;
    }

    private double getConstraintWorkerCapacity(int frameRate) {
        // TODO
        return 0.0;
    }

    private double getConstraintNetwork(int frameRate, Size resolution, int quality) {
        // TODO
        return 0.0;
    }

    // pre-calculated data sizes, obtained from experimental results
    // use primitive array type for fast access
    public int[][] expectedDataSize;

    private int getDataSize(Size resolution, int quality) {
        int r, q;
        switch (resolution.getWidth()) {
            case 640: r = 0; break;
            case 854: r = 1; break;
            case 960: r = 2; break;
            case 1280: r = 3; break;
            default:
                throw new RuntimeException("Unknown resolution: " + resolution.getWidth() + "x" + resolution.getHeight());
        }
        switch (quality) {
            case 30: q = 0; break;
            case 40: q = 1; break;
            case 50: q = 2; break;
            case 60: q = 3; break;
            case 70: q = 4; break;
            case 80: q = 5; break;
            case 90: q = 6; break;
            case 100: q = 7; break;
            default:
                throw new RuntimeException("Unknown quality: " + quality);
        }

        return expectedDataSize[r][q];
    }
    private int calculateNetworkThroughput(int frameRate, Size resolution, int quality) {
        return frameRate * getDataSize(resolution, quality);
    }

    public void adjustCamSettings(List<EDAWorker> workers, CamSettings_Archived innerCamSettings, CamSettings_Archived outerCamSettings) {
        double avgInnerWait = 0.0;
        double avgOuterWait = 0.0;
        double avgNetworkTime = 0.0;

        int totalNetworkTime = 0;
        int totalInnerWait = 0;
        int totalOuterWait = 0;
        int innerCount = 0, outerCount = 0;

        if (workers.size() == 0) {
            Log.d(TAG, "no workers available");
            return;
        }

        for (EDAWorker worker : workers) {
            WorkerStatus status = worker.status;
            innerCount += status.innerHistory.history.size();
            totalInnerWait += status.innerWaiting;

            outerCount += status.outerHistory.history.size();
            totalOuterWait += status.outerWaiting;

            totalNetworkTime += status.innerHistory.totalNetworkTime + status.outerHistory.totalNetworkTime;
        }

        if (innerCount > 0) {
            avgInnerWait = (double)totalInnerWait / workers.size();
        }

        if (outerCount > 0) {
            avgOuterWait = (double)totalOuterWait / workers.size();
        }

        if (innerCount + outerCount > 0) {
            avgNetworkTime = (double)totalNetworkTime / (innerCount + outerCount);
        }
        else {
            Log.d(TAG, "No history available");
            return;
        }

        Log.d(TAG, "innerwait = " + avgInnerWait + ", outerwait = " + avgOuterWait
                + ", network = " + avgNetworkTime);

        boolean x, y; // temporary variables
        innerCamSettings.initializeChanged();
        outerCamSettings.initializeChanged();

        /*
        Rule #1: Do not let the network to be saturated
         */
        if (avgNetworkTime > NETWORK_SLOW) {
            if (!innerCamSettings.decreaseFrameRate()) {
                if (!outerCamSettings.decreaseFrameRate()) {
                    x = innerCamSettings.decreaseQuality();
                    y = outerCamSettings.decreaseQuality();
                    if (!x && !y) {
                        x = innerCamSettings.decreaseResolution();
                        y = outerCamSettings.decreaseResolution();
                        if (!x && !y) {
                            warnMin("network");
                        }
                    }
                }
            }
        }

        /*
        Rule #2: We don't want saturated worker queues
         */
        if (avgInnerWait > WAIT_SLOW || avgOuterWait > WAIT_SLOW) {
            if (!innerCamSettings.decreaseFrameRate()) {
                if (!outerCamSettings.decreaseFrameRate()) {
                    x = innerCamSettings.decreaseResolution();
                    y = outerCamSettings.decreaseResolution();
                    if (!x && !y) {
                        warnMin("worker queue");
                    }
                }
            }
        }

        /*
        Rule #3: If everything is more than good, try measuring in higher quality
         */
        if (avgNetworkTime < NETWORK_FAST && avgInnerWait < WAIT_FAST && avgOuterWait < WAIT_FAST) {
            x = innerCamSettings.increaseQuality();
            y = outerCamSettings.increaseQuality();
            if (!x && !y) {
                if (!outerCamSettings.increaseFrameRate()) {
                    if (!innerCamSettings.increaseFrameRate()) {
                        warnMax("all is well");
                    }
                }
            }
        }

        if (innerCam.outStream != null) {
            innerCam.sendSettings(innerCam.outStream);
            //Log.d(TAG, "sending adjusted setting to innercam");
        }
        if (outerCam.outStream != null) {
            //Log.d(TAG, "sending adjusted setting to outercam");
            outerCam.sendSettings(outerCam.outStream);
        }

        for (EDAWorker worker : workers) {
            worker.status.innerHistory.removeOldResults();
            worker.status.outerHistory.removeOldResults();
            worker.status.calcNetworkTime();
        }
    }

    private void warnMin(String name) {
        Log.w(TAG, "Property '" + name + "' is already minimal");
    }

    private void warnMax(String name) {
        Log.w(TAG, "Property '" + name + "' is already maximal");
    }
}
