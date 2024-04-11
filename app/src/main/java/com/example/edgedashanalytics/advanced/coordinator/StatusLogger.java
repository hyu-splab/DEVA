package com.example.edgedashanalytics.advanced.coordinator;

import android.content.Context;
import android.util.Log;

import com.example.edgedashanalytics.advanced.common.WorkerStatus;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class StatusLogger {
    private static final String TAG = "StatusLogger";

    public static final List<StatusLog> statusLogs = new ArrayList<>();

    public static void log(EDACam innerCam, EDACam outerCam, List<EDAWorker> workers, long timestamp, long sizeDelta, int networkLevel) {

        List<WorkerStatus> workerStatuses = new ArrayList<>();
        long workerInnerCount = 0, workerOuterCount = 0;
        for (EDAWorker worker : workers) {
            workerStatuses.add(new WorkerStatus(worker.status));
            workerInnerCount += worker.status.innerWaiting;
            workerOuterCount += worker.status.outerWaiting;
        }

        StatusLog log = new StatusLog(statusLogs.size() + 1, timestamp,
                innerCam.camParameter, outerCam.camParameter,
                workerInnerCount, workerOuterCount, AdvancedMain.communicator.pendingDataSize,
                sizeDelta, networkLevel, workerStatuses);

        synchronized (statusLogs) {
            statusLogs.add(log);
        }
    }

    public static void writeLogs(Context context, int testNum) {
        synchronized (statusLogs) {
            if (statusLogs.isEmpty()) {
                Log.w(TAG, "No status logs available");
                return;
            }

            String filename = testNum + "_slog.csv";

            File file = new File(context.getExternalFilesDir(null), filename);

            String theLog = writeLogsV2(context, file);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(theLog.getBytes());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static String writeLogsV2(Context context, File file) {
        StringBuilder sb = new StringBuilder();
        long startTime = statusLogs.get(0).timestamp;

        sb.append("Index,Time,innerF,outerF,innerWaiting,outerWaiting,pendingSize,sizeDelta,networkLevel,W0.con,W0.iw,W0.ip,W0.ow,W0.op,W1.con,W1.iw,W1.ip,W1.ow,W1.op,W2.con,W2.iw,W2.ip,W2.ow,W2.op\n");

        for (StatusLog log : statusLogs) {
            sb.append(log.index).append(",").append(log.timestamp - startTime).append(",");
            sb.append(log.innerF).append(",").append(log.outerF).append(",");
            sb.append(log.innerWaiting).append(",").append(log.outerWaiting).append(",");
            sb.append(log.pendingDataSize).append(",").append(log.sizeDelta).append(",");
            sb.append(log.statusLevel).append(",");
            for (WorkerStatusLog status : log.workerStatuses) {
                sb.append(status.isConnected ? 1 : 0).append(",");
                sb.append(status.innerWaiting).append(",").append(status.innerProcessTime).append(",");
                sb.append(status.outerWaiting).append(",").append(status.outerProcessTime).append(",");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    static class StatusLog {
        int index;
        long timestamp;
        double innerF, outerF;
        long innerWaiting, outerWaiting;
        long pendingDataSize, sizeDelta;
        int statusLevel;
        List<WorkerStatusLog> workerStatuses;

        public StatusLog(int index, long timestamp,
                         Parameter innerCamParameter, Parameter outerCamParameter,
                         long innerWaiting, long outerWaiting, long pendingDataSize, long sizeDelta,
                         int statusLevel, List<WorkerStatus> workerStatuses) {
            this.index = index;
            this.timestamp = timestamp;
            innerF = innerCamParameter.fps;
            outerF = outerCamParameter.fps;
            this.innerWaiting = innerWaiting;
            this.outerWaiting = outerWaiting;
            this.pendingDataSize = pendingDataSize;
            this.sizeDelta = sizeDelta;
            this.statusLevel = statusLevel;
            this.workerStatuses = new ArrayList<>();

            int i = 0;
            for (WorkerStatus status : workerStatuses) {
                this.workerStatuses.add(new WorkerStatusLog(status));
                i++;
            }
        }
    }

    static class WorkerStatusLog {
        int innerWaiting, outerWaiting;
        double innerProcessTime, outerProcessTime;
        boolean isConnected;
        public WorkerStatusLog(WorkerStatus status) {
            this.innerWaiting = status.innerWaiting;
            this.innerProcessTime = status.innerHistory.processTime;
            this.outerWaiting = status.outerWaiting;
            this.outerProcessTime = status.outerHistory.processTime;
            this.isConnected = status.isConnected;
        }
    }
}
