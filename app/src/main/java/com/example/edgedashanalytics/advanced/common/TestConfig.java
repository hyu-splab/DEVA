package com.example.edgedashanalytics.advanced.common;

import static com.example.edgedashanalytics.advanced.coordinator.Communicator.queueSizeStealing;
import static com.example.edgedashanalytics.advanced.coordinator.MainRoutine.Experiment.*;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.StringTokenizer;

public class TestConfig {
    public boolean isCoordinator;
    public boolean isWorker;
    public int testNum;
    public String innerCamName, outerCamName;
    public String myName;

    public TestConfig(boolean isCoordinator, boolean isWorker, int testNum, String innerCamName, String outerCamName, String myName) {
        this.isCoordinator = isCoordinator;
        this.isWorker = isWorker;
        this.testNum = testNum;
        this.innerCamName = innerCamName;
        this.outerCamName = outerCamName;
        this.myName = myName;
    }
    private static final String TAG = "TestConfig";


    /*
    Config file structure

    In the first line we have experiment duration in seconds

    Each experiment is written on its own line:

    test_num coordinator_name inner_result_file outer_result_file #_of_workers worker0 exp(0) worker1 exp(1) ...

    where exp(x) is:

    #_of_timestamps timestamp1 timestamp2 ...

    where each timestamp is an integer represented in seconds

    Initially each worker is considered disconnected

    For example, if exp(0) is "1 0" then at the beginning the 0th worker is connected and it will keep being connected till the end of the experiment

    For another example, if it's "3 10 20 30" then it is first unconnected at the beginning, and then it's connected at 10th second, disconnected at 20th second, and reconnected at 30th second
     */
    public static TestConfig readConfigs(Context context) throws Exception {
        File dir = context.getExternalFilesDir(null);

        String whoAmI = readWhoAmI(dir);

        File file = new File(dir + "/conf.txt");
        File tempFile = new File(dir + "/temp.txt");

        if (!file.exists()) {
            File orgFile = new File(dir + "/testconfig.txt");
            Files.copy(orgFile.toPath(), file.toPath());
        }

        BufferedReader br = new BufferedReader(new FileReader(file));
        BufferedWriter bw = new BufferedWriter(new FileWriter(tempFile));

        String line;
        StringTokenizer st;
        boolean first = true;

        // It's common that ~2s of log at the end is missing because of various preparation phases
        long duration = ri(new StringTokenizer(br.readLine()));
        E_EXPERIMENT_DURATION = (duration + 5) * 1000L;
        E_REAL_EXPERIMENT_DURATION = duration * 1000L;
        bw.write(E_EXPERIMENT_DURATION / 1000 + "\n");

        line = br.readLine();
        st = new StringTokenizer(line);
        String innerCamName = st.nextToken();
        String outerCamName = st.nextToken();
        bw.write(line + "\n");

        int testNum = -1;
        String coordinatorID = null;
        int numWorkers;

        boolean isWorker = false;

        while ((line = br.readLine()) != null) {
            if (first) {
                first = false;

                st = new StringTokenizer(line);
                testNum = ri(st);

                numWorkers = ri(st);
                E_workerNameList = new String[numWorkers];
                E_connectionTimestamps = new ArrayList[numWorkers];

                if (E_stealing) {
                    queueSizeStealing = new long[numWorkers];
                }

                for (int i = 0; i < numWorkers; i++) {
                    E_connectionTimestamps[i] = new ArrayList<>();
                    E_workerNameList[i] = rs(st);
                    if (i == 0) {
                        coordinatorID = E_workerNameList[i];
                    }
                    if (whoAmI.equals(E_workerNameList[i])) {
                        isWorker = true;
                    }
                    int numTimestamps = ri(st);
                    for (int j = 0; j < numTimestamps; j++) {
                        int ts = ri(st);
                        Log.v(TAG, "timestamp = " + ts);
                        E_connectionTimestamps[i].add(ts * 1000);
                    }
                }
            }
            else {
                bw.write(line);
                bw.write("\n");
            }
        }

        br.close();
        bw.close();
        file.delete();

        //if (first) {
            Files.copy(tempFile.toPath(), file.toPath());
        //}
        tempFile.delete();

        return new TestConfig(whoAmI.equals(coordinatorID), isWorker, testNum, innerCamName, outerCamName, whoAmI);
    }

    static int ri(StringTokenizer st) {
        return Integer.parseInt(st.nextToken());
    }

    static String rs(StringTokenizer st) {
        return st.nextToken();
    }

    static String readWhoAmI(File dir) throws Exception {
        Scanner sc = new Scanner(new File(dir + "/whoami.txt"));
        String name = sc.next();
        sc.close();
        return name;
    }
}
