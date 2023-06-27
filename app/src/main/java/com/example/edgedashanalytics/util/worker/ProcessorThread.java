package com.example.edgedashanalytics.util.worker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Message;

import com.example.edgedashanalytics.util.log.TimeLog;
import com.example.edgedashanalytics.util.video.analysis.Image2;
import com.example.edgedashanalytics.util.video.analysis.Result2;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ProcessorThread extends Thread {
    static public ArrayBlockingQueue<Image2> queue = new ArrayBlockingQueue<>(5);
    static public Context context;
    static public Handler handler;

    public int tid = 0;
    public int workCount = 0;

    @Override
    public void run() {
        FrameProcessor frameProcessor = null;
        while (true) {
            try {
                Image2 img = queue.poll();
                if (img == null) {
                    Thread.sleep(10);
                    continue;
                }
                TimeLog.worker.add(img.frameNumber + ""); // Uncompress
                Bitmap bitmap = uncompress(img.data);
                long frameNumber = img.frameNumber;
                boolean isInner = img.isInner;
                if (isInner)
                    frameProcessor = new InnerProcessor(bitmap, context);
                else
                    frameProcessor = new OuterProcessor(bitmap, context);

                TimeLog.worker.add(img.frameNumber + ""); // Run FrameProcessor
                String resultString = frameProcessor.run();

                sendResult(isInner, frameNumber, resultString);
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

    public static void sendResult(boolean isInner, long frameNumber, String resultString) {
        Message retMsg = Message.obtain();
        retMsg.obj = new Result2(isInner, frameNumber, resultString);
        handler.sendMessage(retMsg);
    }
}
