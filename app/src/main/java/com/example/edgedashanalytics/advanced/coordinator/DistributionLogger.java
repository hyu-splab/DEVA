package com.example.edgedashanalytics.advanced.coordinator;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class DistributionLogger {
    private static final String TAG = "DistributionLogger";

    public static final List<DistributionLog> logs = new ArrayList<>();

    public static void addLog(ArrayList<Double> workerWeight, ArrayList<Integer> sequence) {
        synchronized (logs) {
            logs.add(new DistributionLog(workerWeight, sequence));
        }
    }

    public static void writeLogs(Context context, int testNum) {
        synchronized (logs) {
            if (logs.isEmpty()) {
                Log.w(TAG, "No distribution logs available");
                return;
            }

            String filename = testNum + "_dlog.csv";

            File file = new File(context.getExternalFilesDir(null), filename);
            StringBuilder sb = new StringBuilder();

            sb.append("Time,W0,W1,W2,C0,C1,C2\n");

            long startTime = logs.get(0).timestamp;

            for (DistributionLog log : logs) {
                sb.append(log.timestamp - startTime).append(",");
                for (int i = 0; i < 3; i++) {
                    if (i >= log.workerWeight.size())
                        sb.append("0,");
                    else
                        sb.append(log.workerWeight.get(i)).append(",");
                }
                for (int i = 0; i < 3; i++) {
                    if (i >= log.cntArray.size())
                        sb.append("0,");
                    else
                        sb.append(log.cntArray.get(i)).append(",");
                }
                for (int i = 0; i < log.sequence.size(); i++) {
                    sb.append(log.sequence.get(i)).append(i == log.sequence.size() - 1 ? "\n" : ",");
                }
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(sb.toString().getBytes());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static class DistributionLog {
        public long timestamp;
        public ArrayList<Double> workerWeight;
        public ArrayList<Integer> sequence;
        public ArrayList<Integer> cntArray;

        public DistributionLog(ArrayList<Double> workerWeight, ArrayList<Integer> sequence) {
            this.timestamp = System.currentTimeMillis();
            this.workerWeight = workerWeight;
            this.sequence = sequence;
            cntArray = new ArrayList<>();
            int n = workerWeight.size();
            for (int i = 0; i < n; i++)
                cntArray.add(0);
            for (Integer x : sequence)
                cntArray.set(x, cntArray.get(x) + 1);
        }
    }
}
