package com.example.edgedashanalytics.advanced.coordinator;

import static com.example.edgedashanalytics.advanced.coordinator.MainRoutine.Experiment.finishExperiments;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.Size;

import com.example.edgedashanalytics.page.main.MainActivity;
import com.example.edgedashanalytics.util.Constants;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class EDACam extends Thread {
    static final String TAG = "EDACam";
    private static final int PORT_INNER = 5555;
    private static final int PORT_OUTER = 5556;


    private final String ip;
    private final int msgCode;

    public ObjectInputStream inStream;
    public ObjectOutputStream outStream;
    private Socket socket;
    public Parameter camParameter;
    public boolean isInner;

    public EDACam(String ip, boolean isInner) {
        this.ip = ip;
        this.isInner = isInner;
        this.msgCode = isInner ? Constants.IMAGE_INNER : Constants.IMAGE_OUTER;
        this.socket = null;
        camParameter = new Parameter(isInner);
    }

    @Override
    public void run() {
        try {
            setup();
            doWork();
        } catch (Exception e) {
            e.printStackTrace();
            try {
                Thread.sleep(1000);
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            if (socket != null && socket.isConnected())
                try { socket.close(); } catch (Exception e2) { e2.printStackTrace(); }
        }
    }

    private void setup() throws Exception {
        while (true) {
            try {
                socket = new Socket(ip, isInner ? PORT_INNER : PORT_OUTER);
            } catch (Exception e) {
                Log.v(TAG, "cannot connect to camera " + ip + "yet, retrying in 1s...");
                Thread.sleep(1000);
                continue;
            }
            break;
        }

        ObjectOutputStream tempOutStream = new ObjectOutputStream(socket.getOutputStream());
        inStream = new ObjectInputStream(socket.getInputStream());

        // to avoid race condition, make sure our first signal is fully sent to the camera
        // before we perform the periodical checks
        // First time connected, send initial settings
        sendSettings(tempOutStream);
        outStream = tempOutStream;
    }

    private void doWork() throws Exception {
        while (true) {
            int frameNum = inStream.readInt();
            byte[] data = (byte[]) inStream.readObject();
            int waiting = inStream.readInt(); // not used yet, can remove later along with dashcam

            MainRoutine.received++;

            if (MainRoutine.received % 100 == 0) {
                Log.v(TAG, "Processed: " + MainRoutine.processed + "/" + MainRoutine.received + "(" + MainRoutine.processed * 100 / (double) MainRoutine.received + "%)");
            }

            CommunicatorMessage msg = new CommunicatorMessage(1, isInner, frameNum, data, System.currentTimeMillis());
            if (Communicator.msgQueue.remainingCapacity() == 0) {
                Log.e(TAG, "Message queue full! Stopping experiment...");
                finishExperiments();
                return;
            }
            Communicator.msgQueue.put(msg);
        }
    }

    public void sendSettings() {
        sendSettings(outStream, camParameter.fps);
    }

    public void sendSettings(ObjectOutputStream outputStream) {
        sendSettings(outputStream, camParameter.fps);
    }

    public static void sendSettings(ObjectOutputStream outputStream, double frameRate) {
        try {
            outputStream.writeDouble(frameRate);
            outputStream.flush();
            outputStream.reset();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
