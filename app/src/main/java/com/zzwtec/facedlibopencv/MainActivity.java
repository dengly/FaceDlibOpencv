package com.zzwtec.facedlibopencv;

import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static String TAG = "MainActivity";

    private Camera mCamera;

    private Boolean initflag = false;
    private Mat mRgba;
    private Mat mRgb;
    private Mat mGray;
    private Mat mBgr;
    private Mat mDisplay;

    private int mWidth, mHeight, type = 1;

    private Bitmap mCacheBitmap;

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
        setContentView(R.layout.activity_main);

        javaCameraView = (JavaCameraView) findViewById(R.id.javaCameraView);
        javaCameraView.setVisibility(SurfaceView.VISIBLE);
        javaCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT); // 设置打开前置摄像头
//        javaCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK); // 设置打开后置摄像头
        javaCameraView.setCvCameraViewListener(this);
        javaCameraView.setClickable(true);
        javaCameraView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCamera = javaCameraView.getCamera();
                if (mCamera!=null) mCamera.autoFocus(null);
            }
        });

        //取得从上一个Activity当中传递过来的Intent对象
        Intent intent = getIntent();
        //从Intent当中根据key取得value
        if (intent != null) {
            type = intent.getIntExtra("type",1);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                if(!initflag){
                    if(type == 1){ // 人脸特征标记
                        // FpsMeter: 5.20
//                        Face.initModel(Constants.getFaceShape5ModelPath(),0);

                        // FpsMeter: 4.7
                        Face.initModel(Constants.getFaceShape68ModelPath(),1);
                    }else if(type == 3){ // 人脸检测

                    }else if(type == 4){ // 人脸检测 通过dnn
                        Face.initModel(Constants.getHumanFaceModelPath(),2);
                    }else if(type == 2){ // 人脸识别
                        Face.initModel(Constants.getFaceShape5ModelPath(),0);
//                        Face.initModel(Constants.getFaceShape68ModelPath(),1);

                        Face.initModel(Constants.getFaceRecognitionV1ModelPath(),3);
                        Face.initFaceDescriptors(Constants.getFacePicDirectoryPath());
                    }

                    initflag = true;
                }
            }
        }).start();
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
        mWidth = width;
        mHeight = height;
        mRgba = new Mat();
        mGray = new Mat();
        mBgr = new Mat();
        mRgb = new Mat();
        mDisplay = new Mat();
    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
        mGray.release();
        mBgr.release();
        mRgb.release();
        mDisplay.release();
    }

    /**
     * 这个方法在子类有有效的对象时需要被调用，并且要把它传递给外部客户（通过回调），然后显示在屏幕上。
     * @param inputFrame
     * @return
     */
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Log.d(TAG, "MainActivity onCameraFrame");

        if(initflag){
            if(type == 1){ // 人脸特征标记
                // FpsMeter: 4.89
                mGray = inputFrame.gray();
                mRgba = inputFrame.rgba();
                mDisplay = mRgba;
                Face.landMarks1(mGray.getNativeObjAddr(),3,mDisplay.getNativeObjAddr());
            }else if(type == 3){ // 人脸检测
                mGray = inputFrame.gray();
                mRgb = inputFrame.rgb();
                mDisplay = mRgb;
                Face.faceDetector(mGray.getNativeObjAddr(),3,mDisplay.getNativeObjAddr());
            }else if(type == 4){ // 人脸检测 通过dnn
                mGray = inputFrame.gray();
                mRgb = inputFrame.rgb();
                mDisplay = mRgb;
                Face.faceDetectorByDNN(mGray.getNativeObjAddr(),3,mDisplay.getNativeObjAddr());
            }else if(type == 2){ // 人脸识别
                mGray = inputFrame.gray();
                mRgb = inputFrame.rgb();
                mDisplay = mRgb;
                Face.faceRecognition(mGray.getNativeObjAddr(),3,mDisplay.getNativeObjAddr());
            }

            return mDisplay;

        }else{
            return inputFrame.rgba();
        }
    }

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("opencv_java3");
        System.loadLibrary("native-lib");
    }
}
