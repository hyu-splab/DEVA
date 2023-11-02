package com.example.edgedashanalytics.advanced.test;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import com.example.edgedashanalytics.advanced.worker.ProcessorThread;
import com.example.edgedashanalytics.util.video.analysis.Frame;
import com.example.edgedashanalytics.util.video.analysis.InnerFrame;
import com.example.edgedashanalytics.util.video.analysis.VideoAnalysis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

public class VideoTest {
    private final static String TAG = "VideoTest";
    public static void test(Context context, String fileName, boolean isInner, List<Integer> qualities) {
        String videoPath = context.getExternalFilesDir(null) + "/" + fileName;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(videoPath);
        String totalFramesString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT);
        int totalFrames = Integer.parseInt(totalFramesString);

        Log.i(TAG, "total frames = " + totalFrames);

        VideoAnalysis analyzer = (isInner ? ProcessorThread.innerProcessor.analyzer : ProcessorThread.outerProcessor.analyzer);

        for (Integer quality : qualities) {
            Log.v(TAG, "Testing " + quality + "...");
            AnalysisResult result = (isInner) ? new InnerAnalysisResult() : new OuterAnalysisResult();

            for (int i = 0; i < totalFrames; i++) {
                Bitmap bitmap = retriever.getFrameAtIndex(i);

                if (quality != 100) {

                    BitmapFactory.Options ops = new BitmapFactory.Options();
                    ops.inMutable = true;
                    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outStream);
                    ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
                    bitmap = BitmapFactory.decodeStream(inStream, null, ops);
                }

                Log.v(TAG, "Analyzing " + i);

                Frame frame = analyzer.analyse(bitmap).get(0);

                //Log.v(TAG, "result: " + ((InnerFrame) frame).getDistracted());

                result.addResult(i, frame);
            }

            result.writeResults(context, quality + ".csv");
        }
    }
}
