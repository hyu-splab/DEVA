package com.example.edgedashanalytics.advanced.worker;

import static com.example.edgedashanalytics.advanced.worker.WorkerThread.N_THREAD;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HardwarePropertiesManager;
import android.os.Message;
import android.util.Log;

import com.example.edgedashanalytics.advanced.common.FrameData;
import com.example.edgedashanalytics.advanced.common.WorkerResult;
import com.example.edgedashanalytics.advanced.coordinator.AdvancedMain;
import com.example.edgedashanalytics.advanced.coordinator.Communicator;
import com.example.edgedashanalytics.page.main.MainActivity;
import com.example.edgedashanalytics.util.hardware.PowerMonitor;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

public class ProcessorThread extends Thread {
    private static final String TAG = "ProcessorThread";
    static public ArrayBlockingQueue<FrameData> queue = new ArrayBlockingQueue<>(100);
    static public Handler handler;

    public static final int QUEUE_FULL = 100; // per thread

    public int tid = 0;
    public int workCount = 0;

    public InnerProcessor innerProcessor = new InnerProcessor();
    public OuterProcessor outerProcessor = new OuterProcessor();

    public static final ArrayList<Integer> processingTimeList = new ArrayList<>();
    public static final int maxListCount = 50;
    public static int debugCount = 0;

    @Override
    public void run() {
        FrameProcessor frameProcessor = null;
        while (true) {
            try {
                FrameData img;
                if (tid != N_THREAD) {
                    img = queue.take();
                }
                else {
                    if (AdvancedMain.isBusy) {
                        img = FrameData.getMeaninglessFrame();
                        //Log.v(TAG, "Busy thread working!!!");
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

                if (!img.isTesting && queue.size() >= QUEUE_FULL * N_THREAD) {
                    Log.v(TAG, "sending failed message " + img.isInner);
                    sendFailedResult(img.isInner, img.dataSize, queue.size());
                    continue;
                }

                Bitmap bitmap = uncompress(img.data);

                int cameraFrameNum = img.cameraFrameNum;
                int frameNum = img.frameNum;
                boolean isInner = img.isInner;

                frameProcessor = (isInner) ? innerProcessor : outerProcessor;

                frameProcessor.setFrame(bitmap);
                frameProcessor.setCameraFrameNum(img.cameraFrameNum);

                long startTime = System.currentTimeMillis();
                FrameProcessor.ProcessResult result = frameProcessor.run();
                long endTime = System.currentTimeMillis();

                if (!img.isTesting && tid != N_THREAD) {
                    sendResult(isInner, img.coordinatorStartTime, frameNum, cameraFrameNum,
                            endTime - startTime, endTime - img.workerStartTime, result.msg, img.dataSize, queue.size(),
                            result.isDistracted, result.hazards);
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

    private void sendFailedResult(boolean isInner, long dataSize, long queueSize) {
        Message retMsg = Message.obtain();
        retMsg.obj = WorkerResult.createFailedResult(
                isInner, dataSize, queueSize, MainActivity.startBatteryLevel - PowerMonitor.getBatteryLevel(MainActivity.context),
                MainActivity.latestTemperatures, MainActivity.latestFrequencies);
        handler.sendMessage(retMsg);
    }

    private Bitmap uncompress(byte[] data) {
        ByteArrayInputStream is = new ByteArrayInputStream(data);
        BitmapFactory.Options ops = new BitmapFactory.Options();
        ops.inMutable = true;
        Bitmap bitmap = BitmapFactory.decodeStream(is, null, ops);
        return bitmap;
    }

    public static void sendResult(boolean isInner, long coordinatorStartTime, int frameNum, int cameraFrameNum,
                                  long processTime, long totalTime, String resultString, long dataSize, long queueSize,
                                  boolean isDistracted, List<String> hazards) {
        Message retMsg = Message.obtain();
        retMsg.obj = WorkerResult.createResult(
                isInner, coordinatorStartTime, frameNum, cameraFrameNum, processTime,
                totalTime, resultString, dataSize, queueSize, isDistracted, hazards,
                MainActivity.startBatteryLevel - PowerMonitor.getBatteryLevel(MainActivity.context),
                MainActivity.latestTemperatures, MainActivity.latestFrequencies);
        handler.sendMessage(retMsg);
    }
}
