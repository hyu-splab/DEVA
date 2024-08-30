package com.example.edgedashanalytics.advanced.worker;

import static com.example.edgedashanalytics.advanced.coordinator.MainRoutine.Experiment.*;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.example.edgedashanalytics.advanced.common.CoordinatorMessage;
import com.example.edgedashanalytics.advanced.common.WorkerInitialInfo;
import com.example.edgedashanalytics.page.main.MainActivity;
import com.example.edgedashanalytics.util.Constants;
import com.example.edgedashanalytics.advanced.common.FrameData;
import com.example.edgedashanalytics.advanced.common.WorkerResult;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class WorkerServer {
    private static final String TAG = "WorkerServer";
    private Handler inHandler;
    private final Thread thread;

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
                serverSocket = new ServerSocket(Constants.PORT_WORKER);
                Log.d(TAG, "Worker opened port " + Constants.PORT_WORKER);
                Socket socket = serverSocket.accept();
                ObjectInputStream instream = new ObjectInputStream(socket.getInputStream());
                ObjectOutputStream outstream = new ObjectOutputStream(socket.getOutputStream());

                outstream.writeObject(new WorkerInitialInfo(MainActivity.temperatureNames, MainActivity.frequencyNames));

                Thread resThread = new Thread(() -> {
                    Looper.prepare();
                    inHandler = new Handler(Looper.myLooper()) {
                        @Override
                        public void handleMessage(Message msg) {
                            try {
                                WorkerResult res = (WorkerResult) msg.obj;

                                outstream.writeObject(res);
                                outstream.flush();
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
                        CoordinatorMessage cMsg = (CoordinatorMessage) instream.readObject();

                        if (cMsg.type != 1) {
                            Message mainMsg = Message.obtain();
                            mainMsg.arg1 = cMsg.type;
                            Handler handler = MainActivity.mainHandler;
                            Log.v(TAG, "Sending mainHandler restart message after 3s");
                            try {
                                Thread.sleep(3000);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            handler.sendMessage(mainMsg);
                            continue;
                        }

                        FrameData image = (FrameData) cMsg.data;

                        if (image.isBusy != E_isBusy) {
                            Log.v(TAG, "isBusy status changed to " + (image.isBusy ? "busy" : "free"));
                            E_isBusy = image.isBusy;
                        }

                        long workerStartTime = System.currentTimeMillis();
                        ProcessorThread.workerStartTimeMap.put(image.frameNum, workerStartTime);
                        if (E_isFinished)
                            continue;

                        ProcessorThread.queue.offer(image);
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
}
