package com.example.edgedashanalytics.advanced.coordinator;

import android.util.Size;

/*
Second version of CamSettings.

In V2, the major difference is that it treats R and Q as a whole since it seems that there is no
reason to separate these parameters so far. Therefore, for these parameters we will have a 'pointer'
that moves on a sorted list, of which each entry has both a R value and a Q value.

Some combinations are strictly worse than another; i.e. its data size is larger and its accuracy is
also worse. The purpose of having such a sorted list is to exclude such 'unnecessary' combinations,
and only a simple 'move pointer' operation will suffice to get a heavier / lighter parameter
combination.

Parameter F, however, still needs to be treated as a separate parameter since its effect is
unrelated to the accuracy unlike R and Q, and will only be used for decreasing workload for the
workers and the network.
 */
public class CamSettings {
    private static final String TAG = "CamSettings";

    public boolean isInner;
    private static final int MAX_F = 30, MIN_F = 2;
    private Parameters p;

    private void initInner() {
        /*p.R = new Size(1280, 720);
        p.Q = 70;
        p.F = 15;*/
    }

    private void initOuter() {
        /*p.R = new Size(1280, 720);
        p.Q = 70;
        p.F = 30;*/
    }

    public CamSettings(boolean isInner) {
        this.isInner = isInner;
        this.p = new Parameters();
        if (isInner)
            initInner();
        else
            initOuter();
    }

    public Size getR() {
        return p.R;
    }

    public int getQ() {
        return p.Q;
    }

    public int getF() {
        return p.F;
    }

    private int increaseRQInner(int amount) {
        for (int loop = 0; loop < amount; loop++) {
            if (!p.increaseLower())
                return loop;
        }
        return amount;
    }

    private int decreaseRQInner(int amount) {
        for (int loop = 0; loop < amount; loop++) {
            if (!p.decreaseHigher())
                return loop;
        }
        return amount;
    }

    private int increaseRQOuter(int amount) {
        for (int loop = 0; loop < amount; loop++) {
            if (p.Q == 0) {
                if (!p.increaseR()) {
                    p.increaseQ();
                }
            }
            else if (!p.increaseQ())
                return loop;
        }
        return amount;
    }

    private int decreaseRQOuter(int amount) {
        for (int loop = 0; loop < amount; loop++) {
            if (p.Q == 0) {
                if (!p.decreaseR())
                    return loop;
            }
            else if (!p.decreaseQ())
                return loop;
        }
        return amount;
    }

    public int increaseRQ(int amount) {
        if (isInner)
            return increaseRQInner(amount);
        else
            return increaseRQOuter(amount);
    }

    public int decreaseRQ(int amount) {
        if (isInner)
            return decreaseRQInner(amount);
        else
            return decreaseRQOuter(amount);
    }

    public int increaseF(int amount) {
        int real = Math.min(amount, MAX_F - p.F);
        p.F += real;
        return real;
    }

    public int decreaseF(int amount) {
        int real = Math.min(amount, p.F - MIN_F);
        p.F -= real;
        return real;
    }

    public double getNormalizedLevel() {
        //return p.R.getWidth() * p.R.getHeight() * getSizeForQuality();
        return p.Ri + p.Qi;
    }

    private double getSizeForQuality() {
        switch (p.Q) {
            case 0: return 0.02;
            case 10: return 0.04;
            case 20: return 0.07;
            case 30: return 0.09;
            case 40: return 0.11;
            case 50: return 0.13;
            case 60: return 0.15;
            case 70: return 0.18;
            case 80: return 0.24;
            case 90: return 0.35;
            case 100: return 1.0;
            default:
                throw new RuntimeException("Unknown quality: " + p.Q);
        }
    }
}
