package com.example.edgedashanalytics.advanced.coordinator;

import static com.example.edgedashanalytics.advanced.coordinator.AdvancedMain.communicator;

import android.os.HardwarePropertiesManager;
import android.util.Log;

import com.example.edgedashanalytics.advanced.common.WorkerStatus;
import com.example.edgedashanalytics.page.main.MainActivity;

import java.util.List;

public class Controller {
    private static final String TAG = "Controller";
    private final EDACam innerCam, outerCam;
    private long prevTotalDataSize;

    // when inner + outer is 20 fps
    private static final double TOO_MANY_WAITING = 3;
    private static final double TOO_FEW_WAITING = 0.1;
    private static final long TOO_MUCH_PENDING = 10000000;
    private static final long TOO_LITTLE_PENDING = 500000;

    public static int lastAction = 0;

    public static long prevPendingDataSize = 0;

    public static boolean connectionChanged = false;

    public Controller(EDACam innerCam, EDACam outerCam) {
        this.innerCam = innerCam;
        this.outerCam = outerCam;
        prevPendingDataSize = 0;
    }

    /*
    19/02/2014: Resolution and Quality are fixed, only modify frame rate
     */
    public void adjustCamSettingsV4(List<EDAWorker> workers, Parameter innerCamParameter, Parameter outerCamParameter) {
        int availableWorkers = Communicator.availableWorkers;
        long pendingDataSize = communicator.pendingDataSize;
        long totalDataSize = communicator.totalDataSize;

        int innerWaiting = 0, outerWaiting = 0;
        long totalQueueSize = 0;

        // If connection status is changed, force-set FPS to something reasonably safe
        if (connectionChanged) {
            setDefaultFPS(innerCamParameter, outerCamParameter);
            connectionChanged = false;
        }

        else {
            for (EDAWorker worker : workers) {
                WorkerStatus status = worker.status;

                if (status.isConnected) {
                    innerWaiting += status.innerWaiting;
                    outerWaiting += status.outerWaiting;
                    totalQueueSize += status.latestQueueSize;
                }
            }

            int totalWaiting = innerWaiting + outerWaiting;
            double currentFPS = innerCamParameter.fps + outerCamParameter.fps;

            totalWaiting = (int)totalQueueSize;

            double weightedWaiting = (20.0 / currentFPS) * totalWaiting;
            //double weightedPending = pendingDataSize;

            Log.v(TAG, "FPS = " + currentFPS + ", waiting = " + totalWaiting + ", available = " + availableWorkers + ", weighted = " + weightedWaiting);

            /* 1 */
            boolean networkSlow = weightedWaiting > TOO_MANY_WAITING; // || weightedPending > TOO_MUCH_PENDING;
            /* 2 */
            boolean networkFast = weightedWaiting < TOO_FEW_WAITING; // && weightedPending < TOO_LITTLE_PENDING;

            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < workers.size(); i++)
                sb.append(" ").append(workers.get(i).status.isConnected ? 1 : 0);

            Log.v(TAG, sb.toString());

            final double outerPrioritizing = 1.5;

            if (networkSlow) {
                if (lastAction != -1) {
                    lastAction = -1;
                    innerCamParameter.decrease();
                    outerCamParameter.decrease();
                }
                else
                    lastAction = 0;
            } else if (networkFast) {
                if (lastAction != 1) {
                    lastAction = 1;
                    if (innerCamParameter.fps * outerPrioritizing < outerCamParameter.fps) {
                        innerCamParameter.increase();
                    } else {
                        if (outerCamParameter.increase() == 0) {
                            innerCamParameter.increase();
                        }
                    }
                }
                else
                    lastAction = 0;
            } else
                lastAction = 0;

            prevPendingDataSize = pendingDataSize;
            long sizeDelta = totalDataSize - prevTotalDataSize;
            prevTotalDataSize = totalDataSize;

            int networkLevel = (networkSlow ? 0 : networkFast ? 2 : 1);

            StatusLogger.log(innerCam, outerCam, workers, sizeDelta, networkLevel);
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
    }

    private void setDefaultFPS(Parameter innerCamParameter, Parameter outerCamParameter) {
        int inF = Communicator.availableWorkers * 2 + 1;
        int outF = (int)(inF * 1.5);
        innerCamParameter.fps = inF;
        outerCamParameter.fps = outF;
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
