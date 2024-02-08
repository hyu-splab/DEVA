package com.example.edgedashanalytics.advanced.coordinator;

import android.util.Size;

public class Parameters {
    // Read-only; don't write these outside this class
    Size R;
    int Q;
    int F;

    int Ri;
    int Qi;
    int Fi;

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

    // 4-level settings
    private static final Size[] resolutions_new = {
            new Size(640, 360),
            new Size(854, 480),
            new Size(960, 540),
            new Size(1280, 720)
    };

    private static final int[] qualities_new = {
            25, 50, 75, 100
    };

    private static final int[] fps_new = {
            5, 10, 15, 20
    };

    public Parameters() {
        this(1, 1, 1);
    }

    public Parameters(int Ri, int Qi, int Fi) {
        this.Ri = Ri;
        this.Qi = Qi;
        this.Fi = Fi;
        this.R = resolutions_new[Ri];
        this.Q = qualities_new[Qi];
        this.F = fps_new[Fi];
    }

    public boolean increaseR() {
        if (Ri == resolutions_new.length - 1)
            return false;
        R = resolutions_new[++Ri];
        return true;
    }

    public boolean decreaseR() {
        if (Ri == 0)
            return false;
        R = resolutions_new[--Ri];
        return true;
    }

    public boolean increaseQ() {
        if (Qi == qualities_new.length - 1)
            return false;
        Q = qualities_new[++Qi];
        return true;
    }

    public boolean decreaseQ() {
        if (Qi == 0)
            return false;
        Q = qualities_new[--Qi];
        return true;
    }

    public boolean increaseF() {
        if (Fi == fps_new.length)
            return false;
        F = fps_new[++Fi];
        return true;
    }

    public boolean decreaseF() {
        if (Fi == 0)
            return false;
        F = fps_new[--Fi];
        return true;
    }


    /*public Parameters() {
        this.Ri = resolutions.length - 5;
        this.Qi = qualities.length - 5;
        this.R = resolutions[Ri];
        this.Q = qualities[Qi];
        this.F = 15;
    }

    public Parameters(int Ri, int Qi, int F) {
        this.Ri = Ri;
        this.Qi = Qi;
        this.F = F;
        this.R = resolutions[Ri];
        this.Q = qualities[Qi];
    }*/

    public boolean increaseQ_old() {
        if (Qi == qualities.length - 1)
            return false;
        Q = qualities[++Qi];
        return true;
    }

    public boolean decreaseQ_old() {
        if (Qi == 0)
            return false;
        Q = qualities[--Qi];
        return true;
    }

    public boolean increaseR_old() {
        if (Ri == resolutions.length - 1)
            return false;
        R = resolutions[++Ri];
        return true;
    }

    public boolean decreaseR_old() {
        if (Ri == 0)
            return false;
        R = resolutions[--Ri];
        return true;
    }

    // Tiebreak rule: keep R higher
    public boolean increaseLower() {
        if (Qi < Ri)
            return increaseQ_old();
        else
            return increaseR_old();
    }

    public boolean decreaseHigher() {
        if (Ri > Qi)
            return decreaseR_old();
        else
            return decreaseQ_old();
    }
}
