package com.example.edgedashanalytics.advanced.coordinator;

import static com.example.edgedashanalytics.advanced.coordinator.MainRoutine.EXPERIMENTING;
import static com.example.edgedashanalytics.advanced.coordinator.MainRoutine.communicator;
import static com.example.edgedashanalytics.advanced.coordinator.MainRoutine.Experiment.*;
import static com.example.edgedashanalytics.advanced.coordinator.Communicator.availableWorkers;
import static com.example.edgedashanalytics.advanced.coordinator.DeviceLogger.DeviceLog;
import static com.example.edgedashanalytics.advanced.coordinator.DeviceLogger.DeviceLog.IndividualDeviceLog;

import android.util.Log;

import com.example.edgedashanalytics.advanced.common.WorkerStatus;
import com.example.edgedashanalytics.page.main.MainActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Controller extends Thread {
    private static final String TAG = "Controller";

    private static final double NETWORK_BANDWIDTH = 100000000 / 8.0; // unit: bytes per second
    public static final double TARGET_LATENCY = 0.2; // unit: s
    private static final double AVERAGE_FRAME_SIZE = 150000.0; // unit: bytes
    private static final double AVERAGE_RESULT_SIZE = 1000.0; // unit: bytes
    private final EDACam innerCam, outerCam;
    private long prevTotalDataSize;

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
    private long startTime, nextCheckpoint;
    public static int sensitive;

    @Override
    public void run() {
        startTime = System.currentTimeMillis();
        MainActivity.readDeviceStatus(startTime);
        nextCheckpoint = startTime + CAMERA_ADJUSTMENT_PERIOD;
        try {
            Thread.sleep(CAMERA_ADJUSTMENT_PERIOD);
            while (true) {
                long curTime = System.currentTimeMillis();
                if (curTime >= nextCheckpoint) {
                    if (E_testConfig.isCoordinator) {
                        if (E_stealing) {
                            fixTo30FPS();
                        }
                        else {
                            MM1();
                        }
                    }
                    if (curTime >= nextCheckpoint) {
                        nextCheckpoint += CAMERA_ADJUSTMENT_PERIOD;
                    }
                }
                Thread.sleep(50);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // For work stealing experiment
    private void fixTo30FPS() {
        innerCamParameter.fps = 30;
        outerCamParameter.fps = 30;
        if (innerCam.outStream != null) {
            innerCam.sendSettings();
        }
        if (outerCam.outStream != null) {
            outerCam.sendSettings();
        }
    }

    private void MM1() {
        long pendingDataSize = communicator.pendingDataSize;
        long totalDataSize = communicator.totalDataSize;

        if (connectionChanged) {
            connectionChanged = false;
        }

        final double dp = 2;

        double fpsNew = 0.0;

        // TODO: Change this to a proper method to detect device connection state changes
        ArrayList<Double> averages = new ArrayList<>();
        double maxAverage = 0.0;
        for (EDAWorker worker : workers) {
            WorkerStatus status = worker.status;
            if (!status.isConnected) {
                continue;
            }
            double average = status.getAverageOuterProcessTime();
            if (average > maxAverage) {
                maxAverage = average;
            }
            averages.add(average);
        }
        for (int i = 0; i < averages.size(); i++) {
            if (averages.get(i) == WorkerStatus.DEFAULT_OUTER_PROCESS_TIME) {
                averages.set(i, maxAverage);
            }
        }

        for (Double average : averages) {

            double first = dp / (average / 1000.0);

            double lg = TARGET_LATENCY;
            double transfer = estimateTrasferTime();

            double second = 1.0 / (lg - transfer);
            fpsNew += first - second;
        }

        fpsNew /= 2.0; // Inner and outer

        Log.w(TAG, "fps = " + fpsNew);

        fpsNew = Math.max(fpsNew, 1.0);
        fpsNew = Math.min(fpsNew, 30.0);
        innerCamParameter.fps = fpsNew;
        outerCamParameter.fps = fpsNew;

        prevPendingDataSize = pendingDataSize;
        long sizeDelta = totalDataSize - prevTotalDataSize;
        prevTotalDataSize = totalDataSize;

        if (EXPERIMENTING) {
            StatusLogger.log(innerCam, outerCam, workers, nextCheckpoint - startTime, sizeDelta, -1);
        }

        if (innerCam.outStream != null) {
            innerCam.sendSettings();
        }
        if (outerCam.outStream != null) {
            outerCam.sendSettings();
        }

        ArrayList<IndividualDeviceLog> logs = new ArrayList<>();
        for (EDAWorker worker : workers) {
            worker.status.innerHistory.removeOldResults();
            worker.status.outerHistory.removeOldResults();
            worker.status.calcNetworkTime();

            if (EXPERIMENTING) {
                IndividualDeviceLog log = new IndividualDeviceLog(worker.status.temperatures, worker.status.frequencies);
                logs.add(log);
            }
        }
        if (EXPERIMENTING) {
            DeviceLogger.addLog(new DeviceLog(nextCheckpoint - startTime, logs));
        }
    }

    public static double estimateTrasferTime() {
        double frameTransferTime = AVERAGE_FRAME_SIZE / NETWORK_BANDWIDTH;
        double resultTransferTime = AVERAGE_RESULT_SIZE / NETWORK_BANDWIDTH;
        return 2 * frameTransferTime + resultTransferTime;
    }

    public void sendRestartMessages(EDACam innerCam, EDACam outerCam) {
        try {
            innerCam.outStream.writeDouble(-1);
            innerCam.outStream.flush();
            innerCam.outStream.reset();
            outerCam.outStream.writeDouble(-1);
            outerCam.outStream.flush();
            outerCam.outStream.reset();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
