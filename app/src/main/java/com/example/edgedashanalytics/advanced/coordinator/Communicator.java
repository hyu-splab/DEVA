package com.example.edgedashanalytics.advanced.coordinator;

/*
Re-implementing Sender to resolve network-related issues.
Still can't figure out why certain devices show extra heavy network delays,
but hopefully this integrated Sender would solve such issues.
 */

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.example.edgedashanalytics.advanced.common.CoordinatorMessage;
import com.example.edgedashanalytics.advanced.common.FrameResult;
import com.example.edgedashanalytics.util.Constants;
import com.example.edgedashanalytics.advanced.common.TimeLog;
import com.example.edgedashanalytics.advanced.common.Image2;
import com.example.edgedashanalytics.advanced.common.WorkerResult;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Communicator extends Thread {
    private static final String TAG = "Communicator";

    private Handler handler;

    public ArrayList<EDAWorker> workers;
    public ArrayList<EDAWorker> allDevices;

    public long pendingDataSize, totalDataSize;
    public long innerWaiting, outerWaiting;

    public ArrayList<ConnectionTimestamp> connectionTimestamps;
    public static boolean[] isConnected;
    public long startTime;

    static class ConnectionTimestamp implements Comparable<ConnectionTimestamp> {
        int workerNum;
        int timestamp;

        public ConnectionTimestamp(int workerNum, int timestamp) {
            this.workerNum = workerNum;
            this.timestamp = timestamp;
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

        connectionTimestamps = new ArrayList<>();
        for (int i = 0; i < AdvancedMain.connectionTimestamps.length; i++) {
            List<Integer> cur = AdvancedMain.connectionTimestamps[i];
            for (Integer timestamp : cur)
                connectionTimestamps.add(new ConnectionTimestamp(i, timestamp));
        }

        Collections.sort(connectionTimestamps);
        isConnected = new boolean[AdvancedMain.connectionTimestamps.length];
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

    public Handler getHandler() {
        return handler;
    }

    // Make sure this thread is created after adding ALL the workers to 'allDevices'
    @Override
    public void run() {
        Looper.prepare();
        handler = new CommunicatorHandler(Looper.myLooper());
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

            new ListenerThread(worker).start();
        }
    }

    private class CommunicatorHandler extends Handler {
        private int connectionTimestampIndex = 0;
        public CommunicatorHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            int workerNum = msg.arg1;

            CoordinatorMessage cMsg;

            long curTime = System.currentTimeMillis() - startTime;

            boolean[] prevConnected = isConnected.clone();
            while (connectionTimestampIndex < connectionTimestamps.size()) {
                ConnectionTimestamp ts = connectionTimestamps.get(connectionTimestampIndex);
                if (ts.timestamp > curTime)
                    break;
                isConnected[ts.workerNum] = !isConnected[ts.workerNum];
                connectionTimestampIndex++;
            }

            boolean connectionChanged = false;

            for (int i = 0; i < isConnected.length; i++) {
                if (isConnected[i] != prevConnected[i]) {
                    connectionChanged = true;
                    break;
                }
            }

            // Inform both distributer and controller independently, as each of them should do their own work
            if (connectionChanged) {
                AdvancedMain.connectionChanged = true;
                Controller.connectionChanged = true;
            }

            try {
                // Experiment finish message (tell everyone to restart)
                if (workerNum == -1) {
                    cMsg = new CoordinatorMessage(2, null);
                    for (EDAWorker worker : allDevices) {
                        ObjectOutputStream outStream = worker.outstream;
                        outStream.writeObject(cMsg);
                        outStream.flush();
                        outStream.reset();
                    }
                    return;
                }
                if (AdvancedMain.isFinished)
                    return;

                if (!isConnected[workerNum]) // Discard frames to disconnected workers
                    return;

                EDAWorker worker = workers.get(workerNum);
                Image2 data = (Image2) msg.obj;
                ObjectOutputStream outStream = worker.outstream;
                //TimeLog.coordinator.add(data.frameNum); // Wait for Result

                if (data.isInner) {
                    worker.status.innerWaiting++;
                    innerWaiting++;
                }
                else {
                    worker.status.outerWaiting++;
                    outerWaiting++;
                }

                pendingDataSize += data.data.length;

                data.coordinatorStartTime = System.currentTimeMillis();
                //Log.d(TAG, "frame " + data.frameNumber + " started at " + data.workerStartTime);

                cMsg = new CoordinatorMessage(1, data);

                outStream.writeObject(cMsg);
                outStream.flush();
                outStream.reset();

                totalDataSize += data.data.length;

                //TimeLog.coordinator.add(data.frameNum); // After send

            } catch (Exception e) {
                e.printStackTrace();
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
                    pendingDataSize -= res.dataSize;

                    if (res.isInner) {
                        worker.status.innerWaiting--;
                        innerWaiting--;
                    }
                    else {
                        worker.status.outerWaiting--;
                        outerWaiting--;
                    }

                    long networkTime = (endTime - res.coordinatorStartTime) - res.totalTime;
                    long turnaround = endTime - res.coordinatorStartTime;
                    //Log.d(TAG, "frame " + res.frameNumber + ": networkTime = (" + endTime + " - " + res.coordinatorStartTime + ") - " + res.totalTime + " = " + networkTime);

                    FrameResult result = new FrameResult(
                            endTime, res.frameNum, res.cameraFrameNum, worker.workerNum, res.isInner,
                            res.totalTime, res.processTime, networkTime, turnaround, res.queueSize);
                    FrameLogger.addResult(result, res.isDistracted, res.hazards, res.dataSize);
                    worker.status.addResult(result);

                    /*AdvancedMain.processed++;
                    if (res.isInner)
                        AdvancedMain.innerCount++;
                    else
                        AdvancedMain.outerCount++;*/
                    //TimeLog.coordinator.finish(res.cameraFrameNum + ""); // Finish
                    //Log.d(TAG, "Got response from the server: isInner = "
                    //+ res.isInner + ", frameNumber = " + res.frameNumber);
                    //Log.d(TAG, res.msg);
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
