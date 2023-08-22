package com.example.edgedashanalytics.util.connection;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.example.edgedashanalytics.util.log.TimeLog;
import com.example.edgedashanalytics.util.video.analysis.Image2;
import com.example.edgedashanalytics.util.worker.WorkerThread;

import java.util.ArrayList;
import java.util.HashMap;

public class Connection {
    private static final String TAG = "Connection";
    //private static ArrayList<Sender> senders = new ArrayList<>();
    private static Sender2 sender;
    public static long innerCount = 0, outerCount = 0, totalCount = 0, processed = 0, dropped = 0;
    public static int selectedCount = 0;
    public static long startTime;
    public static boolean isFinished = false;

    private static final long DELAY_TOO_LONG = 300;
    public static void runImageStreaming() {
        // Start streaming images
        // 1. Connect to the DashCam (a sender application on another Android phone)
        // 2. Connect to worker devices (with another EDA application)
        // Then this device becomes the central device that retrieves images from the DashCam
        // and hands images over to the workers

        connectToDashCam();
        connectToWorkers();

        // and then it should run endlessly until the user taps the stop button
    }

    private static void connectToDashCam() {
        Handler handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message inputMessage) {
                totalCount++;
                TimeLog.coordinator.start(totalCount + ""); // Distribute
                long startTime = System.currentTimeMillis();
                boolean isInner = (inputMessage.what == Receiver.IMAGE_INNER);

                long bestScore = Long.MAX_VALUE;
                int bestWorker = -1;
                for (int i = 0; i < sender.workers.size(); i++) {
                    Sender2.Worker w = sender.workers.get(i);
                    // Log.d(TAG, "Worker " + i + ": " + w.score);

                    long score = w.score;
                    if (score >= bestScore)
                        continue;

                    // realize something went wrong with this worker
                    /*if (s.delay > DELAY_TOO_LONG) {
                        s.delay -= 50; // so that it doesn't get stuck when the sender has no more frames to send or wait
                        continue;
                    }*/

                    bestScore = score;
                    bestWorker = i;
                }

                /*bestWorker = selectedCount % 3;
                bestScore = sender.workers.get(selectedCount % 3).score;
*/
                boolean useDropping = true;

                //Log.d(TAG, "bestScore = " + bestScore);

                if (bestWorker == -1 || bestScore >= 3) {
                    // Uncomment this to include dropped frames
                    // TimeLog.coordinator.finish(totalCount + "");
                    if (useDropping) {
                        TimeLog.coordinator.addEmpty(totalCount + "", 1); // No-ops
                        dropped++;
                        return;
                    }
                    bestWorker = 0; // If we don't use dropping, let the coordinator do the job
                }

                selectedCount++;

                Sender2.Worker worker = sender.workers.get(bestWorker);

                //sender.startTime.put(totalCount, System.currentTimeMillis());
                TimeLog.coordinator.setWorkerNum(totalCount + "", bestWorker);
                worker.score++; // so that it doesn't send multiple items repeatedly
                Handler senderHandler = sender.getHandler();
                while (senderHandler == null) {
                    Log.w(TAG, "senderHandler is still null!!");
                    try {
                        Thread.sleep(100);
                    } catch (Exception e) { e.printStackTrace(); }
                    senderHandler = sender.getHandler();
                }
                Message senderMessage = Message.obtain();
                senderMessage.what = 999;
                senderMessage.arg1 = bestWorker;
                senderMessage.obj = new Image2(isInner, /*isInner ? innerCount : outerCount*/ totalCount, (byte[]) inputMessage.obj);
                senderHandler.sendMessage(senderMessage);

                if (totalCount % 10 == 0) {
                    Log.i(TAG, String.format("Processed: %d/%d (%d%%)", processed, totalCount, processed * 100 / totalCount));
                    double fps = (double)processed / (System.currentTimeMillis() - startTime) * 1000;
                    Log.i(TAG, String.format("Throughput: %.1ffps (avg. %.1ffps per camera)", fps, fps / 2));
                }
            }
        };

        // Inner DashCam
        new Receiver(handler, Receiver.IMAGE_INNER, Receiver.PORT_INNER).start();

        // Outer DashCam
        new Receiver(handler, Receiver.IMAGE_OUTER, Receiver.PORT_OUTER).start();
    }

    public static void connectToWorkers() {
        // This device is the client side
        // Replace this part with actual finding process eventually

        HashMap<String, String> p = new HashMap<>();
        p.put("self", "127.0.0.1");
        p.put("lineage", "192.168.67.32");
        p.put("oneplus", "192.168.67.172");
        p.put("pixel6", "192.168.67.79");
        p.put("oppo", "192.168.67.230");
        p.put("pixel5", "192.168.67.145");
        p.put("lineage2", "192.168.67.72");

        HashMap<String, String> splab = new HashMap<>();

        splab.put("self", "127.0.0.1");
        splab.put("lineage", "192.168.0.103");
        splab.put("pixel5", "192.168.0.105");
        splab.put("oneplus", "192.168.0.104");
        splab.put("oppo", "192.168.0.106");

        //p = splab;


        String[] workerList = {"self", "lineage", "oppo"};

        /* int workerNum = 0;
        for (String name : workerList) {
            Sender sender = new Sender(p.get(name), workerNum);
            sender.start();
            senders.add(sender);
            workerNum++;
        }*/

        sender = new Sender2();
        for (String name : workerList) {
            sender.addWorker(p.get(name));
        }

        sender.start();

        /*// make oppo first, but make it selected last
        Sender temp = senders.get(1);
        senders.set(1, senders.get(2));
        senders.set(2, temp);*/
    }

    public static void workerStart(Context context) {
        WorkerThread workerThread = new WorkerThread(context);
        workerThread.start();
    }
}
