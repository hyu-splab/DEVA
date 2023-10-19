package com.example.edgedashanalytics.advanced.common;

import java.io.Serializable;

public class WorkerMessage implements Serializable {
    public Object msg;

    public WorkerMessage(Object msg) {
        this.msg = msg;
    }
}
