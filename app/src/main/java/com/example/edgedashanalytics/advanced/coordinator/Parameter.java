package com.example.edgedashanalytics.advanced.coordinator;

import android.util.Size;

public class Parameter {

    private static final double FPS_MIN = 1, FPS_MAX = 30;
    private static final double INC_MULTIPLIER = 1.03, DEC_MULTIPLIER = 0.95;

    public boolean isInner;
    public double fps;

    public Parameter(boolean isInner) {
        this.isInner = isInner;
        this.fps = 1;
    }

    public double decrease() {
        double multiplier = Controller.sensitive == 0 ? 1 : 2;
        double val = Math.max(FPS_MIN, fps * Math.pow(DEC_MULTIPLIER, multiplier));
        double ret = fps - val;
        fps = val;
        return ret;
    }

    public double increase() {
        double multiplier = Controller.sensitive == 0 ? 1 : 2;
        double val = Math.min(FPS_MAX, fps * Math.pow(INC_MULTIPLIER, multiplier));
        double ret = val - fps;
        fps = val;
        return ret;
    }
}
