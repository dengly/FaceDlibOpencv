package com.zzwtec.facedlibopencv.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import com.zzwtec.facedlibopencv.jni.Face;
import com.zzwtec.facedlibopencv.R;
import com.zzwtec.facedlibopencv.face.ArcFace;
import com.zzwtec.facedlibopencv.util.Constants;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;

public class ImageActivity extends Activity {

    private int type = 1;
    private ImageView img;
    private Bitmap srcBitmap;
    private Handler mHandler;
    private static String TAG = "ImageActivity";

    private ArcFace mArcFace;

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {

        } else {
            System.loadLibrary("opencv_java3");
            System.loadLibrary("native-lib");
        }

        if(img!=null){
            srcBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.test);
            img.setImageBitmap(srcBitmap);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image);
        mHandler =new Handler();
        img = (ImageView) findViewById(R.id.imageView);

        //取得从上一个Activity当中传递过来的Intent对象
        Intent intent = getIntent();
        //从Intent当中根据key取得value
        if (intent != null) {
            type = intent.getIntExtra("type",1);
        }

        Face.setThreshold(MyApplication.getMyThreshold());
        if(type == 1){ // 人脸检测并标记特征点
            callFaceLandmark();
        }else if(type == 2){ // 人脸识别
            callFaceRecognition();
        }else if(type == 3){ // 虹软图片人脸识别
            arcFace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mArcFace != null){
            mArcFace.destroy();
        }
    }

    // 虹软图片人脸识别
    private void arcFace(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                if(mArcFace == null){
                    mArcFace = new ArcFace(16,2);
                    mArcFace.setThreshold(MyApplication.getMyThreshold());
                }
                if(srcBitmap==null){
                    srcBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.test);
                }

                final Bitmap displayBitmap = Bitmap.createBitmap(srcBitmap.getWidth(), srcBitmap.getHeight(), srcBitmap.getConfig()); //建立一个空的BItMap
                final float score = mArcFace.facedetectionForImage(srcBitmap,displayBitmap);
                runOnUiThread(new Runnable(){
                    @Override
                    public void run() {
                        img.setImageBitmap(displayBitmap);
                        Toast.makeText(getApplicationContext(),"相似度是 "+(score * 100)+"%",Toast.LENGTH_LONG);
                    }
                });
            }
        }).start();
    }

    //68点检测
    private void callFaceLandmark() {
        new Thread(new Runnable() {
            public void run() {
                Face.initModel(Constants.getFaceShape68ModelPath(),1);
                long detectStime =System.currentTimeMillis();
                Mat input = new Mat();
                Mat output = new Mat();
                Utils.bitmapToMat(srcBitmap, input);
                Face.landMarks2(input.getNativeObjAddr(), output.getNativeObjAddr());
                Utils.matToBitmap(output, srcBitmap);
                long detectTime = System.currentTimeMillis() - detectStime;
                String detectTimeStr = "检测68点,耗时:"+ String.valueOf(detectTime) + "ms.";
                Log.e(TAG, detectTimeStr);
                mHandler.post(updateImg);

            }
        }).start();
    }

    // 人脸识别
    private void callFaceRecognition() {
        new Thread(new Runnable() {
            public void run() {
                Face.initModel(Constants.getFaceShape5ModelPath(),0);
                Face.initModel(Constants.getFaceRecognitionV1ModelPath(),3);
                long detectStime =System.currentTimeMillis();
                Mat input = new Mat();
                Mat output = new Mat();
                Utils.bitmapToMat(srcBitmap, input);
                Face.faceRecognitionForPicture(input.getNativeObjAddr(), output.getNativeObjAddr());
                Utils.matToBitmap(output, srcBitmap);
                long detectTime = System.currentTimeMillis() - detectStime;
                String detectTimeStr = "人脸识别,耗时:"+ String.valueOf(detectTime) + "ms.";
                Log.e(TAG, detectTimeStr);
                mHandler.post(updateImg);

            }
        }).start();
    }

    //更新界面
    Runnable updateImg = new Runnable() {
        @Override
        public void run() {
            img.setImageBitmap(srcBitmap);
        }
    };

}
