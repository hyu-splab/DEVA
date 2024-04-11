package com.example.edgedashanalytics.advanced.coordinator;

import static com.example.edgedashanalytics.advanced.coordinator.AdvancedMain.communicator;
import static com.example.edgedashanalytics.advanced.coordinator.AdvancedMain.testConfig;
import static com.example.edgedashanalytics.advanced.coordinator.DeviceLogger.DeviceLog;
import static com.example.edgedashanalytics.advanced.coordinator.DeviceLogger.DeviceLog.IndividualDeviceLog;

import android.os.HardwarePropertiesManager;
import android.util.Log;

import com.example.edgedashanalytics.advanced.common.WorkerStatus;
import com.example.edgedashanalytics.page.main.MainActivity;

import java.util.ArrayList;
import java.util.List;

public class Controller extends Thread {
    private static final String TAG = "Controller";
    private final EDACam innerCam, outerCam;
    private long prevTotalDataSize;

    // when inner + outer is 20 fps
    private static final double TOO_MANY_WAITING = 2;
    private static final double TOO_FEW_WAITING = 0.1;

    public static int lastAction = 0;

    public static long prevPendingDataSize = 0;

    public static boolean connectionChanged = false;
    public static double performanceLostRatio;
    public static int deviceAdded;

    public Controller(EDACam innerCam, EDACam outerCam) {
        this.innerCam = innerCam;
        this.outerCam = outerCam;
        prevPendingDataSize = 0;
    }

    private static final long CAMERA_ADJUSTMENT_PERIOD = 500;
    public List<EDAWorker> workers;
    public Parameter innerCamParameter, outerCamParameter;
    public boolean checkNeeded;
    private long startTime, nextCheckpoint;
    public static int sensitive;

    @Override
    public void run() {
        checkNeeded = false;
        startTime = System.currentTimeMillis();
        MainActivity.readDeviceStatus(startTime);
        nextCheckpoint = startTime + CAMERA_ADJUSTMENT_PERIOD;
        try {
            Thread.sleep(CAMERA_ADJUSTMENT_PERIOD);
            while (true) {
                long curTime = System.currentTimeMillis();
                if (checkNeeded || curTime >= nextCheckpoint) {
                    if (testConfig.isCoordinator) {
                        adjustCamSettingsV4();
                    }
                    checkNeeded = false;
                    if (curTime >= nextCheckpoint) {
                        //MainActivity.readDeviceStatus(nextCheckpoint);
                        nextCheckpoint += CAMERA_ADJUSTMENT_PERIOD;
                    }
                }
                Thread.sleep(50);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
    19/02/2014: Resolution and Quality are fixed, only modify frame rate
     */
    public void adjustCamSettingsV4() {
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

            int totalWaiting;
            double currentFPS = innerCamParameter.fps + outerCamParameter.fps;

            totalWaiting = (int)totalQueueSize;

            double weightedWaiting = (20.0 / currentFPS) * totalWaiting / (1 + (0.5 * (availableWorkers - 1)));
            //double weightedPending = pendingDataSize;

            Log.v(TAG, "FPS = " + currentFPS + ", waiting = " + totalWaiting + ", available = " + availableWorkers + ", weighted = " + weightedWaiting);

            /* 1 */
            boolean networkSlow = weightedWaiting > TOO_MANY_WAITING; // || weightedPending > TOO_MUCH_PENDING;
            /* 2 */
            boolean networkFast = weightedWaiting < TOO_FEW_WAITING; // && weightedPending < TOO_LITTLE_PENDING;

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
                        if (innerCamParameter.increase() == 0) {
                            outerCamParameter.increase();
                        };
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

            StatusLogger.log(innerCam, outerCam, workers, nextCheckpoint - startTime, sizeDelta, networkLevel);
        }

        if (innerCam.outStream != null) {
            innerCam.sendSettings(innerCam.outStream);
        }
        if (outerCam.outStream != null) {
            outerCam.sendSettings(outerCam.outStream);
        }

        ArrayList<IndividualDeviceLog> logs = new ArrayList<>();
        for (EDAWorker worker : workers) {
            worker.status.innerHistory.removeOldResults();
            worker.status.outerHistory.removeOldResults();
            worker.status.calcNetworkTime();

            IndividualDeviceLog log = new IndividualDeviceLog(worker.status.temperatures, worker.status.frequencies);
            logs.add(log);
        }
        DeviceLogger.addLog(new DeviceLog(nextCheckpoint - startTime, logs));

        if (sensitive > 0)
            sensitive--;
    }

    private void setDefaultFPS(Parameter innerCamParameter, Parameter outerCamParameter) {

        double totalFPS = innerCamParameter.fps + outerCamParameter.fps;

        if (performanceLostRatio > 0) {
            totalFPS = Math.max(6, totalFPS * (1 - performanceLostRatio));
        }

        totalFPS += deviceAdded * 6;

        int inF = (int)Math.round(totalFPS / 3);
        int outF = (int)Math.round(totalFPS * 2 / 3);
        innerCamParameter.fps = inF;
        outerCamParameter.fps = outF;

        sensitive = 10; // double rate change for 10 / 2 = 5 seconds
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
