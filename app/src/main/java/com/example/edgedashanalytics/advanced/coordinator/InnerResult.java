package com.example.edgedashanalytics.advanced.coordinator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class InnerResult {
    private static final String TAG = "InnerResult";
    private final List<Result> results;
    public InnerResult() {
        results = new ArrayList<>();
    }

    public void addResult(int frameNum, boolean isDistracted) {
        results.add(new Result(frameNum, isDistracted));
    }

    public String getResultString() {
        StringBuilder sb = new StringBuilder();
        for (Result res : results) {
            sb.append(res.frameNum).append(",").append(res.isDistracted ? "1" : "0").append("\n");
        }
        return sb.toString();
    }

    public InnerAccuracyResult calcAccuracy(InnerResult baseResult) {
        List<Result> baseResults = baseResult.results;

        results.sort(new FrameComp());
        baseResults.sort(new FrameComp());

        int totalCount = results.size();
        int distracted = 0, nonDistracted = 0, distractedWrong = 0, nonDistractedWrong = 0;

        int ri = 0, bi = 0;

        while (ri < results.size() && bi < baseResults.size()) {
            Result base = baseResults.get(bi);
            Result cur = results.get(ri);

            if (base.frameNum != cur.frameNum) {
                if (base.frameNum < cur.frameNum) {
                    bi++;
                }
                else {
                    ri++;
                }
                continue;
            }

            if (base.isDistracted) {
                if (cur.isDistracted)
                    distracted++;
                else
                    nonDistractedWrong++;
            }
            else {
                if (cur.isDistracted)
                    distractedWrong++;
                else
                    nonDistracted++;
            }
            bi++;
            ri++;
        }

        return new InnerAccuracyResult(totalCount, distracted, nonDistracted, distractedWrong, nonDistractedWrong);
    }

    public static class Result {
        int frameNum;
        boolean isDistracted;

        public Result(int frameNum, boolean isDistracted) {
            this.frameNum = frameNum;
            this.isDistracted = isDistracted;
        }
    }

    public static class InnerAccuracyResult {
        public int count, distracted, nonDistracted, distractedWrong, nonDistractedWrong;

        public InnerAccuracyResult(int count, int distracted, int nonDistracted, int distractedWrong, int nonDistractedWrong) {
            this.count = count;
            this.distracted = distracted;
            this.nonDistracted = nonDistracted;
            this.distractedWrong = distractedWrong;
            this.nonDistractedWrong = nonDistractedWrong;
        }
    }

    public static class FrameComp implements Comparator<Result> {

        @Override
        public int compare(Result o1, Result o2) {
            return o1.frameNum - o2.frameNum;
        }
    }
}
