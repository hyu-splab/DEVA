package com.example.edgedashanalytics.advanced.common;

import static com.example.edgedashanalytics.advanced.coordinator.AdvancedMain.EXPERIMENT_DURATION;
import static com.example.edgedashanalytics.advanced.coordinator.AdvancedMain.workerNameList;
import static com.example.edgedashanalytics.advanced.coordinator.AdvancedMain.connectionTimestamps;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.StringTokenizer;

public class TestConfig {
    public boolean isCoordinator;
    public int testNum;
    public String innerResultFile, outerResultFile;

    public TestConfig(boolean isCoordinator, int testNum, String innerResultFile, String outerResultFile) {
        this.isCoordinator = isCoordinator;
        this.testNum = testNum;
        this.innerResultFile = innerResultFile;
        this.outerResultFile = outerResultFile;
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
        boolean first = true;

        EXPERIMENT_DURATION = ri(new StringTokenizer(br.readLine())) * 1000;
        bw.write(EXPERIMENT_DURATION / 1000 + "\n");

        int testNum = -1;
        String coordinatorID = null;
        int numWorkers = -1;

        String innerResultFile = null, outerResultFile = null;

        while ((line = br.readLine()) != null) {
            if (first) {
                first = false;

                StringTokenizer st = new StringTokenizer(line);
                testNum = ri(st);
                coordinatorID = rs(st);

                innerResultFile = rs(st);
                outerResultFile = rs(st);

                numWorkers = ri(st);
                workerNameList = new String[numWorkers];
                connectionTimestamps = new ArrayList[numWorkers];
                for (int i = 0; i < numWorkers; i++) {
                    connectionTimestamps[i] = new ArrayList<>();
                    workerNameList[i] = rs(st);
                    int numTimestamps = ri(st);
                    for (int j = 0; j < numTimestamps; j++)
                        connectionTimestamps[i].add(ri(st) * 1000);
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

        Log.v(TAG, "Test " + testNum + " start");
        Log.v(TAG, "I am " + whoAmI + " and the coordinator is " + coordinatorID);

        return new TestConfig(whoAmI.equals(coordinatorID), testNum, dir + "/" + innerResultFile, dir + "/" + outerResultFile);
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
