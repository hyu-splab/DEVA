package com.example.edgedashanalytics.advanced.worker;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Message;

import com.example.edgedashanalytics.advanced.common.TimeLog;
import com.example.edgedashanalytics.advanced.common.Image2;
import com.example.edgedashanalytics.advanced.common.WorkerResult;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.ArrayBlockingQueue;

public class ProcessorThread extends Thread {
    static public ArrayBlockingQueue<Image2> queue = new ArrayBlockingQueue<>(1000);
    static public Handler handler;

    public int tid = 0;
    public int workCount = 0;

    static public InnerProcessor innerProcessor = new InnerProcessor();
    static public OuterProcessor outerProcessor = new OuterProcessor();

    @Override
    public void run() {
        FrameProcessor frameProcessor = null;
        while (true) {
            try {
                Image2 img = queue.take();

                TimeLog.worker.add(img.frameNum + ""); // Uncompress

                Bitmap bitmap = uncompress(img.data);

                TimeLog.worker.add(img.frameNum + ""); // Process Frame

                int frameNum = img.frameNum;
                boolean isInner = img.isInner;
                if (isInner)
                    frameProcessor = innerProcessor;
                else
                    frameProcessor = outerProcessor;

                frameProcessor.setFrame(bitmap);
                frameProcessor.setCameraFrameNum(img.cameraFrameNum);

                long startTime = System.currentTimeMillis();
                String resultString = frameProcessor.run();
                long endTime = System.currentTimeMillis();

                sendResult(isInner, img.coordinatorStartTime, frameNum, endTime - startTime, endTime - img.workerStartTime, resultString);
                workCount++;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private Bitmap uncompress(byte[] data) {
        InputStream is = new ByteArrayInputStream(data);
        BitmapFactory.Options ops = new BitmapFactory.Options();
        ops.inMutable = true;
        Bitmap bitmap = BitmapFactory.decodeStream(is, null, ops);
        try {
            is.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    public static void sendResult(boolean isInner, long coordinatorStartTime, int frameNum, long processTime, long totalTime, String resultString) {
        Message retMsg = Message.obtain();
        retMsg.obj = new WorkerResult(isInner, coordinatorStartTime, frameNum, processTime, totalTime, resultString);
        handler.sendMessage(retMsg);
    }
}
