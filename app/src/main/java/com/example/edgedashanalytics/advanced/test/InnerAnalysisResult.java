package com.example.edgedashanalytics.advanced.test;

import android.content.Context;
import android.util.Log;

import com.example.edgedashanalytics.util.video.analysis.Frame;
import com.example.edgedashanalytics.util.video.analysis.InnerFrame;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class InnerAnalysisResult extends AnalysisResult {
    private static final String TAG = "InnerAnalysisResult";
    private List<Result> results;
    public InnerAnalysisResult() {
        results = new ArrayList<>();
    }

    @Override
    public void addResult(int frameNum, Frame frame) {
        InnerFrame innerFrame = (InnerFrame) frame;
        results.add(new Result(frameNum, innerFrame.getDistracted()));
    }

    @Override
    public void writeResults(Context context, String fileName) {
        File file = openFile(context, fileName);

        StringBuilder sb = new StringBuilder();
        for (Result res : results) {
            Log.v(TAG, "adding result " + res.frameNum + " " + res.isDistracted);
            sb.append(res.frameNum).append(",").append(res.isDistracted ? "1" : "0").append("\n");
        }

        Log.v(TAG, "writing file " + file.getAbsolutePath());
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(sb.toString().getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void calcAccuracy(AnalysisResult baseResult) {

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
