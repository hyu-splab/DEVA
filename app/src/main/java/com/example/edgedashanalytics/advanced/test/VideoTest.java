package com.example.edgedashanalytics.advanced.test;

import static com.example.edgedashanalytics.advanced.coordinator.AdvancedMain.workerStart;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.util.Log;
import android.util.Size;

import com.example.edgedashanalytics.advanced.common.Image2;
import com.example.edgedashanalytics.advanced.common.TimeLog;
import com.example.edgedashanalytics.advanced.coordinator.AdvancedMain;
import com.example.edgedashanalytics.advanced.worker.ProcessorThread;
import com.example.edgedashanalytics.util.video.analysis.Frame;
import com.example.edgedashanalytics.util.video.analysis.InnerAnalysis;
import com.example.edgedashanalytics.util.video.analysis.InnerFrame;
import com.example.edgedashanalytics.util.video.analysis.OuterAnalysis;
import com.example.edgedashanalytics.util.video.analysis.VideoAnalysis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
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

        VideoAnalysis analyzer = (isInner ? new InnerAnalysis(context) : new OuterAnalysis(context));

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

        String rotationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        int rotation = Integer.parseInt(rotationString);
        Log.v(TAG, "rotation = " + rotation);

        if (rotation == 270 || rotation == 90) {
            int temp = videoWidth;
            videoWidth = videoHeight;
            videoHeight = temp;
        }

        StringBuilder sb = new StringBuilder();

        OuterAnalysis analyzer = new OuterAnalysis(context);

        float scaleFactor = videoWidth / 512f;
        int scaledWidth = (int) (videoWidth / scaleFactor);
        int scaledHeight = (int) (videoHeight / scaleFactor);
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

                sb.append(quality).append(" ").append(resolution.getWidth()).append(" ").append(resolution.getHeight())
                        .append(": ").append(result.calcAccuracy(baseResult)).append("\n");
            }
        }
        sb.append("\n");

        Log.w(TAG, sb.toString());
    }

    public static void testAnalysisSpeed(Context context, int numFrames, Size resolution, int quality) {
        final String innerVideoFileName = "video2.mp4";
        final String outerVideoFileName = "video.mov";

        StringBuilder sb = new StringBuilder();

        AdvancedMain.workerStart();

        byte[][] innerBitmaps = getScaledBitmaps(context, innerVideoFileName, numFrames, resolution, quality);
        byte[][] outerBitmaps = getScaledBitmaps(context, outerVideoFileName, numFrames, resolution, quality);

        long startTime, endTime, duration;

        Log.v(TAG, "inner-only test");
        startTime = System.currentTimeMillis();
        for (int i = 0; i < numFrames; i++) {
            Image2 image = new Image2(true, i, i, innerBitmaps[i]);
            image.isTesting = true;
            try {
                ProcessorThread.queue.put(image);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        while (!ProcessorThread.queue.isEmpty()) {
            try {
                Thread.sleep(100);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        endTime = System.currentTimeMillis();
        duration = endTime - startTime;
        sb.append("inner-only test: ").append(duration).append(" ms, avg: ").append(1000.0 * numFrames / duration).append(" fps\n");

        Log.v(TAG, "outer-only test");
        startTime = System.currentTimeMillis();
        for (int i = 0; i < numFrames; i++) {
            Image2 image = new Image2(false, i, i, outerBitmaps[i]);
            image.isTesting = true;
            try {
                ProcessorThread.queue.put(image);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        while (!ProcessorThread.queue.isEmpty()) {
            try {
                Thread.sleep(100);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        endTime = System.currentTimeMillis();
        duration = endTime - startTime;
        sb.append("outer-only test: ").append(duration).append(" ms, avg: ").append(1000.0 * numFrames / duration).append(" fps\n");

        Log.v(TAG, "alternating test");
        startTime = System.currentTimeMillis();
        for (int i = 0; i < numFrames; i++) {
            Image2 image = new Image2(true, i, i, innerBitmaps[i]);
            Image2 image2 = new Image2(false, i, i, outerBitmaps[i]);
            image.isTesting = image2.isTesting = true;
            try {
                ProcessorThread.queue.put(image);
                ProcessorThread.queue.put(image2);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        while (!ProcessorThread.queue.isEmpty()) {
            try {
                Thread.sleep(100);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        endTime = System.currentTimeMillis();
        duration = endTime - startTime;
        sb.append("alternating test: ").append(duration).append(" ms, avg: ").append(1000.0 * numFrames * 2 / duration).append(" fps\n");

        Log.w(TAG, sb.toString());
    }

    private static byte[][] getScaledBitmaps(Context context, String fileName, int numFrames, Size resolution, int quality) {
        String videoPath = context.getExternalFilesDir(null) + "/" + fileName;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(videoPath);

        byte[][] result = new byte[numFrames][];

        for (int i = 0; i < numFrames; i++) {
            Bitmap bitmap = retriever.getFrameAtIndex(i);
            if (resolution.getWidth() < 1280) {
                bitmap = Bitmap.createScaledBitmap(bitmap, resolution.getWidth(), resolution.getHeight(), true);
            }

            BitmapFactory.Options ops = new BitmapFactory.Options();
            ops.inMutable = true;
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outStream);

            result[i] = outStream.toByteArray();
        }

        return result;
    }

    public static void calculateDataSizes(Context context, String fileName, String resFileName) {
        String videoPath = context.getExternalFilesDir(null) + "/" + fileName;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(videoPath);

        String totalFramesString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT);
        int totalFrames = Integer.parseInt(totalFramesString);

        final Size[] resolutions = {
                /*new Size(640, 360),
                new Size(720, 405),
                new Size(800, 450)*/
                /*new Size(880, 495),
                new Size(960, 540),
                new Size(1040, 585),*/
                new Size(1120, 630),
                new Size(1200, 675),
                new Size(1280, 720)
        };

        final int[] qualities = {
                20, 30, 40, 50, 60, 70, 80, 90, 100
        };

        StringBuilder sb = new StringBuilder();

        for (Size resolution : resolutions) {
            for (int quality : qualities) {
                long totalSize = 0;
                for (int i = 0; i < totalFrames; i++) {
                    Bitmap bitmap = retriever.getFrameAtIndex(i);
                    bitmap = Bitmap.createScaledBitmap(bitmap, resolution.getWidth(), resolution.getHeight(), true);
                    BitmapFactory.Options ops = new BitmapFactory.Options();
                    ops.inMutable = true;
                    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outStream);

                    byte[] data = outStream.toByteArray();
                    totalSize += data.length;
                }
                sb.append(totalSize / totalFrames).append(",");
            }
            sb.deleteCharAt(sb.length() - 1).append("\n");
        }

        File outFile = new File(context.getExternalFilesDir(null) + "/" + resFileName);
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            fos.write(sb.toString().getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void checkFrameSize(Context context, String fileName) {
        String videoPath = context.getExternalFilesDir(null) + "/" + fileName;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(videoPath);

        String totalFramesString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT);
        int totalFrames = Integer.parseInt(totalFramesString);

        for (int i = 0; i < totalFrames; i++) {
            Bitmap bitmap = retriever.getFrameAtIndex(i);
        }
    }
}
