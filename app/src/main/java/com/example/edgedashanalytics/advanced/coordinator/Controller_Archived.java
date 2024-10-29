package com.example.edgedashanalytics.advanced.coordinator;

import static com.example.edgedashanalytics.advanced.coordinator.Communicator.availableWorkers;
import static com.example.edgedashanalytics.advanced.coordinator.MainRoutine.communicator;

import android.util.Log;

import com.example.edgedashanalytics.advanced.common.WorkerStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/*
This class contains all the old attempts of Controller logics.
As these are made only for experiment purpose, so do not expect this to be well-structured.
All class members are declared private to ensure that none of these are called from outside.
*/
public class Controller_Archived {

    private static final String TAG = "Controller_Archived";

    private static final double NETWORK_BANDWIDTH = 100000000 / 8.0; // unit: bytes per second
    private static final double TARGET_LATENCY = 0.2; // unit: s
    private static final double EXTRA_LATENCY = 0.00; // unit: s
    private static final double AVERAGE_FRAME_SIZE = 150000.0; // unit: bytes
    private static final double AVERAGE_RESULT_SIZE = 1000.0; // unit: bytes
    private static final double DEFAULT_ANALYSIS_LATENCY = 0.05;

    private static final double QSIZE_LARGE = 1.0;
    private static final double QSIZE_SMALL = 0.2;
    private static final double CHANGE_RATIO = 1.05;

    private static int queueWarn;
    private EDACam innerCam, outerCam;
    private long prevTotalDataSize;

    private static final double TOO_MANY_WAITING = 2;
    private static final double TOO_FEW_WAITING = 0.1;

    private static int lastAction = 0;

    private static long prevPendingDataSize = 0;

    private static boolean connectionChanged = false;
    private static double performanceLostRatio;
    private static int deviceAdded;

    private static final long CAMERA_ADJUSTMENT_PERIOD = 500;
    private List<EDAWorker> workers;
    private Parameter innerCamParameter, outerCamParameter;
    private long startTime, nextCheckpoint;
    private static int sensitive;

    private boolean notUpdated;

    private double findMaximumAllowedQueueSize() {
        double lo = 0, hi = 20;
        for (int loop = 0; loop < 20; loop++) {
            double mid = (lo + hi) / 2;
            double averageLatency = 0.0;

            double analysisLatency = 0.0;
            double numWorkers = 0;
            double sumLatency = 0;
            for (EDAWorker worker : workers) {
                WorkerStatus status = worker.status;
                if (status.isConnected) {
                    numWorkers++;
                    sumLatency += status.getLatencyWithQueueSize(mid);
                }
            }

            sumLatency /= 1000; // ms to s
            if (sumLatency == 0.0)
                analysisLatency = DEFAULT_ANALYSIS_LATENCY;
            else
                analysisLatency = sumLatency / numWorkers;

            averageLatency = estimateTrasferTime() + analysisLatency + EXTRA_LATENCY;
            if (averageLatency > TARGET_LATENCY)
                hi = mid;
            else
                lo = mid;
        }
        return hi;
    }

    private static double estimateTrasferTime() {
        double frameTransferTime = AVERAGE_FRAME_SIZE / NETWORK_BANDWIDTH;
        double resultTransferTime = AVERAGE_RESULT_SIZE / NETWORK_BANDWIDTH;
        return 2 * frameTransferTime + resultTransferTime;
    }

    private double estimateLatency() {
        double averageLatency = 0.0;

        double analysisLatency = 0.0;

        double numWorkers = 0;
        double sumLatency = 0;
        long sumQueueSize = 0;
        notUpdated = false;
        for (EDAWorker worker : workers) {
            WorkerStatus status = worker.status;
            if (status.isConnected) {
                if (status.lastUpdated == status.lastChecked)
                    notUpdated = true;
                status.lastChecked = status.lastUpdated;
                numWorkers++;
                double weight = status.getWeight();
                sumQueueSize += status.latestQueueSize;
                sumLatency += 1.0 / weight;
            }
        }

        sumLatency /= 1000; // ms to s
        if (sumLatency == 0.0)
            analysisLatency = DEFAULT_ANALYSIS_LATENCY;
        else
            analysisLatency = sumLatency / numWorkers;

        averageLatency = estimateTrasferTime() + analysisLatency + EXTRA_LATENCY;

        Log.w(TAG, "notUpdated = " + notUpdated + ", Latency = " + averageLatency + ", sumQueueSize = " + sumQueueSize);

        return averageLatency;
    }

    /*
    28/07/2024: new strategy
     */
    private void adjustCamSettingsV7() {
        long pendingDataSize = communicator.pendingDataSize;
        long totalDataSize = communicator.totalDataSize;

        // If connection status is changed, force-set FPS to something reasonably safe
        if (connectionChanged) {
            setDefaultFPSV2(innerCamParameter, outerCamParameter);
            connectionChanged = false;
        }

        else {
            double maximumAllowedQueueSize = findMaximumAllowedQueueSize();

            long numWorkers = 0;
            double sumQueueSize = 0.0;

            for (EDAWorker worker : workers) {
                WorkerStatus status = worker.status;
                if (status.isConnected) {
                    numWorkers++;
                    sumQueueSize += status.getAverageQueueSize();
                }
            }

            double averageQueueSize = (numWorkers == 0 ? 0.0 : sumQueueSize / numWorkers);

            final double DO_DECREASE = maximumAllowedQueueSize * 0.5;
            final double CAN_INCREASE = 0.1;

            double currentFPS = innerCamParameter.fps;
            double newFPS;

            if (averageQueueSize > DO_DECREASE) {
                newFPS = currentFPS / CHANGE_RATIO;
            }
            else if (averageQueueSize <= CAN_INCREASE && CAN_INCREASE < DO_DECREASE) {
                newFPS = currentFPS * CHANGE_RATIO;
            }
            else {
                newFPS = currentFPS;
            }

            newFPS = Math.max(newFPS, 1.0);
            newFPS = Math.min(newFPS, 30.0);

            StringBuilder sb = new StringBuilder();
            sb.append("num = ").append(numWorkers);
            sb.append(", max = ").append(String.format(Locale.ENGLISH, "%.02f", maximumAllowedQueueSize));
            sb.append(", cur = ").append(String.format(Locale.ENGLISH, "%.02f", averageQueueSize));
            sb.append(", latency = ").append(String.format(Locale.ENGLISH, "%.02f", estimateLatency()));
            sb.append(", fps = ").append(newFPS);
            Log.w(TAG, sb.toString());

            //newFPS += 1e-9; // To avoid undesired rounding down in any case


            innerCamParameter.fps = newFPS;
            outerCamParameter.fps = newFPS;


            /*Log.v(TAG, "FPS = " + newFPS + ", available = " + availableWorkers);
            if (queueWarn != 0) {
                Log.w(TAG, "queueWarn level " + queueWarn);
            }*/

            prevPendingDataSize = pendingDataSize;
            long sizeDelta = totalDataSize - prevTotalDataSize;
            prevTotalDataSize = totalDataSize;

            StatusLogger.log(innerCam, outerCam, workers, nextCheckpoint - startTime, sizeDelta, -1);
        }

        if (innerCam.outStream != null) {
            innerCam.sendSettings();
        }
        if (outerCam.outStream != null) {
            outerCam.sendSettings();
        }

        ArrayList<DeviceLogger.DeviceLog.IndividualDeviceLog> logs = new ArrayList<>();
        for (EDAWorker worker : workers) {
            worker.status.innerHistory.removeOldResults();
            worker.status.outerHistory.removeOldResults();
            worker.status.calcNetworkTime();

            DeviceLogger.DeviceLog.IndividualDeviceLog log = new DeviceLogger.DeviceLog.IndividualDeviceLog(worker.status.temperatures, worker.status.frequencies);
            logs.add(log);
        }
        DeviceLogger.addLog(new DeviceLogger.DeviceLog(nextCheckpoint - startTime, logs));
    }

    /*
    04/07/2024: Using latency model
     */
    private void adjustCamSettingsV6() {
        long pendingDataSize = communicator.pendingDataSize;
        long totalDataSize = communicator.totalDataSize;

        // If connection status is changed, force-set FPS to something reasonably safe
        if (connectionChanged) {
            setDefaultFPSV2(innerCamParameter, outerCamParameter);
            connectionChanged = false;
        }

        else {
            double averageLatency = estimateLatency();

            double currentFPS = innerCamParameter.fps;
            double newFPS;

            double quiescent = 0.9;

            if (averageLatency > TARGET_LATENCY) {
                //newFPS = currentFPS * DECREASE_MULTIPLIER;
                newFPS = currentFPS / CHANGE_RATIO;
            }
            else if (averageLatency < quiescent * TARGET_LATENCY && (!notUpdated || currentFPS <= 10)) {
                //newFPS = currentFPS + INCREASE_RATE;
                newFPS = currentFPS * CHANGE_RATIO;
            }
            else {
                newFPS = currentFPS;
            }

            newFPS = Math.max(newFPS, 1.0);
            newFPS = Math.min(newFPS, 30.0);

            //newFPS += 1e-9; // To avoid undesired rounding down in any case


            innerCamParameter.fps = newFPS;
            outerCamParameter.fps = newFPS;


            /*Log.v(TAG, "FPS = " + newFPS + ", available = " + availableWorkers);
            if (queueWarn != 0) {
                Log.w(TAG, "queueWarn level " + queueWarn);
            }*/

            prevPendingDataSize = pendingDataSize;
            long sizeDelta = totalDataSize - prevTotalDataSize;
            prevTotalDataSize = totalDataSize;

            StatusLogger.log(innerCam, outerCam, workers, nextCheckpoint - startTime, sizeDelta, -1);
        }

        if (innerCam.outStream != null) {
            innerCam.sendSettings();
        }
        if (outerCam.outStream != null) {
            outerCam.sendSettings();
        }

        ArrayList<DeviceLogger.DeviceLog.IndividualDeviceLog> logs = new ArrayList<>();
        for (EDAWorker worker : workers) {
            worker.status.innerHistory.removeOldResults();
            worker.status.outerHistory.removeOldResults();
            worker.status.calcNetworkTime();

            DeviceLogger.DeviceLog.IndividualDeviceLog log = new DeviceLogger.DeviceLog.IndividualDeviceLog(worker.status.temperatures, worker.status.frequencies);
            logs.add(log);
        }
        DeviceLogger.addLog(new DeviceLogger.DeviceLog(nextCheckpoint - startTime, logs));
    }

    /*
    28/06/2024: Queue size calculated through the average in the history, rather than the latest one
     */
    private void adjustCamSettingsV5() {
        long pendingDataSize = communicator.pendingDataSize;
        long totalDataSize = communicator.totalDataSize;

        long totalQueueSize = 0;
        long numRecords = 0;
        // If connection status is changed, force-set FPS to something reasonably safe
        if (connectionChanged) {
            setDefaultFPSV2(innerCamParameter, outerCamParameter);
            connectionChanged = false;
        }

        else {
            for (EDAWorker worker : workers) {
                WorkerStatus status = worker.status;

                if (status.isConnected) {
                    numRecords += status.innerHistory.history.size();
                    totalQueueSize += status.innerHistory.getSumQueueSize();
                    numRecords += status.outerHistory.history.size();
                    totalQueueSize += status.outerHistory.getSumQueueSize();
                }
            }

            double averageQueueSize = (double) totalQueueSize / numRecords;

            boolean levelLo = queueWarn == 2 || averageQueueSize > QSIZE_LARGE;
            /* 2 */
            boolean levelHi = queueWarn == 0 && averageQueueSize < QSIZE_SMALL;

            double currentFPS = innerCamParameter.fps + outerCamParameter.fps;

            Log.v(TAG, "FPS = " + currentFPS + ", available = " + availableWorkers + ", queueSize = " + averageQueueSize + " (" + totalQueueSize + "/" + numRecords + ")");

            if (queueWarn != 0) {
                Log.w(TAG, "queueWarn level " + queueWarn);
            }

            if (levelLo) {
                if (lastAction != -1) {
                    lastAction = -1;
                    innerCamParameter.decrease();
                    outerCamParameter.decrease();
                }
                else
                    lastAction = 0;
            } else if (levelHi) {
                if (lastAction != 1) {
                    lastAction = 1;
                    innerCamParameter.increase();
                    outerCamParameter.increase();
                }
                else
                    lastAction = 0;
            } else {
                lastAction = 0;
            }

            prevPendingDataSize = pendingDataSize;
            long sizeDelta = totalDataSize - prevTotalDataSize;
            prevTotalDataSize = totalDataSize;

            int networkLevel = (levelLo ? 0 : levelHi ? 2 : 1);

            StatusLogger.log(innerCam, outerCam, workers, nextCheckpoint - startTime, sizeDelta, networkLevel);

        }

        if (innerCam.outStream != null) {
            innerCam.sendSettings(innerCam.outStream);
        }
        if (outerCam.outStream != null) {
            outerCam.sendSettings(outerCam.outStream);
        }

        ArrayList<DeviceLogger.DeviceLog.IndividualDeviceLog> logs = new ArrayList<>();
        for (EDAWorker worker : workers) {
            worker.status.innerHistory.removeOldResults();
            worker.status.outerHistory.removeOldResults();
            worker.status.calcNetworkTime();

            DeviceLogger.DeviceLog.IndividualDeviceLog log = new DeviceLogger.DeviceLog.IndividualDeviceLog(worker.status.temperatures, worker.status.frequencies);
            logs.add(log);
        }
        DeviceLogger.addLog(new DeviceLogger.DeviceLog(nextCheckpoint - startTime, logs));

        if (sensitive > 0)
            sensitive--;
        queueWarn = 0;
    }

    /*
    19/02/2024: Resolution and Quality are fixed, only modify frame rate
     */
    private void adjustCamSettingsV4() {
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

            if (queueWarn != 0) {
                Log.w(TAG, "queueWarn level " + queueWarn);
            }

            /* 1 */
            boolean networkSlow = queueWarn == 2 || weightedWaiting > TOO_MANY_WAITING; // || weightedPending > TOO_MUCH_PENDING;
            /* 2 */
            boolean networkFast = queueWarn == 0 && weightedWaiting < TOO_FEW_WAITING; // && weightedPending < TOO_LITTLE_PENDING;

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

        ArrayList<DeviceLogger.DeviceLog.IndividualDeviceLog> logs = new ArrayList<>();
        for (EDAWorker worker : workers) {
            worker.status.innerHistory.removeOldResults();
            worker.status.outerHistory.removeOldResults();
            worker.status.calcNetworkTime();

            DeviceLogger.DeviceLog.IndividualDeviceLog log = new DeviceLogger.DeviceLog.IndividualDeviceLog(worker.status.temperatures, worker.status.frequencies);
            logs.add(log);
        }
        DeviceLogger.addLog(new DeviceLogger.DeviceLog(nextCheckpoint - startTime, logs));

        if (sensitive > 0)
            sensitive--;
        queueWarn = 0;
    }

    private void setDefaultFPSV2(Parameter innerCamParameter, Parameter outerCamParameter) {
        double totalFPS = innerCamParameter.fps;// + outerCamParameter.fps;

        if (performanceLostRatio > 0) {
            totalFPS = Math.max(5, totalFPS * (1 - performanceLostRatio));
        }

        totalFPS += deviceAdded * 3;

        innerCamParameter.fps = totalFPS;
        outerCamParameter.fps = totalFPS;

        //sensitive = 10;
    }

    private void setDefaultFPS(Parameter innerCamParameter, Parameter outerCamParameter) {
        double totalFPS = innerCamParameter.fps + outerCamParameter.fps;

        if (performanceLostRatio > 0) {
            totalFPS = Math.max(5, totalFPS * (1 - performanceLostRatio));
        }

        Log.v(TAG, "deviceAdded = " + deviceAdded);

        totalFPS += deviceAdded * 5;

        double inF = totalFPS * 2 / 5;
        double outF = totalFPS * 3 / 5;
        innerCamParameter.fps = inF;
        outerCamParameter.fps = outF;

        sensitive = 10; // double rate change for 10 / 2 = 5 seconds
    }
}
