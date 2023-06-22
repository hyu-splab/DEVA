package com.example.edgedashanalytics.util.connection;

import java.io.Serializable;

public class WorkerMessage implements Serializable {
    enum Type {
        AVAILABLE,
        RESULT
    }

    Type type;
    Object msg;

    public WorkerMessage(Type type, Object msg) {
        this.type = type;
        this.msg = msg;
    }
}
