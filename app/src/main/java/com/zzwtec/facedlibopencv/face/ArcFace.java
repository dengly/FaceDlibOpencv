package com.zzwtec.facedlibopencv.face;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.Camera;
import android.util.Log;

import com.arcsoft.facedetection.AFD_FSDKEngine;
import com.arcsoft.facedetection.AFD_FSDKError;
import com.arcsoft.facedetection.AFD_FSDKFace;
import com.arcsoft.facerecognition.AFR_FSDKEngine;
import com.arcsoft.facerecognition.AFR_FSDKError;
import com.arcsoft.facerecognition.AFR_FSDKFace;
import com.arcsoft.facerecognition.AFR_FSDKMatching;
import com.arcsoft.facetracking.AFT_FSDKEngine;
import com.arcsoft.facetracking.AFT_FSDKError;
import com.arcsoft.facetracking.AFT_FSDKFace;
import com.guo.android_extend.image.ImageConverter;
import com.zzwtec.facedlibopencv.util.ImageUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 虹软的ArcFace
 */
public class ArcFace {
    private final String TAG = this.getClass().toString();

    private AFD_FSDKEngine engine_detection;
    private AFR_FSDKEngine engine_recognition;
    private AFT_FSDKEngine engine_tracking;

    private static final String appid = "9ZyLr7ovgeeWhPm7sDpLfVxYBDBDGbksYy1zqcUMxCZF";
    private static final String sdkkey_FD = "5wbN6JiVkfkYYRisexnXfV2bxuFMsFe1LUQoGy5M2osd";
    private static final String sdkkey_FR = "5wbN6JiVkfkYYRisexnXfV36cWJ3i3ZKc7YNpUrC2S9r";
    private static final String sdkkey_FT = "5wbN6JiVkfkYYRisexnXfV2UoVz959C5wk3WS2jgpGBf";

    private static boolean initDB = false;
    private static float myThreshold = 0.5f ; //人脸识别相似度值的决策阈值 值越大越像

    /**
     * 设置人脸识别相似度值的决策阈值 值越大越像 取值在0到1间
     * @param myThreshold
     */
    public static void setThreshold(float myThreshold) {
        if(myThreshold<0 || myThreshold>1)return ;
        ArcFace.myThreshold = myThreshold;
    }
    public static float getMyThreshold() {
        return myThreshold;
    }

    private static int FACE_DOWNSAMPLE_RATIO = 4;

    public static void setFaceDownsampleRatio(int faceDownsampleRatio) {
        FACE_DOWNSAMPLE_RATIO = faceDownsampleRatio;
    }

    private static FaceDB faceDB = new FaceDB();

    static class FaceDB{
        private final List<AFR_FSDKFace> faceList ;
        private final List<String> tagList ;

        public FaceDB(){
            faceList = new ArrayList<>();
            tagList = new ArrayList<>();
        }

        public List<AFR_FSDKFace> getFaceList() {
            return faceList;
        }

        public List<String> getTagList() {
            return tagList;
        }
    }

    public ArcFace(){
        this(16, 5);
    }
    public ArcFace(int scale, int maxFaceNum){
        engine_detection = new AFD_FSDKEngine();
        engine_recognition = new AFR_FSDKEngine();
        engine_tracking = new AFT_FSDKEngine();

        //初始化人脸检测引擎，使用时请替换申请的APPID和SDKKEY
        AFD_FSDKError error_FD = engine_detection.AFD_FSDK_InitialFaceEngine(appid,sdkkey_FD, AFD_FSDKEngine.AFD_OPF_0_HIGHER_EXT, scale, maxFaceNum);
        Log.d(TAG, "AFD_FSDK_InitialFaceEngine = " + error_FD.getCode());

        //初始化人脸识别引擎，使用时请替换申请的APPID 和SDKKEY
        AFR_FSDKError error_FR = engine_recognition.AFR_FSDK_InitialEngine(appid, sdkkey_FR);
        Log.d(TAG, "AFR_FSDK_InitialEngine = " + error_FR.getCode());

        //初始化人脸跟踪引擎，使用时请替换申请的APPID和SDKKEY
        AFT_FSDKError error_FT = engine_tracking.AFT_FSDK_InitialFaceEngine(appid, sdkkey_FT, AFT_FSDKEngine.AFT_OPF_0_HIGHER_EXT, scale, maxFaceNum);
        Log.d("com.arcsoft", "AFT_FSDK_InitialFaceEngine =" + error_FT.getCode());
    }

    /**
     * 初始化人脸特征库
     * @param path
     */
    public void initDB(String path){
        if(initDB && faceDB.faceList !=null && faceDB.faceList.size() > 0){
           return ;
        }
        File root = new File(path);
        if(root.isDirectory()){
            File[] files = root.listFiles();
            if(files != null || files.length>0){
                for(File item : files){
                    if(!item.getName().equals(".") || !item.getName().equals("..")){
                        Bitmap bitmap = BitmapFactory.decodeFile(item.getAbsolutePath());
                        AFR_FSDKFace face = getFace(bitmap);
                        if(face!=null){
                            faceDB.getFaceList().add(face);
                            faceDB.getTagList().add(item.getName().substring(0,item.getName().indexOf(".")));
                        }
                    }
                }
            }
        }
        initDB = true;
    }

    /**
     * 获取人脸特征
     * @param bitmap
     * @return
     */
    public AFR_FSDKFace getFace(Bitmap bitmap){
        byte[] data = getBitmapData(bitmap);
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // 用来存放检测到的人脸信息列表
        List<AFD_FSDKFace> result_FD = new ArrayList<>();
        AFR_FSDKError error_FR;
        AFD_FSDKError error_FD;

        long time0 = -System.nanoTime();
        error_FD = engine_detection.AFD_FSDK_StillImageFaceDetection(data, width, height, AFD_FSDKEngine.CP_PAF_NV21, result_FD);
        double _time0 = (System.nanoTime()+time0) / 1000000.0;
        Log.i(TAG, "虹软 facedetection time: " + _time0 +" ms");
        Log.d(TAG, "AFD_FSDK_StillImageFaceDetection=" + error_FD.getCode());

        AFR_FSDKFace face = new AFR_FSDKFace();

        //输入的data数据为NV21格式（如Camera里NV21格式的preview数据）；人脸坐标一般使用人脸检测返回的Rect传入；人脸角度请按照人脸检测引擎返回的值传入。
        long time1 = -System.nanoTime();
        error_FR = engine_recognition.AFR_FSDK_ExtractFRFeature(data, width, height, AFR_FSDKEngine.CP_PAF_NV21, result_FD.get(0).getRect(), AFR_FSDKEngine.AFR_FOC_0, face);
        double _time1 = (System.nanoTime()+time1) / 1000000.0;
        Log.i(TAG, "虹软 人脸特征提取 time: " + _time1 +" ms");
        Log.d(TAG, "AFR_FSDK_ExtractFRFeature=" + error_FR.getCode());

        return face;
    }

    /**
     *
     * @param bitmap
     * @return
     */
    private byte[] getBitmapData(Bitmap bitmap){
        byte[] data = new byte[bitmap.getWidth() * bitmap.getHeight() * 3 / 2];
        ImageConverter convert = new ImageConverter();
        convert.initial(bitmap.getWidth(), bitmap.getHeight(), ImageConverter.CP_PAF_NV21);
        if (convert.convert(bitmap, data)) {
            Log.d(TAG, "convert ok!");
        }
        convert.destroy();
        return data;
    }

    /**
     * 图片人脸识别
     * @param mBitmap
     */
    public float facedetectionForImage(Bitmap mBitmap, Bitmap displayBitmap){
        byte[] data = getBitmapData(mBitmap);
        int width = mBitmap.getWidth();
        int height = mBitmap.getHeight();

        // 用来存放检测到的人脸信息列表
        List<AFD_FSDKFace> result_FD = new ArrayList<>();
        AFR_FSDKError error_FR;
        AFD_FSDKError error_FD;

        long time0 = -System.nanoTime();
        error_FD = engine_detection.AFD_FSDK_StillImageFaceDetection(data, width, height, AFD_FSDKEngine.CP_PAF_NV21, result_FD);
        double _time0 = (System.nanoTime()+time0) / 1000000.0;
        Log.i(TAG, "虹软 facedetection time: " + _time0 +" ms");
        Log.d(TAG, "AFD_FSDK_StillImageFaceDetection=" + error_FD.getCode());

        AFR_FSDKFace face1 = new AFR_FSDKFace();
        AFR_FSDKFace face2 = new AFR_FSDKFace();

        //输入的data数据为NV21格式（如Camera里NV21格式的preview数据）；人脸坐标一般使用人脸检测返回的Rect传入；人脸角度请按照人脸检测引擎返回的值传入。
        long time1 = -System.nanoTime();
        error_FR = engine_recognition.AFR_FSDK_ExtractFRFeature(data, width, height, AFR_FSDKEngine.CP_PAF_NV21, result_FD.get(0).getRect(), AFR_FSDKEngine.AFR_FOC_0, face1);
        double _time1 = (System.nanoTime()+time1) / 1000000.0;
        Log.i(TAG, "虹软 人脸特征提取 time: " + _time1 +" ms");
        Log.d(TAG, "AFR_FSDK_ExtractFRFeature=" + error_FR.getCode());

        long time2 = -System.nanoTime();
        error_FR = engine_recognition.AFR_FSDK_ExtractFRFeature(data, width, height, AFR_FSDKEngine.CP_PAF_NV21, result_FD.get(1).getRect(), AFR_FSDKEngine.AFR_FOC_0, face2);
        double _time2 = (System.nanoTime()+time2) / 1000000.0;
        Log.i(TAG, "虹软 人脸特征提取 time: " + _time2 +" ms");
        Log.d(TAG, "AFR_FSDK_ExtractFRFeature=" + error_FR.getCode());

        Log.i(TAG, "虹软 两次人脸特征提取平均耗时 time: " + ((_time1+_time2)/2)+" ms");

        //score用于存放人脸对比的相似度值
        AFR_FSDKMatching score = new AFR_FSDKMatching();
        long time3 = -System.nanoTime();
        error_FR = engine_recognition.AFR_FSDK_FacePairMatching(face1, face2, score);
        double _time3 = (System.nanoTime()+time3) / 1000000.0;
        Log.i(TAG, "虹软 人脸特征比对 time: " + _time3 +" ms");
        Log.d(TAG, "AFR_FSDK_FacePairMatching=" + error_FR.getCode());
        Log.i(TAG, "Score:" + score.getScore());
        Log.i(TAG, "虹软 总耗时: " + (_time0+(_time1+_time2)/2+_time3)+" ms ———— 一次人脸检测、两次人脸特征提取平均耗时、一次人脸特征比对");

        Canvas canvas = new Canvas(displayBitmap);
        canvas.drawBitmap(mBitmap,0,0,null);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        for(AFD_FSDKFace item : result_FD){
            canvas.drawLine(item.getRect().left,item.getRect().top,item.getRect().left,item.getRect().bottom,paint);
            canvas.drawLine(item.getRect().right,item.getRect().top,item.getRect().right,item.getRect().bottom,paint);
            canvas.drawLine(item.getRect().left,item.getRect().top,item.getRect().right,item.getRect().top,paint);
            canvas.drawLine(item.getRect().left,item.getRect().bottom,item.getRect().right,item.getRect().bottom,paint);
        }
        Paint textPaint = new Paint();//设置画笔
        textPaint.setTextSize(24.0f);//字体大小
        textPaint.setColor(Color.GREEN);//采用的颜色
        canvas.drawText("threshold:"+score.getScore(),result_FD.get(1).getRect().left,result_FD.get(1).getRect().top-26,textPaint);
        draw(mBitmap, displayBitmap, result_FD, "threshold:"+score.getScore(), 1);
        return score.getScore();
    }

    /**
     * 画线
     * @param mBitmap
     * @param displayBitmap
     * @param face
     */
    private void draw(Bitmap mBitmap, Bitmap displayBitmap, AFD_FSDKFace face){
        List<AFD_FSDKFace> result_FD = new ArrayList<>();
        result_FD.add(face);
        draw(mBitmap, displayBitmap, result_FD, null, 0);
    }
    private void draw(Bitmap mBitmap, Bitmap displayBitmap, List<AFD_FSDKFace> result_FD){
        draw(mBitmap, displayBitmap, result_FD, null, 0);
    }
    private void draw(Bitmap mBitmap, Bitmap displayBitmap, List<AFD_FSDKFace> result_FD, String tips, int tipsIndex){
        Canvas canvas = new Canvas(displayBitmap);
        canvas.drawBitmap(mBitmap,0,0,null);
        Paint paint = new Paint();
        paint.setStrokeWidth(6);
        paint.setColor(Color.RED);
        for(AFD_FSDKFace item : result_FD){
            canvas.drawLine(item.getRect().left,item.getRect().top,item.getRect().left,item.getRect().bottom,paint);
            canvas.drawLine(item.getRect().right,item.getRect().top,item.getRect().right,item.getRect().bottom,paint);
            canvas.drawLine(item.getRect().left,item.getRect().top,item.getRect().right,item.getRect().top,paint);
            canvas.drawLine(item.getRect().left,item.getRect().bottom,item.getRect().right,item.getRect().bottom,paint);
        }
        if(tips!=null){
            Paint textPaint = new Paint();//设置画笔
            textPaint.setTextSize(56.0f);//字体大小
            textPaint.setColor(Color.GREEN);//采用的颜色
            canvas.drawText(tips,result_FD.get(tipsIndex).getRect().left,result_FD.get(tipsIndex).getRect().top-10,textPaint);
        }
    }

    /**
     * 人脸识别
     */
    public float facerecognitionByDB(Bitmap mBitmap, Bitmap displayBitmap){
        if(!initDB){
            displayBitmap = mBitmap;
            return 0;
        }
        Bitmap smallBitmap = mBitmap; // 缩小图片
        if(FACE_DOWNSAMPLE_RATIO!=1){
            smallBitmap = ImageUtil.scaleBitmap(mBitmap,1.0f/FACE_DOWNSAMPLE_RATIO); // 缩小图片
        }
        byte[] data = getBitmapData(smallBitmap);
        int width = smallBitmap.getWidth();
        int height = smallBitmap.getHeight();

        // 用来存放检测到的人脸信息列表
        List<AFD_FSDKFace> result_FD = new ArrayList<>();
        AFR_FSDKError error_FR;
        AFD_FSDKError error_FD;

        long time0 = -System.nanoTime();
        error_FD = engine_detection.AFD_FSDK_StillImageFaceDetection(data, width, height, AFD_FSDKEngine.CP_PAF_NV21, result_FD);
        double _time0 = (System.nanoTime()+time0) / 1000000.0;
        Log.i(TAG, "虹软 facedetection time: " + _time0 +" ms");
        Log.d(TAG, "AFD_FSDK_StillImageFaceDetection=" + error_FD.getCode());

        if(result_FD.size()<=0){
            displayBitmap = mBitmap;
            return 0;
        }

        Rect maxRect = result_FD.get(0).getRect();
        int maxArea = maxRect.width() * maxRect.height();
        int maxOri = result_FD.get(0).getDegree(); // 人脸角度
        for(int i=1; i < result_FD.size(); i++){
            AFD_FSDKFace item = result_FD.get(i);
            Rect itemRect = item.getRect();
            int area = itemRect.width() * itemRect.height();
            if(maxArea < area){
                maxArea = area;
                maxRect = itemRect;
                maxOri = item.getDegree();
            }
        }

        //用来存放提取到的人脸信息, face_1是注册的人脸，face_2是要识别的人脸
        AFR_FSDKFace face = new AFR_FSDKFace();

        //输入的data数据为NV21格式（如Camera里NV21格式的preview数据）；人脸坐标一般使用人脸检测返回的Rect传入；人脸角度请按照人脸检测引擎返回的值传入。
        long time = -System.nanoTime();
        error_FR = engine_recognition.AFR_FSDK_ExtractFRFeature(data, width, height, AFR_FSDKEngine.CP_PAF_NV21, maxRect, maxOri, face);
        double _time1 = (System.nanoTime()+time) / 1000000.0;
        Log.i(TAG, "虹软 人脸特征提取 time: " + _time1 +" ms");

        Log.d(TAG, "Face=" + face.getFeatureData()[0]+ "," + face.getFeatureData()[1] + "," + face.getFeatureData()[2] + "," + error_FR.getCode());

        if(FACE_DOWNSAMPLE_RATIO!=1){
            for(AFD_FSDKFace item : result_FD){
                Rect itemRect = item.getRect();
                itemRect.set(itemRect.left * FACE_DOWNSAMPLE_RATIO, itemRect.top * FACE_DOWNSAMPLE_RATIO, itemRect.right * FACE_DOWNSAMPLE_RATIO, itemRect.bottom * FACE_DOWNSAMPLE_RATIO);
            }
        }

        //score用于存放人脸对比的相似度值
        AFR_FSDKMatching score = new AFR_FSDKMatching();
        for(int i=0; i<faceDB.getFaceList().size(); i++ ){
            AFR_FSDKFace item = faceDB.getFaceList().get(i);
            time = -System.nanoTime();
            error_FR = engine_recognition.AFR_FSDK_FacePairMatching(face, item, score);
            double _time2 = (System.nanoTime()+time) / 1000000.0;
            Log.i(TAG, "虹软 人脸特征比对 time: " + _time2 +" ms");

            Log.d(TAG, "Score:" + score.getScore());
            if(score.getScore() > myThreshold){
                Log.i(TAG, "虹软 总耗时: " + (_time0+_time1+_time2)+" ms ———— 一次人脸检测、一次人脸特征提取、一次人脸特征比对");
                draw(mBitmap, displayBitmap, result_FD, faceDB.getTagList().get(i)+" threshold:"+score.getScore(), 0);
                return score.getScore();
            }
        }
        draw(mBitmap, displayBitmap, result_FD);
        return 0;
    }

    /**
     * 人脸识别
     */
    public float facerecognitionByDB(byte[] data, Camera.Size size, Bitmap mBitmap, Bitmap displayBitmap){
        if(!initDB){
            return 0;
        }
        int width = size.width;
        int height = size.height;

        // 用来存放检测到的人脸信息列表
        List<AFD_FSDKFace> result_FD = new ArrayList<>();
        AFR_FSDKError error_FR;
        AFD_FSDKError error_FD;

        long time0 = -System.nanoTime();
        error_FD = engine_detection.AFD_FSDK_StillImageFaceDetection(data, width, height, AFD_FSDKEngine.CP_PAF_NV21, result_FD);
        double _time0 = (System.nanoTime()+time0) / 1000000.0;
        Log.i(TAG, "虹软 facedetection time: " + _time0 +" ms");
        Log.d(TAG, "AFD_FSDK_StillImageFaceDetection=" + error_FD.getCode());

        if(result_FD.size()<=0){
            return 0;
        }

        Rect maxRect = result_FD.get(0).getRect();
        int maxArea = maxRect.width() * maxRect.height();
        int maxOri = result_FD.get(0).getDegree(); // 人脸角度
        for(int i=1; i < result_FD.size(); i++){
            AFD_FSDKFace item = result_FD.get(i);
            Rect itemRect = item.getRect();
            int area = itemRect.width() * itemRect.height();
            if(maxArea < area){
                maxArea = area;
                maxRect = itemRect;
                maxOri = item.getDegree();
            }
        }

        //用来存放提取到的人脸信息, face_1是注册的人脸，face_2是要识别的人脸
        AFR_FSDKFace face = new AFR_FSDKFace();

        //输入的data数据为NV21格式（如Camera里NV21格式的preview数据）；人脸坐标一般使用人脸检测返回的Rect传入；人脸角度请按照人脸检测引擎返回的值传入。
        long time = -System.nanoTime();
        error_FR = engine_recognition.AFR_FSDK_ExtractFRFeature(data, width, height, AFR_FSDKEngine.CP_PAF_NV21, maxRect, maxOri, face);
        double _time1 = (System.nanoTime()+time) / 1000000.0;
        Log.i(TAG, "虹软 人脸特征提取 time: " + _time1 +" ms");

        Log.d(TAG, "Face=" + face.getFeatureData()[0]+ "," + face.getFeatureData()[1] + "," + face.getFeatureData()[2] + "," + error_FR.getCode());

        //score用于存放人脸对比的相似度值
        AFR_FSDKMatching score = new AFR_FSDKMatching();
        for(int i=0; i<faceDB.getFaceList().size(); i++ ){
            AFR_FSDKFace item = faceDB.getFaceList().get(i);
            time = -System.nanoTime();
            error_FR = engine_recognition.AFR_FSDK_FacePairMatching(face, item, score);
            double _time2 = (System.nanoTime()+time) / 1000000.0;
            Log.i(TAG, "虹软 人脸特征比对 time: " + _time2 +" ms");

            Log.i(TAG, "Score:" + score.getScore());
            if(score.getScore() > myThreshold){
                Log.i(TAG, "虹软 总耗时: " + (_time0+_time1+_time2)+" ms ———— 一次人脸检测、一次人脸特征提取、一次人脸特征比对");
                draw(mBitmap, displayBitmap, result_FD, faceDB.getTagList().get(i)+" threshold:"+score.getScore(), 0);
                return score.getScore();
            }
        }
        draw(mBitmap, displayBitmap, result_FD);
        return 0;
    }

    /**
     * 人脸跟踪
     * @param data
     * @param width
     * @param height
     */
    public List<AFT_FSDKFace> facetracking(byte[] data, int width, int height){
        // 用来存放检测到的人脸信息列表
        List<AFT_FSDKFace> result = new ArrayList<>();

        //输入的data数据为NV21格式（如Camera里NV21格式的preview数据），其中height不能为奇数，人脸跟踪返回结果保存在result。
        long time = -System.nanoTime();
        AFT_FSDKError err = engine_tracking.AFT_FSDK_FaceFeatureDetect(data, width, height, AFT_FSDKEngine.CP_PAF_NV21, result);
        double _time = (System.nanoTime()+time) / 1000000.0;
        Log.i(TAG, "虹软 facetracking time: " + _time +" ms");

        Log.d(TAG, "AFT_FSDK_FaceFeatureDetect =" + err.getCode());
        Log.d(TAG, "Face=" + result.size());
        for (AFT_FSDKFace face : result) {
            Log.d(TAG, "Face:" + face.toString());
        }
        return result;
    }

    /**
     * 人脸检测
     */
    public List<AFD_FSDKFace> facedetection(byte[] data, int width, int height){
        // 用来存放检测到的人脸信息列表
        List<AFD_FSDKFace> result = new ArrayList<AFD_FSDKFace>();

        //输入的data数据为NV21格式（如Camera里NV21格式的preview数据），其中height不能为奇数，人脸检测返回结果保存在result。
        long time = -System.nanoTime();
        AFD_FSDKError err = engine_detection.AFD_FSDK_StillImageFaceDetection(data, width, height, AFD_FSDKEngine.CP_PAF_NV21, result);
        double _time = (System.nanoTime()+time) / 1000000.0;
        Log.i(TAG, "虹软 facedetection time: " + _time +" ms");
        
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
        long time = -System.nanoTime();
        error = engine_recognition.AFR_FSDK_ExtractFRFeature(data1, width, height, AFR_FSDKEngine.CP_PAF_NV21, new Rect(210, 178, 478, 446), AFR_FSDKEngine.AFR_FOC_0, face1);
        double _time = (System.nanoTime()+time) / 1000000.0;
        Log.i(TAG, "虹软 人脸特征提取 time: " + _time +" ms");
        Log.d(TAG, "AFR_FSDK_ExtractFRFeature=" + error.getCode());

        Log.d(TAG, "Face=" + face1.getFeatureData()[0]+ "," + face1.getFeatureData()[1] + "," + face1.getFeatureData()[2] + "," + error.getCode());

        time = -System.nanoTime();
        error = engine_recognition.AFR_FSDK_ExtractFRFeature(data1, width, height, AFR_FSDKEngine.CP_PAF_NV21, new Rect(210, 170, 470, 440), AFR_FSDKEngine.AFR_FOC_0, face2);
        _time = (System.nanoTime()+time) / 1000000.0;
        Log.i(TAG, "虹软 人脸特征提取 time: " + _time +" ms");
        Log.d(TAG, "AFR_FSDK_ExtractFRFeature=" + error.getCode());

        Log.d(TAG, "Face=" + face2.getFeatureData()[0]+ "," + face2.getFeatureData()[1] + "," + face2.getFeatureData()[2] + "," + error.getCode());

        //score用于存放人脸对比的相似度值
        AFR_FSDKMatching score = new AFR_FSDKMatching();
        time = -System.nanoTime();
        error = engine_recognition.AFR_FSDK_FacePairMatching(face1, face2, score);
        _time = (System.nanoTime()+time) / 1000000.0;
        Log.i(TAG, "虹软 人脸特征比对 time: " + _time +" ms");

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
            AFR_FSDKError err = engine_recognition.AFR_FSDK_UninitialEngine();
            Log.d(TAG, "AFR_FSDK_UninitialEngine : " + err.getCode());
            engine_recognition = null;
        }
        if(engine_tracking != null){
            //销毁人脸跟踪引擎
            AFT_FSDKError err = engine_tracking.AFT_FSDK_UninitialFaceEngine();
            Log.d("com.arcsoft", "AFT_FSDK_UninitialFaceEngine =" + err.getCode());
        }
    }
}
