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
public class CamSettingsV2 {
    private static final String TAG = "CamSettingsV2";

    private static final int MAX_F = 30, MIN_F = 5;
    private int frameRate;

    private RQ[] RQList;
    private int RQLevel;
    public CamSettingsV2(boolean isInner) {
        this(isInner ? RQ.innerDataSize : RQ.outerDataSize,
                isInner ? RQ.innerDataSize.length - 1 : RQ.outerDataSize.length - 1);
    }

    public CamSettingsV2(RQ[] RQList, int RQlevel) {
        frameRate = 30;
        this.RQList = RQList;
        this.RQLevel = RQlevel;

        if (RQlevel < 0 || RQlevel >= RQList.length) {
            throw new RuntimeException("RQlevel " + RQlevel + " out of range: 0 ~ " + (RQList.length - 1));
        }
    }

    public Size getR() {
        return RQList[RQLevel].resolution;
    }

    public int getQ() {
        return RQList[RQLevel].quality;
    }

    public int getF() {
        return frameRate;
    }

    public int increaseRQ(int amount) {
        int real = Math.min(amount, RQList.length - 1 - RQLevel);
        RQLevel += real;
        return real;
    }

    public int decreaseRQ(int amount) {
        int real = Math.min(amount, RQLevel);
        RQLevel -= real;
        return real;
    }

    public int increaseF(int amount) {
        int real = Math.min(amount, MAX_F - frameRate);
        frameRate += real;
        return real;
    }

    public int decreaseF(int amount) {
        int real = Math.min(amount, frameRate - MIN_F);
        frameRate -= real;
        return real;
    }

    public double getNormalizedLevel() {
        return (double)RQLevel / RQList.length;
    }
}
