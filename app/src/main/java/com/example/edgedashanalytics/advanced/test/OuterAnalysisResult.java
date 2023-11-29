package com.example.edgedashanalytics.advanced.test;

import android.util.Log;

import com.example.edgedashanalytics.util.video.analysis.Frame;
import com.example.edgedashanalytics.util.video.analysis.Hazard;
import com.example.edgedashanalytics.util.video.analysis.OuterFrame;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class OuterAnalysisResult extends AnalysisResult {
    private static final String TAG = "OuterAnalysisResult";
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
    public String getResultString() {
        StringBuilder sb = new StringBuilder();

        for (Result res : results) {
            sb.append(res.frameNum);
            for (Hazard hazard : res.hazards) {
                sb.append(",").append(hazard.getCategory());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    public String calcAccuracy(AnalysisResult baseResult) {
        List<Result> baseResults = ((OuterAnalysisResult) baseResult).results;

        if (baseResults.size() != results.size()) {
            throw new RuntimeException("Result sizes differ! base = " + baseResults.size() + ", current = " + results.size());
        }

        int totalCount = 0, totalFound = 0, totalNotFound = 0, totalWrongFound = 0;

        for (int i = 0; i < results.size(); i++) {
            Result base = baseResults.get(i);
            Result cur = results.get(i);

            if (base.frameNum != cur.frameNum) {
                throw new RuntimeException(i + "th frame numbers differ! base = " + base.frameNum + ", current = " + cur.frameNum);
            }

            HashMap<String, Integer> baseMap = new HashMap<>(), curMap = new HashMap<>();
            for (Hazard hazard : base.hazards) {
                String category = hazard.getCategory();
                if (!baseMap.containsKey(category)) {
                    baseMap.put(category, 1);
                }
                else {
                    baseMap.put(category, baseMap.get(category) + 1);
                }
            }

            for (Hazard hazard : cur.hazards) {
                String category = hazard.getCategory();
                if (!curMap.containsKey(category)) {
                    curMap.put(category, 1);
                }
                else {
                    curMap.put(category, curMap.get(category) + 1);
                }
            }

            int count = 0, found = 0, notFound = 0, wrongFound = 0;

            for (String key : baseMap.keySet()) {
                int baseCnt = baseMap.get(key);
                count += baseCnt;
                int curCnt = 0;
                if (curMap.containsKey(key)) {
                    curCnt = curMap.get(key);
                }
                found += Math.min(baseCnt, curCnt);
                notFound += Math.max(baseCnt - curCnt, 0);
                wrongFound += Math.max(curCnt - baseCnt, 0);
            }

            for (String key : curMap.keySet()) {
                if (!baseMap.containsKey(key)) {
                    int curCnt = curMap.get(key);
                    wrongFound += curCnt;
                }
                // If it exists in baseMap, it's already calculated when we traversed baseMap
            }

            totalCount += count;
            totalFound += found;
            totalNotFound += notFound;
            totalWrongFound += wrongFound;
        }

        return totalCount + " " + totalFound + " " + totalNotFound + " " + totalWrongFound;
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
