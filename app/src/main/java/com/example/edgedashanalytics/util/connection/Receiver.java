package com.example.edgedashanalytics.util.connection;

import android.os.Handler;

public class Receiver {
    static public final int IMAGE_INNER = 1001;
    static public final int IMAGE_OUTER = 1002;

    static public final int PORT_INNER = 5575;
    static public final int PORT_OUTER = 5576;

    static public void run(Handler handler, int msgCode, int port) {
        ReceiverThread thread = new ReceiverThread(handler, msgCode, port);
        thread.start();
    }
}
