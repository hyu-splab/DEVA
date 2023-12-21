package com.example.edgedashanalytics.advanced.coordinator;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.Size;

import com.example.edgedashanalytics.advanced.common.TimeLog;
import com.example.edgedashanalytics.util.Constants;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

public class EDACam extends Thread {
    static final String TAG = "ReceiverThread";

    private final Handler handler;
    private final int msgCode;
    private final int port;

    public ObjectInputStream inStream;
    public ObjectOutputStream outStream;
    private Socket socket;
    public CamSettings camSettings;

    public EDACam(Handler handler, boolean isInner) {
        // handler: the handler for the processing thread to hand over the image data
        this.handler = handler;
        this.msgCode = isInner ? Constants.IMAGE_INNER : Constants.IMAGE_OUTER;
        this.port = isInner ? Constants.PORT_INNER : Constants.PORT_OUTER;
        this.socket = null;
        camSettings = new CamSettings(isInner);
    }

    @Override
    public void run() {
        try {
            setup();
            doWork();
        } catch (Exception e) {
            if (socket != null && socket.isConnected())
                try { socket.close(); } catch (Exception e2) { e2.printStackTrace(); }
            e.printStackTrace();
        }
    }

    private void setup() throws Exception {
        ServerSocket serverSocket = new ServerSocket(port);
        Log.d(TAG, "opened a port " + port);
        socket = serverSocket.accept();
        inStream = new ObjectInputStream(socket.getInputStream());

        // to avoid race condition, make sure our first signal is fully sent to the camera
        // before we perform the periodical checks
        ObjectOutputStream tempOutStream = new ObjectOutputStream(socket.getOutputStream());
        // First time connected, send initial settings
        sendSettings(tempOutStream);
        outStream = tempOutStream;
    }

    private void doWork() throws Exception {
        while (true) {
            int frameNum = inStream.readInt();
            byte[] data = (byte[]) inStream.readObject();
            if (AdvancedMain.isFinished)
                continue;
            if (AdvancedMain.totalCount == 1) {
                AdvancedMain.startTime = System.currentTimeMillis();
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        TimeLog.coordinator.writeLogs();
                    }
                }, Constants.EXPERIMENT_DURATION);
            }

            Message msg = Message.obtain();
            msg.arg1 = frameNum;
            msg.what = msgCode;
            msg.obj = data;

            handler.sendMessage(msg);
        }
    }

    public void sendSettings(ObjectOutputStream outputStream) {
        //Log.v(TAG, "Sending settings (" + camSettings.getRQLevel() + "/" + camSettings.getRQListSize() + ")");
        sendSettings(outputStream, camSettings.getR(), camSettings.getQ(), camSettings.getF());
    }

    public static void sendSettings(ObjectOutputStream outputStream, Size resolution, int quality, int frameRate) {
        try {
            Log.v(TAG, "Sending settings: " + resolution.getWidth() + " " + resolution.getHeight() + " " + quality + " " + frameRate);
            outputStream.writeInt(resolution.getWidth());
            outputStream.writeInt(resolution.getHeight());
            outputStream.writeInt(quality);
            outputStream.writeInt(frameRate);
            outputStream.flush();
            outputStream.reset();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
