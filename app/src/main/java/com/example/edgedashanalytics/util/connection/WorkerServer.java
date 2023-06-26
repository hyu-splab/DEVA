package com.example.edgedashanalytics.util.connection;

import static com.example.edgedashanalytics.util.connection.Receiver.IMAGE_INNER;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.example.edgedashanalytics.util.log.TimeLog;
import com.example.edgedashanalytics.util.video.analysis.Image2;
import com.example.edgedashanalytics.util.video.analysis.Result2;
import com.example.edgedashanalytics.util.worker.ProcessorThread;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class WorkerServer {
    private static final String TAG = "WorkerServer";
    public static final int PORT = 5555;
    private Handler inHandler;
    private Thread thread;

    // We only need one instance of this server as it will 1 to 1 connect to the central device
    // so no need to provide additional information

    public WorkerServer() {
        thread = new WorkerServerThread();
        inHandler = null;
    }

    public void run() {
        thread.start();
    }

    public Handler getHandler() {
        return inHandler;
    }

    class WorkerServerThread extends Thread {
        @Override
        public void run() {
            ServerSocket serverSocket = null;
            try {
                serverSocket = new ServerSocket(PORT);
                Log.d(TAG, "Worker opened port " + PORT);
                Socket socket = serverSocket.accept();
                ObjectInputStream instream = new ObjectInputStream(socket.getInputStream());
                ObjectOutputStream outstream = new ObjectOutputStream(socket.getOutputStream());

                Thread resThread = new Thread(() -> {
                    Looper.prepare();
                    inHandler = new Handler(Looper.myLooper()) {
                        @Override
                        public void handleMessage(Message msg) {
                            WorkerMessage wMsg = new WorkerMessage(ProcessorThread.queue.size(), msg.obj);
                            try {
                                TimeLog.worker.add(((Result2)msg.obj).frameNumber + ""); // Return Result to Coordinator
                                outstream.writeObject(wMsg);
                                TimeLog.worker.finish(((Result2)msg.obj).frameNumber + ""); // Finish
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    };
                    Looper.loop();
                });
                resThread.start();

                try {
                    while (true) {
                        // start
                        Image2 image = (Image2) instream.readObject();
                        TimeLog.worker.start(image.frameNumber + ""); // Send to WorkerThread

                        //Log.d(TAG, "Worker start processing: " + image.frameNumber);

                        try {
                            ProcessorThread.queue.put(image);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    socket.close(); //소켓 해제
                    throw new RuntimeException();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
