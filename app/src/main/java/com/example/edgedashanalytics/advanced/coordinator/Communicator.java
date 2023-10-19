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

import com.example.edgedashanalytics.advanced.common.WorkerMessage;
import com.example.edgedashanalytics.util.Constants;
import com.example.edgedashanalytics.util.log.TimeLog;
import com.example.edgedashanalytics.util.video.analysis.Image2;
import com.example.edgedashanalytics.util.video.analysis.Result2;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class Communicator extends Thread {
    private static final String TAG = "Sender2";

    private Handler handler;

    ArrayList<EDAWorker> workers;

    public Communicator() {
        workers = new ArrayList<>();
    }

    public void addWorker(String ip) {
        workers.add(new EDAWorker(ip));
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

            new Timer().schedule(new PingThread(handler, workerNum), 500);
        }
    }

    private class CommunicatorHandler extends Handler {
        public CommunicatorHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            int workerNum = msg.arg1;
            int messageType = msg.arg2;

            if (messageType == 1) {

            }

            try {
                ObjectOutputStream outstream = workers.get(workerNum).outstream;

                if (messageType == 1) {
                    TimeLog.coordinator.add(((Image2) msg.obj).frameNumber); // Wait for Result
                }

                outstream.writeInt(messageType);

                if (messageType == 1) {
                    outstream.writeObject(msg.obj);
                }

                outstream.flush();
                outstream.reset();

                if (messageType == 1) {
                    TimeLog.coordinator.add(((Image2) msg.obj).frameNumber); // After send
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class PingThread extends TimerTask {
        Handler handler;
        int workerNum;
        public PingThread(Handler handler, int workerNum) {
            this.handler = handler;
            this.workerNum = workerNum;
        }
        @Override
        public void run() {
            Message msg = Message.obtain();
            msg.arg1 = workerNum;
            msg.arg2 = 2;
            handler.sendMessage(msg);
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
                    int msgType = worker.instream.readInt();

                    if (msgType == 1) { // ping

                    }

                    WorkerMessage msg = (WorkerMessage) worker.instream.readObject();
                    Result2 res = (Result2) msg.msg;

                    Connection.processed++;
                    if (res.isInner)
                        Connection.innerCount++;
                    else
                        Connection.outerCount++;
                    TimeLog.coordinator.finish(res.frameNumber + ""); // Finish
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
