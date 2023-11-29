package com.example.edgedashanalytics.advanced.coordinator;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.example.edgedashanalytics.advanced.common.WorkerStatus;
import com.example.edgedashanalytics.util.Constants;
import com.example.edgedashanalytics.advanced.common.TimeLog;
import com.example.edgedashanalytics.advanced.common.Image2;
import com.example.edgedashanalytics.advanced.worker.WorkerThread;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class AdvancedMain {
    private static final String TAG = "AdvancedMain";
    //private static ArrayList<Sender> senders = new ArrayList<>();
    private static Communicator communicator;
    public static int innerCount = 0, outerCount = 0, totalCount = 0, processed = 0, dropped = 0;
    public static int selectedCount = 0;
    public static long startTime;
    public static boolean isFinished = false;
    public static HashMap<String, String> s22, splab;

    private static EDACam innerCam, outerCam;
    private static Controller controller;

    public static void runImageStreaming() {
        // Start streaming images
        // 1. Connect to the DashCam (a sender application on another Android phone)
        // 2. Connect to worker devices (with another EDA application)
        // Then this device becomes the central device that retrieves images from the DashCam
        // and hands images over to the workers

        connectToDashCam();
        connectToWorkers();

        // and then it should run endlessly until the user taps the stop button

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                controller.adjustCamSettings(communicator.workers, innerCam.camSettings, outerCam.camSettings);
                StatusLogger.log(innerCam, outerCam, communicator.workers);
            }
        }, 1000, 2000);
    }

    private static void connectToDashCam() {
        Handler handler = new Distributer(Looper.getMainLooper());
        // Inner DashCam
        innerCam = new EDACam(handler, true);
        innerCam.start();

        // Outer DashCam
        outerCam = new EDACam(handler, false);
        outerCam.start();

        controller = new Controller(innerCam, outerCam);
    }

    public static void connectToWorkers() {
        // This device is the client side
        // Replace this part with actual finding process eventually
        createDeviceList();
        HashMap<String, String> p = s22;

        String[] workerList = {"self"};

        communicator = new Communicator();
        int workerNum = 0;
        for (String name : workerList) {
            communicator.addWorker(workerNum++, p.get(name));
        }

        communicator.start();
    }

    public static void createDeviceList() {
        s22 = new HashMap<>();
        s22.put("self", "127.0.0.1");
        s22.put("lineage", "192.168.118.32");
        s22.put("oneplus", "192.168.118.172");
        s22.put("pixel6", "192.168.118.79");
        s22.put("oppo", "192.168.118.230");
        s22.put("pixel5", "192.168.118.145");
        s22.put("lineage2", "192.168.118.72");

        splab = new HashMap<>();

        splab.put("self", "127.0.0.1");
        splab.put("lineage", "192.168.0.103");
        splab.put("pixel5", "192.168.0.106");
        splab.put("oneplus", "192.168.0.107");
        splab.put("oppo", "192.168.0.105");

    }

    public static void workerStart() {
        WorkerThread workerThread = new WorkerThread();
        workerThread.start();
    }

    static class Distributer extends Handler {
        private static final double SCORE_THRESHOLD = 300;

        public Distributer(Looper looper) {
            super(looper);
        }

        private List<Integer> sequence;
        private List<Integer> workerWeight;
        private int sequenceLength;

        private void makeWorkerSequence() {
            int numWorker = communicator.workers.size();
            int[] workerPriority = new int[numWorker];

            // The weighted round robin scheduling algorithm
            sequence = new ArrayList<>();
            for (int i = 0; i < sequenceLength; i++) {
                int maxPriority = 0;
                int maxIndex = -1;
                for (int j = 0; j < numWorker; j++) {
                    workerPriority[j] += workerWeight.get(j);
                    if (workerPriority[j] > maxPriority) {
                        maxPriority = workerPriority[j];
                        maxIndex = j;
                    }
                }
                sequence.add(maxIndex);
            }
        }

        private void calculateWorkerWeight() {
            // TODO: Using worker status, calculate worker weights

        }

        private int findBestWorker(boolean isInner) {
            double bestScore = Double.MAX_VALUE;
            int bestWorker = -1;

            for (int i = 0; i < communicator.workers.size(); i++) {
                EDAWorker w = communicator.workers.get(i);

                WorkerStatus status = w.status;

                double waitTime;

                if (status.innerWaiting + status.outerWaiting <= 1) {
                    waitTime = 0;
                }
                else {
                    waitTime = ((status.innerWaiting * status.innerProcessTime())
                            + (status.outerWaiting * status.outerProcessTime())) / 2;
                }

                double queueTime = Math.max(waitTime - status.networkTime, 0.0);
                double score = status.networkTime + queueTime
                        + (isInner ? status.innerProcessTime() : status.outerProcessTime());

                //Log.d(TAG, "" + i + ": " + waitTime + " " + status.networkTime + " " + queueTime + " " + score);

                if (score < bestScore) {
                    bestScore = score;
                    bestWorker = i;
                }
            }

            if (bestScore > SCORE_THRESHOLD) {
                bestWorker = -1;
                //Log.d(TAG, "bestScore is " + bestScore + ", dropping");
            }

            return bestWorker;
        }

        @Override
        public void handleMessage(Message inputMessage) {
            totalCount++;
            TimeLog.coordinator.start(totalCount + ""); // Distribute
            boolean isInner = (inputMessage.what == Constants.IMAGE_INNER);

            int bestWorker = findBestWorker(isInner);
            if (bestWorker == -1) { // Dropping
                return;
            }

            TimeLog.coordinator.setWorkerNum(totalCount + "", bestWorker);
            selectedCount++;

            EDAWorker worker = communicator.workers.get(bestWorker);

            Handler senderHandler = communicator.getHandler();
            while (senderHandler == null) {
                Log.w(TAG, "senderHandler is still null!!");
                try {
                    Thread.sleep(100);
                } catch (Exception e) { e.printStackTrace(); }
                senderHandler = communicator.getHandler();
            }

            Message senderMessage = Message.obtain();
            senderMessage.what = 999;
            senderMessage.arg1 = bestWorker;
            senderMessage.obj = new Image2(isInner, totalCount, inputMessage.arg1, (byte[]) inputMessage.obj);
            TimeLog.coordinator.add(totalCount + ""); // message to network thread
            senderHandler.sendMessage(senderMessage);

            /* if (totalCount % 10 == 0) {
                Log.i(TAG, String.format("Processed: %d/%d (%d%%)", processed, totalCount, processed * 100 / totalCount));
                double fps = (double)processed / (System.currentTimeMillis() - startTime) * 1000;
                Log.i(TAG, String.format("Throughput: %.1ffps (avg. %.1ffps per camera)", fps, fps / 2));
            } */
        }
    }
}
