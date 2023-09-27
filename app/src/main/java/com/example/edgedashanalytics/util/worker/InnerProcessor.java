package com.example.edgedashanalytics.util.worker;

import static com.example.edgedashanalytics.util.log.TimeLog.context;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.util.Log;

import com.example.edgedashanalytics.util.file.JsonManager;
import com.example.edgedashanalytics.util.video.analysis.Frame;
import com.example.edgedashanalytics.util.video.analysis.Hazard;
import com.example.edgedashanalytics.util.video.analysis.InnerAnalysis;
import com.example.edgedashanalytics.util.video.analysis.InnerFrame;
import com.example.edgedashanalytics.util.video.analysis.OuterFrame;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.StringTokenizer;

public class InnerProcessor extends FrameProcessor {
    private static final String TAG = "InnerProcessor";
    public InnerAnalysis analyzer;
    public InnerProcessor() {
        analyzer = new InnerAnalysis(context);
    }

    public ArrayList<AnalysisResult> analysisResults = new ArrayList<>();

    public HashMap<Integer, Integer> map = new HashMap<>();

    @Override
    public String run() {
        List<Frame> result = analyzer.analyse(frame);
        StringBuilder resultString = new StringBuilder();
        for (Frame f : result)
            resultString.append(JsonManager.writeToString(result));

        addResult(result);

        if (cameraFrameNum == 1550) {
            calcAccuracy();
        }

        //Log.d(TAG, "result: " + resultString.toString());
        return resultString.toString();
    }

    public void readDistraction() {
        File path = context.getExternalFilesDir(null);
        File file = new File(path, "distraction.csv");

        try (FileInputStream stream = new FileInputStream(file)) {
            Scanner scanner = new Scanner(stream);
            while (scanner.hasNextLine()) {
                StringTokenizer st = new StringTokenizer(scanner.nextLine(), ",");
                int frameNum = Integer.parseInt(st.nextToken());
                int distracted = Integer.parseInt(st.nextToken());
                map.put(frameNum, distracted);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void calcAccuracy() {
        readDistraction();

        int distracted = 0, notDistracted = 1;
        int falseNegative = 0, falsePositive = 0;

        for (AnalysisResult res : analysisResults) {
            if (!map.containsKey(res.frameNum)) {
                Log.d(TAG, res.frameNum + " does not exist!1!");
                continue;
            }

            if (res.isDistracted)
                distracted++;
            else
                notDistracted++;

            if (map.get(res.frameNum) == 0 && res.isDistracted)
                falsePositive++;
            else if (map.get(res.frameNum) == 1 && !res.isDistracted)
                falseNegative++;
        }

        Log.d(TAG, "distracted = " + distracted + ", notDistracted = " + notDistracted);
        Log.d(TAG, "falsePositive = " + falsePositive + ", falseNegative = " + falseNegative);
    }

    private void addResult(List<Frame> result) {
        AnalysisResult res = null;

        for (Frame f : result) {
            InnerFrame frame = (InnerFrame) f;
            res = new AnalysisResult(cameraFrameNum, frame.getDistracted());
        }

        analysisResults.add(res);
    }

    public void writeResults() {
        File path = context.getExternalFilesDir(null);
        File file = new File(path, "distraction.csv");

        StringBuilder sb = new StringBuilder();

        for (AnalysisResult res : analysisResults) {
            sb.append(res.frameNum).append(",").append(res.isDistracted ? "1" : "0").append("\n");
        }

        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(sb.toString().getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    class AnalysisResult {
        public int frameNum;
        public Boolean isDistracted;

        public AnalysisResult(int frameNum, boolean isDistracted) {
            this.frameNum = frameNum;
            this.isDistracted = isDistracted;
        }
    }
}
