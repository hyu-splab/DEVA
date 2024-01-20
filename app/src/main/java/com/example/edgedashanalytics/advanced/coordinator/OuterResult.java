package com.example.edgedashanalytics.advanced.coordinator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class OuterResult {
    private static final String TAG = "OuterAnalysisResult";
    private final List<Result> results;
    public OuterResult() {
        results = new ArrayList<>();
    }

    public void addResult(int frameNum, List<String> hazards) {
        results.add(new Result(frameNum, hazards));
    }

    public String getResultString() {
        StringBuilder sb = new StringBuilder();

        for (Result res : results) {
            sb.append(res.frameNum);
            for (String category : res.hazards) {
                sb.append(",").append(category);
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public OuterAccuracyResult calcAccuracy(OuterResult baseResult) {
        List<Result> baseResults = baseResult.results;

        results.sort(new FrameComp());
        baseResults.sort(new FrameComp());

        int totalCount = 0, totalFound = 0, totalNotFound = 0, totalWrongFound = 0;

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

            HashMap<String, Integer> baseMap = new HashMap<>(), curMap = new HashMap<>();
            for (String category : base.hazards) {
                if (!baseMap.containsKey(category)) {
                    baseMap.put(category, 1);
                }
                else {
                    baseMap.put(category, baseMap.get(category) + 1);
                }
            }

            for (String category : cur.hazards) {
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

            bi++;
            ri++;
        }

        return new OuterAccuracyResult(totalCount, totalFound, totalNotFound, totalWrongFound);
    }

    public static class Result {
        int frameNum;
        List<String> hazards;
        public Result(int frameNum, List<String> hazards) {
            this.frameNum = frameNum;
            this.hazards = hazards;
        }
    }

    public static class OuterAccuracyResult {
        public int count, found, notFound, wrongFound;

        public OuterAccuracyResult(int count, int found, int notFound, int wrongFound) {
            this.count = count;
            this.found = found;
            this.notFound = notFound;
            this.wrongFound = wrongFound;
        }
    }

    public static class FrameComp implements Comparator<Result> {

        @Override
        public int compare(Result o1, Result o2) {
            return o1.frameNum - o2.frameNum;
        }
    }
}
