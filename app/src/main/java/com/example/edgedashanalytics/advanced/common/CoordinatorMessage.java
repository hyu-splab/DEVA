package com.example.edgedashanalytics.advanced.common;

import java.io.Serializable;

public class CoordinatorMessage implements Serializable {
    // 1: image data, 2: restart, 3: quit
    public int type;
    public Object data;

    public CoordinatorMessage(int type, Object data) {
        this.type = type;
        this.data = data;
    }
}
