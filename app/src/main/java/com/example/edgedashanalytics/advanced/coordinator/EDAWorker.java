package com.example.edgedashanalytics.advanced.coordinator;

import com.example.edgedashanalytics.advanced.common.WorkerStatus;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class EDAWorker {
    public int workerNum;
    public String ip;
    public ObjectOutputStream outstream;
    public ObjectInputStream instream;

    public WorkerStatus status;

    public EDAWorker(int workerNum, String ip) {
        status = new WorkerStatus();
        this.workerNum = workerNum;
        this.ip = ip;
    }
}
