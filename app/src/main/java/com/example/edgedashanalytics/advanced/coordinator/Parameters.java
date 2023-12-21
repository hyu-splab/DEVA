package com.example.edgedashanalytics.advanced.coordinator;

import android.util.Size;

public class Parameters {
    // Read-only; don't write these outside this class
    Size R;
    int Q;
    int F;

    int Ri;
    int Qi;

    private static final Size[] resolutions = {
            new Size(640, 360),
            new Size(704, 396),
            new Size(768, 432),
            new Size(832, 468),
            new Size(896, 504),
            new Size(960, 540),
            new Size(1024, 576),
            new Size(1088, 612),
            new Size(1152, 648),
            new Size(1216, 684),
            new Size(1280, 720),
    };

    private static final int[] qualities = {
            0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100
    };

    public Parameters() {
        this.Ri = resolutions.length - 5;
        this.Qi = qualities.length - 5;
        this.R = resolutions[Ri];
        this.Q = qualities[Qi];
        this.F = 15;
    }

    public Parameters(Size R, int Q, int F) {
        this.R = R;
        this.Q = Q;
        this.F = F;
    }

    public boolean increaseQ() {
        if (Qi == qualities.length - 1)
            return false;
        Q = qualities[++Qi];
        return true;
    }

    public boolean decreaseQ() {
        if (Qi == 0)
            return false;
        Q = qualities[--Qi];
        return true;
    }

    public boolean increaseR() {
        if (Ri == resolutions.length - 1)
            return false;
        R = resolutions[++Ri];
        return true;
    }

    public boolean decreaseR() {
        if (Ri == 0)
            return false;
        R = resolutions[--Ri];
        return true;
    }

    // Tiebreak rule: keep R higher
    public boolean increaseLower() {
        if (Qi < Ri)
            return increaseQ();
        else
            return increaseR();
    }

    public boolean decreaseHigher() {
        if (Ri > Qi)
            return decreaseR();
        else
            return decreaseQ();
    }
}
