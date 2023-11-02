package com.example.edgedashanalytics.advanced.coordinator;

import android.util.Log;

import com.example.edgedashanalytics.advanced.common.WorkerHistory;
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
    private static final double PROCESS_SLOW_INNER = 100, PROCESS_SLOW_OUTER = 200;
    private static final double WAIT_SLOW = 2.5;

    private static final double NETWORK_FAST = 100;
    private static final double WAIT_FAST = 1.5;

    public void adjustCamSettings(List<EDAWorker> workers, CamSettings innerCamSettings, CamSettings outerCamSettings) {
        double avgInnerWait = 0.0;
        double avgInnerProcess = 0.0;
        double avgOuterWait = 0.0;
        double avgOuterProcess = 0.0;
        double avgNetworkTime = 0.0;

        int totalNetworkTime = 0;
        int totalInnerWait = 0;
        int totalInnerProcess = 0;
        int totalOuterWait = 0;
        int totalOuterProcess = 0;
        int innerCount = 0, outerCount = 0;

        if (workers.size() == 0) {
            Log.d(TAG, "no workers available");
            return;
        }

        for (EDAWorker worker : workers) {
            WorkerStatus status = worker.status;
            innerCount += status.innerHistory.history.size();
            totalInnerWait += status.innerWaiting;
            totalInnerProcess += status.innerHistory.totalProcessTime;

            outerCount += status.outerHistory.history.size();
            totalOuterWait += status.outerWaiting;
            totalOuterProcess += status.outerHistory.totalProcessTime;

            totalNetworkTime += status.innerHistory.totalNetworkTime + status.outerHistory.totalNetworkTime;
        }

        if (innerCount > 0) {
            avgInnerWait = (double)totalInnerWait / workers.size();
            avgInnerProcess = (double)totalInnerProcess / innerCount;
        }

        if (outerCount > 0) {
            avgOuterWait = (double)totalOuterWait / workers.size();
            avgOuterProcess = (double)totalOuterProcess / outerCount;
        }

        if (innerCount + outerCount > 0) {
            avgNetworkTime = (double)totalNetworkTime / (innerCount + outerCount);
        }
        else {
            Log.d(TAG, "No history available");
            return;
        }

        Log.d(TAG, "innerwait = " + avgInnerWait + ", innerprocess = " + avgInnerProcess
        + ", outerwait = " + avgOuterWait + ", outerprocess = " + avgOuterProcess
        + ", network = " + avgNetworkTime);

        boolean x, y; // temporary variables
        innerCamSettings.initializeChanged();
        outerCamSettings.initializeChanged();

        /*
        Rule #1: Never let TFLife take too much time processing
         */
        if (avgInnerProcess > PROCESS_SLOW_INNER || avgOuterProcess > PROCESS_SLOW_OUTER) {
            if (!innerCamSettings.decreaseResolution()) {
                warnMin("inner process");
            }
            if (!outerCamSettings.decreaseResolution()) {
                warnMin("outer process");
            }
        }

        /*
        Rule #2: Do not let the network to be saturated
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
        Rule #3: We don't want saturated worker queues
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
        Rule #4: If everything is more than good, try measuring in higher quality
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
