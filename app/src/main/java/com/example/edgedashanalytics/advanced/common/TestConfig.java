package com.example.edgedashanalytics.advanced.common;

import static com.example.edgedashanalytics.advanced.coordinator.AdvancedMain.workerNameList;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
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
                for (int i = 0; i < numWorkers; i++) {
                    workerNameList[i] = rs(st);
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
