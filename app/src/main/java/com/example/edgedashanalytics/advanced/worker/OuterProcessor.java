package com.example.edgedashanalytics.advanced.worker;

import static com.example.edgedashanalytics.page.main.MainActivity.context;

import android.graphics.Bitmap;
import android.util.Log;

import com.example.edgedashanalytics.page.main.MainActivity;
import com.example.edgedashanalytics.util.file.JsonManager;
import com.example.edgedashanalytics.util.video.analysis.Frame;
import com.example.edgedashanalytics.util.video.analysis.Hazard;
import com.example.edgedashanalytics.util.video.analysis.OuterAnalysis;
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

public class OuterProcessor extends FrameProcessor {
    private static final String TAG = "OuterProcessor";
    public OuterAnalysis analyzer;
    public OuterProcessor() {
        analyzer = new OuterAnalysis(context);
    }

    public ArrayList<AnalysisResult> analysisResults = new ArrayList<>();

    public HashMap<Integer, HashMap<String, Integer>> map = new HashMap<>();
    public static int inputSize;

    @Override
    public ProcessResult run() {
        int videoWidth = frame.getWidth();
        int videoHeight = frame.getHeight();

        //Log.v(TAG, "videoWidth = " + videoWidth + ", inputSize = " + inputSize);

        float scaleFactor = videoWidth / (float)inputSize;
        int scaledWidth = (int) (videoWidth / scaleFactor);
        int scaledHeight = (int) (videoHeight / scaleFactor);

        List<Frame> result = analyzer.analyse(Bitmap.createScaledBitmap(frame, scaledWidth, scaledHeight, false), scaleFactor);

        OuterFrame outerFrame = (OuterFrame) result.get(0);

        /*StringBuilder sb = new StringBuilder();
        sb.append(cameraFrameNum).append(",").append(outerFrame.getHazards().size());
        for (Hazard hazard : outerFrame.getHazards()) {
            sb.append(",").append(hazard.getCategory());
        }

        Log.v(TAG, sb.toString());*/

        StringBuilder resultString = new StringBuilder();

        List<String> hazards = new ArrayList<>();
        for (Frame f : result) {
            for (Hazard hazard : ((OuterFrame) f).getHazards()) {
                hazards.add(hazard.getCategory());
            }
            resultString.append(JsonManager.writeToString(result));
        }
        /*addResult(result);

        //Log.d(TAG, "result: " + resultString.toString());

        if (cameraFrameNum == 1195) {
            calcAccuracy();
        }*/

        return new ProcessResult(resultString.toString(), false, hazards);
    }

    private void addResult(List<Frame> result) {
        AnalysisResult res = new AnalysisResult(cameraFrameNum);

        for (Frame f : result) {
            OuterFrame frame = (OuterFrame) f;
            for (Hazard hazard : frame.getHazards()) {
                res.categoryList.add(hazard.getCategory());
            }
        }

        Collections.sort(res.categoryList);
        analysisResults.add(res);
    }

    public void readHazards() {
        File path = context.getExternalFilesDir(null);
        File file = new File(path, "hazards.csv");

        try (FileInputStream stream = new FileInputStream(file)) {
            Scanner scanner = new Scanner(stream);
            while (scanner.hasNextLine()) {
                StringTokenizer st = new StringTokenizer(scanner.nextLine(), ",");
                int frameNum = Integer.parseInt(st.nextToken());
                HashMap<String, Integer> cMap = new HashMap<>();
                while (st.hasMoreTokens()) {
                    String category = st.nextToken();
                    if (!cMap.containsKey(category)) {
                        cMap.put(category, 1);
                    }
                    else {
                        cMap.put(category, cMap.get(category) + 1);
                    }
                }
                map.put(frameNum, cMap);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void calcAccuracy() {
        readHazards();

        int totalHazards = 0, totalDetections = 0;
        int undetected = 0, falsePositive = 0;

        for (AnalysisResult res : analysisResults) {
            if (!map.containsKey(res.frameNum)) {
                Log.d(TAG, "frame number " + res.frameNum + " + does not exist!!!");
                continue;
            }

            HashMap<String, Integer> cMap = new HashMap<>(map.get(res.frameNum));

            for (String category : cMap.keySet()) {
                totalHazards += cMap.get(category);
            }

            totalDetections += res.categoryList.size();
            for (String category : res.categoryList) {
                if (cMap.containsKey(category)) {
                    cMap.put(category, cMap.get(category) - 1);
                }
                else {
                    cMap.put(category, -1);
                }
            }

            for (String category : cMap.keySet()) {
                int val = cMap.get(category);
                if (val > 0)
                    undetected += val;
                else if (val < 0)
                    falsePositive += -val;
            }
        }

        Log.d(TAG, "totalHazards = " + totalHazards + ", totalDetections = " + totalDetections);
        Log.d(TAG, "undetected = " + undetected + ", falsePositive = " + falsePositive);
    }

    public void writeResults() {
        File path = context.getExternalFilesDir(null);
        File file = new File(path, "hazards.csv");

        StringBuilder sb = new StringBuilder();

        for (AnalysisResult res : analysisResults) {
            sb.append(res.frameNum);
            for (String category : res.categoryList) {
                sb.append(",").append(category);
            }
            sb.append("\n");
        }

        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(sb.toString().getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    class AnalysisResult {
        public int frameNum;
        public List<String> categoryList = new ArrayList<>();

        public AnalysisResult(int frameNum) {
            this.frameNum = frameNum;
        }
    }
}
