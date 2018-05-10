package com.zzwtec.facedlibopencv.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.zzwtec.facedlibopencv.R;
import com.zzwtec.facedlibopencv.face.ArcFace;
import com.zzwtec.facedlibopencv.jni.Face;
import com.zzwtec.facedlibopencv.util.CameraUtil;
import com.zzwtec.facedlibopencv.util.Constants;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static String TAG = "VideoActivity";

    private Button button;
    private Camera mCamera;

    private Mat mRgba;
    private Mat mRgb;
    private Mat mGray;
    private Mat mBgr;
    private Mat mDisplay;

    private int mWidth, mHeight, type = 1;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //保持屏幕常亮
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_video);
        imageView = (ImageView) findViewById(R.id.imageView);
        javaCameraView = (JavaCameraView) findViewById(R.id.javaCameraView);
        javaCameraView.setVisibility(SurfaceView.VISIBLE);

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
            if(type == 1){ // 人脸特征标记
                // FpsMeter: 4.89
                mRgba = inputFrame.rgba();
                mGray = inputFrame.gray();
                mDisplay = mRgba;
                Face.landMarks1(mGray.getNativeObjAddr(),3,mDisplay.getNativeObjAddr());
            }else if(type == 3){ // 人脸检测
                mRgb = inputFrame.rgb();
                mGray = inputFrame.gray();
                mDisplay = mRgb;
                Face.faceDetector(mGray.getNativeObjAddr(),3,mDisplay.getNativeObjAddr());
            }else if(type == 4){ // 人脸检测 通过dnn
                mRgb = inputFrame.rgb();
                mGray = inputFrame.gray();
                mDisplay = mRgb;
                Face.faceDetectorByDNN(mGray.getNativeObjAddr(),3,mDisplay.getNativeObjAddr());
            }else if(type == 2 ){ // 同步人脸识别
                mRgb = inputFrame.rgb();
                mGray = inputFrame.gray();
                mDisplay = mRgb;
                int re = Face.faceRecognition(mGray.getNativeObjAddr(),3,mDisplay.getNativeObjAddr(),Constants.getFacePicDirectoryPath());
                final Bitmap srcBitmap = Bitmap.createBitmap(mDisplay.width(), mDisplay.height(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(mDisplay, srcBitmap);
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
            }else if(type == 6){ // 虹软视频人脸同步识别
                mRgba = inputFrame.rgba();
                Bitmap srcBitmap = Bitmap.createBitmap(mRgba.width(), mRgba.height(), Bitmap.Config.ARGB_8888);
                final Bitmap displayBitmap = Bitmap.createBitmap(srcBitmap.getWidth(), srcBitmap.getHeight(), srcBitmap.getConfig()); //建立一个空的BItMap
                Utils.matToBitmap(mRgba,srcBitmap);
                float score = mArcFace.facerecognitionByDB(srcBitmap,displayBitmap);
                if(score==0){
                    mDisplay = mRgba;
                }else{
                    Utils.bitmapToMat(displayBitmap,mDisplay);
                    if(score > MyApplication.getMyThreshold()){
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getApplicationContext(),"找到匹配的人",Toast.LENGTH_LONG);
                                imageView.setImageBitmap(displayBitmap);
                            }
                        });
                    }
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

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("opencv_java3");
        System.loadLibrary("native-lib");
    }
}
