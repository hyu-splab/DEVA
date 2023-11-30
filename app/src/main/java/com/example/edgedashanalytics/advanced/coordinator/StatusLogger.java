package com.example.edgedashanalytics.advanced.coordinator;

import android.content.Context;
import android.util.Log;
import android.util.Size;

import com.example.edgedashanalytics.advanced.common.WorkerStatus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StatusLogger {
    private static final String TAG = "StatusLogger";

    public static final List<StatusLog> statusLogs = new ArrayList<>();

    public static void log(EDACam innerCam, EDACam outerCam, List<EDAWorker> workers) {

        List<WorkerStatus> workerStatuses = new ArrayList<>();
        for (EDAWorker worker : workers) {
            workerStatuses.add(new WorkerStatus(worker.status));
        }

        StatusLog log = new StatusLog(statusLogs.size() + 1, System.currentTimeMillis(),
                innerCam.camSettings, outerCam.camSettings, workerStatuses);

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
            StringBuilder sb = new StringBuilder();

            sb.append(now.format(formatter)).append("\n\n");

            long startTime = statusLogs.get(0).timestamp;

            for (StatusLog log : statusLogs) {
                sb.append(log.index).append(",").append(log.timestamp - startTime).append("\n");
                sb.append(writeSingleLog(log));
                sb.append("\n");
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(sb.toString().getBytes());
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    public static StringBuilder writeSingleLog(StatusLog log) {
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
    }

    public static String getLatestLogText() {
        if (statusLogs.isEmpty())
            return "No logs available yet";
        return writeSingleLog(statusLogs.get(statusLogs.size() - 1)).toString();
    }

    static class StatusLog {
        int index;
        long timestamp;

        Size innerR, outerR;
        int innerQ, innerF, outerQ, outerF;
        List<WorkerStatus> workerStatuses;

        public StatusLog(int index, long timestamp,
                         CamSettingsV2 innerCamSettings, CamSettingsV2 outerCamSettings,
                         List<WorkerStatus> workerStatuses) {
            this.index = index;
            this.timestamp = timestamp;
            innerR = innerCamSettings.getR();
            innerQ = innerCamSettings.getQ();
            innerF = innerCamSettings.getF();
            outerR = outerCamSettings.getR();
            outerQ = outerCamSettings.getQ();
            outerF = outerCamSettings.getF();
            this.workerStatuses = workerStatuses;
        }
    }
}
