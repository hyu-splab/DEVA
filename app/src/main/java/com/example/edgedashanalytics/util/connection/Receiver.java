package com.example.edgedashanalytics.util.connection;

import static com.example.edgedashanalytics.util.connection.Connection.startTime;
import static com.example.edgedashanalytics.util.connection.Connection.totalCount;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.example.edgedashanalytics.page.main.MainActivity;
import com.example.edgedashanalytics.util.log.TimeLog;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

public class Receiver extends Thread {
    /*

     */

    static public final int IMAGE_INNER = 1001;
    static public final int IMAGE_OUTER = 1002;

    static public final int PORT_INNER = 5575;
    static public final int PORT_OUTER = 5576;

    static final String TAG = "ReceiverThread";

    private Handler handler;
    private int msgCode;
    private int port;

    public Receiver(Handler handler, int msgCode, int port) {
        // handler: the handler for the processing thread to hand over the image data
        this.handler = handler;
        this.msgCode = msgCode;
        this.port = port;
    }

    @Override
    public void run() {
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(port);
            Log.d(TAG, "opened a port " + port);
            Socket socket = serverSocket.accept();
            ObjectInputStream instream = new ObjectInputStream(socket.getInputStream());
            ObjectOutputStream outstream = new ObjectOutputStream(socket.getOutputStream());
            try {
                while (true) {
                    byte[] data = (byte[]) instream.readObject();
                    if (Connection.isFinished)
                        continue;
                    if (totalCount == 1) {
                        startTime = System.currentTimeMillis();
                        new Timer().schedule(new TimerTask() {
                            @Override
                            public void run() {
                                TimeLog.coordinator.writeLogs();
                            }
                        }, MainActivity.experimentDuration);
                    }

                    //outstream.writeObject("ok");

                    Message msg = Message.obtain();
                    msg.what = msgCode;
                    msg.obj = data;

                    handler.sendMessage(msg);
                }
            } catch (Exception e) {
                e.printStackTrace();
                socket.close();
                throw new RuntimeException();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
