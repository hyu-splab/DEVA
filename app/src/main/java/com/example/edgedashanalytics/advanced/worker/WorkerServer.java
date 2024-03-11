package com.example.edgedashanalytics.advanced.worker;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.example.edgedashanalytics.advanced.common.CoordinatorMessage;
import com.example.edgedashanalytics.advanced.coordinator.AdvancedMain;
import com.example.edgedashanalytics.page.main.MainActivity;
import com.example.edgedashanalytics.util.Constants;
import com.example.edgedashanalytics.advanced.common.TimeLog;
import com.example.edgedashanalytics.advanced.common.Image2;
import com.example.edgedashanalytics.advanced.common.WorkerResult;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

public class WorkerServer {
    private static final String TAG = "WorkerServer";
    private Handler inHandler;
    private final Thread thread;

    public static long innerCount = 0, outerCount = 0;

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

                Thread resThread = new Thread(() -> {
                    Looper.prepare();
                    inHandler = new Handler(Looper.myLooper()) {
                        @Override
                        public void handleMessage(Message msg) {
                            try {
                                WorkerResult res = (WorkerResult) msg.obj;
                                //Log.v(TAG, "Frame " + res.frameNum + ": isInner = " + res.isInner + ", hazards is null = " + (res.hazards == null));
                                if (res.isInner)
                                    innerCount++;
                                else
                                    outerCount++;

                                // Log.d(TAG, "processTime = " + res.processTime);

                                //TimeLog.worker.add(res.frameNum + ""); // Return Result
                                outstream.writeObject(res);
                                outstream.flush();
                                //TimeLog.worker.finish(res.frameNum + ""); // After send
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    };
                    Looper.loop();
                });
                resThread.start();

                try {
                    int cnt = 0;
                    while (true) {
                        CoordinatorMessage cMsg = (CoordinatorMessage) instream.readObject();

                        long sTime = System.currentTimeMillis();
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

                        Image2 image = (Image2) cMsg.data;
                        image.workerStartTime = System.currentTimeMillis();
                        if (AdvancedMain.isFinished)
                            continue;
                        /*if (cnt == 0) {
                            new Timer().schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    TimeLog.worker.writeLogs();
                                }
                            }, Constants.EXPERIMENT_DURATION);
                        }*/
                        //TimeLog.worker.start(image.frameNum + ""); // Enqueue
                        cnt++;

                        ProcessorThread.queue.offer(image);
                        long eTime = System.currentTimeMillis();
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
