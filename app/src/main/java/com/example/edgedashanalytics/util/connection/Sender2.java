package com.example.edgedashanalytics.util.connection;

/*
Re-implementing Sender to resolve network-related issues.
Still can't figure out why certain devices show extra heavy network delays,
but hopefully this integrated Sender would solve such issues.
 */

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.example.edgedashanalytics.util.log.TimeLog;
import com.example.edgedashanalytics.util.video.analysis.Image2;
import com.example.edgedashanalytics.util.video.analysis.Result2;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;

public class Sender2 extends Thread {
    private static final String TAG = "Sender2";
    public static final int port = 5555;

    private Handler handler;

    ArrayList<Worker> workers;

    public Sender2() {
        workers = new ArrayList<>();
    }

    public void addWorker(String ip) {
        workers.add(new Worker(ip));
        // Should not try connecting now; this method is called from the main thread
    }

    public Handler getHandler() {
        return handler;
    }

    // Make sure this thread is created after adding ALL the workers
    @Override
    public void run() {
        Looper.prepare();
        handler = new Handler(Looper.myLooper()) {
            @Override
            public void handleMessage(Message inputMessage) {
                int workerNum = inputMessage.arg1;
                try {
                    //Log.d(TAG, "sending to the worker: " + ip);
                    TimeLog.coordinator.add(((Image2)inputMessage.obj).frameNumber + ""); // Wait for Result
                    ObjectOutputStream outstream = workers.get(workerNum).outstream;
                    outstream.writeObject(inputMessage.obj);
                    outstream.flush();
                    outstream.reset();
                    TimeLog.coordinator.add(((Image2)inputMessage.obj).frameNumber + ""); // After send
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        for (Worker worker : workers) {
            String ip = worker.ip;
            Socket socket;
            ObjectOutputStream outstream;
            ObjectInputStream instream;
            while (true) {
                try {
                    Log.d(TAG, "Trying to connect to worker: " + ip + ":" + port);
                    socket = new Socket(ip, port);
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

        Looper.loop();
    }

    private class ListenerThread extends Thread {
        Worker worker;
        public ListenerThread(Worker worker) {
            this.worker = worker;
        }
        @Override
        public void run() {
            while (true) {
                try {
                    WorkerMessage msg = (WorkerMessage) worker.instream.readObject();
                    Result2 res = (Result2) msg.msg;

                    worker.score = msg.score;
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

    public class Worker {
        String ip;
        ObjectOutputStream outstream;
        ObjectInputStream instream;
        long score;

        public Worker(String ip) {
            this.ip = ip;
        }
    }
}
