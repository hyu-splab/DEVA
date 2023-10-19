package com.example.edgedashanalytics.advanced.worker;

import android.os.Handler;

import com.example.edgedashanalytics.advanced.coordinator.WorkerServer;

public class WorkerThread extends Thread {
    private static final String TAG = "WorkerThread";
    private static WorkerServer workerServer = null;
    public static final int N_THREAD = 1;

    public static ProcessorThread[] pt;
    public WorkerThread() {
    }

    @Override
    public void run() {
        pt = new ProcessorThread[N_THREAD];
        for (int i = 0; i < N_THREAD; i++) {
            pt[i] = new ProcessorThread();
            pt[i].tid = i;
            pt[i].start();
        }
        workerServer = new WorkerServer();
        workerServer.run(); // note: this is not Thread.run()

        ProcessorThread.handler = getHandler();
    }

    private static Handler getHandler() {
        Handler handler = workerServer.getHandler();
        while (handler == null) {
            //Log.w(TAG, "workerServer handler is not ready!!");
            try {
                Thread.sleep(100);
            } catch (Exception e) { e.printStackTrace(); }
            handler = workerServer.getHandler();
        }
        return handler;
    }
}
