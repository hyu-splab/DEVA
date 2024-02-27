package com.example.edgedashanalytics.advanced.coordinator;

import static com.example.edgedashanalytics.advanced.coordinator.AdvancedMain.communicator;

import android.util.Log;

import com.example.edgedashanalytics.advanced.common.WorkerStatus;

import java.util.List;

public class Controller {
    private static final String TAG = "Controller";
    private final EDACam innerCam, outerCam;
    private long prevPendingDataSize, prevTotalDataSize;

    private static final double INNER_TIME_MULTIPLIER = 2.2;
    // 3.0 for 2 threads, 2.0 for 1 thread (tentative)
    private static final double CAPACITY_LENIENCY = 2.0;
    private static final double CAPACITY_MULTIPLIER = 0.7;
    private static final double F_WEIGHT = 2.0;
    private static final int F_DEC_AMOUNT = 5, F_INC_AMOUNT = 2;
    private static final int RQ_DEC_AMOUNT = 2, RQ_INC_AMOUNT = 1;
    private static final double TOO_MANY_WAITING = 3;
    private static final double TOO_FEW_WAITING = 1;
    private static final long TOO_MUCH_PENDING = 5000000;
    private static final long TOO_LITTLE_PENDING = 2000000;
    private static final double INNER_OUTER_RATIO = 1.5;

    private static int okStreak = 0;

    public static boolean connectionChanged = false;

    public Controller(EDACam innerCam, EDACam outerCam) {
        this.innerCam = innerCam;
        this.outerCam = outerCam;
        prevPendingDataSize = 0;
    }

    private double getWorkerCapacity(List<EDAWorker> workers) {
        int numHistory = 0;
        for (EDAWorker worker : workers) {
            WorkerStatus status = worker.status;
            numHistory += status.innerHistory.history.size() + status.outerHistory.history.size();
        }

        double totalInnerProcessTime = 0, totalOuterProcessTime = 0;
        for (EDAWorker worker : workers) {
            WorkerStatus status = worker.status;
            totalInnerProcessTime += status.innerProcessTime();
            totalOuterProcessTime += status.outerProcessTime();
        }

        // capacity is "how many frames can be addressed within a second?"
        // so it's (# of workers) / (average time to process one frame)
        double avgProcessTime = (INNER_TIME_MULTIPLIER * totalInnerProcessTime + totalOuterProcessTime) / numHistory;
        return (workers.size() / avgProcessTime) * 1000 * CAPACITY_LENIENCY;
    }

    /*
    19/02/2014: Resolution and Quality are fixed, only modify frame rate
     */
    public void adjustCamSettingsV4(List<EDAWorker> workers, CamSettings innerCamSettings, CamSettings outerCamSettings) {
        long pendingDataSize = communicator.pendingDataSize;
        long totalDataSize = communicator.totalDataSize;

        int innerWaiting = 0, outerWaiting = 0;
        int numWorkers = workers.size();

        if (connectionChanged) {
            setDefaultFPS(innerCamSettings, outerCamSettings);
            connectionChanged = false;
        }

        // 1. Calculate average network speed for all workers
        double workerCapacity = getWorkerCapacity(workers);

        for (EDAWorker worker : workers) {
            WorkerStatus status = worker.status;
            innerWaiting += status.innerWaiting;
            outerWaiting += status.outerWaiting;
        }

        int totalWaiting = innerWaiting + outerWaiting;

        /* 1 */ boolean networkSlow = totalWaiting > TOO_MANY_WAITING * numWorkers || pendingDataSize > TOO_MUCH_PENDING * numWorkers;
        /* 2 */ boolean networkFast = totalWaiting < TOO_FEW_WAITING * numWorkers && pendingDataSize < TOO_LITTLE_PENDING * numWorkers;

        int iF = innerCamSettings.getF(), oF = outerCamSettings.getF();
        double weightedF = iF * INNER_TIME_MULTIPLIER + oF;

        /* 3 */ boolean workerSlow = workerCapacity < weightedF;
        /* 4 */ boolean workerFast = workerCapacity * CAPACITY_MULTIPLIER > weightedF;

        int innerF = innerCamSettings.getF();
        int outerF = outerCamSettings.getF();

        StringBuilder sb = new StringBuilder();
        sb.append(totalWaiting).append(" ").append(networkSlow).append(" ").append(networkFast).append("\n");
        sb.append(workerCapacity).append(" ").append(weightedF).append(" ").append(workerSlow).append(" ").append(workerFast).append("\n");

        Log.v(TAG, sb.toString());

        final int outerPrioritizing = 5;

        if (networkSlow || workerSlow) {
            innerCamSettings.decreaseV4(1);
            outerCamSettings.decreaseV4(1);
            okStreak = 0;
        }

        else if (networkFast || workerFast) {
            okStreak++;
            if (okStreak == 1) {
                if (innerF + outerPrioritizing < outerF) {
                    innerCamSettings.increaseV4(1);
                } else {
                    if (outerCamSettings.increaseV4(1) == 0) {
                        innerCamSettings.increaseV4(1);
                    }
                }
                okStreak = 0;
            }
        }
        else {
            okStreak = 0;
        }

        if (innerCam.outStream != null) {
            innerCam.sendSettings(innerCam.outStream);
        }
        if (outerCam.outStream != null) {
            outerCam.sendSettings(outerCam.outStream);
        }

        for (EDAWorker worker : workers) {
            worker.status.innerHistory.removeOldResults();
            worker.status.outerHistory.removeOldResults();
            worker.status.calcNetworkTime();
        }

        prevPendingDataSize = pendingDataSize;
        long sizeDelta = totalDataSize - prevTotalDataSize;
        prevTotalDataSize = totalDataSize;

        int capacityLevel = (workerSlow ? 0 : workerFast ? 2 : 1);
        int networkLevel = (networkSlow ? 0 : networkFast ? 2 : 1);

        StatusLogger.log(innerCam, outerCam, workers, sizeDelta, capacityLevel, networkLevel);
    }

    private void setDefaultFPS(CamSettings innerCamSettings, CamSettings outerCamSettings) {
        int numActive = 0;
        for (int i = 0; i < Communicator.isConnected.length; i++)
            if (Communicator.isConnected[i])
                numActive++;
        innerCamSettings.setF(numActive * 3);
        outerCamSettings.setF(numActive * 3 + 5);
    }

    // New version: Always move R, Q, F as a whole
    // Adjust settings only when both constraints are satisfied
    public void adjustCamSettingsV3(List<EDAWorker> workers, CamSettings innerCamSettings, CamSettings outerCamSettings) {
        double workerCapacity;

        long pendingDataSize = communicator.pendingDataSize;
        long totalDataSize = communicator.totalDataSize;

        int innerWaiting = 0, outerWaiting = 0;

        // 1. Calculate average network speed for all workers
        int numHistory = 0;
        int numWorkers = workers.size();
        for (EDAWorker worker : workers) {
            WorkerStatus status = worker.status;
            innerWaiting += status.innerWaiting;
            outerWaiting += status.outerWaiting;
            numHistory += status.innerHistory.history.size() + status.outerHistory.history.size();
        }

        double totalInnerProcessTime = 0, totalOuterProcessTime = 0;
        for (EDAWorker worker : workers) {
            WorkerStatus status = worker.status;
            totalInnerProcessTime += status.innerProcessTime();
            totalOuterProcessTime += status.outerProcessTime();
        }

        // capacity is "how many frames can be addressed within a second?"
        // so it's (# of workers) / (average time to process one frame)
        double avgProcessTime = (INNER_TIME_MULTIPLIER * totalInnerProcessTime + totalOuterProcessTime) / numHistory;
        workerCapacity = (workers.size() / avgProcessTime) * 1000 * CAPACITY_LENIENCY;

        /*
        Four boolean values:
        1. network too slow
        2. network too fast
        3. workers cannot handle
        4. workers can handle more

        We decrease settings if 1 or 3 hold.
        Otherwise, we increase settings if 2 or 4 hold.

        In other words, we only decrease settings if both 1 and 4 hold or both 2 and 3 hold.
         */

        // No need to weight them since we're only considering network speed here
        // double weightedWaiting = innerWaiting * INNER_TIME_MULTIPLIER + outerWaiting;

        int totalWaiting = innerWaiting + outerWaiting;

        /* 1 */ boolean networkSlow = totalWaiting > TOO_MANY_WAITING * numWorkers || pendingDataSize > TOO_MUCH_PENDING;
        /* 2 */ boolean networkFast = totalWaiting < TOO_FEW_WAITING * numWorkers && pendingDataSize < TOO_LITTLE_PENDING;

        int iF = innerCamSettings.getF(), oF = outerCamSettings.getF();
        double weightedF = iF * INNER_TIME_MULTIPLIER + oF;

        /* 3 */ boolean workerSlow = workerCapacity < weightedF;
        /* 4 */ boolean workerFast = workerCapacity * CAPACITY_MULTIPLIER > weightedF;

        int innerLevel = innerCamSettings.getTotalLevel();
        int outerLevel = outerCamSettings.getTotalLevel();

        StringBuilder sb = new StringBuilder();
        sb.append(totalWaiting).append(" ").append(networkSlow).append(" ").append(networkFast).append("\n");
        sb.append(workerCapacity).append(" ").append(weightedF).append(" ").append(workerSlow).append(" ").append(workerFast).append("\n");

        Log.v(TAG, sb.toString());

        if (networkSlow || workerSlow) {
            if (innerLevel * INNER_OUTER_RATIO > outerLevel) {
                if (innerCamSettings.decrease(1) == 0) {
                    outerCamSettings.decrease(1);
                }
            }
            else {
                outerCamSettings.decrease(1);
            }
            okStreak = 0;
        }

        else if (networkFast || workerFast) {
            okStreak++;
            if (okStreak == 2) {
                if (innerLevel * INNER_OUTER_RATIO < outerLevel) {
                    innerCamSettings.increase(1);
                } else {
                    if (outerCamSettings.increase(1) == 0) {
                        innerCamSettings.increase(1);
                    }
                }
                okStreak = 0;
            }
        }
        else
            okStreak = 0;

        if (innerCam.outStream != null) {
            innerCam.sendSettings(innerCam.outStream);
        }
        if (outerCam.outStream != null) {
            outerCam.sendSettings(outerCam.outStream);
        }
        //Log.v(TAG, "pendingDataSize = " + pendingDataSize);

        for (EDAWorker worker : workers) {
            worker.status.innerHistory.removeOldResults();
            worker.status.outerHistory.removeOldResults();
            worker.status.calcNetworkTime();
        }

        prevPendingDataSize = pendingDataSize;
        long sizeDelta = totalDataSize - prevTotalDataSize;
        prevTotalDataSize = totalDataSize;

        int capacityLevel = (workerSlow ? 0 : workerFast ? 2 : 1);
        int networkLevel = (networkSlow ? 0 : networkFast ? 2 : 1);

        StatusLogger.log(innerCam, outerCam, workers, sizeDelta, capacityLevel, networkLevel);
    }

    // New version of cam settings adjustment algorithm.
    public void adjustCamSettingsV2(List<EDAWorker> workers, CamSettings innerCamSettings, CamSettings outerCamSettings) {
        double workerCapacity;

        long pendingDataSize = communicator.pendingDataSize;

        int innerWaiting = 0, outerWaiting = 0;

        // 1. Calculate average network speed for all workers
        int numHistory = 0;
        int numWorkers = workers.size();
        for (EDAWorker worker : workers) {
            WorkerStatus status = worker.status;
            innerWaiting += status.innerWaiting;
            outerWaiting += status.outerWaiting;
            numHistory += status.innerHistory.history.size() + status.outerHistory.history.size();
        }

        /* 2. Total capacity of workers are calculated
        - Time multiplier is a constant, calculated based on experimental results
        - Outer analysis typically takes considerably more time, so we assume that
          inner time should get some weights
        - It is needed to consider both values since both cameras may not always be available */

        double totalInnerProcessTime = 0, totalOuterProcessTime = 0;
        for (EDAWorker worker : workers) {
            WorkerStatus status = worker.status;
            totalInnerProcessTime += status.innerProcessTime();
            totalOuterProcessTime += status.outerProcessTime();
        }

        // capacity is "how many frames can be addressed within a second?"
        // so it's (# of workers) / (average time to process one frame)
        double avgProcessTime = (INNER_TIME_MULTIPLIER * totalInnerProcessTime + totalOuterProcessTime) / numHistory;
        workerCapacity = workers.size() / avgProcessTime * 1000 * CAPACITY_LENIENCY;

        // Need to reduce F if the workload is too high
        boolean fpsDecreased = false, fpsIncreased = false;

        int iF = innerCamSettings.getF(), oF = outerCamSettings.getF();
        double weightedF = iF * INNER_TIME_MULTIPLIER + oF;

        //Log.v(TAG, "workerCapacity = " + workerCapacity + ", F = " + weightedF);

        double weightedWaiting = innerWaiting * INNER_TIME_MULTIPLIER + outerWaiting;
        boolean networkSlow = weightedWaiting > TOO_MANY_WAITING * numWorkers || pendingDataSize > TOO_MUCH_PENDING;
        boolean networkFast = pendingDataSize < 500000 && weightedWaiting < 0.5 * TOO_MANY_WAITING;

        double RQLevel = innerCamSettings.getNormalizedLevel() + outerCamSettings.getNormalizedLevel();
        double FLevel = innerCamSettings.getF() + outerCamSettings.getF();

        // If there is too much workload, reduce F a little.
        if (workerCapacity < weightedF) {
            if (iF * F_WEIGHT > oF)
            {
                if (innerCamSettings.decreaseF(F_DEC_AMOUNT) > 0) {
                    fpsDecreased = true;
                }
                else if (outerCamSettings.decreaseF(F_DEC_AMOUNT) > 0) {
                    fpsDecreased = true;
                }
            }
            else if (outerCamSettings.decreaseF(F_DEC_AMOUNT) > 0) {
                fpsDecreased = true;
            }
        }

        // Otherwise, if we can definitely work more, increase F a little.
        else if (networkFast && FLevel * 2 < RQLevel && workerCapacity * CAPACITY_MULTIPLIER > weightedF) {
            if (iF * F_WEIGHT < oF) {
                if (innerCamSettings.increaseF(F_INC_AMOUNT) > 0) {
                    fpsIncreased = true;
                } else if (outerCamSettings.increaseF(F_INC_AMOUNT) > 0) {
                    fpsIncreased = true;
                }
            } else if (outerCamSettings.increaseF(F_INC_AMOUNT) > 0) {
                fpsIncreased = true;
            }
        }


        // Need to reduce data throughput if the network is saturated
        // However, if we already decreased F above, we can wait a little to see if this also
        // solves network bottleneck
        if (!fpsDecreased && networkSlow) {
            //Log.v(TAG, "pending vs prev = " + pendingDataSize + " " + prevPendingDataSize);
            int x;
            // We want to move these at similar rate
            if (innerCamSettings.getNormalizedLevel() > outerCamSettings.getNormalizedLevel()) {
                x = innerCamSettings.decreaseRQ(RQ_DEC_AMOUNT);
                //Log.v(TAG, "decreased inner: " + x);
            }
            else {
                x = outerCamSettings.decreaseRQ(RQ_DEC_AMOUNT);
                //Log.v(TAG, "decreased outer: " + x);
            }
            if (x == 0) {
                if (iF * F_WEIGHT > oF)
                {
                    if (innerCamSettings.decreaseF(F_DEC_AMOUNT) > 0) {
                        fpsDecreased = true;
                    }
                    else if (outerCamSettings.decreaseF(F_DEC_AMOUNT) > 0) {
                        fpsDecreased = true;
                    }
                }
                else if (outerCamSettings.decreaseF(F_DEC_AMOUNT) > 0) {
                    fpsDecreased = true;
                }
            }
        }

        // Similarly, if we increased F above, it might already be threatening network throughput
        // so refrain from increasing RQ at the same time
        else if (!fpsIncreased && networkFast) {
            // We have some possibility that we can provide better analysis
            if (innerCamSettings.getNormalizedLevel() < outerCamSettings.getNormalizedLevel()) {
                innerCamSettings.increaseRQ(RQ_INC_AMOUNT);
            }
            else {
                outerCamSettings.increaseRQ(RQ_INC_AMOUNT);
            }
        }

        if (innerCam.outStream != null) {
            innerCam.sendSettings(innerCam.outStream);
        }
        if (outerCam.outStream != null) {
            outerCam.sendSettings(outerCam.outStream);
        }
        //Log.v(TAG, "pendingDataSize = " + pendingDataSize);

        for (EDAWorker worker : workers) {
            worker.status.innerHistory.removeOldResults();
            worker.status.outerHistory.removeOldResults();
            worker.status.calcNetworkTime();
        }

        prevPendingDataSize = pendingDataSize;
    }

    public void sendRestartMessages(EDACam innerCam, EDACam outerCam) {
        try {
            innerCam.outStream.writeInt(-1);
            innerCam.outStream.flush();
            innerCam.outStream.reset();
            outerCam.outStream.writeInt(-1);
            outerCam.outStream.flush();
            outerCam.outStream.reset();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
