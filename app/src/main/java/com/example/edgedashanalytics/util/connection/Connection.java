package com.example.edgedashanalytics.util.connection;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.Size;

import com.example.edgedashanalytics.page.main.MainActivity;
import com.example.edgedashanalytics.util.Constants;
import com.example.edgedashanalytics.util.log.TimeLog;
import com.example.edgedashanalytics.util.video.analysis.Image2;
import com.example.edgedashanalytics.util.worker.WorkerThread;

import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class Connection {
    private static final String TAG = "Connection";
    //private static ArrayList<Sender> senders = new ArrayList<>();
    private static Communicator sender;
    public static long innerCount = 0, outerCount = 0, totalCount = 0, processed = 0, dropped = 0;
    public static int selectedCount = 0;
    public static long startTime;
    public static boolean isFinished = false;

    private static final long DELAY_TOO_LONG = 300;

    private static Receiver innerReceiver, outerReceiver;

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

                if (inputMessage.what == 999) { // Check status
                    ObjectOutputStream innerStream, outerStream;
                    innerStream = innerReceiver.outstream;
                    outerStream = outerReceiver.outstream;




                    // TODO: Insert good adaptive algorithm here
                    if (innerStream != null) {
                        Receiver.sendSettings(innerStream, new Size(1280, 720), 50, 30);
                    }

                    if (outerStream != null) {
                        Receiver.sendSettings(outerStream, new Size(1280, 720), 50, 30);
                    }

                    return;
                }

                totalCount++;
                TimeLog.coordinator.start(totalCount + ""); // Distribute
                boolean isInner = (inputMessage.what == Constants.IMAGE_INNER);

                double bestScore = 1e9;
                int bestWorker = -1;
                for (int i = 0; i < sender.workers.size(); i++) {
                    Communicator.Worker w = sender.workers.get(i);

                    double score = 0; // TODO: Insert good formula here
                    if (score >= bestScore)
                        continue;

                    bestScore = score;
                    bestWorker = i;
                }

                boolean useDropping = false;

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

                Communicator.Worker worker = sender.workers.get(bestWorker);

                //sender.startTime.put(totalCount, System.currentTimeMillis());
                TimeLog.coordinator.setWorkerNum(totalCount + "", bestWorker);
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
                senderMessage.arg2 = 1;
                senderMessage.obj = new Image2(isInner, /*isInner ? innerCount : outerCount*/ totalCount, inputMessage.arg1, (byte[]) inputMessage.obj);
                TimeLog.coordinator.add(totalCount + ""); // message to network thread

                senderHandler.sendMessage(senderMessage);

                if (totalCount % 10 == 0) {
                    Log.i(TAG, String.format("Processed: %d/%d (%d%%)", processed, totalCount, processed * 100 / totalCount));
                    double fps = (double)processed / (System.currentTimeMillis() - startTime) * 1000;
                    Log.i(TAG, String.format("Throughput: %.1ffps (avg. %.1ffps per camera)", fps, fps / 2));
                }
            }
        };

        // Inner DashCam
        innerReceiver = new Receiver(handler, Constants.IMAGE_INNER, Constants.PORT_INNER);
        innerReceiver.start();

        // Outer DashCam
        outerReceiver = new Receiver(handler, Constants.IMAGE_OUTER, Constants.PORT_OUTER);
        outerReceiver.start();

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                Message msg = Message.obtain();
                msg.what = 999;
                handler.sendMessage(msg);
            }
        }, 500);
    }

    public static void connectToWorkers() {
        // This device is the client side
        // Replace this part with actual finding process eventually

        HashMap<String, String> p = new HashMap<>();
        p.put("self", "127.0.0.1");
        p.put("lineage", "192.168.118.32");
        p.put("oneplus", "192.168.118.172");
        p.put("pixel6", "192.168.118.79");
        p.put("oppo", "192.168.118.230");
        p.put("pixel5", "192.168.118.145");
        p.put("lineage2", "192.168.118.72");

        HashMap<String, String> splab = new HashMap<>();

        splab.put("self", "127.0.0.1");
        splab.put("lineage", "192.168.0.103");
        splab.put("pixel5", "192.168.0.106");
        splab.put("oneplus", "192.168.0.107");
        splab.put("oppo", "192.168.0.105");

        //p = splab;

        String[] workerList = {"self"};

        /* int workerNum = 0;
        for (String name : workerList) {
            Sender sender = new Sender(p.get(name), workerNum);
            sender.start();
            senders.add(sender);
            workerNum++;
        }*/

        sender = new Communicator();
        for (String name : workerList) {
            sender.addWorker(p.get(name));
        }

        sender.start();

        /*// make oppo first, but make it selected last
        Sender temp = senders.get(1);
        senders.set(1, senders.get(2));
        senders.set(2, temp);*/
    }

    public static void workerStart() {
        WorkerThread workerThread = new WorkerThread();
        workerThread.start();
    }
}
