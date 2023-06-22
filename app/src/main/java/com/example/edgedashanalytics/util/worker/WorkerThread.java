package com.example.edgedashanalytics.util.worker;

import static com.example.edgedashanalytics.util.connection.Receiver.IMAGE_INNER;
import static com.example.edgedashanalytics.util.connection.Receiver.IMAGE_OUTER;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.example.edgedashanalytics.util.connection.WorkerServer;
import com.example.edgedashanalytics.util.log.TimeLog;
import com.example.edgedashanalytics.util.video.analysis.Image2;
import com.example.edgedashanalytics.util.video.analysis.Result2;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class WorkerThread extends Thread {
    private static final String TAG = "WorkerThread";
    private Handler handler;
    private Context context;
    private static WorkerServer workerServer = null;
    public WorkerThread(Context context) {
        handler = null;
        this.context = context;
    }

    @Override
    public void run() {
        Looper.prepare();
        handler = new Handler(Looper.myLooper()) {
            FrameProcessor frameProcessor = null;
            @Override
            public void handleMessage(Message msg) {
                Image2 img = (Image2) msg.obj;
                TimeLog.worker.add(img.frameNumber + ""); // Notify Availability
                sendScore(0L);
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
                Handler retHandler = workerServer.getHandler();
                while (retHandler == null) {
                    Log.w(TAG, "retHandler is not ready!!");
                    try {
                        Thread.sleep(100);
                    } catch (Exception e) { e.printStackTrace(); }
                    retHandler = workerServer.getHandler();
                }

                TimeLog.worker.add(img.frameNumber + ""); // Start Sending Result
                sendResult(isInner, frameNumber, resultString);
            }
        };
        workerServer = new WorkerServer(handler);
        workerServer.run();
        Looper.loop();
    }

    private static Handler getHandler() {
        Handler handler = workerServer.getHandler();
        while (handler == null) {
            Log.w(TAG, "workerServer handler is not ready!!");
            try {
                Thread.sleep(100);
            } catch (Exception e) { e.printStackTrace(); }
            handler = workerServer.getHandler();
        }
        return handler;
    }

    public static void sendScore(long score) {
        Message scoreMsg = Message.obtain();
        scoreMsg.what = 998;
        scoreMsg.obj = score;
        getHandler().sendMessage(scoreMsg);
    }

    public static void sendResult(boolean isInner, long frameNumber, String resultString) {
        Message retMsg = Message.obtain();
        retMsg.what = 999;
        retMsg.obj = new Result2(isInner, frameNumber, resultString);
        getHandler().sendMessage(retMsg);
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
}
