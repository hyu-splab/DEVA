package com.example.edgedashanalytics.advanced.test;

import android.content.Context;
import android.util.Log;

import com.example.edgedashanalytics.util.video.analysis.Frame;

import java.io.File;
import java.io.FileOutputStream;

public abstract class AnalysisResult {
    private static final String TAG = "AnalysisResult";
    public abstract void addResult(int frameNum, Frame frame);
    protected abstract String getResultString();
    public abstract String calcAccuracy(AnalysisResult baseResult);

    private File openFile(Context context, String fileName) {
        File path = context.getExternalFilesDir(null);
        return new File(path, fileName);
    }

    public void writeResults(Context context, String fileName) {
        File file = openFile(context, fileName);
        String resultString = getResultString();

        Log.v(TAG, "writing file " + file.getAbsolutePath());
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(resultString.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
