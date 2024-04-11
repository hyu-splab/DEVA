package com.example.edgedashanalytics.advanced.common;

import android.util.Log;

import java.io.Serializable;
import java.util.ArrayList;

public class WorkerInitialInfo implements Serializable {
    private static final String TAG = "WorkerInitialInfo";
    public ArrayList<String> temperatureNames;
    public ArrayList<String> frequencyNames;

    public WorkerInitialInfo(ArrayList<String> temperatureNames, ArrayList<String> frequencyNames) {
        this.temperatureNames = temperatureNames;
        this.frequencyNames = frequencyNames;
    }
}
