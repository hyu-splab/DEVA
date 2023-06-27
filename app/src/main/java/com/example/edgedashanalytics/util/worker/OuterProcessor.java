package com.example.edgedashanalytics.util.worker;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.example.edgedashanalytics.util.file.JsonManager;
import com.example.edgedashanalytics.util.video.analysis.Frame;
import com.example.edgedashanalytics.util.video.analysis.InnerAnalysis;
import com.example.edgedashanalytics.util.video.analysis.OuterAnalysis;

public class OuterProcessor extends FrameProcessor {
    private static final String TAG = "OuterProcessor";
    static OuterAnalysis analyzer;
    public OuterProcessor(Bitmap bitmap, Context context) {
        super(bitmap);
        if (analyzer == null)
            analyzer = new OuterAnalysis(context);
    }

    @Override
    public String run() {
        Frame result = analyzer.analyse(frame);
        String resultString = JsonManager.writeToString(result);

        //Log.d(TAG, "Outer result = " + resultString);
        return resultString;
    }
}
