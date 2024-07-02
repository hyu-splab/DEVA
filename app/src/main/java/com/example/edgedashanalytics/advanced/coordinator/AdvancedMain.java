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
    public static HashMap<String, String> s22, splab, p6;
    public static String[] allDevices;
    public static boolean connectionChanged = false;

    public static EDACam innerCam, outerCam;
    public static Controller controller;
    public static long EXPERIMENT_DURATION = (long)1e9; // should be overwritten by testconfig.txt

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
                        new Size(1120, 630),
                        new Size(1200, 675),
                        new Size(1280, 720)
                };

                final Integer[] qualities = {
                        20, 30, 40, 50, 60, 70, 80, 90, 100
                };
                VideoTest.testOuterAnalysisAccuracy(context, "video.mov", Arrays.asList(qualities), Arrays.asList(resolutions), 0, 200);
            }).start();
            return;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void calculateDataSize(Context context) {
        try {
            new Thread(() -> {
                VideoTest.calculateDataSizes(context, "video.mov", "size.txt");
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
        try {
            Thread.sleep(500);
            testConfig = TestConfig.readConfigs(context);

            makeBaseInnerResult(testConfig.innerResultFile);
            makeBaseOuterResult(testConfig.outerResultFile);
        } catch (Exception e) {
            e.printStackTrace();
        }

        workerStart();

        // If the test is running but it's not a participant, do some warmup while it's done
        if (/*true || */(testConfig.testNum != -1 && !testConfig.isWorker)) { testAnalysisSpeed(context, 100, 99999999); return; }

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
                DistributionLogger.writeLogs(context, testConfig.testNum);
                //DeviceLogger.writeLogs(context, testConfig.testNum);

                Log.v(TAG, "Wrote logs");

                // 4
                controller.sendRestartMessages(innerCam, outerCam);

                Log.v(TAG, "Sent cameras restart message");

                // 3
                try {
                    Communicator.msgQueue.put(new CommunicatorMessage(-1));
                } catch (Exception e) {
                    e.printStackTrace();
                }

                Log.v(TAG, "Sent workers restart message");
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
        HashMap<String, String> p = p6;

        communicator = new Communicator();
        int workerNum = 0;
        HashSet<String> workerNames = new HashSet<>();
        for (String name : workerNameList) {
            workerNames.add(name);
            communicator.addWorker(workerNum++, p.get(name));
        }

        for (String deviceName : allDevices) {
            if (!workerNames.contains(deviceName)) {
                communicator.addOther(p.get(deviceName));
            }
        }

        communicator.start();
    }

    public static void createDeviceList() {
        {
            s22 = new HashMap<>();
            s22.put("self", "127.0.0.1");
            s22.put("lineage", "192.168.118.32");
            s22.put("oneplus", "192.168.118.172");
            s22.put("pixel6", "192.168.118.79");
            s22.put("oppo", "192.168.118.230");
            s22.put("pixel5", "192.168.118.145");
            s22.put("lineage2", "192.168.118.72");
            s22.put("s22", "192.168.118.159");
        } // s22
        {
            splab = new HashMap<>();
            splab.put("self", "127.0.0.1");
            splab.put("lineage", "192.168.0.105");
            splab.put("oneplus", "192.168.0.106");
            splab.put("pixel6", "192.168.0.103");
            splab.put("oppo", "192.168.0.104");
            splab.put("pixel5", "192.168.0.101");
            splab.put("lineage2", "192.168.0.107");
            splab.put("s22", "192.168.0.108");
        } // splab

        final int p6IP = 228;
        {
            p6 = new HashMap<>();
            p6.put("self", "127.0.0.1");
            p6.put("lineage", "192.168." + p6IP + ".163");
            p6.put("oneplus", "192.168." + p6IP + ".191");
            p6.put("pixel6", "192.168." + p6IP + ".85");
            p6.put("oppo", "192.168." + p6IP + ".20");
            p6.put("pixel5", "192.168." + p6IP + ".201");
            p6.put("lineage2", "192.168." + p6IP + ".213");
            p6.put("s22", "192.168." + p6IP + ".6");
        } // pixel6

        int exp = -1;
        switch (exp) {
            case 1:
                allDevices = new String[]{"oneplus", "oppo", "s22", "lineage2"};
                innerCamIP = p6.get("lineage");
                outerCamIP = p6.get("pixel5");
                break;
            case 2:
            case 4:
                allDevices = new String[]{"lineage2", "lineage", "pixel5"};
                innerCamIP = p6.get("oneplus");
                outerCamIP = p6.get("oppo");
                break;
            case 3:
                allDevices = new String[]{"oneplus", "lineage2", "pixel5"};
                innerCamIP = p6.get("lineage");
                outerCamIP = p6.get("oppo");
                break;
            case 7:
                allDevices = new String[]{"oneplus", "oppo", "s22"};
                innerCamIP = p6.get("lineage");
                outerCamIP = p6.get("pixel5");
                break;
            default:
                allDevices = new String[]{"lineage2", "pixel5", "oneplus", "oppo"};
                innerCamIP = p6.get("lineage");
                outerCamIP = p6.get("s22");
                break;
        }
    }

    public static void workerStart() {
        WorkerThread workerThread = new WorkerThread();
        workerThread.start();
    }

    static class Distributer {
        private static final int sequenceLength = 20; // tentative
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

            ArrayList<Double> workerWeight = calculateWorkerWeightV2();
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
                    WorkerStatus status = w.status;

                    double weight = status.getPerformance();
                    if (i == 0)
                        weight *= 0.8;
                    weight *= Math.max(0.5, 1.0 - status.latestQueueSize * 0.1);
                    weights.add(weight);
                }
                else {
                    weights.add(0.0);
                }
            }

            return weights;
        }

        private ArrayList<Double> calculateWorkerWeightV2() {
            ArrayList<Double> weights = new ArrayList<>();
            for (int i = 0; i < communicator.workers.size(); i++) {
                EDAWorker w = communicator.workers.get(i);
                if (w.status.isConnected) {
                    WorkerStatus status = w.status;

                    double avg = status.getAverage();
                    double weight = 1.0 / (avg * (status.latestQueueSize + 1));
                    weights.add(weight);
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
