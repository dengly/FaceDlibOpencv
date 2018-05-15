package com.zzwtec.facedlibopencv.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Trace;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.zzwtec.facedlibopencv.R;
import com.zzwtec.facedlibopencv.face.ArcFace;
import com.zzwtec.facedlibopencv.face.TensorFlow;
import com.zzwtec.facedlibopencv.face.TensorFlowRecognition;
import com.zzwtec.facedlibopencv.jni.Face;
import com.zzwtec.facedlibopencv.util.CameraUtil;
import com.zzwtec.facedlibopencv.util.Constants;
import com.zzwtec.facedlibopencv.util.ImageUtil;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static String TAG = "VideoActivity";

    private Button button;
    private TextView textView;
    private Camera mCamera;

    private Mat mRgba;
    private Mat mRgb;
    private Mat mGray;
    private Mat mBgr;
    private Mat mDisplay;

    private int mWidth=0, mHeight=0, type = 1;

    private ImageView imageView;

    private volatile boolean check=false;

    private ArcFace mArcFace;

    private ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();

    private JavaCameraView javaCameraView;
    private BaseLoaderCallback baseLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    javaCameraView.enableView();
                }
                break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };


    private static final String TF_MODEL_FILE = "file:///android_asset/ssd_mobilenet_v1_android_export.pb";
    private static final String TF_LABELS_FILE = "file:///android_asset/coco_labels_list.txt";
    private TensorFlow mTensorFlow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //保持屏幕常亮
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_video);
        imageView = (ImageView) findViewById(R.id.imageView);
        javaCameraView = (JavaCameraView) findViewById(R.id.javaCameraView);
        javaCameraView.setVisibility(SurfaceView.VISIBLE);
        textView = (TextView) findViewById(R.id.textDlib);
        textView.setVisibility(View.VISIBLE);

        int mOrientation = getWindowManager().getDefaultDisplay().getRotation();

        ArcFace.setFaceDownsampleRatio(MyApplication.getFaceDownsampleRatio());
        Face.setFaceDownsampleRatio(MyApplication.getFaceDownsampleRatio());

        javaCameraView.setCameraDisplayRotation(CameraUtil.getCameraDisplayRotation(mOrientation));
        if(MyApplication.getCameraInfoMap().get(MyApplication.getCurrentCameraId()) == Camera.CameraInfo.CAMERA_FACING_FRONT){
            javaCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT); // 设置打开前置摄像头
        }else{
            javaCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK); // 设置打开后置摄像头
        }

        javaCameraView.setMaxFrameSize(MyApplication.getWindowWidth(),MyApplication.getWindowHeight());

        javaCameraView.setCvCameraViewListener(this);
        javaCameraView.setClickable(true);
        javaCameraView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCamera = javaCameraView.getCamera();
                if (mCamera!=null) mCamera.autoFocus(null);
            }
        });

        button = (Button)findViewById(R.id.button);
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MyApplication.switchCameraId();
                if(MyApplication.getCameraInfoMap().get(MyApplication.getCurrentCameraId()) == Camera.CameraInfo.CAMERA_FACING_FRONT){
                    javaCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT); // 设置打开前置摄像头
                }else{
                    javaCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK); // 设置打开后置摄像头
                }
                javaCameraView.changeCamera();
            }
        });

        //取得从上一个Activity当中传递过来的Intent对象
        Intent intent = getIntent();
        //从Intent当中根据key取得value
        if (intent != null) {
            int tempType = intent.getIntExtra("type",1);
            if(type != tempType){
                type = tempType;
            }
        }
        Face.setThreshold(MyApplication.getMyThreshold());

        singleThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if(type == 6 || type == 7){ // 虹软视频人脸同步识别  虹软视频人脸异步识别
                    if(mArcFace==null){
                        mArcFace = new ArcFace(16,1);
                        mArcFace.setThreshold(MyApplication.getMyThreshold());
                    }
                    mArcFace.initDB(Constants.getFacePicDirectoryPath());
                    runOnUiThread(new Runnable() {
                                      @Override
                                      public void run() {
                                          textView.setVisibility(View.GONE);
                                      }
                                  });
                }else if(type == 8){ // TensorFlow视频同步物体识别
                    mTensorFlow = TensorFlow.create(getAssets(),TF_MODEL_FILE,TF_LABELS_FILE);
                    mTensorFlow.enableStatLogging(true);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textView.setVisibility(View.GONE);
                        }
                    });
                }else{
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            textView.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mArcFace != null){
            mArcFace.destroy();
        }
        if(mTensorFlow!=null){
            mTensorFlow.close();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (javaCameraView != null)
            javaCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, baseLoaderCallback);
        } else {
            baseLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        imageView.getLayoutParams().width = width/6 ;
        imageView.getLayoutParams().height = height/6 ;
        mWidth = width;
        mHeight = height;
    }

    @Override
    public void onCameraViewStopped() {
        if(mRgba!=null)mRgba.release();
        if(mGray!=null)mGray.release();
        if(mBgr!=null)mBgr.release();
        if(mRgb!=null)mRgb.release();
        if(mDisplay!=null)mDisplay.release();
    }

    /**
     * 这个方法在子类有有效的对象时需要被调用，并且要把它传递给外部客户（通过回调），然后显示在屏幕上。
     * @param inputFrame
     * @return
     */
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Log.d(TAG, "VideoActivity onCameraFrame");

        if(MyApplication.getInitflag()){
            if(type >= 1 && type <= 5){ // dlib
                mDisplay = DlibProcess(inputFrame);
            }else if(type >= 6 && type <= 7){ // 虹软
                mDisplay = ArcFaceProcess(inputFrame);
            }else if(type == 8){ // TensorFlow
                mDisplay = TensorFlowProcess(inputFrame);
            }
            return mDisplay;
        }else{
            mRgba = inputFrame.rgba();
            final Bitmap srcBitmap = Bitmap.createBitmap(mRgba.width(), mRgba.height(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(mRgba,srcBitmap);
            runOnUiThread(new Runnable() {
                              @Override
                              public void run() {
                                  imageView.setImageBitmap(srcBitmap);
                              }
            });
            return mRgba;
        }
    }

    // TensorFlow
    private Mat TensorFlowProcess(CameraBridgeViewBase.CvCameraViewFrame inputFrame){
        if(type == 8){ // TensorFlow视频同步物体识别
            mRgb = inputFrame.rgb();
            if(mTensorFlow!=null){
                if(mTensorFlow.isInitFlags()){
                    Bitmap srcBitmap = Bitmap.createBitmap(mRgb.width(), mRgb.height(), Bitmap.Config.ARGB_8888);
                    final Bitmap displayBitmap = Bitmap.createBitmap(srcBitmap.getWidth(), srcBitmap.getHeight(), srcBitmap.getConfig()); //建立一个空的BItMap
                    Utils.matToBitmap(mRgb,srcBitmap);
                    Bitmap smallBitmap = srcBitmap; // 缩小图片
                    if(MyApplication.getFaceDownsampleRatio() != 1){
                        smallBitmap = ImageUtil.scaleBitmap(srcBitmap,1.0f / MyApplication.getFaceDownsampleRatio()); // 缩小图片
                    }
                    long startTime = -System.nanoTime();
                    List<TensorFlowRecognition> list = mTensorFlow.recognizeImage(smallBitmap);
                    double _time = (System.nanoTime()+startTime) / 1000000.0;
                    Log.i(TAG, "TensorFlow视频同步物体识别 time: " + _time +" ms");
                    if(list!=null && list.size()>0) {
                        int scale = MyApplication.getFaceDownsampleRatio();
                        if(scale!=1){
                            for(TensorFlowRecognition item : list){
                                RectF temp = item.getLocation();
                                temp.top = temp.top * scale;
                                temp.left = temp.left * scale;
                                temp.right = temp.right * scale;
                                temp.bottom = temp.bottom * scale;
                                item.setLocation(temp);
                            }
                        }
                        mTensorFlow.draw(srcBitmap,displayBitmap,list);
                        Utils.bitmapToMat(displayBitmap,mDisplay);
                    }else{
                        mDisplay = mRgb;
                    }
                }else {
                    if(mWidth!=0){
                        if(MyApplication.getFaceDownsampleRatio() != 1){
                            mTensorFlow.initBuffers(mWidth / MyApplication.getFaceDownsampleRatio(), mHeight / MyApplication.getFaceDownsampleRatio());
                        }else{
                            mTensorFlow.initBuffers(mWidth, mHeight);
                        }
                    }
                    mDisplay = mRgb;
                }
            }else{
                mDisplay = mRgba;
            }
        }
        return mDisplay;
    }

    // dlib
    private Mat DlibProcess(CameraBridgeViewBase.CvCameraViewFrame inputFrame){
        if(type == 1){ // 人脸特征标记
            // FpsMeter: 4.89

            Trace.beginSection("Face.landMarks1");
            mRgba = inputFrame.rgba();
            mGray = inputFrame.gray();
            mDisplay = mRgba;
            Face.landMarks1(mGray.getNativeObjAddr(),3,mDisplay.getNativeObjAddr());
            Trace.endSection();
        }else if(type == 3){ // 人脸检测
            Trace.beginSection("Face.faceDetector");
            mRgb = inputFrame.rgb();
            mGray = inputFrame.gray();
            mDisplay = mRgb;
            Face.faceDetector(mGray.getNativeObjAddr(),3,mDisplay.getNativeObjAddr());
            Trace.endSection();
        }else if(type == 4){ // 人脸检测 通过dnn
            Trace.beginSection("Face.faceDetectorByDNN");
            mRgb = inputFrame.rgb();
            mGray = inputFrame.gray();
            mDisplay = mRgb;
            Face.faceDetectorByDNN(mGray.getNativeObjAddr(),3,mDisplay.getNativeObjAddr());
            Trace.endSection();
        }else if(type == 2 ){ // 同步人脸识别
            Trace.beginSection("Face.faceRecognition");
            mRgb = inputFrame.rgb();
            mGray = inputFrame.gray();
            mDisplay = mRgb;
            int re = Face.faceRecognition(mGray.getNativeObjAddr(),3,mDisplay.getNativeObjAddr(),Constants.getFacePicDirectoryPath());
            final Bitmap srcBitmap = Bitmap.createBitmap(mDisplay.width(), mDisplay.height(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(mDisplay, srcBitmap);
            Trace.endSection();
            if(re>0){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(),"找到匹配的人",Toast.LENGTH_LONG);
                        imageView.setImageBitmap(srcBitmap);
                    }
                });
            }
        }else if(type == 5){ //异步人脸识别
            mRgb = inputFrame.rgb();
            mGray = inputFrame.gray();
            mDisplay = mRgb;

            if(!check){
                final Mat gray = mGray.clone();
                final Mat display = mDisplay.clone();
                check = true;
                singleThreadExecutor.execute(
                        new Runnable() {
                            @Override
                            public void run() {
                                int re = Face.faceRecognition(gray.getNativeObjAddr(),3,display.getNativeObjAddr(),Constants.getFacePicDirectoryPath());
                                final Bitmap srcBitmap = Bitmap.createBitmap(display.width(), display.height(), Bitmap.Config.ARGB_8888);
                                Utils.matToBitmap(display, srcBitmap);
                                gray.release();
                                display.release();
                                if(re>0){
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(getApplicationContext(),"找到匹配的人",Toast.LENGTH_LONG);
                                            imageView.setImageBitmap(srcBitmap);
                                        }
                                    });
                                }
                                check = false;
                            }
                        }
                );
            }
        }
        return mDisplay;
    }

    // 虹软
    private Mat ArcFaceProcess(CameraBridgeViewBase.CvCameraViewFrame inputFrame){
        if(type == 6){ // 虹软视频人脸同步识别
            Trace.beginSection("ArcFace.facerecognitionByDB");
            mRgba = inputFrame.rgba();
            mDisplay = mRgba;
            Bitmap srcBitmap = Bitmap.createBitmap(mRgba.width(), mRgba.height(), Bitmap.Config.ARGB_8888);
            final Bitmap displayBitmap = Bitmap.createBitmap(srcBitmap.getWidth(), srcBitmap.getHeight(), srcBitmap.getConfig()); //建立一个空的BItMap
            Utils.matToBitmap(mRgba,srcBitmap);
            float score = mArcFace.facerecognitionByDB(srcBitmap,displayBitmap);
            Trace.endSection();
            if(score > MyApplication.getMyThreshold()){
                Utils.bitmapToMat(displayBitmap,mDisplay);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(),"找到匹配的人",Toast.LENGTH_LONG);
                        imageView.setImageBitmap(displayBitmap);
                    }
                });
            }
        }else if(type == 7){ // 虹软视频人脸异步识别
            mRgba = inputFrame.rgba();
            mDisplay = mRgba;

            if(!check){
                final Mat rgba = mRgba.clone();
                check = true;
                singleThreadExecutor.execute(
                        new Runnable() {
                            @Override
                            public void run() {
                                final Bitmap srcBitmap = Bitmap.createBitmap(rgba.width(), rgba.height(), Bitmap.Config.ARGB_8888);
                                final Bitmap displayBitmap = Bitmap.createBitmap(srcBitmap.getWidth(), srcBitmap.getHeight(), srcBitmap.getConfig()); //建立一个空的BItMap
                                Utils.matToBitmap(rgba,srcBitmap);
                                rgba.release();
                                final float score = mArcFace.facerecognitionByDB(srcBitmap,displayBitmap);
                                if(score > MyApplication.getMyThreshold()){
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(getApplicationContext(),"找到匹配的人",Toast.LENGTH_LONG);
                                            imageView.setImageBitmap(displayBitmap);
                                        }
                                    });
                                }
                                check = false;
                            }
                        }
                );
            }
        }
        return mDisplay;
    }

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("opencv_java3");
        System.loadLibrary("native-lib");
    }
}
