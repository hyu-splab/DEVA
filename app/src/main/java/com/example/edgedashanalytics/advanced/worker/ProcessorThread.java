package com.example.edgedashanalytics.advanced.worker;

import static com.example.edgedashanalytics.advanced.common.WorkerStatus.DEFAULT_INNER_PROCESS_TIME;
import static com.example.edgedashanalytics.advanced.common.WorkerStatus.DEFAULT_OUTER_PROCESS_TIME;
import static com.example.edgedashanalytics.advanced.worker.WorkerThread.N_THREAD;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HardwarePropertiesManager;
import android.os.Message;
import android.util.Log;

import androidx.collection.CircularArray;

import com.example.edgedashanalytics.advanced.common.FrameData;
import com.example.edgedashanalytics.advanced.common.WorkerResult;
import com.example.edgedashanalytics.advanced.coordinator.AdvancedMain;
import com.example.edgedashanalytics.advanced.coordinator.Communicator;
import com.example.edgedashanalytics.advanced.coordinator.Controller;
import com.example.edgedashanalytics.page.main.MainActivity;
import com.example.edgedashanalytics.util.hardware.PowerMonitor;

import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

public class ProcessorThread extends Thread {
    private static final String TAG = "ProcessorThread";
    public static final HashMap<Integer, Long> workerStartTimeMap = new HashMap<>();
    private static final int MIN_RECORDS = 50;
    static public ArrayBlockingQueue<FrameData> queue = new ArrayBlockingQueue<>(100);
    static public Handler handler;

    public int tid = 0;
    public int workCount = 0;

    public InnerProcessor innerProcessor = new InnerProcessor();
    public OuterProcessor outerProcessor = new OuterProcessor();

    public static final ArrayList<Integer> processingTimeList = new ArrayList<>();
    public static final int maxListCount = 50;
    public static int debugCount = 0;

    public static long innerAnalysisTimeSum = 0, outerAnalysisTimeSum = 0;
    public static double averageInnerAnalysisTime = DEFAULT_INNER_PROCESS_TIME, averageOuterAnalysisTime = DEFAULT_OUTER_PROCESS_TIME;

    private static final ArrayDeque<Long> innerHistory = new ArrayDeque<>(), outerHistory = new ArrayDeque<>();
    private static long busyCnt = 0;
    @Override
    public void run() {
        FrameProcessor frameProcessor = null;
        long total = 0, dropped = 0;
        while (true) {

            try {
                FrameData img;
                if (tid < N_THREAD) {
                    img = queue.take();
                }
                else {
                    if (AdvancedMain.isBusy) {
                        img = FrameData.getMeaninglessFrame();
                        Bitmap bitmap = uncompress(img.data);

                        boolean isInner = img.isInner;

                        frameProcessor = (isInner) ? innerProcessor : outerProcessor;

                        frameProcessor.setFrame(bitmap);

                        //long startTime = System.currentTimeMillis();
                        FrameProcessor.ProcessResult result = frameProcessor.run();
                        busyCnt++;
                        Log.v(TAG, "Busy count: " + busyCnt);
                        continue;
                    }
                    else {
                        try {
                            Thread.sleep(100);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        continue;
                    }
                }
                total++;

                long startTime = System.currentTimeMillis();

                /*if (!img.isTesting && queue.size() >= QUEUE_FULL * N_THREAD) {
                    Log.v(TAG, "sending failed message " + img.isInner);
                    sendFailedResult(queue.size());
                    continue;
                }*/
                long queueTime = startTime - workerStartTimeMap.get(img.frameNum);
                int historyCount = (img.isInner ? innerHistory.size() : outerHistory.size());

                boolean useDropping = false;
                // Decide whether to drop only when we have enough history records to judge reasonably
                if (useDropping && historyCount >= MIN_RECORDS) {
                    double overallLatency = estimateOverallLatency(queueTime, img.isInner);
                    if (overallLatency > Controller.TARGET_LATENCY) {
                        dropped++;
                        long estimatedAnalysisTime = (long) (img.isInner ? averageInnerAnalysisTime : averageOuterAnalysisTime);
                        Log.w(TAG, "Dropped " + dropped + "/" + total + ", latency = " + overallLatency + ", avg = " + averageInnerAnalysisTime + " " + averageOuterAnalysisTime);
                        sendFailedResult(img.frameNum, queue.size(), estimatedAnalysisTime, queueTime + estimatedAnalysisTime);
                        continue;
                    }
                }

                Bitmap bitmap = uncompress(img.data);


                int frameNum = img.frameNum;
                boolean isInner = img.isInner;

                frameProcessor = (isInner) ? innerProcessor : outerProcessor;

                frameProcessor.setFrame(bitmap);

                //long startTime = System.currentTimeMillis();
                FrameProcessor.ProcessResult result = frameProcessor.run();
                long endTime = System.currentTimeMillis();

                if (!img.isTesting && tid < N_THREAD) {
                    long processTime = endTime - startTime;

                    synchronized (innerHistory) {
                        if (img.isInner) {
                            innerHistory.addLast(processTime);
                            innerAnalysisTimeSum += processTime;
                        }
                        else {
                            outerHistory.addLast(processTime);
                            outerAnalysisTimeSum += processTime;
                        }
                        removeOldResults();
                        calculateAverageAnalysisTime();

                        //Log.w(TAG, "inner = " + innerAnalysisTimeSum + " " + innerHistory.size() + " " + averageInnerAnalysisTime);
                        //Log.w(TAG, "outer = " + outerAnalysisTimeSum + " " + outerHistory.size() + " " + averageOuterAnalysisTime);
                    }

                    /*if (!img.isInner) {
                        Log.w(TAG, "Done outer: " + processTime);
                    }*/

                    sendResult(frameNum, processTime, endTime - workerStartTimeMap.get(frameNum),
                            result.msg, queue.size(), result.isDistracted, result.hazards);
                }
                else if (img.isTesting) {
                    synchronized (processingTimeList) {
                        processingTimeList.add((int)(endTime - startTime));
                        if (processingTimeList.size() == maxListCount) {
                            int sum = 0;
                            for (Integer x : processingTimeList)
                                sum += x;
                            Log.v(TAG, debugCount * maxListCount + " ~ " + ((debugCount + 1) * maxListCount - 1) + ": " + ((double)sum / maxListCount));
                            debugCount++;
                            processingTimeList.clear();
                        }
                    }
                }
                workCount++;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Call this WITHIN synchronized (innerHistory) block
    private void removeOldResults() {
        if (innerHistory.size() > MIN_RECORDS) {
            long history = innerHistory.pop();
            innerAnalysisTimeSum -= history;
        }

        if (outerHistory.size() > MIN_RECORDS) {
            long history = outerHistory.pop();
            outerAnalysisTimeSum -= history;
        }
    }

    // Call this WITHIN synchronized (innerHistory) block
    private void calculateAverageAnalysisTime() {
        if (!innerHistory.isEmpty())
            averageInnerAnalysisTime = innerAnalysisTimeSum / (double)innerHistory.size();
        else
            averageInnerAnalysisTime = DEFAULT_INNER_PROCESS_TIME;

        if (!outerHistory.isEmpty())
            averageOuterAnalysisTime = outerAnalysisTimeSum / (double)outerHistory.size();
        else
            averageOuterAnalysisTime = DEFAULT_OUTER_PROCESS_TIME;
    }

    private double estimateOverallLatency(long queueTime, boolean isInner) {
        double transferTime = Controller.estimateTrasferTime();
        //Log.w(TAG, "transferTime = " + transferTime);
        return transferTime + (queueTime + (isInner ? averageInnerAnalysisTime : averageOuterAnalysisTime)) / 1000.0;
    }

    private Bitmap uncompress(byte[] data) {
        ByteArrayInputStream is = new ByteArrayInputStream(data);
        BitmapFactory.Options ops = new BitmapFactory.Options();
        ops.inMutable = true;
        Bitmap bitmap = BitmapFactory.decodeStream(is, null, ops);
        return bitmap;
    }

    private void sendFailedResult(int frameNum, long queueSize, long processTime, long totalTime) {
        Message retMsg = Message.obtain();
        retMsg.obj = WorkerResult.createFailedResult(frameNum, queueSize, processTime, totalTime, MainActivity.latestTemperatures, MainActivity.latestFrequencies);
        handler.sendMessage(retMsg);
    }


    public static void sendResult(int frameNum, long processTime, long totalTime, String resultString,
                                  long queueSize, boolean isDistracted, List<String> hazards) {
        Message retMsg = Message.obtain();
        retMsg.obj = WorkerResult.createResult(
                frameNum, processTime,
                totalTime, resultString, queueSize, isDistracted, hazards,
                MainActivity.latestTemperatures, MainActivity.latestFrequencies);
        handler.sendMessage(retMsg);
    }
}