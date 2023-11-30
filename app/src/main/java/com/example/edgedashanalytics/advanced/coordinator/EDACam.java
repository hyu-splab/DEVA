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

    private boolean isInner;

    private Handler handler;
    private int msgCode;
    private int port;

    public ObjectInputStream instream;
    public ObjectOutputStream outstream;
    private Socket socket;
    public CamSettingsV2 camSettings;

    public EDACam(Handler handler, boolean isInner) {
        // handler: the handler for the processing thread to hand over the image data
        this.isInner = isInner;
        this.handler = handler;
        this.msgCode = isInner ? Constants.IMAGE_INNER : Constants.IMAGE_OUTER;
        this.port = isInner ? Constants.PORT_INNER : Constants.PORT_OUTER;
        this.socket = null;
        camSettings = new CamSettingsV2(isInner);
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
        instream = new ObjectInputStream(socket.getInputStream());

        // to avoid race condition, make sure our first signal is fully sent to the camera
        // before we perform the periodical checks
        ObjectOutputStream tempOutstream = new ObjectOutputStream(socket.getOutputStream());
        // First time connected, send initial settings
        sendSettings(tempOutstream);
        outstream = tempOutstream;
    }

    private void doWork() throws Exception {
        while (true) {
            int frameNum = instream.readInt();
            byte[] data = (byte[]) instream.readObject();
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
        sendSettings(outputStream, camSettings.getR(), camSettings.getQ(), camSettings.getF());
    }

    public static void sendSettings(ObjectOutputStream outputStream, Size resolution, int quality, int frameRate) {
        try {
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
