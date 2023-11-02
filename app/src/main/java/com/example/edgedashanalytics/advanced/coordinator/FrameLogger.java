package com.example.edgedashanalytics.advanced.coordinator;

import android.content.Context;
import android.util.Log;

import com.example.edgedashanalytics.advanced.common.FrameResult;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class FrameLogger {
    private static final String TAG = "FrameLogger";

    public static final List<FrameResult> results = new ArrayList<>();

    public static void addResult(FrameResult result) {
        synchronized (results) {
            results.add(result);
        }
    }

    public static void writeLogs(Context context) {
        synchronized (results) {
            if (results.isEmpty()) {
                Log.w(TAG, "No frame logs available");
                return;
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
            LocalDateTime now = LocalDateTime.now();
            String filename = "flog.csv";

            File file = new File(context.getExternalFilesDir(null), filename);
            StringBuilder sb = new StringBuilder();

            sb.append(now.format(formatter)).append("\n\n");

            sb.append("frameNum,timestamp,isInner,workerNum,processTime,networkTime\n");

            long startTime = results.get(0).timestamp;

            for (FrameResult result : results) {
                sb.append(result.frameNum).append(",")
                        .append(result.timestamp - startTime).append(",")
                        .append(result.isInner ? "in" : "out").append(",")
                        .append(result.workerNum).append(",")
                        .append(result.processTime).append(",")
                        .append(result.networkTime).append("\n");
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(sb.toString().getBytes());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
