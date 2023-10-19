package com.example.edgedashanalytics.advanced.coordinator;

import com.example.edgedashanalytics.advanced.common.FrameResult;
import com.example.edgedashanalytics.advanced.common.WorkerStatus;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayDeque;
import java.util.Deque;

public class EDAWorker {
    public String ip;
    public ObjectOutputStream outstream;
    public ObjectInputStream instream;
    public long score; // should be removed later

    public WorkerStatus status;

    public EDAWorker(String ip) {
        status = new WorkerStatus();
        this.ip = ip;
    }
}
