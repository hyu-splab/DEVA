package com.example.edgedashanalytics.util.connection;

import java.io.Serializable;

public class WorkerMessage implements Serializable {
    long score;
    Object msg;

    public WorkerMessage(long score, Object msg) {
        this.score = score;
        this.msg = msg;
    }
}
