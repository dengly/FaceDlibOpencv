package com.zzwtec.facedlibopencv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import org.opencv.android.OpenCVLoader;

public class MainActivity extends Activity {

    private Button bt, recognitionbt, videoBt, videoRecognitionBt, videoDetectorBt, videoDetectorDnnBt, videoRecognitionAsynBt, arcRecognition_button, video_arc_recognition_button, video_arc_recognition_asyn_button;
    private Handler mHandler;
    private Boolean initflag = false;
    private static String TAG = "MainActivity";

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
        setContentView(R.layout.activity_main);
        mHandler =new Handler();

        final Context mContext = getApplicationContext();
        initImageBtn(mContext);
        initVideoBtn(mContext);

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

    private void initImageBtn(final Context mContext){

        bt = (Button) findViewById(R.id.button);
        bt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(mContext, ImageActivity.class);
                intent.putExtra("type",1); // 人脸检测并标记特征点
                startActivity(intent);
            }
        });

        recognitionbt = (Button) findViewById(R.id.recognition_button);
        recognitionbt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(mContext, ImageActivity.class);
                intent.putExtra("type",2); // 人脸识别
                startActivity(intent);
            }
        });

        arcRecognition_button = (Button) findViewById(R.id.arcRecognition_button);
        arcRecognition_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(mContext, ImageActivity.class);
                intent.putExtra("type",3); // 虹软图片人脸识别
                startActivity(intent);
            }
        });

    }

    private void initVideoBtn(final Context mContext){
        videoBt = (Button) findViewById(R.id.video_button);
        videoBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(mContext, VideoActivity.class);
                intent.putExtra("type",1); // 人脸特征标记
                startActivity(intent);
            }
        });

        videoRecognitionBt = (Button) findViewById(R.id.video_recognition_button);
        videoRecognitionBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(mContext, VideoActivity.class);
                intent.putExtra("type",2); // 同步人脸识别
                startActivity(intent);
            }
        });

        videoDetectorBt = (Button) findViewById(R.id.video_detector_button);
        videoDetectorBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(mContext, VideoActivity.class);
                intent.putExtra("type",3); // 人脸检测
                startActivity(intent);
            }
        });

        videoDetectorDnnBt = (Button) findViewById(R.id.video_detector_dnn_button);
        videoDetectorDnnBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(mContext, VideoActivity.class);
                intent.putExtra("type",4); // 人脸检测 通过dnn
                startActivity(intent);
            }
        });
        videoRecognitionAsynBt = (Button) findViewById(R.id.video_recognition_asyn_button);
        videoRecognitionAsynBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(mContext, VideoActivity.class);
                intent.putExtra("type",5); // 异步人脸识别
                startActivity(intent);
            }
        });

        video_arc_recognition_button = (Button) findViewById(R.id.video_arc_recognition_button);
        video_arc_recognition_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(mContext, VideoActivity.class);
                intent.putExtra("type",6); // 虹软视频人脸同步识别
                startActivity(intent);
            }
        });

        video_arc_recognition_asyn_button = (Button) findViewById(R.id.video_arc_recognition_asyn_button);
        video_arc_recognition_asyn_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(mContext, VideoActivity.class);
                intent.putExtra("type",7); // 虹软视频人脸异步识别
                startActivity(intent);
            }
        });

    }
}
