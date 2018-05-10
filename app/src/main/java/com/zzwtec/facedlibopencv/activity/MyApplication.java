package com.zzwtec.facedlibopencv.activity;

import android.app.Application;
import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.WindowManager;

import com.zzwtec.facedlibopencv.face.ArcFace;
import com.zzwtec.facedlibopencv.jni.Face;

import java.util.HashMap;
import java.util.Map;

public class MyApplication extends Application {
    private static final String TAG = "MyApplication";

    private static int cameraIdIndex=0;
    private static int[] cameraIds;
    private static Map<Integer,Integer> cameraInfoMap = new HashMap<>();
    private static float myThreshold = 0.56f; //人脸识别相似度值的决策阈值 值越大越像
    private static int faceDownsampleRatio=1; //图片压缩比

    private static int windowWidth;
    private static int windowHeight;

    public static int getFaceDownsampleRatio() {
        return faceDownsampleRatio;
    }

    public static int getWindowWidth() {
        return windowWidth;
    }

    public static int getWindowHeight() {
        return windowHeight;
    }

    public static float getMyThreshold() {
        return myThreshold;
    }

    public static int getCameraIdIndex() {
        return cameraIdIndex;
    }

    public static Map<Integer, Integer> getCameraInfoMap() {
        return cameraInfoMap;
    }

    public static int[] getCameraIds() {
        return cameraIds;
    }

    public static int getCurrentCameraId(){
        if(cameraIds!=null && cameraIds.length>0){
            return cameraIds[cameraIdIndex];
        }
        return -1;
    }

    public static int switchCameraId(){
        if(cameraIds!=null && cameraIds.length>0){
            cameraIdIndex = (++cameraIdIndex) % cameraIds.length;
            return cameraIds[cameraIdIndex];
        }
        return -1;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        WindowManager windowManager = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
        windowWidth = windowManager.getDefaultDisplay().getWidth();
        windowHeight = windowManager.getDefaultDisplay().getHeight();
        Log.i(TAG, "WindowWidth:" + MyApplication.getWindowWidth() + " WindowHeight:" + MyApplication.getWindowHeight());

        long windowArea = 1l * windowWidth * windowHeight;
        faceDownsampleRatio =
                windowArea >= 8847360l ? 12 : // 4096 * 2160
                windowArea >= 2073600l ? 6 : // 1920 * 1080
                windowArea >= 1228800l ? 4 : // 1280 * 960
                windowArea >= 691200l ? 3 : // 960 * 720
                windowArea >= 307200l ? 2 : // 640 * 480
                                        1;
        ArcFace.setFaceDownsampleRatio(faceDownsampleRatio);
        Face.setFaceDownsampleRatio(faceDownsampleRatio);

        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        if(Camera.getNumberOfCameras()>0){
            cameraIds = new int[Camera.getNumberOfCameras()];
        }
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            cameraIds[i] = i;
            Camera.getCameraInfo(i, cameraInfo);
            Log.i(TAG, "camera "+i + " info:"+ (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT? "FRONT":"BACK") );
            cameraInfoMap.put(i, cameraInfo.facing);
            if(cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK){
                cameraIdIndex = i;
            }
        }
    }

    static {
        System.loadLibrary("opencv_java3");
        System.loadLibrary("native-lib");
    }
}
