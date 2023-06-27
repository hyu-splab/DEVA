package com.example.edgedashanalytics.util.worker;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.util.Log;

import com.example.edgedashanalytics.util.file.JsonManager;
import com.example.edgedashanalytics.util.video.analysis.Frame;
import com.example.edgedashanalytics.util.video.analysis.InnerAnalysis;

public class InnerProcessor extends FrameProcessor {
    private static final String TAG = "InnerProcessor";
    public static InnerAnalysis analyzer;
    public InnerProcessor(Bitmap bitmap) {
        super(bitmap);
    }

    @Override
    public String run() {
        Frame result = analyzer.analyse(frame);
        String resultString = JsonManager.writeToString(result);

        return resultString;
    }
}
