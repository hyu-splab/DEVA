package com.example.edgedashanalytics.advanced.test;

import android.content.Context;

import com.example.edgedashanalytics.util.video.analysis.Frame;
import com.example.edgedashanalytics.util.video.analysis.Hazard;
import com.example.edgedashanalytics.util.video.analysis.OuterFrame;

import java.util.ArrayList;
import java.util.List;

public class OuterAnalysisResult extends AnalysisResult {
    private List<Result> results;
    public OuterAnalysisResult() {
        results = new ArrayList<>();
    }

    @Override
    public void addResult(int frameNum, Frame frame) {
        OuterFrame outerFrame = (OuterFrame) frame;
        results.add(new Result(frameNum, outerFrame.getHazards()));
    }

    @Override
    public void writeResults(Context context, String fileName) {

    }

    @Override
    public void calcAccuracy(AnalysisResult baseResult) {

    }

    static class Result {
        int frameNum;
        List<Hazard> hazards;
        public Result(int frameNum, List<Hazard> hazards) {
            this.frameNum = frameNum;
            this.hazards = hazards;
        }
    }
}
