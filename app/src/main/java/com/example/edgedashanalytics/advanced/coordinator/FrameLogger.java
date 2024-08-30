package com.example.edgedashanalytics.advanced.coordinator;

import static com.example.edgedashanalytics.advanced.coordinator.MainRoutine.Experiment.*;
import static com.example.edgedashanalytics.advanced.coordinator.Communicator.recordMap;

import android.content.Context;
import android.util.Log;

import com.example.edgedashanalytics.advanced.common.AnalysisResult;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FrameLogger {
    private static final String TAG = "FrameLogger";

    public static final List<AnalysisResult> results = new ArrayList<>();

    public static final InnerResult innerResult = new InnerResult();
    public static final OuterResult outerResult = new OuterResult();

    public static InnerResult baseInnerResult;
    public static OuterResult baseOuterResult;

    public static void addResult(AnalysisResult result, boolean isDistracted, List<String> hazards, long dataSize) {
        synchronized (results) {
            results.add(result);
            RecordC rc;
            synchronized (recordMap) {
                rc = recordMap.get(result.frameNum);
            }
            if (result.isInner) {
                innerResult.addResult((int)rc.cameraFrameNum, isDistracted);
            }
            else {
                outerResult.addResult((int)rc.cameraFrameNum, hazards, dataSize, result.workerNum);
            }
        }
    }

    public static void writeLogs(Context context, int testNum) {
        synchronized (results) {
            //InnerResult.InnerAccuracyResult innerAccuracyResult = innerResult.calcAccuracy(baseInnerResult);
            //OuterResult.OuterAccuracyResult outerAccuracyResult = outerResult.calcAccuracy(baseOuterResult);
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

            sb.append("frameNum,timestamp,isInner,workerNum,processTime,workerTime,networkTime,turnaround,queueSize\n");

            long startTime = results.get(0).timestamp;

            long totalWorkerTime = 0, totalProcessTime = 0, totalNetworkTime = 0, totalTurnAround = 0, totalQueueSize = 0;
            long inCount = 0, outCount = 0;

            synchronized (recordMap) {
                for (AnalysisResult result : results) {
                    if (result.timestamp - startTime > E_REAL_EXPERIMENT_DURATION)
                        break;
                    totalWorkerTime += result.workerTime;
                    totalProcessTime += result.processTime;
                    totalNetworkTime += result.networkTime;
                    totalTurnAround += result.responseTime;
                    totalQueueSize += result.queueSize;
                    if (result.isInner)
                        inCount++;
                    else
                        outCount++;
                }

                int numResults = results.size();
                /*sb.append("I.Accuracy,")
                        .append(innerAccuracyResult.count).append(",")
                        .append(innerAccuracyResult.distracted).append(",")
                        .append(innerAccuracyResult.nonDistracted).append(",")
                        .append(innerAccuracyResult.distractedWrong).append(",")
                        .append(innerAccuracyResult.nonDistractedWrong).append("\n");

                sb.append("O.Accuracy,")
                        .append(outerAccuracyResult.count).append(",")
                        .append(outerAccuracyResult.found).append(",")
                        .append(outerAccuracyResult.notFound).append(",")
                        .append(outerAccuracyResult.wrongFound).append("\n");*/

                sb.append("Dropped,").append(Communicator.failed).append("\n");

                sb.append(",,,,")
                        .append(D(totalProcessTime / (double)numResults, 2)).append(",")
                        .append(D(totalWorkerTime / (double)numResults, 2)).append(",")
                        .append(D(totalNetworkTime / (double)numResults, 2)).append(",")
                        .append(D(totalTurnAround / (double)numResults, 2)).append(",")
                        .append(D(totalQueueSize / (double)numResults, 2)).append(",")
                        .append(inCount).append(",").append(outCount).append("\n");

                for (AnalysisResult result : results) {
                    if (result.timestamp - startTime > E_REAL_EXPERIMENT_DURATION)
                        break;
                    RecordC rc = recordMap.get(result.frameNum);
                    sb.append(rc.cameraFrameNum).append(",")
                            .append(result.timestamp - startTime).append(",")
                            .append(result.isInner ? "in" : "out").append(",")
                            .append(result.workerNum).append(",")
                            .append(result.processTime).append(",")
                            .append(result.workerTime).append(",")
                            .append(result.networkTime).append(",")
                            .append(result.responseTime).append(",")
                            .append(result.queueSize).append("\n");
                }
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(sb.toString().getBytes());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        writeSimplifiedLogs(context, testNum);
    }

    private static void writeSimplifiedLogs(Context context, int testNum) {
        synchronized (results) {
            String inFileName = testNum + "_res_in.txt";
            String outFileName = testNum + "_res_out.txt";

            File inFile = new File(context.getExternalFilesDir(null), inFileName);
            File outFile = new File(context.getExternalFilesDir(null), outFileName);

            StringBuilder sbIn = new StringBuilder();
            StringBuilder sbOut = new StringBuilder();

            long startTime = results.get(0).timestamp;

            for (AnalysisResult result : results) {
                if (result.timestamp - startTime > E_REAL_EXPERIMENT_DURATION)
                    break;
                StringBuilder sb = (result.isInner ? sbIn : sbOut);

                sb.append(result.timestamp - startTime).append("\t")
                        .append(result.workerNum).append("\t")
                        .append(result.processTime).append("\t")
                        .append(result.responseTime).append("\t")
                        .append(result.queueSize).append("\n");
            }

            try (FileOutputStream fos = new FileOutputStream(inFile)) {
                fos.write(sbIn.toString().getBytes());
            } catch (Exception e) {
                e.printStackTrace();
            }

            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(sbOut.toString().getBytes());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static String D(double val, int digits) {
        return String.format(Locale.ENGLISH, "%." + digits + "f", val);
    }
}
