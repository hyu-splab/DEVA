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

public class Communicator extends Thread {
    private static final String TAG = "Communicator";

    private Handler handler;

    public ArrayList<EDAWorker> workers;

    public long pendingDataSize;

    public Communicator() {
        workers = new ArrayList<>();
        pendingDataSize = 0;
    }

    public void addWorker(int workerNum, String ip) {
        workers.add(new EDAWorker(workerNum, ip));
        // Should not try connecting now; this method is called from the main thread
    }

    public Handler getHandler() {
        return handler;
    }

    // Make sure this thread is created after adding ALL the workers to 'workers'
    @Override
    public void run() {
        Looper.prepare();
        handler = new CommunicatorHandler(Looper.myLooper());
        connect();
        Looper.loop();
    }

    private void connect() {
        for (int workerNum = 0; workerNum < workers.size(); workerNum++) {
            EDAWorker worker = workers.get(workerNum);
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
        public CommunicatorHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            int workerNum = msg.arg1;

            try {
                EDAWorker worker = workers.get(workerNum);
                Image2 data = (Image2) msg.obj;
                ObjectOutputStream outStream = worker.outstream;
                TimeLog.coordinator.add(data.frameNum); // Wait for Result

                if (data.isInner) {
                    worker.status.innerWaiting++;
                }
                else {
                    worker.status.outerWaiting++;
                }

                data.coordinatorStartTime = System.currentTimeMillis();
                //Log.d(TAG, "frame " + data.frameNumber + " started at " + data.workerStartTime);

                CoordinatorMessage cMsg = new CoordinatorMessage(1, data);

                outStream.writeObject(cMsg);
                outStream.flush();
                outStream.reset();

                pendingDataSize += data.data.length;

                TimeLog.coordinator.add(data.frameNum); // After send

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
                    }
                    else {
                        worker.status.outerWaiting--;
                    }

                    long networkTime = (endTime - res.coordinatorStartTime) - res.totalTime;
                    //Log.d(TAG, "frame " + res.frameNumber + ": networkTime = (" + endTime + " - " + res.coordinatorStartTime + ") - " + res.totalTime + " = " + networkTime);

                    FrameResult result = new FrameResult(endTime, res.frameNum, worker.workerNum, res.isInner, res.processTime, networkTime);
                    FrameLogger.addResult(result);
                    worker.status.addResult(result);

                    /*AdvancedMain.processed++;
                    if (res.isInner)
                        AdvancedMain.innerCount++;
                    else
                        AdvancedMain.outerCount++;*/
                    TimeLog.coordinator.finish(res.frameNum + ""); // Finish
                    //Log.d(TAG, "Got response from the server: isInner = "
                    //+ res.isInner + ", frameNumber = " + res.frameNumber);
                    //Log.d(TAG, res.msg);
                } catch(Exception e){
                    e.printStackTrace();
                }
            }
        }
    }
}
