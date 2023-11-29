package com.example.edgedashanalytics.advanced.coordinator;

import android.util.Size;

import com.example.edgedashanalytics.advanced.common.WorkerStatus;

import java.util.List;

public class Controller_Archived {
    static final int[] frameRateValues = {5, 10, 15, 20, 25, 30};
    static final Size[] resolutionValues = {
            new Size(640, 360),
            new Size(854, 480),
            new Size(960, 540),
            new Size(1280, 720)
    };
    static final int[] qualityValues = {30, 40, 50, 60, 70, 80, 90, 100};

    public void adjustV2(List<EDAWorker> workers, CamSettings innerCamSettings, CamSettings outerCamSettings) {
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
}
