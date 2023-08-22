package com.example.edgedashanalytics.util.connection;

import static com.example.edgedashanalytics.util.connection.Receiver.IMAGE_INNER;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.example.edgedashanalytics.page.main.MainActivity;
import com.example.edgedashanalytics.util.log.TimeLog;
import com.example.edgedashanalytics.util.video.analysis.Image2;
import com.example.edgedashanalytics.util.video.analysis.Result2;
import com.example.edgedashanalytics.util.worker.ProcessorThread;
import com.example.edgedashanalytics.util.worker.WorkerThread;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

public class WorkerServer {
    private static final String TAG = "WorkerServer";
    public static final int PORT = 5555;
    private Handler inHandler;
    private Thread thread;

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
                            try {
                                long score = ProcessorThread.queue.size();
                                Result2 res = (Result2) msg.obj;
                                if (res.isInner)
                                    innerCount++;
                                else
                                    outerCount++;
                                WorkerMessage wMsg = new WorkerMessage(score, res);

                                TimeLog.worker.finish(((Result2)msg.obj).frameNumber + ""); // Return Result
                                outstream.writeObject(wMsg);
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
                    int cnt = 0;
                    while (true) {
                        // start
                        Image2 image = (Image2) instream.readObject();
                        if (Connection.isFinished)
                            continue;
                        if (cnt == 0) {
                            new Timer().schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    TimeLog.worker.writeLogs();
                                }
                            }, MainActivity.experimentDuration);
                        }
                        TimeLog.worker.start(image.frameNumber + ""); // Enqueue
                        cnt++;

                        ProcessorThread.queue.offer(image);

                        /*if (cnt % 10 == 0) {
                            StringBuilder sb = new StringBuilder();
                            sb.append("Thread working progress: ");
                            for (int i = 0; i < WorkerThread.N_THREAD; i++) {
                                sb.append(WorkerThread.pt[i].workCount).append(" ");
                            }
                            Log.d(TAG, sb.toString());
                        }*/
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
