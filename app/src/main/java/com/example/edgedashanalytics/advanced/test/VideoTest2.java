package com.example.edgedashanalytics.advanced.test;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import com.example.edgedashanalytics.advanced.worker.ProcessorThread;
import com.example.edgedashanalytics.util.video.analysis.Frame;
import com.example.edgedashanalytics.util.video.analysis.Hazard;
import com.example.edgedashanalytics.util.video.analysis.InnerFrame;
import com.example.edgedashanalytics.util.video.analysis.OuterFrame;
import com.example.edgedashanalytics.util.video.analysis.VideoAnalysis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

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

        String videoWidthString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        String videoHeightString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        int videoWidth = Integer.parseInt(videoWidthString);
        int videoHeight = Integer.parseInt(videoHeightString);

        float scaleFactor = videoWidth / 192f;
        int scaledWidth = (int) (videoWidth / scaleFactor);
        int scaledHeight = (int) (videoHeight / scaleFactor);

        VideoAnalysis analyzer = (isInner ? ProcessorThread.innerProcessor.analyzer : ProcessorThread.outerProcessor.analyzer);

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < totalFrames; i++) {
            Bitmap bitmap = retriever.getFrameAtIndex(i);

            BitmapFactory.Options ops = new BitmapFactory.Options();
            ops.inMutable = true;
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            bitmap = Bitmap.createScaledBitmap(bitmap, 1280, 720, true);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outStream);
            ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray());
            bitmap = BitmapFactory.decodeStream(inStream, null, ops);

            Frame frame = analyzer.analyse(Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, false), scaleFactor).get(0);

            if (i % 10 == 0) {
                Log.v(TAG, "Testing (" + i + " / " + totalFrames);
            }
            sb.append(i).append(",");

            if (isInner) {
                InnerFrame innerFrame = (InnerFrame) frame;
                sb.append(innerFrame.getDistracted() ? 1 : 0);
            }
            else {
                OuterFrame outerFrame = (OuterFrame) frame;
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
    }
}
