package com.zzwtec.facedlibopencv.activity;

import android.app.Application;
import android.hardware.Camera;
import android.util.Log;

import com.zzwtec.facedlibopencv.jni.Face;
import com.zzwtec.facedlibopencv.face.ArcFace;

import java.util.HashMap;
import java.util.Map;

public class MyApplication extends Application {
    private static final String TAG = "MyApplication";

    private static int cameraIdIndex=0;
    private static int[] cameraIds;
    private static Map<Integer,Integer> cameraInfoMap = new HashMap<>();
    private static float myThreshold = 0.56f; //人脸识别相似度值的决策阈值 值越大越像

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
        ArcFace.setFaceDownsampleRatio(4);
        Face.setFaceDownsampleRatio(4);

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
