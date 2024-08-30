package com.example.edgedashanalytics.advanced.coordinator;

/*
Re-implementing Sender to resolve network-related issues.
Still can't figure out why certain devices show extra heavy network delays,
but hopefully this integrated Sender would solve such issues.
 */

import static com.example.edgedashanalytics.advanced.coordinator.MainRoutine.Experiment.*;
import static com.example.edgedashanalytics.advanced.coordinator.MainRoutine.controller;
import static com.example.edgedashanalytics.advanced.coordinator.MainRoutine.distributer;

import android.os.Looper;
import android.util.Log;

import com.example.edgedashanalytics.advanced.common.CoordinatorMessage;
import com.example.edgedashanalytics.advanced.common.AnalysisResult;
import com.example.edgedashanalytics.advanced.common.WorkerInitialInfo;
import com.example.edgedashanalytics.util.Constants;
import com.example.edgedashanalytics.advanced.common.FrameData;
import com.example.edgedashanalytics.advanced.common.WorkerResult;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;

public class Communicator extends Thread {
    private static final String TAG = "Communicator";
    public static ArrayBlockingQueue<CommunicatorMessage> msgQueue = new ArrayBlockingQueue<>(100);
    public final static HashMap<Integer, RecordC> recordMap = new HashMap<>();

    public ArrayList<EDAWorker> workers;
    public ArrayList<EDAWorker> allDevices;

    public long pendingDataSize, totalDataSize;
    public final Object pendingDataSizeLock = new Object();

    public ArrayList<ConnectionTimestamp> connectionTimestamps;
    public static boolean[] isConnected;
    public static boolean[] isBusy;
    public static int availableWorkers;
    public long startTime;
    public static long failed;

    static class ConnectionTimestamp implements Comparable<ConnectionTimestamp> {
        int type;
        int workerNum;
        int timestamp;

        public ConnectionTimestamp(int workerNum, int timestamp) {
            if (timestamp >= 0)
                type = 1;
            else
                type = 2;
            this.workerNum = workerNum;
            this.timestamp = Math.abs(timestamp);
        }

        @Override
        public int compareTo(ConnectionTimestamp o) {
            return this.timestamp - o.timestamp;
        }
    }

    public Communicator() {
        workers = new ArrayList<>();
        allDevices = new ArrayList<>();
        pendingDataSize = 0;

        int numDevices = E_connectionTimestamps.length;

        connectionTimestamps = new ArrayList<>();
        for (int i = 0; i < numDevices; i++) {
            List<Integer> cur = E_connectionTimestamps[i];
            for (Integer timestamp : cur)
                connectionTimestamps.add(new ConnectionTimestamp(i, timestamp));
        }

        Collections.sort(connectionTimestamps);
        for (ConnectionTimestamp ct : connectionTimestamps) {
            Log.v(TAG, ct.timestamp + ": " + ct.workerNum + " " + ct.type);
        }
        isConnected = new boolean[numDevices];
        isBusy = new boolean[numDevices];
    }

    public void addWorker(int workerNum, String ip) {
        EDAWorker worker = new EDAWorker(workerNum, ip);
        workers.add(worker);
        allDevices.add(worker);
        // Should not try connecting now; this method is called from the main thread
    }

    public void addOther(String ip) {
        EDAWorker worker = new EDAWorker(-1, ip);
        allDevices.add(worker);
    }

    // Make sure this thread is created after adding ALL the workers to 'allDevices'
    @Override
    public void run() {
        Looper.prepare();
        connect();
        startTime = System.currentTimeMillis();
        Looper.loop();
    }

    private void connect() {
        Log.v(TAG, "allDevices.size() = " + allDevices.size());
        for (int i = 0; i < allDevices.size(); i++) {
            EDAWorker worker = allDevices.get(i);
            String ip = worker.ip;
            Socket socket;
            ObjectOutputStream outstream;
            ObjectInputStream instream;
            while (true) {
                try {
                    Log.d(TAG, "Trying to connect to worker: " + ip + ":" + Constants.PORT_WORKER);
                    socket = new Socket(ip, Constants.PORT_WORKER);
                    Log.d(TAG, "connected to " + ip);

                    outstream = new ObjectOutputStream(socket.getOutputStream());
                    instream = new ObjectInputStream(socket.getInputStream());
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.w(TAG, "Retrying for " + ip + "...");
                    try {
                        Thread.sleep(1000);
                    } catch (Exception e2) {

                    }
                    continue;
                }
                break;
            }
            worker.outstream = outstream;
            worker.instream = instream;
        }

        for (int i = 0; i < workers.size(); i++) {
            EDAWorker worker = workers.get(i);
            // read initial device status
            try {
                WorkerInitialInfo info = (WorkerInitialInfo) worker.instream.readObject();
                DeviceLogger.devices.add(new DeviceLogger.DeviceInfo(info.temperatureNames, info.frequencyNames));
            } catch (Exception e) {
                e.printStackTrace();
            }

            new ListenerThread(worker).start();
        }

        new SenderThread().start();
    }

    private class SenderThread extends Thread {
        private int connectionTimestampIndex = 0;
        private long totalCnt = 0;

        private long coordinatorTimeTotal = 0;
        private ArrayDeque<Long> coordinatorTime = new ArrayDeque<>();
        @Override
        public void run() {
            while (true) {
                CommunicatorMessage msg;
                try {
                    msg = msgQueue.take();
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }

                CoordinatorMessage cMsg;

                // Experiment finish message (tell everyone to restart)
                if (msg.type == -1) {
                    try {
                        cMsg = new CoordinatorMessage(2, null);
                        for (EDAWorker worker : allDevices) {
                            ObjectOutputStream outStream = worker.outstream;
                            outStream.writeObject(cMsg);
                            outStream.flush();
                            outStream.reset();
                        }
                        continue;
                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }
                }

                int workerNum = distributer.getNextWorker(msg.isInner);

                long curTime = System.currentTimeMillis() - startTime;

                boolean[] prevConnected = isConnected.clone();

                while (connectionTimestampIndex < connectionTimestamps.size()) {
                    ConnectionTimestamp ts = connectionTimestamps.get(connectionTimestampIndex);
                    if (ts.timestamp > curTime)
                        break;
                    Log.w(TAG, "timestamp = " + ts.timestamp + ", type = " + ts.type);
                    if (ts.type == 1) {
                        isConnected[ts.workerNum] = !isConnected[ts.workerNum];
                    } else if (ts.type == 2) {
                        isBusy[ts.workerNum] = !isBusy[ts.workerNum];
                        Log.v(TAG, ts.workerNum + " is now in " + (isBusy[ts.workerNum] ? "busy" : "free") + " state");
                    }
                    connectionTimestampIndex++;
                }

                boolean connectionChanged = false;

                availableWorkers = 0;
                double[] performances = new double[isConnected.length];
                double performanceSum = 0, performanceLost = 0;
                int devicesAdded = 0;
                for (int i = 0; i < isConnected.length; i++) {
                    workers.get(i).status.isConnected = isConnected[i];
                    double performance = workers.get(i).status.getPerformance();
                    if (prevConnected[i]) {
                        performanceSum += performance;
                        performances[i] = performance;
                    }
                    if (isConnected[i] != prevConnected[i]) {
                        connectionChanged = true;
                        if (!isConnected[i]) {
                            performanceLost += performance;
                        } else {
                            devicesAdded++;
                        }
                    }
                    if (isConnected[i])
                        availableWorkers++;
                }

                // Inform both distributer and controller independently, as each of them should do their own work
                if (connectionChanged) {
                    Controller.performanceLostRatio = (performanceSum == 0 ? 0 : performanceLost / performanceSum);
                    Log.v(TAG, String.format(Locale.ENGLISH, "Lost ratio: %.4f / %.4f = %.4f", performanceLost, performanceSum, Controller.performanceLostRatio));
                    Controller.deviceAdded = devicesAdded;

                    MainRoutine.connectionChanged = true;
                    Controller.connectionChanged = true;

                    controller.checkNeeded = true;
                }

                /*if ((++totalCnt % 100) == 0) {
                    Log.v(TAG, "totalCnt = " + totalCnt + ", msgQueue size = " + msgQueue.size());
                }*/

                try {
                    // No available workers, just to change connection statuses
                    if (workerNum == -2)
                        continue;

                    if (E_isFinished)
                        continue;

                    EDAWorker worker = workers.get(workerNum);
                    if (!worker.status.isConnected)
                        continue;

                    totalCnt++;

                    FrameData data = new FrameData(msg.isInner, (int)totalCnt, msg.data, isBusy[workerNum]);
                    ObjectOutputStream outStream = worker.outstream;

                    if (data.isInner) {
                        worker.status.innerWaiting++;
                    } else {
                        worker.status.outerWaiting++;
                    }

                    synchronized (pendingDataSizeLock) {
                        pendingDataSize += data.data.length;
                    }

                    RecordC rc = new RecordC(msg.isInner, msg.frameNum, msg.receivedTime, data.data.length);

                    synchronized (recordMap) {
                        //Log.w(TAG, "frame num " + rc.cameraFrameNum + " is " + (rc.isInner ? "inner" : "outer"));
                        recordMap.put((int)totalCnt, rc);
                    }

                    cMsg = new CoordinatorMessage(1, data);

                    long tt = (System.currentTimeMillis() - msg.receivedTime);
                    coordinatorTimeTotal += tt;
                    coordinatorTime.push(tt);
                    if (coordinatorTime.size() == 100) {
                        //Log.v(TAG, "Average time spent in coordinator: " + (double) coordinatorTimeTotal / 100);
                        coordinatorTimeTotal = 0;
                        coordinatorTime.clear();
                    }

                    outStream.writeObject(cMsg);
                    outStream.flush();
                    outStream.reset();

                    totalDataSize += data.data.length;

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class ListenerThread extends Thread {
        EDAWorker worker;
        public ListenerThread(EDAWorker worker) {
            this.worker = worker;
        }
        @Override
        public void run() {
            while (true) {
                try {
                    WorkerResult res = (WorkerResult) worker.instream.readObject();
                    long endTime = System.currentTimeMillis();

                    worker.status.latestQueueSize = res.queueSize;

                    RecordC rc;
                    synchronized (recordMap) {
                        //Log.w(TAG, "getting frame num " + res.frameNum);
                        rc = recordMap.get(res.frameNum);
                    }

                    synchronized (pendingDataSizeLock) {
                        pendingDataSize -= rc.dataSize;
                    }

                    if (rc.isInner) {
                        worker.status.innerWaiting--;
                    }
                    else {
                        worker.status.outerWaiting--;
                    }

                    long networkTime = (endTime - rc.receivedTime) - res.totalTime;
                    long turnaround = endTime - rc.receivedTime;

                    //if (!rc.isInner)
                    //    Log.w(TAG, "processTime = " + res.processTime);

                    AnalysisResult result = new AnalysisResult(
                            endTime, res.frameNum, worker.workerNum, rc.isInner,
                            res.totalTime, res.processTime, networkTime, turnaround, res.queueSize);
                    if (startTime == -1)
                        startTime = System.currentTimeMillis();
                    if (!res.success) {
                        Log.v(TAG, "Got a failed frame");
                        if (endTime - startTime <= E_REAL_EXPERIMENT_DURATION) {
                            failed++;
                        }
                    }
                    else {
                        FrameLogger.addResult(result, res.isDistracted, res.hazards, rc.dataSize);
                    }
                    worker.status.addResult(result);

                    worker.status.temperatures = res.temperatures;
                    worker.status.frequencies = res.frequencies;

                } catch(Exception e){
                    try {
                        Thread.sleep(1000);
                    } catch (Exception e2) {
                        e2.printStackTrace();
                    }
                    e.printStackTrace();
                }
            }
        }
    }
}
