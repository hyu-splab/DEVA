package com.example.edgedashanalytics.advanced.test;

import com.example.edgedashanalytics.util.video.analysis.Frame;
import com.example.edgedashanalytics.util.video.analysis.InnerFrame;

import java.util.ArrayList;
import java.util.List;

public class InnerAnalysisResult extends AnalysisResult {
    private static final String TAG = "InnerAnalysisResult";
    private final List<Result> results;
    public InnerAnalysisResult() {
        results = new ArrayList<>();
    }

    @Override
    public void addResult(int frameNum, Frame frame) {
        InnerFrame innerFrame = (InnerFrame) frame;
        results.add(new Result(frameNum, innerFrame.getDistracted()));
    }

    @Override
    public String getResultString() {
        StringBuilder sb = new StringBuilder();
        for (Result res : results) {
            sb.append(res.frameNum).append(",").append(res.isDistracted ? "1" : "0").append("\n");
        }
        return sb.toString();
    }

    @Override
    public String calcAccuracy(AnalysisResult baseResult) {
        List<Result> baseResults = ((InnerAnalysisResult) baseResult).results;

        if (baseResults.size() != results.size()) {
            throw new RuntimeException("Result sizes differ! base = " + baseResults.size() + ", current = " + results.size());
        }

        int totalCount = results.size();
        int distracted = 0, nonDistracted = 0, distractedWrong = 0, nonDistractedWrong = 0;

        for (int i = 0; i < results.size(); i++) {
            Result base = baseResults.get(i);
            Result cur = results.get(i);

            if (base.frameNum != cur.frameNum) {
                throw new RuntimeException(i + "th frame numbers differ! base = " + base.frameNum + ", current = " + cur.frameNum);
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
        }

        return totalCount + " " + distracted + " " + nonDistracted + " " + distractedWrong + " " + nonDistractedWrong;
    }

    private static class Result {
        int frameNum;
        boolean isDistracted;

        public Result(int frameNum, boolean isDistracted) {
            this.frameNum = frameNum;
            this.isDistracted = isDistracted;
        }
    }
}
