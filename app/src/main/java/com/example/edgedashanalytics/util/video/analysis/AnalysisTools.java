package com.example.edgedashanalytics.util.video.analysis;

import static com.example.edgedashanalytics.util.file.FileManager.getResultDirPath;

import android.content.Context;
import android.util.Log;

import com.example.edgedashanalytics.event.result.AddResultEvent;
import com.example.edgedashanalytics.event.video.AddEvent;
import com.example.edgedashanalytics.event.video.RemoveEvent;
import com.example.edgedashanalytics.event.video.Type;
import com.example.edgedashanalytics.model.Result;
import com.example.edgedashanalytics.model.Video;
import com.example.edgedashanalytics.util.file.FileManager;

import org.greenrobot.eventbus.EventBus;

import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class AnalysisTools {
    private final static String TAG = AnalysisTools.class.getSimpleName();

    private final static ExecutorService executor = Executors.newSingleThreadExecutor();
    private final static LinkedHashMap<String, Future<?>> analysisFutures = new LinkedHashMap<>();

    public static void processVideo(Video video, Context context) {
        Log.d(TAG, String.format("Analysing %s", video.getName()));

        final String output = String.format("%s/%s", getResultDirPath(),
                FileManager.getResultNameFromVideoName(video.getName()));

        Future<?> future = executor.submit(processRunnable(video, output, context));
        analysisFutures.put(video.getData(), future);

        EventBus.getDefault().post(new AddEvent(video, Type.PROCESSING));
        EventBus.getDefault().post(new RemoveEvent(video, Type.RAW));
    }

    private static Runnable processRunnable(Video video, String outPath, Context context) {
        return () -> {
            VideoAnalysis videoAnalysis = new VideoAnalysis(video.getData(), outPath);
            videoAnalysis.analyse(context);

            analysisFutures.remove(video.getData());

            Result result = new Result(outPath, FileManager.getFilenameFromPath(outPath));
            EventBus.getDefault().post(new AddResultEvent(result));
            EventBus.getDefault().post(new RemoveEvent(video, Type.PROCESSING));
        };
    }

    public static void cancelProcess(String videoPath) {
        Future<?> future = analysisFutures.remove(videoPath);

        if (future != null) {
            Log.i(TAG, String.format("Cancelling processing of %s", videoPath));
            future.cancel(false);
        } else {
            Log.e(TAG, String.format("Cannot cancel processing of %s", videoPath));
        }
    }
}
