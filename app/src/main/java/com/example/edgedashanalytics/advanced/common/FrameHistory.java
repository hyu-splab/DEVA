package com.example.edgedashanalytics.advanced.common;

import java.util.ArrayDeque;
import java.util.Deque;

public class FrameHistory {
    public static final int MAX_HISTORY = 10;
    public Deque<FrameRecord> innerFrames, outerFrames;

    public FrameHistory() {
        innerFrames = new ArrayDeque<>();
        outerFrames = new ArrayDeque<>();
    }

    public void addRecord(FrameRecord record) {
        Deque<FrameRecord> frames = record.isInner ? innerFrames : outerFrames;
        frames.add(record);
        if (frames.size() > MAX_HISTORY) {
            frames.pop();
        }
    }

    /*
    TODO: All values here are temporary.
     */
    private static final double NETWORK_SLOW = 200;
    private static final double PROCESS_SLOW_INNER = 100, PROCESS_SLOW_OUTER = 100;
    private static final double WAIT_SLOW = 100;

    private static final double NETWORK_FAST = 50;
    private static final double WAIT_FAST = 20;

    public void analyze() {
        double avgInnerWait = 0.0;
        double avgInnerProcess = 0.0;
        double avgOuterWait = 0.0;
        double avgOuterProcess = 0.0;
        double avgNetworkTime = 0.0;

        int totalNetworkTime = 0;

        int totalInnerWait = 0;
        int totalInnerProcess = 0;
        for (FrameRecord record : innerFrames) {
            totalInnerWait += record.waitTime;
            totalInnerProcess += record.processTime;
            totalNetworkTime += record.networkTime;
        }
        if (innerFrames.size() > 0) {
            avgInnerWait = (double)totalInnerWait / innerFrames.size();
            avgInnerProcess = (double)totalInnerProcess / innerFrames.size();
        }

        int totalOuterWait = 0;
        int totalOuterProcess = 0;
        for (FrameRecord record : outerFrames) {
            totalOuterWait += record.waitTime;
            totalOuterProcess += record.processTime;
            totalNetworkTime += record.networkTime;
        }
        if (outerFrames.size() > 0) {
            avgOuterWait = (double)totalOuterWait / outerFrames.size();
            avgOuterProcess = (double)totalOuterProcess / outerFrames.size();
        }

        int totalNum = innerFrames.size() + outerFrames.size();
        if (totalNum > 0) {
            avgNetworkTime = (double)totalNetworkTime / totalNum;
        }

        /*
        Rule #1: Never let TFLife take too much time processing
         */
        if (avgInnerProcess > PROCESS_SLOW_INNER || avgOuterProcess > PROCESS_SLOW_OUTER) {

        }

        /*
        Rule #2: Do not let the network to be saturated
         */
        if (avgNetworkTime > NETWORK_SLOW) {

        }

        /*
        Rule #3: We don't want saturated worker queues
         */
        if (avgInnerWait > WAIT_SLOW && avgOuterWait > WAIT_SLOW) {

        }

        /*
        Rule #4: If everything is more than good, try measuring in higher quality
         */
        if (avgNetworkTime < NETWORK_FAST && avgInnerWait < WAIT_FAST && avgOuterWait < WAIT_FAST) {

        }
    }
}
