package com.zzwtec.facedlibopencv.face;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Trace;
import android.util.Log;

import com.zzwtec.facedlibopencv.activity.MyApplication;

import org.tensorflow.Graph;
import org.tensorflow.Operation;
import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Vector;

/**
 * TensorFlow
 * 参考 https://www.tensorflow.org/mobile/linking_libs
 */
public class TensorFlow {
    /*
    // Load the model from disk. 从磁盘加载模型
    TensorFlowInferenceInterface inferenceInterface = new TensorFlowInferenceInterface(assetManager, modelFilename);

    // Copy the input data into TensorFlow. 将输入数据复制到TensorFlow中
    inferenceInterface.feed(inputName, floatValues, 1, inputSize, inputSize, 3);

    // Run the inference call. 运行推理
    inferenceInterface.run(outputNames, logStats);

    // Copy the output Tensor back into the output array. 将输出张量复制回输出数组
    inferenceInterface.fetch(outputName, outputs);
    */


    private static String TAG = "TensorFlow";

    private static TensorFlow mTensorFlow;

    // Only return this many results. 设置最大返回结果
    private static final int MAX_RESULTS = 100;
    private static final int INPUT_SIZE = 300;

    // Config values.
    private String inputName;

    private TensorFlowInferenceInterface inferenceInterface;

    private Vector<String> labels = new Vector<String>();
    private int width, height;
    private int[] intValues; // 原图像素
    private byte[] byteValues; // 原图像素的RGB
    private float[] outputLocations; // 输出
    private float[] outputScores; // 输出
    private float[] outputClasses; // 输出
    private float[] outputNumDetections; // 输出
    private String[] outputNames; // 输出
    private boolean initFlags = false;

    private boolean logStats = false;

    private final float minimumConfidence = 0.6f;

    private TensorFlow(){}

    public static TensorFlow create(AssetManager assetManager, String modelFilename, String labelFilename){
        if(mTensorFlow==null){
            mTensorFlow = new TensorFlow();

            try{
                String actualFilename = labelFilename.split("file:///android_asset/")[1];
                InputStream labelsInput = assetManager.open(actualFilename);
                BufferedReader br = null;
                br = new BufferedReader(new InputStreamReader(labelsInput));
                String line;
                while ((line = br.readLine()) != null) {
                    mTensorFlow.labels.add(line);
                }
                br.close();
            }catch (Exception e){
                throw new RuntimeException("Problem reading label file!" , e);
            }

            mTensorFlow.inferenceInterface = new TensorFlowInferenceInterface(assetManager, modelFilename);

            final Graph g = mTensorFlow.inferenceInterface.graph();

            mTensorFlow.inputName = "image_tensor";
            /*
             * The inputName node has a shape of [N, H, W, C], where
             * N is the batch size
             * H = W are the height and width
             * C is the number of channels (3 for our purposes - RGB)
             * inputName 节点的形状为[N，H，W，C]，其中
             * N 是批量大小
             * H、W 是高度和宽度
             * C 是通道数量（3个用于我们的目的--RGB）
             */
            final Operation inputOp = g.operation(mTensorFlow.inputName);
            if (inputOp == null) {
                throw new RuntimeException("Failed to find input Node '" + mTensorFlow.inputName + "'");
            }
            // The outputScoresName node has a shape of [N, NumLocations], where N is the batch size.
            // outputScoresName 节点的形状为[N，NumLocations]，其中N是批量大小。
            final Operation outputOp1 = g.operation("detection_scores");
            if (outputOp1 == null) {
                throw new RuntimeException("Failed to find output Node 'detection_scores'");
            }
            final Operation outputOp2 = g.operation("detection_boxes");
            if (outputOp2 == null) {
                throw new RuntimeException("Failed to find output Node 'detection_boxes'");
            }
            final Operation outputOp3 = g.operation("detection_classes");
            if (outputOp3 == null) {
                throw new RuntimeException("Failed to find output Node 'detection_classes'");
            }
        }
        return mTensorFlow;
    }

    public static TensorFlow getTensorFlow(){
        return mTensorFlow;
    }

    public void initBuffers(int width, int height){
        this.width = width;
        this.height = height;
        // Pre-allocate buffers.
        outputNames = new String[] {"detection_boxes", "detection_scores", "detection_classes", "num_detections"};
        intValues = new int[width * height]; // 像素
        byteValues = new byte[width * height * 3]; // 像素的RGB
        outputScores = new float[MAX_RESULTS];
        outputLocations = new float[MAX_RESULTS * 4];
        outputClasses = new float[MAX_RESULTS];
        outputNumDetections = new float[1];
        initFlags = true;
    }

    public boolean isInitFlags(){
        return initFlags;
    }

    /**
     * 识别图像
     * @param bitmap ARGB_8888 格式图片
     * @return
     */
    public List<TensorFlowRecognition> recognizeImage(final Bitmap bitmap) {
        Trace.beginSection("recognizeImage"); // 记录此方法，以便可以使用systrace进行分析。

        Trace.beginSection("preprocessBitmap");
        // Preprocess the image data from 0-255 int to normalized float based on the provided parameters.
        // 根据提供的参数将图像数据从0-255 int预处理为标准化浮点数。
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        for (int i = 0; i < intValues.length; ++i) {
            byteValues[i * 3 + 2] = (byte) (intValues[i] & 0xFF); // ARGB_8888 中的 B
            byteValues[i * 3 + 1] = (byte) ((intValues[i] >> 8) & 0xFF); // ARGB_8888 中的 G
            byteValues[i * 3 + 0] = (byte) ((intValues[i] >> 16) & 0xFF); // ARGB_8888 中的 G
        }
        Trace.endSection(); // preprocessBitmap

        // Copy the input data into TensorFlow.
        // 将输入数据复制到TensorFlow中。
        Trace.beginSection("feed");
        inferenceInterface.feed(inputName, byteValues, 1, height, width, 3);
        Trace.endSection();

        // Run the inference call.
        // 运行推理。
        Trace.beginSection("run");
        inferenceInterface.run(outputNames, logStats);
        Trace.endSection();

        // Copy the output Tensor back into the output array.
        // 将输出张量复制回输出数组。
        Trace.beginSection("fetch");
        outputLocations = new float[MAX_RESULTS * 4]; // left、top、right、bottom 4个
        outputScores = new float[MAX_RESULTS];
        outputClasses = new float[MAX_RESULTS];
        outputNumDetections = new float[1];
        inferenceInterface.fetch(outputNames[0], outputLocations); // detection_boxes
        inferenceInterface.fetch(outputNames[1], outputScores); // detection_scores
        inferenceInterface.fetch(outputNames[2], outputClasses); // detection_classes
        inferenceInterface.fetch(outputNames[3], outputNumDetections); // num_detections
        Trace.endSection();

        // Find the best detections.
        // 找到最佳的检测结果。
        // 创建一个排序的队列
        final PriorityQueue<TensorFlowRecognition> pq =
                new PriorityQueue<TensorFlowRecognition>(
                        1,
                        new Comparator<TensorFlowRecognition>() {
                            @Override
                            public int compare(final TensorFlowRecognition lhs, final TensorFlowRecognition rhs) { // 排序
                                // Intentionally reversed to put high confidence at the head of the queue.
                                // 有意扭转对队列头高置信度。
                                return Float.compare(rhs.getConfidence(), lhs.getConfidence());
                            }
                        });

        // Scale them back to the input size.
        // 将它们缩放回输入大小。
        for (int i = 0; i < outputScores.length; ++i) {
            final RectF detection = new RectF(
                            outputLocations[4 * i + 1] * width,
                            outputLocations[4 * i] * height,
                            outputLocations[4 * i + 3] * width,
                            outputLocations[4 * i + 2] * height);
            TensorFlowRecognition result = new TensorFlowRecognition("" + i, labels.get((int) outputClasses[i]), outputScores[i], detection);
            Log.d(TAG,result.toString());
            if(result.getConfidence() >= minimumConfidence){
                pq.add(result);
            }
        }

        final ArrayList<TensorFlowRecognition> recognitions = new ArrayList<TensorFlowRecognition>();
        for (int i = 0; i < Math.min(pq.size(), MAX_RESULTS); ++i) {
            recognitions.add(pq.poll());
        }

        Trace.endSection();
        return recognitions;
    }

    public void draw(final Bitmap mBitmap, Bitmap displayBitmap, final List<TensorFlowRecognition> results){
        Trace.beginSection("draw");
        final Canvas canvas = new Canvas(displayBitmap);
        canvas.drawBitmap(mBitmap,0,0,null);
        final Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStrokeWidth(2.0f);
        Paint textPaint = new Paint();//设置画笔
        textPaint.setTextSize(24.0f);//字体大小
        textPaint.setColor(Color.GREEN);//采用的颜色

        for (final TensorFlowRecognition result : results) {
            RectF location = result.getLocation();
            canvas.drawLine(location.left,location.top,location.left,location.bottom,paint);
            canvas.drawLine(location.right,location.top,location.right,location.bottom,paint);
            canvas.drawLine(location.left,location.top,location.right,location.top,paint);
            canvas.drawLine(location.left,location.bottom,location.right,location.bottom,paint);
            canvas.drawText(result.getTitle(),location.left,location.top - 26,textPaint);
        }
        Trace.endSection();
    }

    public void enableStatLogging(final boolean debug){
        this.logStats = logStats;
    }

    public String getStatString(){
        if(inferenceInterface!=null){
            return inferenceInterface.getStatString();
        }
        return null;
    }

    public void close(){
        if(inferenceInterface!=null){
            inferenceInterface.close(); // 需要调用 close 释放资源
            inferenceInterface = null;
        }
    }
}
