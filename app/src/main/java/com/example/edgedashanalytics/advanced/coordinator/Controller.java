package com.example.edgedashanalytics.advanced.coordinator;

import android.util.Log;
import android.util.Size;

import com.example.edgedashanalytics.advanced.common.WorkerStatus;

import java.util.List;

public class Controller {
    private static final String TAG = "Controller";

    private final EDACam innerCam;
    private final EDACam outerCam;

    public Controller(EDACam innerCam, EDACam outerCam) {
        this.innerCam = innerCam;
        this.outerCam = outerCam;
    }

    /*
    TODO: All values here are temporary.
     */
    private static final double NETWORK_SLOW = 300;
    private static final double WAIT_SLOW = 2.5;

    private static final double NETWORK_FAST = 100;
    private static final double WAIT_FAST = 1.5;

    // New version of cam settings adjustment algorithm.
    public void adjustCamSettingsV2(List<EDAWorker> workers, CamSettingsV2 innerCamSettings, CamSettingsV2 outerCamSettings) {
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
        - TODO: We need to set a score for latency.
         */

        // Need to reduce F if the workload is too high
        boolean fpsDecreased = false;

        int iF = innerCamSettings.getF(), oF = outerCamSettings.getF();

        if (workerCapacity < iF + oF) {
            // How much more important is to frequently analyse the outer video?
            final double outerFpsWeight = 2.0;

            if (iF * outerFpsWeight > oF)
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

        // Need to reduce data throughput if the network is saturated
        // However, if we already decreased F above, we can wait a little to see if this also
        // solves network bottleneck
        if (!fpsDecreased) {
            if (networkTime > NETWORK_SLOW) {
                // We want to move these at similar rate
                if (innerCamSettings.getNormalizedLevel() > outerCamSettings.getNormalizedLevel()) {
                    innerCamSettings.decreaseRQ(1);
                }
                else {
                    outerCamSettings.decreaseRQ(1);
                }
            }

            // We have some possibility that we can provide better analysis
            else if (networkTime < NETWORK_FAST) {

            }
        }

        if (innerCam.outstream != null) {
            innerCam.sendSettings(innerCam.outstream);
            //Log.d(TAG, "sending adjusted setting to innercam");
        }
        if (outerCam.outstream != null) {
            //Log.d(TAG, "sending adjusted setting to outercam");
            outerCam.sendSettings(outerCam.outstream);
        }

        for (EDAWorker worker : workers) {
            worker.status.innerHistory.removeOldResults();
            worker.status.outerHistory.removeOldResults();
            worker.status.calcNetworkTime();
        }
    }

    public void adjustCamSettings(List<EDAWorker> workers, CamSettings innerCamSettings, CamSettings outerCamSettings) {
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

        if (innerCam.outstream != null) {
            innerCam.sendSettings(innerCam.outstream);
            //Log.d(TAG, "sending adjusted setting to innercam");
        }
        if (outerCam.outstream != null) {
            //Log.d(TAG, "sending adjusted setting to outercam");
            outerCam.sendSettings(outerCam.outstream);
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
