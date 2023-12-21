package com.example.edgedashanalytics.advanced.coordinator;

import android.util.Log;

import com.example.edgedashanalytics.advanced.common.WorkerStatus;

import java.util.List;

public class Controller {
    private static final String TAG = "Controller";

    private final EDACam innerCam;
    private final EDACam outerCam;

    private long prevPendingDataSize;

    public Controller(EDACam innerCam, EDACam outerCam) {
        this.innerCam = innerCam;
        this.outerCam = outerCam;
        prevPendingDataSize = 0;
    }

    /*
    TODO: All values here are temporary.
     */
    private static final double NETWORK_SLOW = 500;
    private static final double WAIT_SLOW = 2.5;

    private static final double NETWORK_FAST = 200;
    private static final double WAIT_FAST = 1.5;

    // New version of cam settings adjustment algorithm.
    public void adjustCamSettingsV2(List<EDAWorker> workers, CamSettings innerCamSettings, CamSettings outerCamSettings) {
        double networkTime;
        double workerCapacity;

        long pendingDataSize = AdvancedMain.communicator.pendingDataSize;

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
        Time multiplier is a constant, calculated based on experimental results
        TODO: get this more accurately
        So far, it is deemed that outer analysis typically takes considerably more time
        so we assume that inner time should get more attention than its value

        It is needed to consider both values since both cameras may not always be available
        */
        final double innerTimeMultiplier = 2;
        final double leniency = 1.5;

        double totalInnerProcessTime = 0, totalOuterProcessTime = 0;
        for (EDAWorker worker : workers) {
            WorkerStatus status = worker.status;
            totalInnerProcessTime += status.innerProcessTime();
            totalOuterProcessTime += status.outerProcessTime();
        }

        // capacity is "how many frames can be addressed within a second?"
        // so it's (# of workers) / (average time to process one frame)
        double avgProcessTime = (innerTimeMultiplier * totalInnerProcessTime + totalOuterProcessTime) / numHistory;
        //Log.v(TAG, "avg = " + avgProcessTime);
        workerCapacity = workers.size() / avgProcessTime * 1000 * leniency;

        /*
        Time to actually implement adjusting algorithm!

        The adjusting algorithm is about changing three parameters: R, Q, and F.

        So we have networkTime and workerCapacity as two major factors,
        and therefore we can check two things:
        1. networkTime: How many data can we actually afford to send to the workers?
        2. workerCapacity: How much the workers can actually work?
        networkTime is purely affected by the data size, i.e. all three parameters count here.
        workerCapacity is barely affected by R and Q, so it's mainly about the frame rate.

        Since we abandoned brute force search with these parameters, we should instead consider
        which parameters we will prioritize to change in which situation.

        [List of the things we have learned so far]
        Inner analysis:
        - Best standard seems to be "Flag correctness"
        - The overall accuracy is mostly related to the total data size, i.e. both Q and R count.
        - The analyzer tends to flag as 'distracted' for small-size frames. The main reason for
          this might be that the detector falsely detect eyes or hands and think that they are
          not at the right place.

        Outer analysis:
        - Best standard seems to be "Found %"
        - Rather than Q, its accuracy is highly affected by R.
        - The detector does NOT tend to falsely detect objects, unless the R is extremely low.

        Also, the overall data size tends to be a little larger for outer videos, probably due to
        relatively complicated scenes.

        Our main goal is as follows:
        - Provide as best detection/estimation result as possible.
        - Keep delivering the results within 'reasonable' delay.

        To do this, we need a few targeted details:
        - To provide best detection/estimation result within limited network capacity, we need to
          make the data size as small as possible.
        - To keep 'reasonable' delay, we need not only not to over-saturate the network, but also
          to keep the frame rate fairly high.
        - If the overall network latency is low enough, we can decrease the frame rate and still
          achieve fairly good reaction time. i.e. we can always get a very recent detection result.

        Additionally, a few rules can be added:
        - We would like to keep more frequent analysis for outer videos compared to inner analysis.
          This is because the driver is unlikely to change their distractedness status very
          frequently, while the objects in front of the car can be changed anytime. Besides, outer
          objects are directly related to the car's safety even if the driver is concentrating,
          while even if the driver is distracted the car won't be in danger right away as long as
          there is nothing outside.

        Implementation details:
        - For inner analysis, we have the data size chart for R and Q, and we know that its
          correlation with accuracy is quite high (though it cannot be mathematically proved). So,
          we can make a list sorted by data size and move a 'pointer' on it.
        - For outer analysis, it seems that we don't want to decrease R if possible, as even with
          960x540 R and 100 Q it's barely as good as 1280x720 R and 30 Q, of which the data size is
          nearly 7 times smaller. Removing inversion of data sizes, the only part where decreasing R
          is when Q < 10, which is already too inaccurate to be used. So for now, I am going to try
          to fix outer R at 1280x720 and only move Q.
         */
        // ==============================================================================
        /* PLAN CHANGED

        We should NEVER use a fixed chart, so instead we have to
         */

        // Need to reduce F if the workload is too high
        boolean fpsDecreased = false, fpsIncreased = false;

        int iF = innerCamSettings.getF(), oF = outerCamSettings.getF();

        // How much more important is to frequently analyse the outer video?
        final double outerFWeight = 2.0;

        Log.v(TAG, "workerCapacity = " + workerCapacity + ", F = " + iF + " * " + innerTimeMultiplier + " + " + oF);

        // If there is too much workload, reduce F a little.
        if (workerCapacity < iF * innerTimeMultiplier + oF) {
            if (iF * outerFWeight > oF)
            {
                if (innerCamSettings.decreaseF(5) > 0) {
                    fpsDecreased = true;
                }
                else if (outerCamSettings.decreaseF(5) > 0) {
                    fpsDecreased = true;
                }
            }
            else if (outerCamSettings.decreaseF(5) > 0) {
                fpsDecreased = true;
            }
        }

        // Otherwise, if we can definitely work more, increase F a little.
        else if (pendingDataSize < 2000000 && workerCapacity * 0.8 > iF * innerTimeMultiplier + oF) {
            if (iF * outerFWeight < oF) {
                if (innerCamSettings.increaseF(2) > 0) {
                    fpsIncreased = true;
                } else if (outerCamSettings.increaseF(2) > 0) {
                    fpsIncreased = true;
                }
            } else if (outerCamSettings.increaseF(2) > 0) {
                fpsIncreased = true;
            }
        }


        // Need to reduce data throughput if the network is saturated
        // However, if we already decreased F above, we can wait a little to see if this also
        // solves network bottleneck
        if (!fpsDecreased) {
            Log.v(TAG, "pending vs prev = " + pendingDataSize + " " + prevPendingDataSize);
            if (pendingDataSize > 1000000 && (pendingDataSize > 2000000 || pendingDataSize > prevPendingDataSize) /*networkTime > NETWORK_SLOW*/) {
                int x;
                // We want to move these at similar rate
                if (innerCamSettings.getNormalizedLevel() > outerCamSettings.getNormalizedLevel()) {
                    x = innerCamSettings.decreaseRQ(3);
                    Log.v(TAG, "decreased inner: " + x);
                }
                else {
                    x = outerCamSettings.decreaseRQ(3);
                    Log.v(TAG, "decreased outer: " + x);
                }
                if (x == 0) {
                    if (iF * outerFWeight > oF)
                    {
                        if (innerCamSettings.decreaseF(5) > 0) {
                            fpsDecreased = true;
                        }
                        else if (outerCamSettings.decreaseF(5) > 0) {
                            fpsDecreased = true;
                        }
                    }
                    else if (outerCamSettings.decreaseF(5) > 0) {
                        fpsDecreased = true;
                    }
                }
            }
        }

        // Similarly, if we increased F above, it might already be threatening network throughput
        // so refrain from increase RQ at the same time
        else if (!fpsIncreased) {
            // We have some possibility that we can provide better analysis
            if (pendingDataSize < 1000000 /*networkTime < NETWORK_FAST*/) {
                if (innerCamSettings.getNormalizedLevel() < outerCamSettings.getNormalizedLevel()) {
                    innerCamSettings.increaseRQ(1);
                }
                else {
                    outerCamSettings.increaseRQ(1);
                }
            }
        }

        if (innerCam.outStream != null) {
            innerCam.sendSettings(innerCam.outStream);
        }
        if (outerCam.outStream != null) {
            outerCam.sendSettings(outerCam.outStream);
        }
        Log.v(TAG, "pendingDataSize = " + pendingDataSize);

        for (EDAWorker worker : workers) {
            worker.status.innerHistory.removeOldResults();
            worker.status.outerHistory.removeOldResults();
            worker.status.calcNetworkTime();
        }

        prevPendingDataSize = pendingDataSize;
    }
}
