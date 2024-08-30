package com.example.edgedashanalytics.advanced.test;

import android.content.Context;
import android.util.Size;

import java.util.Arrays;

public class TestUtils {
    public static void createVideoAnalysisData(Context context) {
        try {
            new Thread(() -> {
                VideoTest2.test("video2.mp4", "inner_lightning.txt", context, true);
                VideoTest2.test("video.mov", "outer_mobilenet_v1.txt", context, false);
            }).start();
            return;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void testInnerVideo(Context context) {
        try {
            new Thread(() -> {
                final Size[] resolutions = {
                        new Size(1280, 720)
                };

                final Integer[] qualities = {
                        20, 30, 40, 50, 60, 70, 80, 90, 100
                };
                VideoTest.testInnerAnalysisAccuracy(context, "inner.mp4", Arrays.asList(qualities), Arrays.asList(resolutions));
            }).start();
            return;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void testOuterVideo(Context context) {
        try {
            new Thread(() -> {
                final Size[] resolutions = {
                        /*new Size(640, 360),
                        new Size(720, 405),
                        new Size(800, 450)*/
                        /*new Size(880, 495),
                        new Size(960, 540),
                        new Size(1040, 585),*/
                        //new Size(1120, 630),
                        //new Size(1200, 675),
                        new Size(1280, 720)
                };

                final Integer[] qualities = {
                        20, 30, 40, 50, 60, 70, 80, 90, 100
                };
                VideoTest.testOuterAnalysisAccuracy(context, "outer.mov", Arrays.asList(qualities), Arrays.asList(resolutions));
            }).start();
            return;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void calculateDataSize(Context context) {
        try {
            new Thread(() -> {
                VideoTest.calculateDataSizes(context, "inner.mp4", "inner-size.txt");
            }).start();
            return;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void testAnalysisSpeed(Context context, int numFrames, int numTest) {
        try {
            new Thread(() -> {
                VideoTest.testAnalysisSpeed(context, numFrames, new Size(1280, 720), 100, numTest);
            }).start();;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
