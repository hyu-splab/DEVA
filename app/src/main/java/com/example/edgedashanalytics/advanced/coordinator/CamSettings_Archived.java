package com.example.edgedashanalytics.advanced.coordinator;

import android.util.Size;

public class CamSettings_Archived {
    private final Size[] resolutions;
    private final int[] qualities;
    private final int[] frameRates;

    public boolean isInner;
    private int idxResolution;
    private int idxQuality;
    private int idxFrameRate;
    private boolean changedResolution;
    private boolean changedQuality;
    private boolean changedFrameRate;

    public CamSettings_Archived(boolean isInner) {
        resolutions = new Size[] {
                new Size(640, 360),
                new Size(854, 480),
                new Size(960, 540),
                new Size(1280, 720)
        };

        qualities = new int[] {30, 40, 50/*, 60, 70*/};
        frameRates = new int[] {5, 10, 15, 20, 25, 30};

        this.isInner = isInner;

        idxResolution = resolutions.length - 1;
        idxQuality = qualities.length - 1;
        idxFrameRate = frameRates.length - 1;

        initializeChanged();
    }

    public CamSettings_Archived(CamSettings_Archived org) {
        resolutions = org.resolutions;
        qualities = org.qualities;
        frameRates = org.frameRates;
        isInner = org.isInner;
        idxResolution = org.idxResolution;
        idxQuality = org.idxQuality;
        idxFrameRate = org.idxFrameRate;

        initializeChanged();
    }

    public void initializeChanged() {
        changedResolution = changedQuality = changedFrameRate = false;
    }

    public boolean increaseResolution() {
        if (changedResolution || idxResolution == resolutions.length - 1)
            return false;
        idxResolution++;
        changedResolution = true;
        return true;
    }

    public boolean decreaseResolution() {
        if (changedResolution || idxResolution == 0)
            return false;
        idxResolution--;
        changedResolution = true;
        return true;
    }

    public boolean increaseQuality() {
        if (changedQuality || idxQuality == qualities.length - 1)
            return false;
        idxQuality++;
        changedQuality = true;
        return true;
    }

    public boolean decreaseQuality() {
        if (changedQuality || idxQuality == 0)
            return false;
        idxQuality--;
        changedQuality = true;
        return true;
    }

    public boolean increaseFrameRate() {
        if (changedFrameRate || idxFrameRate == frameRates.length - 1)
            return false;
        idxFrameRate++;
        changedFrameRate = true;
        return true;
    }

    public boolean decreaseFrameRate() {
        if (changedFrameRate || idxFrameRate == 0 || (!isInner && idxFrameRate == 1))
            return false;
        idxFrameRate--;
        changedFrameRate = true;
        return true;
    }

    public Size getResolution() {
        return resolutions[idxResolution];
    }

    public int getQuality() {
        return qualities[idxQuality];
    }

    public int getFrameRate() {
        return frameRates[idxFrameRate];
    }
}
