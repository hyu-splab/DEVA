package com.example.edgedashanalytics.advanced.coordinator;

import android.util.Log;

import com.example.edgedashanalytics.advanced.common.WorkerStatus;

import java.util.List;

public class Controller {
    private static final String TAG = "Controller";

    public Controller() {

    }

    /*
    TODO: All values here are temporary.
     */
    private static final double NETWORK_SLOW = 200;
    private static final double PROCESS_SLOW_INNER = 100, PROCESS_SLOW_OUTER = 100;
    private static final double WAIT_SLOW = 100;

    private static final double NETWORK_FAST = 50;
    private static final double WAIT_FAST = 20;

    public void adjustCamSettings(List<EDAWorker> workers, CamSettings innerCam, CamSettings outerCam) {
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

        for (EDAWorker worker : workers) {
            WorkerStatus status = worker.status;
            innerCount += status.innerHistory.history.size();
            totalInnerWait += status.innerHistory.waiting;
            totalInnerProcess += status.innerHistory.processTime;

            outerCount += status.outerHistory.history.size();
            totalOuterWait += status.outerHistory.waiting;
            totalOuterProcess += status.outerHistory.processTime;

            totalNetworkTime += status.networkTime;
        }

        if (innerCount > 0) {
            avgInnerWait = (double)totalInnerWait / innerCount;
            avgInnerProcess = (double)totalInnerProcess / innerCount;
        }

        if (outerCount > 0) {
            avgOuterWait = (double)totalOuterWait / outerCount;
            avgOuterProcess = (double)totalOuterProcess / outerCount;
        }

        if (innerCount + outerCount > 0) {
            avgNetworkTime = (double)totalNetworkTime / (innerCount + outerCount);
        }

        boolean x, y; // temporary variables
        innerCam.initializeChanged();
        outerCam.initializeChanged();

        /*
        Rule #1: Never let TFLife take too much time processing
         */
        if (avgInnerProcess > PROCESS_SLOW_INNER || avgOuterProcess > PROCESS_SLOW_OUTER) {
            if (!innerCam.decreaseResolution()) {
                warnMin("inner process");
            }
            if (!outerCam.decreaseResolution()) {
                warnMin("outer process");
            }
        }

        /*
        Rule #2: Do not let the network to be saturated
         */
        if (avgNetworkTime > NETWORK_SLOW) {
            if (!innerCam.decreaseFrameRate()) {
                if (!outerCam.decreaseFrameRate()) {
                    x = innerCam.decreaseQuality();
                    y = outerCam.decreaseQuality();
                    if (!x && !y) {
                        x = innerCam.decreaseResolution();
                        y = outerCam.decreaseResolution();
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
        if (avgInnerWait > WAIT_SLOW && avgOuterWait > WAIT_SLOW) {
            if (!innerCam.decreaseFrameRate()) {
                if (!outerCam.decreaseFrameRate()) {
                    x = innerCam.decreaseResolution();
                    y = outerCam.decreaseResolution();
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
            x = innerCam.increaseQuality();
            y = outerCam.increaseQuality();
            if (!x && !y) {
                if (!outerCam.increaseFrameRate()) {
                    if (!innerCam.increaseFrameRate()) {
                        warnMax("all is well");
                    }
                }
            }
        }
    }

    private void warnMin(String name) {
        Log.w(TAG, "Property '" + name + "' is already minimal");
    }

    private void warnMax(String name) {
        Log.w(TAG, "Property '" + name + "' is already maximal");
    }
}
