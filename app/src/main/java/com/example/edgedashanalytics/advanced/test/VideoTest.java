package com.example.edgedashanalytics.advanced.test;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.util.Log;
import android.util.Size;

import com.example.edgedashanalytics.advanced.worker.ProcessorThread;
import com.example.edgedashanalytics.util.video.analysis.Frame;
import com.example.edgedashanalytics.util.video.analysis.InnerFrame;
import com.example.edgedashanalytics.util.video.analysis.OuterAnalysis;
import com.example.edgedashanalytics.util.video.analysis.VideoAnalysis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

public class VideoTest {
    private final static String TAG = "VideoTest";
    public static void test(Context context, String fileName, boolean isInner, List<Integer> qualities, int frameStart, int frameEnd, Size resolution) {
        String videoPath = context.getExternalFilesDir(null) + "/" + fileName;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(videoPath);
        String totalFramesString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT);
        int totalFrames = Integer.parseInt(totalFramesString);

        if (frameStart == -1) {
            frameStart = 0;
            frameEnd = totalFrames - 1;
        }

        Log.i(TAG, "total frames = " + totalFrames);

        String videoWidthString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        String videoHeightString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        int videoWidth = Integer.parseInt(videoWidthString);
        int videoHeight = Integer.parseInt(videoHeightString);

        float scaleFactor = videoWidth / 192f;
        int scaledWidth = (int) (videoWidth / scaleFactor);
        int scaledHeight = (int) (videoHeight / scaleFactor);

        VideoAnalysis analyzer = (isInner ? ProcessorThread.innerProcessor.analyzer : ProcessorThread.outerProcessor.analyzer);

        for (Integer quality : qualities) {
            Log.v(TAG, "Testing " + quality + "...");
            AnalysisResult result = (isInner) ? new InnerAnalysisResult() : new OuterAnalysisResult();

            for (int i = frameStart; i <= frameEnd; i++) {
                Bitmap bitmap = retriever.getFrameAtIndex(i);

                if (resolution.getWidth() < 1280) {
                    bitmap = Bitmap.createScaledBitmap(bitmap, resolution.getWidth(), resolution.getHeight(), true);
                }

                if (quality != 100) {

                    BitmapFactory.Options ops = new BitmapFactory.Options();
                    ops.inMutable = true;
                    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outStream);
                    ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
                    bitmap = BitmapFactory.decodeStream(inStream, null, ops);
                }

                if (i % 10 == 0) {
                    Log.v(TAG, "Analyzing " + i);
                }

                Frame frame = analyzer.analyse(Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, false), scaleFactor).get(0);

                //Log.v(TAG, "result: " + ((InnerFrame) frame).getDistracted());

                result.addResult(i, frame);
            }

            result.writeResults(context, quality + ".csv");
        }
    }

    public static void test2(Context context, String fileName, List<Integer> qualities, List<Size> resolutions, int frameStart, int frameEnd) {
        String videoPath = context.getExternalFilesDir(null) + "/" + fileName;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(videoPath);
        String totalFramesString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT);
        int totalFrames = Integer.parseInt(totalFramesString);

        if (frameStart == -1) {
            frameStart = 0;
            frameEnd = totalFrames - 1;
        }

        StringBuilder sb = new StringBuilder();

        for (Integer quality : qualities) {
            for (Size resolution : resolutions) {
                long totalSize = 0;
                long worstSize = 0;
                for (int i = frameStart; i <= frameEnd; i++) {
                    Bitmap bitmap = retriever.getFrameAtIndex(i);

                    if (resolution.getWidth() < 1280) {
                        bitmap = Bitmap.createScaledBitmap(bitmap, resolution.getWidth(), resolution.getHeight(), true);
                    }

                    BitmapFactory.Options ops = new BitmapFactory.Options();
                    ops.inMutable = true;
                    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outStream);

                    int len = outStream.toByteArray().length;
                    totalSize += len;
                    if (len > worstSize)
                        worstSize = len;
                }

                sb.append(quality).append(" ").append(resolution.getWidth()).append("x").append(resolution.getHeight())
                        .append(" = ").append(totalSize / (frameEnd - frameStart + 1)).append(" ").append(worstSize);
            }
        }

        Log.v(TAG, sb.toString());
    }

    /*
    Test for outer analysis accuracy
     */
    public static void testOuterAnalysisAccuracy(Context context, String fileName, List<Integer> qualities, List<Size> resolutions, int frameStart, int frameEnd) {
        String videoPath = context.getExternalFilesDir(null) + "/" + fileName;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(videoPath);
        String totalFramesString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT);
        int totalFrames = Integer.parseInt(totalFramesString);

        if (frameStart == -1) {
            frameStart = 0;
            frameEnd = totalFrames - 1;
        }

        String videoWidthString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        String videoHeightString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        int videoWidth = Integer.parseInt(videoWidthString);
        int videoHeight = Integer.parseInt(videoHeightString);

        float scaleFactor = videoWidth / 192f;
        int scaledWidth = (int) (videoWidth / scaleFactor);
        int scaledHeight = (int) (videoHeight / scaleFactor);

        StringBuilder sb = new StringBuilder();

        OuterAnalysis analyzer = ProcessorThread.outerProcessor.analyzer;

        // Calculate base things first
        OuterAnalysisResult baseResult = new OuterAnalysisResult();
        for (int i = frameStart; i <= frameEnd; i++) {
            Bitmap bitmap = retriever.getFrameAtIndex(i);

            BitmapFactory.Options ops = new BitmapFactory.Options();
            ops.inMutable = true;
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outStream);
            ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
            bitmap = BitmapFactory.decodeStream(inStream, null, ops);

            Frame frame = analyzer.analyse(Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, false), i, scaleFactor).get(0);
            baseResult.addResult(i, frame);
        }

        for (Integer quality : qualities) {
            for (Size resolution : resolutions) {
                OuterAnalysisResult result = new OuterAnalysisResult();
                for (int i = frameStart; i <= frameEnd; i++) {
                    Bitmap bitmap = retriever.getFrameAtIndex(i);

                    if (resolution.getWidth() < 1280) {
                        bitmap = Bitmap.createScaledBitmap(bitmap, resolution.getWidth(), resolution.getHeight(), true);
                    }

                    BitmapFactory.Options ops = new BitmapFactory.Options();
                    ops.inMutable = true;
                    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outStream);
                    ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
                    bitmap = BitmapFactory.decodeStream(inStream, null, ops);

                    Frame frame = analyzer.analyse(Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, false), i, scaleFactor).get(0);
                    result.addResult(i, frame);
                }

                sb.append(quality).append(" ").append(resolution.getWidth()).append("x").append(resolution.getHeight())
                        .append(": ").append(result.calcAccuracy(baseResult)).append("\n");
            }
        }

        Log.v(TAG, sb.toString());
    }
}
