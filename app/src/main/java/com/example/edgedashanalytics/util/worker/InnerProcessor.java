package com.example.edgedashanalytics.util.worker;

import static com.example.edgedashanalytics.util.log.TimeLog.context;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.util.Log;

import com.example.edgedashanalytics.util.file.JsonManager;
import com.example.edgedashanalytics.util.video.analysis.Frame;
import com.example.edgedashanalytics.util.video.analysis.InnerAnalysis;

import java.util.List;

public class InnerProcessor extends FrameProcessor {
    private static final String TAG = "InnerProcessor";
    public InnerAnalysis analyzer;
    public InnerProcessor() {
        analyzer = new InnerAnalysis(context);
    }

    @Override
    public String run() {
        List<Frame> result = analyzer.analyse(frame);
        StringBuilder resultString = new StringBuilder();
        for (Frame f : result)
            resultString.append(JsonManager.writeToString(result));

        return resultString.toString();
    }
}
