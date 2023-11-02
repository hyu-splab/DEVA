package com.example.edgedashanalytics.advanced.test;

import static com.example.edgedashanalytics.advanced.common.TimeLog.context;

import android.content.Context;

import com.example.edgedashanalytics.util.video.analysis.Frame;

import java.io.File;

public abstract class AnalysisResult {
    public abstract void addResult(int frameNum, Frame frame);
    public abstract void writeResults(Context context, String fileName);
    public abstract void calcAccuracy(AnalysisResult baseResult);

    public File openFile(Context context, String fileName) {
        File path = context.getExternalFilesDir(null);
        return new File(path, fileName);
    }
}
