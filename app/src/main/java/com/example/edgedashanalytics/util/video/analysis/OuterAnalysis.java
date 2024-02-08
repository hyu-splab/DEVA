package com.example.edgedashanalytics.util.video.analysis;

import static com.example.edgedashanalytics.page.main.MainActivity.I_TAG;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.example.edgedashanalytics.R;
import com.example.edgedashanalytics.advanced.worker.OuterProcessor;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

// https://www.tensorflow.org/lite/models/object_detection/overview
// https://tfhub.dev/tensorflow/collections/lite/task-library/object-detector/1
// https://www.tensorflow.org/lite/performance/best_practices
// https://www.tensorflow.org/lite/guide/android
// https://www.tensorflow.org/lite/inference_with_metadata/task_library/object_detector
// https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/running_on_mobile_tf2.md
public class OuterAnalysis extends VideoAnalysis {
    private static final String TAG = OuterAnalysis.class.getSimpleName();

    // Different models have different maximum detection limits
    //  MobileNet's is 10, EfficientDet's is 25
    private static final int MAX_DETECTIONS = -1;
    private static final float MIN_SCORE = 0.2f;

    private static int inputSize;

    // TODO: We're only using one detector, need to replace this with a single instance
    private ArrayList<ObjectDetector> detectorList = new ArrayList<>();

    // Include or exclude bicycles?
    private static final ArrayList<String> vehicleCategories = new ArrayList<>(Arrays.asList(
            "bicycle", "car", "motorcycle", "bus", "truck"
    ));

    private static final int[] models = {
            //R.string.mobilenet_v1_key,
            //R.string.efficientdet_lite0_key,
            //R.string.efficientdet_lite1_key,
            //R.string.efficientdet_lite2_key,
            R.string.efficientdet_lite3_key,
            //R.string.efficientdet_lite4_key
    };

    public OuterAnalysis(Context context) {
        super(context);

        BaseOptions baseOptions = BaseOptions.builder().setNumThreads(TF_THREAD_NUM).build();

        ObjectDetector.ObjectDetectorOptions objectDetectorOptions = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setMaxResults(MAX_DETECTIONS)
                .setScoreThreshold(MIN_SCORE)
                .build();

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        for (int model : models) {
            String modelFilename = pref.getString("object_model", context.getString(model));

            try (Interpreter interpreter = new Interpreter(FileUtil.loadMappedFile(context, modelFilename))) {
                if (detectorList.isEmpty()) {
                    inputSize = interpreter.getInputTensor(0).shape()[1];
                    OuterProcessor.inputSize = inputSize;
                }
            } catch (IOException e) {
                Log.w(I_TAG, String.format("Model failure:\n  %s", e.getMessage()));
            }

            try {
                detectorList.add(ObjectDetector.createFromFileAndOptions(
                        context, modelFilename, objectDetectorOptions));
            } catch (IOException e) {
                Log.w(I_TAG, String.format("Model failure:\n  %s", e.getMessage()));
            }
        }
    }

    long totalTime = 0;

    List<Frame> processFrame(Bitmap bitmap, int frameIndex, float scaleFactor) {
        long startTime = System.currentTimeMillis();

        ArrayList<Frame> resultList = new ArrayList<>();
        Bitmap.Config config = bitmap.getConfig();

        //Log.v(TAG, "bitmap size = " + bitmap.getWidth() + " " + bitmap.getHeight() + " " + config.toString() + " " + scaleFactor);
        //Log.v(TAG, "==== detection start ====");
        for (ObjectDetector detector : detectorList) {
            //Log.v(TAG, "detector: " + detector.toString());

            TensorImage ti = TensorImage.fromBitmap(bitmap);

            List<Detection> detectionList = detector.detect(ti);
            List<Hazard> hazards = new ArrayList<>(detectionList.size());

            //Log.v(TAG, "detector detected " + detectionList.size() + " objects");

            for (Detection detection : detectionList) {
                //Log.d(TAG, "2");
                List<Category> categoryList = detection.getCategories();

                if (categoryList == null || categoryList.size() == 0) {
                    continue;
                }
                Category category = categoryList.get(0);

                RectF detBox = detection.getBoundingBox();
                Rect boundingBox = new Rect(
                        (int) (detBox.left * scaleFactor),
                        (int) (detBox.top * scaleFactor),
                        (int) (detBox.right * scaleFactor),
                        (int) (detBox.bottom * scaleFactor)
                );

                int origWidth = (int) (bitmap.getWidth() * scaleFactor);
                int origHeight = (int) (bitmap.getHeight() * scaleFactor);

                hazards.add(new Hazard(
                        category.getLabel(),
                        category.getScore(),
                        isDanger(boundingBox, category.getLabel(), origWidth, origHeight),
                        boundingBox
                ));
            }

            //Log.v(TAG, "==== detection end ====");

            //Log.d(TAG, "3");
            if (verbose) {
                //Log.d(TAG, "4");
                String resultHead = String.format(
                        Locale.ENGLISH,
                        "Analysis completed for frame: %04d\nDetected hazards: %02d\n",
                        frameIndex, hazards.size()
                );
                StringBuilder builder = new StringBuilder(resultHead);

                for (Hazard hazard : hazards) {
                    builder.append("  ");
                    builder.append(hazard.toString());
                }
                builder.append('\n');

                String resultMessage = builder.toString();
                Log.v(TAG, resultMessage);
            }

            resultList.add(new OuterFrame(frameIndex, hazards));
        }

        long endTime = System.currentTimeMillis();
        totalTime += endTime - startTime;

        //Log.d(TAG, "(Outer) Average time: " + (totalTime / (double)frameCnt));

        return resultList;
    }

    private boolean isDanger(Rect boundingBox, String category, int imageWidth, int imageHeight) {
        if (vehicleCategories.contains(category)) {
            // Check tailgating
            Rect tailgateZone = getTailgateZone(imageWidth, imageHeight);
            return tailgateZone.contains(boundingBox) || tailgateZone.intersect(boundingBox);
        } else {
            // Check obstruction
            Rect dangerZone = getDangerZone(imageWidth, imageHeight);
            return dangerZone.contains(boundingBox) || dangerZone.intersect(boundingBox);
        }
    }

    private Rect getDangerZone(int imageWidth, int imageHeight) {
        int dangerLeft = imageWidth / 4;
        int dangerRight = (imageWidth / 4) * 3;
        int dangerTop = (imageHeight / 10) * 4;

        return new Rect(dangerLeft, dangerTop, dangerRight, imageHeight);
    }

    private Rect getTailgateZone(int imageWidth, int imageHeight) {
        int tailLeft = imageWidth / 3;
        int tailRight = (imageWidth / 3) * 2;
        int tailTop = (imageHeight / 4) * 3;
        // Exclude driving car's bonnet, assuming it always occupies the same space
        //  Realistically, due to dash cam position/angle, bonnets will occupy differing proportion of the video
        int tailBottom = imageHeight - imageHeight / 10;

        return new Rect(tailLeft, tailTop, tailRight, tailBottom);
    }

    void setup(int width, int height) {
        // Doesn't need setup
    }

    float getScaleFactor(int width) {
        return 1.0f; // width / (float) inputSize;
    }

    public void printParameters() {
        StringJoiner paramMessage = new StringJoiner("\n  ");
        paramMessage.add("Outer analysis parameters:");
        paramMessage.add(String.format("MAX_DETECTIONS: %s", MAX_DETECTIONS));
        paramMessage.add(String.format("MIN_SCORE: %s", MIN_SCORE));
        paramMessage.add(String.format("TensorFlow Threads: %s", TF_THREAD_NUM));
        paramMessage.add(String.format("Analysis Threads: %s", THREAD_NUM));

        Log.i(I_TAG, paramMessage.toString());
    }
}
