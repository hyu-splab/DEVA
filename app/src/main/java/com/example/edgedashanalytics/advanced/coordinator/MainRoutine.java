package com.example.edgedashanalytics.advanced.coordinator;

import android.content.Context;
import android.util.Log;

import com.example.edgedashanalytics.advanced.common.TestConfig;
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

public class MainRoutine {
    public static final boolean EXPERIMENTING = false;
    private static final String TAG = "MainRoutine";
    public static Communicator communicator;
    public static boolean connectionChanged = false;

    public static EDACam innerCam, outerCam;
    public static Controller controller;
    public static Distributer distributer;


    // All experiment-specific things go here
    public static class Experiment {
        public static boolean E_isFinished = false;
        public static HashMap<String, String> E_hotSpot;
        public static String[] E_allDevices;
        public static long E_EXPERIMENT_DURATION = (long)1e9; // should be overwritten by testconfig.txt
        public static long E_REAL_EXPERIMENT_DURATION = (long)1e9;

        public static String[] E_workerNameList;
        public static ArrayList<Integer>[] E_connectionTimestamps;

        public static TestConfig E_testConfig;

        private static String E_innerCamIP, E_outerCamIP;
        public static boolean E_isBusy;
        public static void experimentMain(Context context) {

            if (EXPERIMENTING) {
                try {
                    Thread.sleep(500);
                    E_testConfig = TestConfig.readConfigs(context);

                    makeBaseInnerResult(context.getExternalFilesDir(null) + "/inner_lightning.txt");
                    makeBaseOuterResult(context.getExternalFilesDir(null) + "/outer_mobilenet_v1.txt");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            workerStart();

            if (E_testConfig.isCoordinator) {
                runExperiment(context);
            } else {
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

        public static void runExperiment(Context context) {
            // A preprocessor for running experiments

            createDeviceList();

            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    doRunExperiment(context);
                }
            }, 7000);
        }

        public static void doRunExperiment(Context context) {
            connectToWorkers();

            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }

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
                    E_isFinished = true;

                    // Write logs
                    StatusLogger.writeLogs(context, E_testConfig.testNum);
                    FrameLogger.writeLogs(context, E_testConfig.testNum);
                    DistributionLogger.writeLogs(context, E_testConfig.testNum);
                    // Tell dashcams that the experiment is finished
                    controller.sendRestartMessages(innerCam, outerCam);

                    // Send workers restart message
                    try {
                        Communicator.msgQueue.put(new CommunicatorMessage(-1));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, E_EXPERIMENT_DURATION);
        }

        private static void connectToDashCam() {
            distributer = new Distributer();
            // Inner DashCam
            innerCam = new EDACam(E_innerCamIP, true);
            innerCam.start();

            // Outer DashCam
            outerCam = new EDACam(E_outerCamIP, false);
            outerCam.start();

            controller = new Controller(innerCam, outerCam);
        }

        public static void connectToWorkers() {
            // This device is the client side
            // Replace this part with actual finding process eventually

            communicator = new Communicator();
            int workerNum = 0;
            HashSet<String> workerNames = new HashSet<>();
            for (String name : E_workerNameList) {
                workerNames.add(name);
                communicator.addWorker(workerNum++, E_hotSpot.get(name));
            }

            for (String deviceName : E_allDevices) {
                if (!workerNames.contains(deviceName)) {
                    communicator.addOther(E_hotSpot.get(deviceName));
                }
            }

            communicator.start();
        }

        public static void createDeviceList() {
            IPLists.createIPList();
            E_hotSpot = IPLists.getByName(E_testConfig.myName);
            if (E_hotSpot == null) {
                throw new RuntimeException("Unregistered hotspot device: " + E_testConfig.myName);
            }
            E_innerCamIP = E_hotSpot.get(E_testConfig.innerCamName);
            E_outerCamIP = E_hotSpot.get(E_testConfig.outerCamName);

            E_allDevices = new String[]{};
        }

        public static void workerStart() {
            WorkerThread workerThread = new WorkerThread();
            workerThread.start();
        }
    }


    // Main routine for the coordinator
    public static void mainRoutine() {

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
