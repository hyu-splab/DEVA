package com.example.edgedashanalytics.advanced.coordinator;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.example.edgedashanalytics.advanced.common.TestConfig;
import com.example.edgedashanalytics.advanced.common.WorkerStatus;
import com.example.edgedashanalytics.advanced.test.VideoTest2;
import com.example.edgedashanalytics.page.main.MainActivity;
import com.example.edgedashanalytics.util.Constants;
import com.example.edgedashanalytics.advanced.common.TimeLog;
import com.example.edgedashanalytics.advanced.common.Image2;
import com.example.edgedashanalytics.advanced.worker.WorkerThread;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;

public class AdvancedMain {
    private static final String TAG = "AdvancedMain";
    public static Communicator communicator;
    public static int totalCount = 0;
    public static int selectedCount = 0;
    public static boolean isFinished = false;
    public static HashMap<String, String> s22, splab;

    private static EDACam innerCam, outerCam;
    private static Controller controller;
    private static final long CAMERA_ADJUSTMENT_PERIOD = 500;
    private static final long EXPERIMENT_DURATION = 3 * 60 * 1000;

    public static String[] workerNameList;

    public static TestConfig testConfig;
    private static Handler communicatorHandler = null;

    private static String innerCamIP, outerCamIP;

    public static void createVideoAnalysisData(Context context) {
        try {
            new Thread(() -> {
                VideoTest2.test("video.mov", "outer2.txt", context, false);
            }).start();
            return;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void advancedMain(Context context) {
        //if (true) {createVideoAnalysisData(context); return;}
        try {
            testConfig = TestConfig.readConfigs(context);

            makeBaseInnerResult(testConfig.innerResultFile);
            makeBaseOuterResult(testConfig.outerResultFile);

        } catch (Exception e) {
            e.printStackTrace();
        }

        workerStart();

        if (testConfig.isCoordinator) {
            long timeStart = System.currentTimeMillis();
            run(context);
        }
    }

    static int ri(StringTokenizer st) {
        return Integer.parseInt(st.nextToken());
    }

    static String rs(StringTokenizer st) {
        return st.nextToken();
    }

    private static void makeBaseInnerResult(String innerResultFile) throws Exception {
        FrameLogger.baseInnerResult = new InnerResult();
        File file = new File(innerResultFile);
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line;

        while ((line = br.readLine()) != null) {
            StringTokenizer st = new StringTokenizer(line, ",");
            int frameNum = ri(st);
            boolean isDistracted = (ri(st) == 1);
            FrameLogger.baseInnerResult.addResult(frameNum, isDistracted);
        }
        br.close();
    }
    private static void makeBaseOuterResult(String outerResultFile) throws Exception {
        FrameLogger.baseOuterResult = new OuterResult();
        File file = new File(outerResultFile);
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line;

        while ((line = br.readLine()) != null) {
            StringTokenizer st = new StringTokenizer(line, ",");
            int frameNum = ri(st);
            int numHazards = ri(st);
            List<String> hazards = new ArrayList<>();
            for (int i = 0; i < numHazards; i++) {
                hazards.add(rs(st));
            }
            FrameLogger.baseOuterResult.addResult(frameNum, hazards);
        }
        br.close();
    }

    public static void run(Context context) {
        // Start streaming images
        // 1. Connect to the DashCam (a sender application on another Android phone)
        // 2. Connect to worker devices (with another EDA application)
        // Then this device becomes the central device that retrieves images from the DashCam
        // and hands images over to the workers

        createDeviceList();

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                run2(context);
            }
        }, 5000);

        Log.v(TAG, "Starting experiment in 5s...");
    }

    public static void run2(Context context) {
        connectToWorkers();

        try {
            Thread.sleep(1000);
        } catch (Exception e) { e.printStackTrace(); }

        connectToDashCam();

        // Camera adjustment every fixed time
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                controller.adjustCamSettingsV2(communicator.workers, innerCam.camSettings, outerCam.camSettings);
                StatusLogger.log(innerCam, outerCam, communicator.workers);
            }
        }, CAMERA_ADJUSTMENT_PERIOD, CAMERA_ADJUSTMENT_PERIOD);

        // Finish experiment and restart
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                /*
                1. TODO: stop every process (is this needed to avoid crashes?)
                2. write logs
                3. send cameras restart message
                4. send every worker (including non-workers) restart message
                 */

                // 1
                isFinished = true;

                Log.v(TAG, "Stopping experiment");
                // 2
                StatusLogger.writeLogs(context, testConfig.testNum);
                FrameLogger.writeLogs(context, testConfig.testNum);

                Log.v(TAG, "Wrote logs");

                // 4
                controller.sendRestartMessages(innerCam, outerCam);

                Log.v(TAG, "Sent cameras restart message");

                // 3
                Message senderMessage = Message.obtain();
                senderMessage.arg1 = -1;
                communicatorHandler.sendMessage(senderMessage);

                Log.v(TAG, "Sent workers restart message");
            }
        }, EXPERIMENT_DURATION);
    }

    private static void connectToDashCam() {
        Handler handler = new Distributer(Looper.getMainLooper());
        // Inner DashCam
        innerCam = new EDACam(handler, innerCamIP, true);
        innerCam.start();

        Log.v(TAG, "innerCam created");

        // Outer DashCam
        outerCam = new EDACam(handler, outerCamIP, false);
        outerCam.start();

        Log.v(TAG, "outerCam created");

        controller = new Controller(innerCam, outerCam);
    }

    public static void connectToWorkers() {
        // This device is the client side
        // Replace this part with actual finding process eventually
        HashMap<String, String> p = s22;

        communicator = new Communicator();
        int workerNum = 0;
        HashSet<String> workerNames = new HashSet<>();
        for (String name : workerNameList) {
            workerNames.add(name);
            communicator.addWorker(workerNum++, p.get(name));
        }

        String[] allDevices = new String[]{"lineage2", "oppo", "oneplus", "pixel5"};

        for (String deviceName : allDevices) {
            if (!workerNames.contains(deviceName)) {
                communicator.addOther(p.get(deviceName));
            }
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

        innerCamIP = "192.168.118.159";
        outerCamIP = "192.168.118.79";
    }

    public static void workerStart() {
        WorkerThread workerThread = new WorkerThread();
        workerThread.start();
    }

    static class Distributer extends Handler {
        private static final int sequenceLength = 50; // tentative
        private List<Integer> sequence;
        private int sequenceIndex;

        public Distributer(Looper looper) {
            super(looper);
            sequence = new ArrayList<>();
            sequenceIndex = 0;
        }

        private void makeWorkerSequence() {
            int numWorker = communicator.workers.size();
            double[] workerPriority = new double[numWorker];

            List<Double> workerWeight = calculateWorkerWeight();

            // The weighted distribution algorithm
            sequence = new ArrayList<>();

            for (int i = 0; i < sequenceLength; i++) {
                double maxPriority = -1;
                int maxIndex = -1;
                for (int j = 0; j < numWorker; j++) {
                    workerPriority[j] += workerWeight.get(j);
                    if (workerPriority[j] > maxPriority) {
                        maxPriority = workerPriority[j];
                        maxIndex = j;
                    }
                }
                sequence.add(maxIndex);
                workerPriority[maxIndex] = 0; // WHY DID I FORGET THIS
            }
        }

        private List<Double> calculateWorkerWeight() {
            List<Double> weights = new ArrayList<>();
            for (int i = 0; i < communicator.workers.size(); i++) {
                EDAWorker w = communicator.workers.get(i);
                WorkerStatus status = w.status;

                weights.add(status.getPerformance());
            }

            return weights;
        }

        private int getNextWorker() {
            if (sequenceIndex == sequence.size()) {
                makeWorkerSequence();
                sequenceIndex = 0;
            }
            return sequence.get(sequenceIndex++);
        }

        @Override
        public void handleMessage(Message inputMessage) {
            totalCount++;
            TimeLog.coordinator.start(totalCount + ""); // Distribute
            boolean isInner = (inputMessage.what == Constants.IMAGE_INNER);

            int bestWorker = getNextWorker();

            TimeLog.coordinator.setWorkerNum(totalCount + "", bestWorker);
            selectedCount++;

            EDAWorker worker = communicator.workers.get(bestWorker);

            while (communicatorHandler == null) {
                Log.w(TAG, "communicatorHandler is still null!!");
                try {
                    Thread.sleep(100);
                } catch (Exception e) { e.printStackTrace(); }
                communicatorHandler = communicator.getHandler();
            }

            Message senderMessage = Message.obtain();
            senderMessage.what = 999;
            senderMessage.arg1 = bestWorker;
            senderMessage.obj = new Image2(isInner, totalCount, inputMessage.arg1, (byte[]) inputMessage.obj);
            //TimeLog.coordinator.add(totalCount + ""); // message to network thread
            communicatorHandler.sendMessage(senderMessage);
        }
    }
}
