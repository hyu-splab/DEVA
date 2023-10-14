package com.example.edgedashanalytics.util.connection;

import java.io.Serializable;

public class WorkerMessage implements Serializable {
    Object msg;

    public WorkerMessage(Object msg) {
        this.msg = msg;
    }
}
