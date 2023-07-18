package com.example.edgedashanalytics.util.worker;

import static com.example.edgedashanalytics.util.log.TimeLog.context;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.example.edgedashanalytics.util.file.JsonManager;
import com.example.edgedashanalytics.util.video.analysis.Frame;
import com.example.edgedashanalytics.util.video.analysis.InnerAnalysis;
import com.example.edgedashanalytics.util.video.analysis.OuterAnalysis;

import java.util.List;

public class OuterProcessor extends FrameProcessor {
    private static final String TAG = "OuterProcessor";
    public OuterAnalysis analyzer;
    public OuterProcessor() {
        analyzer = new OuterAnalysis(context);
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
