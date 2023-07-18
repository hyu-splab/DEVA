package com.example.edgedashanalytics.util.log;

import android.content.Context;
import android.util.Log;

import com.example.edgedashanalytics.util.connection.Connection;
import com.example.edgedashanalytics.util.connection.WorkerServer;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;

public class TimeLog {
    private static final String TAG = "TimeLog";

    private static final String[] coordinatorColumns = {
            "Frame Number",
            "Distribute",
            "Wait for Result",
            "Total"
    };
    private static final String[] workerColumns = {
            "Frame Number",
            "Enqueue",
            "Uncompress",
            "Process Frame",
            "Total"
    };

    public static Context context;

    public static TimeLog coordinator = new TimeLog(false), worker = new TimeLog(true);

    public static void clearLogs() {
        coordinator = new TimeLog(false);
        worker = new TimeLog(true);
    }

    private final HashMap<String, Test> map = new HashMap<>();
    private final ArrayList<Test> ls = new ArrayList<>();
    private int ticket = 0;
    private final boolean isWorker;
    private TimeLog(boolean isWorker) {
        this.isWorker = isWorker;
    }

    public void start(String name) {
        synchronized (map) {
            map.put(name, new Test(name, ++ticket));
        }
    }

    private Test getTest(String name) {
        Test test;
        synchronized (map) {
            try {
                test = map.get(name);
                if (test == null)
                    throw new NullPointerException();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
        return test;
    }

    public void add(String name) {
        getTest(name).add();
    }

    public void addEmpty(String name, int times) {
        Test test = getTest(name);
        test.add();
        for (int i = 0; i < times - 1; i++)
            test.addEmpty();
    }

    public void finish(String name) {
        Test test;
        synchronized (map) {
            try {
                test = map.remove(name);
                if (test == null)
                    throw new NullPointerException();
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
        test.finish();
        ls.add(test);
    }

    public void writeLogs() {
        Connection.isFinished = true;
        synchronized (map) {
            // Sort the results
            Collections.sort(ls);

            File path = context.getExternalFilesDir(null);
            File file = new File(path, (isWorker ? "w" : "c") + "log.txt");

            int finishedCount = 0; // Count only the finished ones
            long earliest = Long.MAX_VALUE;
            long latest = Long.MIN_VALUE;
            try {
                StringBuilder sb = new StringBuilder();
                Log.d(TAG, ls.size() + " logs available");

                String[] columns = (isWorker ? workerColumns : coordinatorColumns);
                long[] sumArray = new long[columns.length - 1];
                for (int i = 0; i < columns.length; i++) {
                    sb.append(columns[i]).append(i == columns.length - 1 ? "\n" : ",");
                }

                for (Test test : ls) {
                    if (!test.isFinished) {
                        Log.d(TAG, "finish() hasn't been called for log '" + test.name + "' ");
                        continue;
                    }
                    sb.append(test.name).append(",");
                    ArrayList<Test.CheckPoint> cp = test.checkPoints;
                    earliest = Math.min(earliest, cp.get(0).timestamp);
                    latest = Math.max(latest, cp.get(cp.size() - 1).timestamp);
                    boolean isCanceled = false;
                    for (int i = 0; i < cp.size() - 1; i++) {
                        Test.CheckPoint c = cp.get(i);
                        long interval = cp.get(i + 1).timestamp - c.timestamp;
                        if (cp.get(i + 1).timestamp == -1)
                            isCanceled = true;
                        sb.append(isCanceled ? "-" : interval + ",");
                        sumArray[i] += interval;
                    }
                    long total = cp.get(cp.size() - 1).timestamp - cp.get(0).timestamp;
                    sb.append(total).append("\n");
                    sumArray[sumArray.length - 1] += total;
                    if (!isCanceled)
                        finishedCount++;
                }
                if (finishedCount == 0) {
                    Log.d(TAG, "No finished logs available");
                    return;
                }

                sb.append("avg,");
                for (int i = 0; i < sumArray.length; i++) {
                    sb.append(String.format(Locale.getDefault(), "%.3f", (double) sumArray[i] / finishedCount))
                            .append(i == sumArray.length - 1 ? "\n" : ",");
                }

                sb.append("summary,");
                long totalTime = latest - earliest;
                long innerCount = isWorker ? WorkerServer.innerCount : Connection.innerCount;
                long outerCount = isWorker ? WorkerServer.outerCount : Connection.outerCount;
                sb.append(isWorker ? finishedCount : Connection.totalCount).append(",");
                if (!isWorker)
                    sb.append(Connection.processed).append(",").append(Connection.dropped).append(",");
                sb.append(totalTime).append(",")
                        .append(String.format(Locale.getDefault(), "%.3f,",
                                finishedCount * 1000.0 / totalTime))
                        .append(innerCount + outerCount).append(",")
                        .append(innerCount).append(",").append(outerCount).append("\n");


                try (FileOutputStream stream = new FileOutputStream(file)) {
                    stream.write(sb.toString().getBytes());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            Log.d(TAG, "wrote log in " + file.getAbsolutePath());
            Log.d(TAG, "============== TEST ENDED ==============");
            Log.d(TAG, "============== TEST ENDED ==============");
            Log.d(TAG, "============== TEST ENDED ==============");
            Log.d(TAG, "============== TEST ENDED ==============");
            Log.d(TAG, "============== TEST ENDED ==============");
            Log.d(TAG, "============== TEST ENDED ==============");
            Log.d(TAG, "============== TEST ENDED ==============");
            Log.d(TAG, "============== TEST ENDED ==============");

        }
    }

    class Test implements Comparable {

        private final String name;
        private final ArrayList<CheckPoint> checkPoints;
        private final int order;
        private boolean isFinished;

        public Test(String name, int order) {
            checkPoints = new ArrayList<>();
            this.name = name;
            this.order = order;
            this.isFinished = false;
            add();
        }

        public void add() {
            checkPoints.add(new CheckPoint());
            //Log.d(TAG, name + ": checkpoint added at " + checkPoints.get(checkPoints.size() - 1).timestamp);
        }

        public void addEmpty() {
            checkPoints.add(new CheckPoint(false));
        }

        public void finish() {
            checkPoints.add(new CheckPoint());
            //Log.d(TAG, name + ": checkpoint added at " + checkPoints.get(checkPoints.size() - 1).timestamp);
            isFinished = true;
        }

        @Override
        public int compareTo(Object o) {
            Test other = (Test) o;
            return Integer.compare(order, other.order);
        }

        private class CheckPoint {
            public long timestamp;

            public CheckPoint() {
                this(true);
            }
            public CheckPoint(boolean ok) {
                if (!ok)
                    timestamp = -1;
                else
                    timestamp = System.currentTimeMillis();
            }
        }
    }
}
