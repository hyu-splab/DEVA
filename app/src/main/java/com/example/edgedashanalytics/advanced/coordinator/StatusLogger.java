package com.example.edgedashanalytics.advanced.coordinator;

import android.content.Context;
import android.util.Log;
import android.util.Size;

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

    public static void log(EDACam innerCam, EDACam outerCam, List<EDAWorker> workers) {

        List<WorkerStatus> workerStatuses = new ArrayList<>();
        for (EDAWorker worker : workers) {
            workerStatuses.add(new WorkerStatus(worker.status));
        }

        StatusLog log = new StatusLog(statusLogs.size() + 1, System.currentTimeMillis(),
                innerCam.camSettings, outerCam.camSettings, AdvancedMain.communicator.pendingDataSize, workerStatuses);

        synchronized (statusLogs) {
            statusLogs.add(log);
        }
    }

    public static void writeLogs(Context context) {
        synchronized (statusLogs) {
            if (statusLogs.isEmpty()) {
                Log.w(TAG, "No status logs available");
                return;
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
            LocalDateTime now = LocalDateTime.now();
            String filename = "slog.csv";

            File file = new File(context.getExternalFilesDir(null), filename);
            /*StringBuilder sb = new StringBuilder();

            sb.append(now.format(formatter)).append("\n\n");

            long startTime = statusLogs.get(0).timestamp;

            for (StatusLog log : statusLogs) {
                sb.append(log.index).append(",").append(log.timestamp - startTime).append("\n");
                sb.append(writeSingleLog(log));
                sb.append("\n");
            }*/

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

        // Columns:
        // index, time, innerR WxH, innerQ, innerF, outerR WxH, outerQ, outerF, pendingSize, W0, W1, ...
        // Each worker: innerWaiting, innerProcessTime, outerProcessTime, networkTime

        sb.append("Index,Time,InnerR,innerQ,innerF,outerR,outerQ,outerF,pendingSize,W0.iw,W0.ip,W0.ow,W0.op,W1.iw,W1.ip,W1.ow,W1.op,W2.iw,W2.ip,W2.ow,W2.op\n");

        for (StatusLog log : statusLogs) {
            sb.append(log.index).append(",").append(log.timestamp - startTime).append(",");
            sb.append(log.innerR.getWidth()).append("x").append(log.innerR.getHeight()).append(",");
            sb.append(log.innerQ).append(",").append(log.innerF).append(",");
            sb.append(log.outerR.getWidth()).append("x").append(log.outerR.getHeight()).append(",");
            sb.append(log.outerQ).append(",").append(log.outerF).append(",");
            sb.append(log.pendingDataSize).append(",");
            for (WorkerStatusLog status : log.workerStatuses) {
                sb.append(status.innerWaiting).append(",").append(status.innerProcessTime).append(",");
                sb.append(status.outerWaiting).append(",").append(status.outerProcessTime).append(",");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /*public static StringBuilder writeSingleLog(StatusLog log) {
        StringBuilder sb = new StringBuilder();
        writeLine(sb, "Inner", Arrays.asList(
                log.innerQ,
                log.innerF,
                log.innerR.getWidth(),
                log.innerR.getHeight()));
        writeLine(sb, "Outer", Arrays.asList(
                log.outerQ,
                log.outerF,
                log.outerR.getWidth(),
                log.outerR.getHeight()));

        int workerNum = 0;
        for (WorkerStatus status : log.workerStatuses) {
            writeLine(sb, "Worker " + workerNum,
                    Arrays.asList(
                            status.innerWaiting,
                            status.innerProcessTime(),
                            status.outerWaiting,
                            status.outerProcessTime(),
                            status.networkTime));
            workerNum++;
        }
        return sb;
    }

    private static void writeLine(StringBuilder sb, String name, List<?> list) {
        sb.append(name);
        for (Object o : list) {
            sb.append(",").append(o instanceof Double
                    ? String.format(Locale.getDefault(), "%.1f", (double)o)
                    : o);
        }
        sb.append("\n");
    }*/

    public static String getLatestLogText() {
        if (statusLogs.isEmpty())
            return "No logs available yet";
        return "Not implemented yet";//writeSingleLog(statusLogs.get(statusLogs.size() - 1)).toString();
    }

    static class StatusLog {
        int index;
        long timestamp;

        Size innerR, outerR;
        int innerQ, innerF, outerQ, outerF;
        long pendingDataSize;
        List<WorkerStatusLog> workerStatuses;

        public StatusLog(int index, long timestamp,
                         CamSettings innerCamSettings, CamSettings outerCamSettings,
                         long pendingDataSize,
                         List<WorkerStatus> workerStatuses) {
            this.index = index;
            this.timestamp = timestamp;
            innerR = innerCamSettings.getR();
            innerQ = innerCamSettings.getQ();
            innerF = innerCamSettings.getF();
            outerR = outerCamSettings.getR();
            outerQ = outerCamSettings.getQ();
            outerF = outerCamSettings.getF();
            this.pendingDataSize = pendingDataSize;
            this.workerStatuses = new ArrayList<>();
            for (WorkerStatus status : workerStatuses) {
                this.workerStatuses.add(new WorkerStatusLog(status));
            }
        }
    }

    static class WorkerStatusLog {
        int innerWaiting, outerWaiting;
        double innerProcessTime, outerProcessTime;
        public WorkerStatusLog(WorkerStatus status) {
            this.innerWaiting = status.innerWaiting;
            this.innerProcessTime = status.innerProcessTime();
            this.outerWaiting = status.outerWaiting;
            this.outerProcessTime = status.outerProcessTime();
        }
    }
}
