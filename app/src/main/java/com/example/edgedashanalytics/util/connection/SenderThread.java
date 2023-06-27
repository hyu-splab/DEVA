package com.example.edgedashanalytics.util.connection;

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

public class SenderThread extends Thread {
    private static final String TAG = "SenderThread";

    private String ip;
    private int port;
    private Handler handler;

    public ObjectOutputStream outstream;
    public ObjectInputStream instream;
    private long score;

    public SenderThread(String ip, int port) {
        this.ip = ip;
        this.port = port;
        handler = null;
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
                    TimeLog.coordinator.add(((Image2)inputMessage.obj).frameNumber + ""); // Wait for Result
                    outstream.writeObject(inputMessage.obj);
                    //outstream.flush();
                    outstream.reset();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        Socket socket;
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
                    WorkerMessage msg = (WorkerMessage) instream.readObject();
                    Result2 res = (Result2) msg.msg;
                    score = msg.score;
                    TimeLog.coordinator.finish(res.frameNumber + ""); // Finish
                    //Log.d(TAG, "Got response from the server: isInner = "
                            //+ res.isInner + ", frameNumber = " + res.frameNumber);
                    //Log.d(TAG, "frameNumber = " + res.frameNumber + ", msg = " + res.msg);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
