package com.example.edgedashanalytics.advanced.coordinator;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

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

    Deque<Long> recentTime;
    //HashMap<Long, Long> startTime;
    long delay;
    final int DEQUE_CAPACITY = 3;

    public Sender(String ip, int workerNum) {
        this.ip = ip;
        this.workerNum = workerNum;
        recentTime = new ArrayDeque<>(DEQUE_CAPACITY);
        //startTime = new HashMap<>();
        handler = null;
        delay = 0;
    }

    @Override
    public void run() {
        Looper.prepare();
        handler = new Handler(Looper.myLooper()) {
            @Override
            public void handleMessage(Message inputMessage) {
                try {
                    outstream.writeObject(inputMessage.obj);
                    outstream.flush();
                    outstream.reset();
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

    private class ListenerThread extends Thread {
        @Override
        public void run() {
            while (true) {
                try {
                    WorkerResult res = (WorkerResult) instream.readObject();

                    /*long endTime = System.currentTimeMillis();
                    if (recentTime.size() == DEQUE_CAPACITY)
                        recentTime.pop();
                    try {
                        recentTime.push(endTime - startTime.remove(res.cameraFrameNum));
                    } catch (NullPointerException e) {
                        e.printStackTrace();
                    }
                    long total = 0;
                    for (Long t : recentTime)
                        total += t;
                    delay = total / recentTime.size();*/

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
