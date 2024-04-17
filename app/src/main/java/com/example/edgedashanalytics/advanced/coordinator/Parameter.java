package com.example.edgedashanalytics.advanced.coordinator;

import android.util.Size;

public class Parameter {

    private static final double INNER_F_MIN = 1, OUTER_F_MIN = 1, INNER_F_MAX = 30, OUTER_F_MAX = 30;
    public static final double INNER_FPS_DEFAULT = 4, OUTER_FPS_DEFAULT = 6;
    private static final double INC_MULTIPLIER = 1.03, DEC_MULTIPLIER = 0.95;
    private static final double INC_MIN = 0.3, DEC_MIN = 0.5;
    public boolean isInner;
    public double fps;

    public Parameter(boolean isInner) {
        this.isInner = isInner;
        this.fps = 1;
    }

    public double decrease() {
        double multiplier = Controller.sensitive == 0 ? 1 : 2;
        double val = Math.max(isInner ? INNER_F_MIN : OUTER_F_MIN, Math.min(fps - DEC_MIN * multiplier, fps * Math.pow(DEC_MULTIPLIER, multiplier)));
        double ret = fps - val;
        fps = val;
        return ret;
    }

    public double increase() {
        double multiplier = Controller.sensitive == 0 ? 1 : 2;
        double val = Math.min(isInner ? INNER_F_MAX : OUTER_F_MAX, Math.max(fps + INC_MIN * multiplier, fps * Math.pow(INC_MULTIPLIER, multiplier)));
        double ret = val - fps;
        fps = val;
        return ret;
    }
}
