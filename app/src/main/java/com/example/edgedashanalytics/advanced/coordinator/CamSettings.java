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
    private final Parameters p;

    public CamSettings(boolean isInner) {
        this.isInner = isInner;
        if (isInner)
            this.p = new Parameters(1, 1, 1);
        else
            this.p = new Parameters(2, 2, 2);
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

    public int increase(int amount) {
        for (int loop = 0; loop < amount; loop++) {
            if (isInner) {
                // R >= Q >= F
                if (p.Ri > p.Qi)
                    p.increaseQ();
                else if (p.Qi > p.Fi)
                    p.increaseF();
                else if (!p.increaseR())
                    return loop;
            } else {
                // R + Q >= F * 2, Q first
                if (p.Ri + p.Qi >= (p.Fi + 1) * 2)
                    p.increaseF();
                else if (!p.increaseQ()) {
                    if (!p.increaseR())
                        return loop;
                }
            }
        }
        return amount;
    }

    // between 0 and 9, inclusive
    public int getTotalLevel() {
        return p.Ri + p.Qi + p.Fi;
    }

    public int decrease(int amount) {
        for (int loop = 0; loop < amount; loop++) {
            if (isInner) {
                // R >= Q >= F
                if (p.Ri > p.Qi)
                    p.decreaseR();
                else if (p.Qi > p.Fi)
                    p.decreaseQ();
                else if (!p.decreaseF())
                    return loop;
            } else {
                // R + Q >= F * 2, Q first
                if (p.Ri + p.Qi <= p.Fi * 2)
                    p.decreaseF();
                else if (!p.decreaseQ()) {
                    if (!p.decreaseR())
                        return loop;
                }
            }
        }
        return amount;
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
                if (!p.increaseR_old()) {
                    p.increaseQ_old();
                }
            }
            else if (!p.increaseQ_old())
                return loop;
        }
        return amount;
    }

    private int decreaseRQOuter(int amount) {
        for (int loop = 0; loop < amount; loop++) {
            if (p.Q == 0) {
                if (!p.decreaseR_old())
                    return loop;
            }
            else if (!p.decreaseQ_old())
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
