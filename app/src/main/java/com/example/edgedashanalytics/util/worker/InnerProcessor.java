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
    static InnerAnalysis analyzer = null;
    public InnerProcessor(Bitmap bitmap, Context context) {
        super(bitmap);
        if (analyzer == null)
            analyzer = new InnerAnalysis(context);
    }

    @Override
    public String run() {
        Frame result = analyzer.analyse(frame);
        String resultString = JsonManager.writeToString(result);

        Log.d(TAG, "Inner result = " + resultString);
        return resultString;
    }
}
