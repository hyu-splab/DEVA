package com.example.edgedashanalytics.advanced.worker;

import static com.example.edgedashanalytics.advanced.worker.WorkerThread.N_THREAD;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.example.edgedashanalytics.advanced.common.TimeLog;
import com.example.edgedashanalytics.advanced.common.Image2;
import com.example.edgedashanalytics.advanced.common.WorkerResult;
import com.example.edgedashanalytics.page.main.MainActivity;
import com.example.edgedashanalytics.util.hardware.PowerMonitor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

public class ProcessorThread extends Thread {
    private static final String TAG = "ProcessorThread";
    static public ArrayBlockingQueue<Image2> queue = new ArrayBlockingQueue<>(100);
    static public Handler handler;

    public static final int QUEUE_FULL = 100; // per thread

    public int tid = 0;
    public int workCount = 0;

    public InnerProcessor innerProcessor = new InnerProcessor();
    public OuterProcessor outerProcessor = new OuterProcessor();

    @Override
    public void run() {
        FrameProcessor frameProcessor = null;
        while (true) {
            try {
                Image2 img = queue.take();

                if (queue.size() >= QUEUE_FULL * N_THREAD) {
                    Log.v(TAG, "sending failed message " + img.isInner);
                    sendFailedResult(img.isInner, img.dataSize);
                    continue;
                }

                Bitmap bitmap = uncompress(img.data);

                int cameraFrameNum = img.cameraFrameNum;
                int frameNum = img.frameNum;
                boolean isInner = img.isInner;
                if (isInner)
                    frameProcessor = innerProcessor;
                else
                    frameProcessor = outerProcessor;

                frameProcessor.setFrame(bitmap);
                frameProcessor.setCameraFrameNum(img.cameraFrameNum);

                long startTime = System.currentTimeMillis();
                FrameProcessor.ProcessResult result = frameProcessor.run();
                long endTime = System.currentTimeMillis();

                if (!img.isTesting) {
                    sendResult(isInner, img.coordinatorStartTime, frameNum, cameraFrameNum,
                            endTime - startTime, endTime - img.workerStartTime, result.msg, img.dataSize, queue.size(),
                            result.isDistracted, result.hazards);
                    Log.v(TAG, "Analysis done");
                }
                workCount++;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void sendFailedResult(boolean isInner, long dataSize) {
        Message retMsg = Message.obtain();
        retMsg.obj = new WorkerResult(isInner, dataSize, MainActivity.startBatteryLevel - PowerMonitor.getBatteryLevel(MainActivity.context));
        handler.sendMessage(retMsg);
    }

    private Bitmap uncompress(byte[] data) {
        ByteArrayInputStream is = new ByteArrayInputStream(data);
        BitmapFactory.Options ops = new BitmapFactory.Options();
        ops.inMutable = true;
        Bitmap bitmap = BitmapFactory.decodeStream(is, null, ops);
        return bitmap;
    }

    public static void sendResult(boolean isInner, long coordinatorStartTime, int frameNum, int cameraFrameNum, long processTime, long totalTime, String resultString, long dataSize, long queueSize, boolean isDistracted, List<String> hazards) {
        Message retMsg = Message.obtain();
        retMsg.obj = new WorkerResult(isInner, coordinatorStartTime, frameNum, cameraFrameNum, processTime,
                totalTime, resultString, dataSize, queueSize, isDistracted, hazards, MainActivity.startBatteryLevel - PowerMonitor.getBatteryLevel(MainActivity.context));
        handler.sendMessage(retMsg);
    }
}
