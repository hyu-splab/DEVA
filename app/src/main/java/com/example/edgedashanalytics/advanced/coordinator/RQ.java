package com.example.edgedashanalytics.advanced.coordinator;

import android.util.Size;

public class RQ {
    public Size resolution;
    public int quality;

    public RQ(Size resolution, int quality) {
        this.resolution = resolution;
        this.quality = quality;
    }

    public RQ(int width, int quality) {
        this(new Size(width, getHeight(width)), quality);
    }

    private static int getHeight(int width) {
        int height = 0;
        switch (width) {
            case 1280: height = 720; break;
            case 960: height = 540; break;
            case 854: height = 480; break;
            case 640: height = 360; break;
            default: throw new RuntimeException("Unknown width: " + width);
        }
        return height;
    }

    // This chart is based on data size experiment:
    public static final RQ[] innerDataSize = {
            new RQ(new Size(1280, 720), 100),
            new RQ(new Size(960, 540), 100),
            new RQ(new Size(854, 480), 100),
            new RQ(new Size(1280, 720), 90),
            new RQ(new Size(640, 360), 100),
            new RQ(new Size(1280, 720), 80),
            new RQ(new Size(960, 540), 90),
            new RQ(new Size(1280, 720), 70),
            new RQ(new Size(854, 480), 90),
            new RQ(new Size(1280, 720), 60),
            new RQ(new Size(960, 540), 80),
            new RQ(new Size(1280, 720), 50),
            new RQ(new Size(854, 480), 80),
            new RQ(new Size(640, 360), 90),
            new RQ(new Size(1280, 720), 40),
            new RQ(new Size(960, 540), 70),
            new RQ(new Size(1280, 720), 30),
            new RQ(new Size(854, 480), 70),
            new RQ(new Size(960, 540), 60),
            new RQ(new Size(960, 540), 50),
            new RQ(new Size(854, 480), 60),
            new RQ(new Size(640, 360), 80),
            new RQ(new Size(1280, 720), 20),
            new RQ(new Size(960, 540), 40),
            new RQ(new Size(854, 480), 50),
            new RQ(new Size(640, 360), 70),
            new RQ(new Size(854, 480), 40),
            new RQ(new Size(960, 540), 30),
            new RQ(new Size(640, 360), 60),
            new RQ(new Size(854, 480), 30),
            new RQ(new Size(960, 540), 20),
            new RQ(new Size(1280, 720), 10),
            new RQ(new Size(640, 360), 50),
            new RQ(new Size(640, 360), 40),
            new RQ(new Size(854, 480), 20),
            new RQ(new Size(640, 360), 30),
            new RQ(new Size(960, 540), 10),
            new RQ(new Size(640, 360), 20),
            new RQ(new Size(854, 480), 10),
            new RQ(new Size(1280, 720), 0),
            new RQ(new Size(640, 360), 10),
            new RQ(new Size(960, 540), 0),
            new RQ(new Size(854, 480), 0),
            new RQ(new Size(640, 360), 0),
    };

    public static final RQ[] outerDataSize = {
            new RQ(new Size(1280, 720), 100),
            new RQ(new Size(1280, 720), 90),
            new RQ(new Size(1280, 720), 80),
            new RQ(new Size(1280, 720), 70),
            new RQ(new Size(1280, 720), 60),
            new RQ(new Size(1280, 720), 50),
            new RQ(new Size(1280, 720), 40),
            new RQ(new Size(1280, 720), 30),
            new RQ(new Size(1280, 720), 20),
            new RQ(new Size(1280, 720), 10),
            new RQ(new Size(1280, 720), 0),
    };
}