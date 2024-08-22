package com.example.edgedashanalytics.advanced.coordinator;

import static com.example.edgedashanalytics.advanced.worker.WorkerThread.N_THREAD;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.Size;

import com.example.edgedashanalytics.advanced.common.TestConfig;
import com.example.edgedashanalytics.advanced.common.WorkerStatus;
import com.example.edgedashanalytics.advanced.test.VideoTest;
import com.example.edgedashanalytics.advanced.test.VideoTest2;
import com.example.edgedashanalytics.util.Constants;
import com.example.edgedashanalytics.advanced.common.FrameData;
import com.example.edgedashanalytics.advanced.worker.WorkerThread;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;

public class AdvancedMain {
    private static final String TAG = "AdvancedMain";
    public static Communicator communicator;
    public static boolean isFinished = false;
    public static HashMap<String, String> hotSpot;
    public static String[] allDevices;
    public static boolean connectionChanged = false;

    public static EDACam innerCam, outerCam;
    public static Controller controller;
    public static long EXPERIMENT_DURATION = (long)1e9; // should be overwritten by testconfig.txt
    public static long REAL_EXPERIMENT_DURATION = (long)1e9;

    public static String[] workerNameList;
    public static ArrayList<Integer>[] connectionTimestamps;

    public static TestConfig testConfig;

    private static String innerCamIP, outerCamIP;
    public static boolean isBusy;
    public static Distributer distributer;

    public static void createVideoAnalysisData(Context context) {
        try {
            new Thread(() -> {
                VideoTest2.test("video2.mp4", "inner_lightning.txt", context, true);
                VideoTest2.test("video.mov", "outer_mobilenet_v1.txt", context, false);
            }).start();
            return;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void testInnerVideo(Context context) {
        try {
            new Thread(() -> {
                final Size[] resolutions = {
                        new Size(1280, 720)
                };

                final Integer[] qualities = {
                        20, 30, 40, 50, 60, 70, 80, 90, 100
                };
                VideoTest.testInnerAnalysisAccuracy(context, "inner.mp4", Arrays.asList(qualities), Arrays.asList(resolutions));
            }).start();
            return;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void testOuterVideo(Context context) {
        try {
            new Thread(() -> {
                final Size[] resolutions = {
                        /*new Size(640, 360),
                        new Size(720, 405),
                        new Size(800, 450)*/
                        /*new Size(880, 495),
                        new Size(960, 540),
                        new Size(1040, 585),*/
                        //new Size(1120, 630),
                        //new Size(1200, 675),
                        new Size(1280, 720)
                };

                final Integer[] qualities = {
                        20, 30, 40, 50, 60, 70, 80, 90, 100
                };
                VideoTest.testOuterAnalysisAccuracy(context, "outer.mov", Arrays.asList(qualities), Arrays.asList(resolutions));
            }).start();
            return;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void calculateDataSize(Context context) {
        try {
            new Thread(() -> {
                VideoTest.calculateDataSizes(context, "inner.mp4", "inner-size.txt");
            }).start();
            return;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void testAnalysisSpeed(Context context, int numFrames, int numTest) {
        try {
            new Thread(() -> {
                VideoTest.testAnalysisSpeed(context, numFrames, new Size(1280, 720), 100, numTest);
            }).start();;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void advancedMain(Context context) {

        if (false) {
            testInnerVideo(context);
            //testOuterVideo(context);
            //calculateDataSize(context);
            return;
        }



        try {
            Thread.sleep(500);
            testConfig = TestConfig.readConfigs(context);

            makeBaseInnerResult(context.getExternalFilesDir(null) + "/inner_lightning.txt");
            makeBaseOuterResult(context.getExternalFilesDir(null) + "/outer_mobilenet_v1.txt");
        } catch (Exception e) {
            e.printStackTrace();
        }

        workerStart();

        // If the test is running but it's not a participant, do some warmup while it's done
        //if (/*true || */(testConfig.testNum != -1 && !testConfig.isWorker)) { testAnalysisSpeed(context, 100, 99999999); return; }

        if (testConfig.isCoordinator) {
            run(context);
        }
        else {
            controller = new Controller(null, null);
            controller.start();
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
            FrameLogger.baseOuterResult.addResult(frameNum, hazards, 0, 0);
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
        }, 7000);

        Log.v(TAG, "Starting experiment in 7s...");
    }

    public static void run2(Context context) {
        connectToWorkers();

        try {
            Thread.sleep(1000);
        } catch (Exception e) { e.printStackTrace(); }

        connectToDashCam();

        controller.workers = communicator.workers;
        controller.innerCamParameter = innerCam.camParameter;
        controller.outerCamParameter = outerCam.camParameter;

        controller.start();

        // Experiment finish
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                // TODO: Is this option needed?
                isFinished = true;

                // Write logs
                StatusLogger.writeLogs(context, testConfig.testNum);
                FrameLogger.writeLogs(context, testConfig.testNum);
                DistributionLogger.writeLogs(context, testConfig.testNum);

                // Tell dashcams that the experiment is finished
                controller.sendRestartMessages(innerCam, outerCam);

                // Send workers restart message
                try {
                    Communicator.msgQueue.put(new CommunicatorMessage(-1));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, EXPERIMENT_DURATION);
    }

    private static void connectToDashCam() {
        distributer = new Distributer();
        // Inner DashCam
        innerCam = new EDACam(innerCamIP, true);
        innerCam.start();

        // Outer DashCam
        outerCam = new EDACam(outerCamIP, false);
        outerCam.start();

        controller = new Controller(innerCam, outerCam);
    }

    public static void connectToWorkers() {
        // This device is the client side
        // Replace this part with actual finding process eventually

        communicator = new Communicator();
        int workerNum = 0;
        HashSet<String> workerNames = new HashSet<>();
        for (String name : workerNameList) {
            workerNames.add(name);
            communicator.addWorker(workerNum++, hotSpot.get(name));
        }

        for (String deviceName : allDevices) {
            if (!workerNames.contains(deviceName)) {
                communicator.addOther(hotSpot.get(deviceName));
            }
        }

        communicator.start();
    }

    public static void createDeviceList() {
        IPLists.createIPList();
        hotSpot = IPLists.getByName(testConfig.myName);
        if (hotSpot == null) {
            throw new RuntimeException("Unregistered hotspot device: " + testConfig.myName);
        }
        innerCamIP = hotSpot.get(testConfig.innerCamName);
        outerCamIP = hotSpot.get(testConfig.outerCamName);

        allDevices = new String[]{};
    }

    public static void workerStart() {
        WorkerThread workerThread = new WorkerThread();
        workerThread.start();
    }

    static class Distributer {
        private static final int sequenceLength = 10;
        private ArrayList<Integer> innerSequence, outerSequence;
        private int innerSequenceIndex, outerSequenceIndex;

        public Distributer() {
            innerSequence = new ArrayList<>();
            outerSequence = new ArrayList<>();
            innerSequenceIndex = outerSequenceIndex = 0;
        }

        private ArrayList<Integer> makeWorkerSequence() {
            int numWorker = communicator.workers.size();
            double[] workerPriority = new double[numWorker];

            ArrayList<Double> workerWeight = calculateWorkerWeight();
            double weightSum = 0;
            for (Double d : workerWeight)
                weightSum += d;

            // The weighted distribution algorithm
            ArrayList<Integer> sequence = new ArrayList<>();

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
                workerPriority[maxIndex] -= weightSum;
            }

            DistributionLogger.addLog(workerWeight, sequence);

            return sequence;
        }

        private ArrayList<Double> calculateWorkerWeight() {
            ArrayList<Double> weights = new ArrayList<>();
            for (int i = 0; i < communicator.workers.size(); i++) {
                EDAWorker w = communicator.workers.get(i);
                if (w.status.isConnected) {
                    weights.add(w.status.getWeight());
                }
                else {
                    weights.add(0.0);
                }
            }

            return weights;
        }

        public int getNextWorker(boolean isInner) {
            if (Communicator.availableWorkers == 0) {
                Log.v(TAG, "No available workers!");
                return -2;
            }
            if (connectionChanged
                    || (isInner && innerSequenceIndex == innerSequence.size())
                    || (!isInner && outerSequenceIndex == outerSequence.size())) {
                if (isInner || connectionChanged) {
                    innerSequence = makeWorkerSequence();
                    innerSequenceIndex = 0;
                }
                if (!isInner || connectionChanged) {
                    outerSequence = makeWorkerSequence();
                    outerSequenceIndex = 0;
                }
                connectionChanged = false;
            }
            return (isInner ? innerSequence.get(innerSequenceIndex++) : outerSequence.get(outerSequenceIndex++));
        }
    }
}
