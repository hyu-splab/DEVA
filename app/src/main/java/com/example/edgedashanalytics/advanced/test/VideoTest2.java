package com.example.edgedashanalytics.advanced.test;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import com.example.edgedashanalytics.advanced.worker.InnerProcessor;
import com.example.edgedashanalytics.advanced.worker.OuterProcessor;
import com.example.edgedashanalytics.advanced.worker.ProcessorThread;
import com.example.edgedashanalytics.util.video.analysis.Frame;
import com.example.edgedashanalytics.util.video.analysis.Hazard;
import com.example.edgedashanalytics.util.video.analysis.InnerAnalysis;
import com.example.edgedashanalytics.util.video.analysis.InnerFrame;
import com.example.edgedashanalytics.util.video.analysis.OuterAnalysis;
import com.example.edgedashanalytics.util.video.analysis.OuterFrame;
import com.example.edgedashanalytics.util.video.analysis.VideoAnalysis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public class VideoTest2 {
    private static final String TAG = "VideoTest2";
    public static void test(String videoName, String outFileName, Context context, boolean isInner) {
        String dir = context.getExternalFilesDir(null).getAbsolutePath();
        String videoPath = dir + "/" + videoName;

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(videoPath);
        String totalFramesString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT);
        int totalFrames = Integer.parseInt(totalFramesString);

        Log.i(TAG, "total frames = " + totalFrames);

        VideoAnalysis analyzer = (isInner ? new InnerAnalysis(context) : new OuterAnalysis(context));

        try {
            Thread.sleep(1000);
        } catch (Exception e) {
            e.printStackTrace();
        }

        String videoWidthString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        String videoHeightString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        int videoWidth = Integer.parseInt(videoWidthString);
        int videoHeight = Integer.parseInt(videoHeightString);

        videoWidth = 1280;
        videoHeight = 720;

        float scaleFactor = videoWidth / 512f/*(float)(isInner ? InnerProcessor.inputWidth : OuterProcessor.inputSize)*/;
        int scaledWidth = (int) (videoWidth / scaleFactor);
        int scaledHeight = (int) (videoHeight / scaleFactor);

        Log.v(TAG, "scales = " + scaleFactor + " " + scaledWidth + " " + scaledHeight);

        StringBuilder sb = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();

        for (int i = 0; i < totalFrames; i++) {
            Bitmap bitmap = retriever.getFrameAtIndex(i);

            BitmapFactory.Options ops = new BitmapFactory.Options();
            ops.inMutable = true;
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            Log.v(TAG, "resolution = " + bitmap.getWidth() + " " + bitmap.getHeight());
            //if (bitmap.getWidth() != 1280) {
                bitmap = Bitmap.createScaledBitmap(bitmap, 1280, 720, true);
            //}
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outStream);
            byte[] data = outStream.toByteArray();
            long sum = 0;
            for (int j = 0; j < data.length; j++) {
                sum += data[j];
            }
            sb2.append(i).append(",").append(sum).append("\n");
            ByteArrayInputStream inStream = new ByteArrayInputStream(data);
            bitmap = BitmapFactory.decodeStream(inStream, null, ops);

            List<Frame> frames = analyzer.analyse(Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, false), scaleFactor);
            Log.v(TAG, "frames.size() = " + frames.size());
            Frame frame = frames.get(0);
            if (i % 10 == 0) {
                Log.v(TAG, "Testing (" + i + " / " + totalFrames);
            }
            sb.append(i).append(",");

            if (isInner) {
                //Log.v(TAG, "Inner frame");
                InnerFrame innerFrame = (InnerFrame) frame;
                sb.append(innerFrame.getDistracted() ? 1 : 0);
            }
            else {
                //Log.v(TAG, "Outer frame");
                OuterFrame outerFrame = (OuterFrame) frame;
                Log.v(TAG, "frame " + i + ": " + outerFrame.getHazards().size() + " objects");
                sb.append(outerFrame.getHazards().size());
                for (Hazard hazard : outerFrame.getHazards()) {
                    sb.append(",").append(hazard.getCategory());
                }
            }

            sb.append("\n");
        }

        File outFile = new File(dir + "/" + outFileName);
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            fos.write(sb.toString().getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }

        Log.v(TAG, sb2.toString());
    }
}
