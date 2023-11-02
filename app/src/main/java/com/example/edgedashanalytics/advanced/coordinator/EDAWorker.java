package com.example.edgedashanalytics.advanced.coordinator;

import com.example.edgedashanalytics.advanced.common.FrameResult;
import com.example.edgedashanalytics.advanced.common.WorkerStatus;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayDeque;
import java.util.Deque;

public class EDAWorker {
    public int workerNum;
    public String ip;
    public ObjectOutputStream outstream;
    public ObjectInputStream instream;
    public long score; // should be removed later

    public WorkerStatus status;

    public EDAWorker(int workerNum, String ip) {
        status = new WorkerStatus();
        this.workerNum = workerNum;
        this.ip = ip;
    }
}
