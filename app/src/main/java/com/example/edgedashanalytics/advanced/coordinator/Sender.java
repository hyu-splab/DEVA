package com.example.edgedashanalytics.advanced.coordinator;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.example.edgedashanalytics.advanced.common.TimeLog;
import com.example.edgedashanalytics.advanced.common.Image2;
import com.example.edgedashanalytics.advanced.common.WorkerResult;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;

/* unused */
public class Sender extends Thread {
    public static final int port = 5555;
    public String ip;
    public int workerNum;

    private static final String TAG = "Sender";

    private Handler handler;

    public ObjectOutputStream outstream;
    public ObjectInputStream instream;
    private long score;

    Deque<Long> recentTime;
    HashMap<Long, Long> startTime;
    long delay;
    final int DEQUE_CAPACITY = 3;

    public Sender(String ip, int workerNum) {
        this.ip = ip;
        this.workerNum = workerNum;
        recentTime = new ArrayDeque<>(DEQUE_CAPACITY);
        startTime = new HashMap<>();
        handler = null;
        score = 0;
        delay = 0;
    }

    public long getScore() {
        return score;
    }

    public Handler getHandler() {
        return handler;
    }

    @Override
    public void run() {
        score = Long.MAX_VALUE;
        Looper.prepare();
        handler = new Handler(Looper.myLooper()) {
            @Override
            public void handleMessage(Message inputMessage) {
                // score = Long.MAX_VALUE;
                try {
                    //Log.d(TAG, "sending to the worker: " + ip);
                    TimeLog.coordinator.add(((Image2)inputMessage.obj).frameNum + ""); // Wait for Result
                    outstream.writeObject(inputMessage.obj);
                    outstream.flush();
                    outstream.reset();
                    TimeLog.coordinator.add(((Image2)inputMessage.obj).frameNum + ""); // After send
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        Socket socket;
        while (true) {
            try {
                Log.d(TAG, "Trying to connect to worker: " + ip + ":" + port);
                socket = new Socket(ip, port);
                Log.d(TAG, "connected to " + ip);
                score = 0;
                outstream = new ObjectOutputStream(socket.getOutputStream());
                instream = new ObjectInputStream(socket.getInputStream());


                // Separate thread for listening
                new ListenerThread().start();

            } catch (Exception e) {
                e.printStackTrace();
                Log.w(TAG, "Retrying for " + ip + "...");
                continue;
            }
            break;
        }
        Looper.loop();
    }

    public void setScore(long score) {
        this.score = score;
    }

    private class ListenerThread extends Thread {
        @Override
        public void run() {
            while (true) {
                try {
                    WorkerResult res = (WorkerResult) instream.readObject();

                    long endTime = System.currentTimeMillis();
                    if (recentTime.size() == DEQUE_CAPACITY)
                        recentTime.pop();
                    try {
                        recentTime.push(endTime - startTime.remove(res.frameNum));
                    } catch (NullPointerException e) {
                        e.printStackTrace();
                    }
                    long total = 0;
                    for (Long t : recentTime)
                        total += t;
                    delay = total / recentTime.size();

                    score = 0; //msg.score;
                    AdvancedMain.processed++;
                    if (res.isInner)
                        AdvancedMain.innerCount++;
                    else
                        AdvancedMain.outerCount++;
                    TimeLog.coordinator.finish(res.frameNum + ""); // Finish
                    //Log.d(TAG, "Got response from the server: isInner = "
                    //+ res.isInner + ", frameNumber = " + res.frameNumber);
                    //Log.d(TAG, res.msg);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
