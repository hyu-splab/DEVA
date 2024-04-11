package com.example.edgedashanalytics.advanced.coordinator;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

public class DeviceLogger {

    private static final String TAG = "DeviceLogger";
    public final static ArrayList<DeviceInfo> devices = new ArrayList<>();
    private static final ArrayList<DeviceLog> logs = new ArrayList<>();

    public static void addLog(DeviceLog log) {
        synchronized (logs) {
            logs.add(log);
        }
    }

    public static void writeLogs(Context context, int testNum) {
        try {
            synchronized (logs) {
                StringBuilder sb = new StringBuilder();

                sb.append("timestamp");
                int id = 0;
                int[] sizes = new int[devices.size()];
                for (DeviceInfo device : devices) {
                    for (String temperatureName : device.temperatureNames) {
                        sb.append(",").append("T-W").append(id).append("-").append(temperatureName);
                    }

                    for (String frequencyName : device.frequencyNames) {
                        sb.append(",").append("F-W").append(id).append("-").append(frequencyName);
                    }
                    sizes[id] = device.temperatureNames.size() + device.frequencyNames.size();
                    id++;
                }

                sb.append("\n");

                for (DeviceLog log : logs) {
                    sb.append(log.timestamp);
                    id = 0;
                    for (DeviceLog.IndividualDeviceLog iLog : log.logs) {
                        if (iLog.temperatures == null) {
                            Log.w(TAG, "temperature info not available!!!");
                            for (int i = 0; i < sizes[id]; i++)
                                sb.append(",");
                            id++;
                            continue;
                        }
                        for (Integer temperature : iLog.temperatures) {
                            sb.append(",").append(temperature);
                        }

                        for (Integer frequency : iLog.frequencies) {
                            sb.append(",").append(frequency);
                        }
                        id++;
                    }

                    sb.append("\n");
                }

                String filename = testNum + "_devlog.csv";
                File file = new File(context.getExternalFilesDir(null), filename);

                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(sb.toString().getBytes());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    static class DeviceInfo {
        public ArrayList<String> temperatureNames;
        public ArrayList<String> frequencyNames;

        public DeviceInfo(ArrayList<String> temperatureNames, ArrayList<String> frequencyNames) {
            this.temperatureNames = temperatureNames;
            this.frequencyNames = frequencyNames;
        }
    };

    public static class DeviceLog {
        public long timestamp;

        public static class IndividualDeviceLog {
            public ArrayList<Integer> temperatures;
            public ArrayList<Integer> frequencies;

            public IndividualDeviceLog(ArrayList<Integer> temperatures, ArrayList<Integer> frequencies) {
                this.temperatures = temperatures;
                this.frequencies = frequencies;
            }
        }

        public ArrayList<IndividualDeviceLog> logs;

        public DeviceLog(long timestamp) {
            this.timestamp = timestamp;
            logs = new ArrayList<>();
        }

        public DeviceLog(long timestamp, ArrayList<IndividualDeviceLog> logs) {
            this.timestamp = timestamp;
            this.logs = logs;
        }
    }


}
