package com.example.edgedashanalytics.advanced.coordinator;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Deque;

public class EDAWorker {
    public String ip;
    public ObjectOutputStream outstream;
    public ObjectInputStream instream;
    public long score; // should be removed later

    public int innerWaiting, outerWaiting;
    public int innerProcTime, outerProcTime;
    public int networkTime;
    public Deque<FrameRecord> frameHistory;

    public EDAWorker(String ip) {
        this.ip = ip;
    }
}
