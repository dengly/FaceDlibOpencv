package com.zzwtec.facedlibopencv;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

import com.arcsoft.facedetection.AFD_FSDKEngine;
import com.arcsoft.facedetection.AFD_FSDKError;
import com.arcsoft.facedetection.AFD_FSDKFace;
import com.arcsoft.facerecognition.AFR_FSDKEngine;
import com.arcsoft.facerecognition.AFR_FSDKError;
import com.arcsoft.facerecognition.AFR_FSDKFace;
import com.arcsoft.facerecognition.AFR_FSDKMatching;
import com.guo.android_extend.image.ImageConverter;

import java.util.ArrayList;
import java.util.List;

/**
 * 虹软的ArcFace
 */
public class ArcFace {
    private final String TAG = this.getClass().toString();

    private AFD_FSDKEngine engine_detection;
    private AFR_FSDKEngine engine_recognition;
    private static final String appid = "9ZyLr7ovgeeWhPm7sDpLfVxYBDBDGbksYy1zqcUMxCZF";
    private static final String sdkkey_FD = "5wbN6JiVkfkYYRisexnXfV2bxuFMsFe1LUQoGy5M2osd";
    private static final String sdkkey_FR = "5wbN6JiVkfkYYRisexnXfV36cWJ3i3ZKc7YNpUrC2S9r";

    private static boolean initDB = false;
    private static final float myThreshold = 0.5f ; //人脸识别相似度值的决策阈值

    private static final List<AFR_FSDKFace> faceList = new ArrayList<>();

    public ArcFace(){
        engine_detection = new AFD_FSDKEngine();
        engine_recognition = new AFR_FSDKEngine();

        //初始化人脸检测引擎，使用时请替换申请的APPID和SDKKEY
        AFD_FSDKError err = engine_detection.AFD_FSDK_InitialFaceEngine(appid,sdkkey_FD, AFD_FSDKEngine.AFD_OPF_0_HIGHER_EXT, 16, 5);
        Log.d(TAG, "AFD_FSDK_InitialFaceEngine = " + err.getCode());

        //初始化人脸识别引擎，使用时请替换申请的APPID 和SDKKEY
        AFR_FSDKError error = engine_recognition.AFR_FSDK_InitialEngine(appid, sdkkey_FR);
        Log.d(TAG, "AFR_FSDK_InitialEngine = " + error.getCode());
    }

    public void initDB(String path){

        initDB = true;
    }

    /**
     * 图片人脸识别
     * @param mBitmap
     */
    public float facedetectionForImage(Bitmap mBitmap){
        byte[] data = new byte[mBitmap.getWidth() * mBitmap.getHeight() * 3 / 2];
        ImageConverter convert = new ImageConverter();
        convert.initial(mBitmap.getWidth(), mBitmap.getHeight(), ImageConverter.CP_PAF_NV21);
        if (convert.convert(mBitmap, data)) {
            Log.d(TAG, "convert ok!");
        }
        convert.destroy();
        int width = mBitmap.getWidth();
        int height = mBitmap.getHeight();

        List<AFD_FSDKFace> result = new ArrayList<AFD_FSDKFace>();

        long time0 = -System.currentTimeMillis();
        engine_detection.AFD_FSDK_StillImageFaceDetection(data, width, height, AFD_FSDKEngine.CP_PAF_NV21, result);
        Log.i(TAG, "虹软 facedetection time: " + (System.currentTimeMillis()+time0)+" ms");

        AFR_FSDKFace face1 = new AFR_FSDKFace();
        AFR_FSDKFace face2 = new AFR_FSDKFace();

        //输入的data数据为NV21格式（如Camera里NV21格式的preview数据）；人脸坐标一般使用人脸检测返回的Rect传入；人脸角度请按照人脸检测引擎返回的值传入。
        long time1 = -System.currentTimeMillis();
        engine_recognition.AFR_FSDK_ExtractFRFeature(data, width, height, AFR_FSDKEngine.CP_PAF_NV21, result.get(0).getRect(), AFR_FSDKEngine.AFR_FOC_0, face1);
        Log.i(TAG, "虹软 人脸特征提取 time: " + (System.currentTimeMillis()+time1)+" ms");

        long time2 = -System.currentTimeMillis();
        engine_recognition.AFR_FSDK_ExtractFRFeature(data, width, height, AFR_FSDKEngine.CP_PAF_NV21, result.get(2).getRect(), AFR_FSDKEngine.AFR_FOC_0, face2);
        Log.i(TAG, "虹软 人脸特征提取 time: " + (System.currentTimeMillis()+time2)+" ms");

        Log.i(TAG, "虹软 两次人脸特征提取平均耗时 time: " + ((time1+time2)/2)+" ms");

        //score用于存放人脸对比的相似度值
        AFR_FSDKMatching score = new AFR_FSDKMatching();
        long time3 = -System.currentTimeMillis();
        engine_recognition.AFR_FSDK_FacePairMatching(face1, face2, score);
        Log.i(TAG, "虹软 人脸特征比对 time: " + (System.currentTimeMillis()+time3)+" ms");
        Log.i(TAG, "Score:" + score.getScore());
        Log.i(TAG, "虹软 总耗时: " + (time0+(time1+time2)/2+time3)+" ms ———— 一次人脸检测、两次人脸特征提取平均耗时、一次人脸特征比对");
        return score.getScore();
    }

    /**
     * 人脸识别
     */
    public float facerecognitionByDB(byte[] data, Rect rect, int width, int height){
        if(!initDB){
            return 0;
        }
        //用来存放提取到的人脸信息, face_1是注册的人脸，face_2是要识别的人脸
        AFR_FSDKFace face = new AFR_FSDKFace();

        AFR_FSDKError error;
        //输入的data数据为NV21格式（如Camera里NV21格式的preview数据）；人脸坐标一般使用人脸检测返回的Rect传入；人脸角度请按照人脸检测引擎返回的值传入。
        long time = -System.currentTimeMillis();
        error = engine_recognition.AFR_FSDK_ExtractFRFeature(data, width, height, AFR_FSDKEngine.CP_PAF_NV21, rect, AFR_FSDKEngine.AFR_FOC_0, face);
        Log.i(TAG, "虹软 人脸特征提取 time: " + (System.currentTimeMillis()+time)+" ms");

        Log.d(TAG, "Face=" + face.getFeatureData()[0]+ "," + face.getFeatureData()[1] + "," + face.getFeatureData()[2] + "," + error.getCode());

        //score用于存放人脸对比的相似度值
        AFR_FSDKMatching score = new AFR_FSDKMatching();
        for(AFR_FSDKFace item : faceList){
            time = -System.currentTimeMillis();
            error = engine_recognition.AFR_FSDK_FacePairMatching(face, item, score);
            Log.i(TAG, "虹软 人脸特征比对 time: " + (System.currentTimeMillis()+time)+" ms");

            Log.d(TAG, "Score:" + score.getScore());
            if(score.getScore() > myThreshold){
                return score.getScore();
            }
        }
        return 0;
    }

    /**
     * 人脸检测
     */
    public List<AFD_FSDKFace> facedetection(byte[] data, int width, int height){
        // 用来存放检测到的人脸信息列表
        List<AFD_FSDKFace> result = new ArrayList<AFD_FSDKFace>();

        //输入的data数据为NV21格式（如Camera里NV21格式的preview数据），其中height不能为奇数，人脸检测返回结果保存在result。
        long time = -System.currentTimeMillis();
        AFD_FSDKError err = engine_detection.AFD_FSDK_StillImageFaceDetection(data, width, height, AFD_FSDKEngine.CP_PAF_NV21, result);
        Log.i(TAG, "虹软 facedetection time: " + (System.currentTimeMillis()+time)+" ms");
        
        Log.d(TAG, "AFD_FSDK_StillImageFaceDetection =" + err.getCode());
        Log.d(TAG, "Face=" + result.size());
        for (AFD_FSDKFace face : result) {
            Log.d(TAG, "Face:" + face.toString());
        }
        return result;
    }

    /**
     * 人脸识别
     */
    public float facerecognition(byte[] data1, byte[] data2, int width, int height){
        //用来存放提取到的人脸信息, face_1是注册的人脸，face_2是要识别的人脸
        AFR_FSDKFace face1 = new AFR_FSDKFace();
        AFR_FSDKFace face2 = new AFR_FSDKFace();

        AFR_FSDKError error;
        //输入的data数据为NV21格式（如Camera里NV21格式的preview数据）；人脸坐标一般使用人脸检测返回的Rect传入；人脸角度请按照人脸检测引擎返回的值传入。
        long time = -System.currentTimeMillis();
        error = engine_recognition.AFR_FSDK_ExtractFRFeature(data1, width, height, AFR_FSDKEngine.CP_PAF_NV21, new Rect(210, 178, 478, 446), AFR_FSDKEngine.AFR_FOC_0, face1);
        Log.i(TAG, "虹软 人脸特征提取 time: " + (System.currentTimeMillis()+time)+" ms");

        Log.d(TAG, "Face=" + face1.getFeatureData()[0]+ "," + face1.getFeatureData()[1] + "," + face1.getFeatureData()[2] + "," + error.getCode());

        time = -System.currentTimeMillis();
        error = engine_recognition.AFR_FSDK_ExtractFRFeature(data1, width, height, AFR_FSDKEngine.CP_PAF_NV21, new Rect(210, 170, 470, 440), AFR_FSDKEngine.AFR_FOC_0, face2);
        Log.i(TAG, "虹软 人脸特征提取 time: " + (System.currentTimeMillis()+time)+" ms");

        Log.d(TAG, "Face=" + face2.getFeatureData()[0]+ "," + face2.getFeatureData()[1] + "," + face2.getFeatureData()[2] + "," + error.getCode());

        //score用于存放人脸对比的相似度值
        AFR_FSDKMatching score = new AFR_FSDKMatching();
        time = -System.currentTimeMillis();
        error = engine_recognition.AFR_FSDK_FacePairMatching(face1, face2, score);
        Log.i(TAG, "虹软 人脸特征比对 time: " + (System.currentTimeMillis()+time)+" ms");

        Log.d(TAG, "AFR_FSDK_FacePairMatching=" + error.getCode());
        Log.d(TAG, "Score:" + score.getScore());

        return score.getScore();
    }

    /**
     * 销毁引擎
     */
    public void destroy(){
        if(engine_detection != null){
            //销毁人脸检测引擎
            AFD_FSDKError err = engine_detection.AFD_FSDK_UninitialFaceEngine();
            Log.d(TAG, "AFD_FSDK_UninitialFaceEngine =" + err.getCode());
            engine_detection = null;
        }
        if(engine_detection != null){
            //销毁人脸识别引擎
            AFR_FSDKError error = engine_recognition.AFR_FSDK_UninitialEngine();
            Log.d(TAG, "AFR_FSDK_UninitialEngine : " + error.getCode());
            engine_recognition = null;
        }
    }
}
