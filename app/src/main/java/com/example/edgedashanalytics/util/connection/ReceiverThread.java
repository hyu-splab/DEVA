package com.example.edgedashanalytics.util.connection;

import static com.example.edgedashanalytics.util.connection.Connection.isFinished;
import static com.example.edgedashanalytics.util.connection.Connection.startTime;
import static com.example.edgedashanalytics.util.connection.Connection.totalCount;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.example.edgedashanalytics.page.main.MainActivity;
import com.example.edgedashanalytics.util.log.TimeLog;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

public class ReceiverThread extends Thread {
    static final String TAG = "ReceiverThread";

    private Handler handler;
    private int msgCode;
    private int port;
    
    public ReceiverThread(Handler handler, int msgCode, int port) {
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