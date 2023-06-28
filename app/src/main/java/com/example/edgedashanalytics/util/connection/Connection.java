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
    private static ArrayList<Sender> senders = new ArrayList<>();
    public static long innerCount = 0, outerCount = 0, totalCount = 0, processed = 0, dropped = 0;
    public static long startTime;
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
                boolean isInner = (inputMessage.what == Receiver.IMAGE_INNER);
                if (isInner)
                    innerCount++;
                else
                    outerCount++;

                Sender sender = null;
                long bestScore = Long.MAX_VALUE;
                for (int i = 0; i < senders.size(); i++) {
                    Sender s = senders.get(i);
                    //Log.d(TAG, "sender " + i + ": " + s.getScore());
                    long score = s.getScore();
                    if (score >= bestScore)
                        continue;
                    bestScore = score;
                    sender = s;
                }

                boolean useDropping = true;

                //Log.d(TAG, "bestScore = " + bestScore);

                if (sender == null || bestScore >= 3) {
                    // Uncomment this to include dropped frames
                    // TimeLog.coordinator.finish(totalCount + "");
                    if (useDropping) {
                        TimeLog.coordinator.addEmpty(totalCount + "", 1); // No-ops
                        dropped++;
                        return;
                    }
                    sender = senders.get(0); // If we don't use dropping, let the coordinator do the job
                }

                sender.setScore(sender.getScore() + 1); // so that it doesn't send multiple items repeatedly
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
        Receiver.run(handler, Receiver.IMAGE_INNER, Receiver.PORT_INNER);

        // Outer DashCam
        Receiver.run(handler, Receiver.IMAGE_OUTER, Receiver.PORT_OUTER);
    }

    public static void connectToWorkers() {
        // This device is the client side
        // Replace this part with actual finding process eventually

        HashMap<String, String> p = new HashMap<>();
        p.put("self", "127.0.0.1");
        p.put("lineage", "192.168.68.32");
        p.put("oneplus", "192.168.68.172");
        p.put("pixel6", "192.168.68.79");
        p.put("oppo", "192.168.68.230");
        p.put("pixel5", "192.168.68.145");
        p.put("lineage2", "192.168.68.72");

        String[] workerList = {"self"};
        for (String name : workerList) {
            Sender sender = new Sender(p.get(name));
            sender.run();
            senders.add(sender);
        }
    }

    public static void workerStart(Context context) {
        WorkerThread workerThread = new WorkerThread(context);
        workerThread.start();
    }
}
