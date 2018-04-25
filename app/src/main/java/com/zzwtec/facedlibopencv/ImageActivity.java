package com.zzwtec.facedlibopencv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;

public class ImageActivity extends Activity {

    private Button bt, videoBt, videoRecognitionBt, videoDetectorBt;
    private ImageView img;
    private Bitmap srcBitmap;
    private Handler mHandler;
    private Boolean initflag = false;
    private static String TAG = "ImageActivity";

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {

        } else {
            System.loadLibrary("opencv_java3");
            System.loadLibrary("native-lib");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image);
        mHandler =new Handler();
        img = (ImageView) findViewById(R.id.imageView);
        bt = (Button) findViewById(R.id.button);
        srcBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.face1);
        img.setImageBitmap(srcBitmap);

        final Context mContext = getApplicationContext();

        videoBt = (Button) findViewById(R.id.video_button);
        videoBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(mContext, MainActivity.class);
                intent.putExtra("type",1); // 人脸特征标记
                startActivity(intent);
            }
        });

        videoRecognitionBt = (Button) findViewById(R.id.video_recognition_button);
        videoRecognitionBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(mContext, MainActivity.class);
                intent.putExtra("type",2); // 人脸识别
                startActivity(intent);
            }
        });

        videoDetectorBt = (Button) findViewById(R.id.video_detector_button);
        videoDetectorBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(mContext, MainActivity.class);
                intent.putExtra("type",3); // 人脸检测
                startActivity(intent);
            }
        });

        bt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callFaceLandmark();
            }
        });

        bt.setVisibility(View.GONE);
        videoBt.setVisibility(View.GONE);
        videoRecognitionBt.setVisibility(View.GONE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG,"copy file ...");
                Face.FaceModelFileUtils.copyFaceRecognitionV1ModelFile(getApplicationContext());
                Face.FaceModelFileUtils.copyFaceShape5ModelFile(getApplicationContext());
                Face.FaceModelFileUtils.copyFaceShape68ModelFile(getApplicationContext());
                Face.FaceModelFileUtils.copyHumanFaceModelFile(getApplicationContext());
                Face.FaceModelFileUtils.copyFacePicFile(getApplicationContext());
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        bt.setVisibility(View.VISIBLE);
                        videoBt.setVisibility(View.VISIBLE);
                        videoRecognitionBt.setVisibility(View.VISIBLE);
                    }
                });
                Log.d(TAG,"copy file over");
            }
        }).start();
    }

    //68点检测
    private void callFaceLandmark() {
        new Thread(new Runnable() {
            public void run() {
                if (!initflag) {
                    Face.initModel(Constants.getFaceShape68ModelPath(),1);
                    initflag = true;
                }
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

    //更新界面
    Runnable updateImg = new Runnable() {
        @Override
        public void run() {
            img.setImageBitmap(srcBitmap);
        }
    };

}
