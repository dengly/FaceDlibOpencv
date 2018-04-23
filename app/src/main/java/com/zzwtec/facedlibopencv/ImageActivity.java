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
import android.widget.Toast;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.io.File;

public class ImageActivity extends Activity {

    private Button bt;
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

        Button videoBt = (Button) findViewById(R.id.video_button);
        videoBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(mContext, MainActivity.class));
            }
        });


        bt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callFaceLandmark();
            }
        });

        final String targetPath = Constants.getFaceShapeModelPath();
        if (!new File(targetPath).exists()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG,"Copy landmark model to " + targetPath);
                    Toast.makeText(ImageActivity.this, "Copy landmark model to " + targetPath, Toast.LENGTH_SHORT).show();
                }
            });
            FileUtils.copyFileFromAssetsToOthers(getApplicationContext(), "shape_predictor_68_face_landmarks.dat", targetPath);
        }
    }

    //68点检测
    private void callFaceLandmark() {
        new Thread(new Runnable() {
            public void run() {
                if (!initflag) {
                    Face.initModel(Constants.getFaceShapeModelPath());
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
