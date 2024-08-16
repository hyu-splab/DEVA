package com.example.edgedashanalytics.advanced.common;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;

public class FrameData implements Serializable {
    public boolean isInner;
    public int frameNum;
    public byte[] data;
    public boolean isTesting = false;
    public boolean isBusy;

    public FrameData(boolean isInner, int frameNum, byte[] data, boolean isBusy) {
        this.isInner = isInner;
        this.frameNum = frameNum;
        this.data = data;
        this.isBusy = isBusy;
    }

    private static FrameData meaninglessFrame;
    public static FrameData getMeaninglessFrame() {
        if (meaninglessFrame == null) {
            Bitmap bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888);
            BitmapFactory.Options ops = new BitmapFactory.Options();
            ops.inMutable = true;
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outStream);
            byte[] data = outStream.toByteArray();
            meaninglessFrame = new FrameData(false, -1, data, true);
        }
        return meaninglessFrame;
    }
}
