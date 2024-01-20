package com.example.edgedashanalytics.advanced.coordinator;

import android.content.Context;
import android.util.Log;

import com.example.edgedashanalytics.advanced.common.FrameResult;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class FrameLogger {
    private static final String TAG = "FrameLogger";

    public static final List<FrameResult> results = new ArrayList<>();

    public static final InnerResult innerResult = new InnerResult();
    public static final OuterResult outerResult = new OuterResult();

    public static InnerResult baseInnerResult;
    public static OuterResult baseOuterResult;

    public static void addResult(FrameResult result, boolean isDistracted, List<String> hazards) {
        synchronized (results) {
            results.add(result);
            //Log.v(TAG, "addResult: " + result.frameNum + " " + result.isInner + " " + (hazards == null));
            if (result.isInner) {
                innerResult.addResult(result.cameraFrameNum, isDistracted);
            }
            else {
                if (hazards == null) {
                    Log.v(TAG, "How is hazards null???????????");
                }
                outerResult.addResult(result.cameraFrameNum, hazards);
            }
        }
    }

    public static void writeLogs(Context context, int testNum) {
        synchronized (results) {
            InnerResult.InnerAccuracyResult innerAccuracyResult = innerResult.calcAccuracy(baseInnerResult);
            OuterResult.OuterAccuracyResult outerAccuracyResult = outerResult.calcAccuracy(baseOuterResult);
            if (results.isEmpty()) {
                Log.w(TAG, "No frame logs available");
                return;
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
            LocalDateTime now = LocalDateTime.now();
            String filename = testNum + "_flog.csv";

            File file = new File(context.getExternalFilesDir(null), filename);
            StringBuilder sb = new StringBuilder();

            sb.append(now.format(formatter)).append("\n\n");

            sb.append("frameNum,timestamp,isInner,workerNum,workerTime,processTime,networkTime,turnaround\n");

            long startTime = results.get(0).timestamp;

            for (FrameResult result : results) {
                sb.append(result.cameraFrameNum).append(",")
                        .append(result.timestamp - startTime).append(",")
                        .append(result.isInner ? "in" : "out").append(",")
                        .append(result.workerNum).append(",")
                        .append(result.workerTime).append(",")
                        .append(result.processTime).append(",")
                        .append(result.networkTime).append(",")
                        .append(result.turnaround).append("\n");
            }

            sb.append("I.Accuracy,")
                    .append(innerAccuracyResult.count).append(",")
                    .append(innerAccuracyResult.distracted).append(",")
                    .append(innerAccuracyResult.nonDistracted).append(",")
                    .append(innerAccuracyResult.distractedWrong).append(",")
                    .append(innerAccuracyResult.nonDistractedWrong).append("\n");

            sb.append("O.Accuracy,")
                    .append(outerAccuracyResult.count).append(",")
                    .append(outerAccuracyResult.found).append(",")
                    .append(outerAccuracyResult.notFound).append(",")
                    .append(outerAccuracyResult.wrongFound).append("\n");

            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(sb.toString().getBytes());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
