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
    private Context context;
    private static WorkerServer workerServer = null;
    public static final int N_THREAD = 2;
    public WorkerThread(Context context) {
        this.context = context;
    }

    @Override
    public void run() {
        ProcessorThread.context = context;
        for (int i = 0; i < N_THREAD; i++)
            new ProcessorThread().start();
        workerServer = new WorkerServer();
        workerServer.run(); // note: this is not Thread.run()

        ProcessorThread.handler = getHandler();
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
}
