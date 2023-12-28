package com.example.edgedashanalytics.advanced.common;

import static com.example.edgedashanalytics.advanced.coordinator.AdvancedMain.workerNameList;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.StringTokenizer;

public class TestConfig {
    private static final String TAG = "TestConfig";
    static boolean readConfigs(Context context) throws Exception {
        File dir = context.getExternalFilesDir(null);

        int whoAmI = readWhoAmI(dir);

        File file = new File(dir + "/testconfig.txt");
        File tempFile = new File(dir + "/temp.txt");

        BufferedReader br = new BufferedReader(new FileReader(file));
        BufferedWriter bw = new BufferedWriter(new FileWriter(tempFile));

        String line;
        boolean first = true;

        int testNum = -1;
        int coordinatorID = -1;
        int numWorkers = -1;

        while ((line = br.readLine()) != null) {
            if (first) {
                first = false;

                StringTokenizer st = new StringTokenizer(line);
                testNum = ri(st);
                coordinatorID = ri(st);

                numWorkers = ri(st);
                workerNameList = new String[numWorkers];
                for (int i = 0; i < numWorkers; i++) {
                    workerNameList[i] = rs(st);
                }
            }
            else {
                bw.write(line);
            }
        }

        br.close();
        bw.close();
        file.delete();

        Files.copy(tempFile.toPath(), file.toPath());
        tempFile.delete();

        Log.v(TAG, "Test " + testNum + " start");

        return whoAmI == coordinatorID;
    }

    static int ri(StringTokenizer st) {
        return Integer.parseInt(st.nextToken());
    }

    static String rs(StringTokenizer st) {
        return st.nextToken();
    }

    static int readWhoAmI(File dir) throws Exception {
        Scanner sc = new Scanner(new File(dir + "whoami.txt"));
        int val = sc.nextInt();
        sc.close();
        return val;
    }
}
