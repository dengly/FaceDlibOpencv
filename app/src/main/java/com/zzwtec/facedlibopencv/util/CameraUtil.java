package com.zzwtec.facedlibopencv.util;

import android.app.Activity;
import android.hardware.Camera;
import android.util.Log;
import android.view.Surface;

import com.zzwtec.facedlibopencv.activity.MyApplication;

public class CameraUtil {
    // 获取摄像头合适角度
    public static int getCameraDisplayRotation(Activity activity) {
        int cameraId = MyApplication.getCurrentCameraId() ;
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Log.i("CameraUtil","DefaultDisplay rotation:"+rotation);
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (cameraId == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        Log.i("CameraUtil",cameraId == Camera.CameraInfo.CAMERA_FACING_FRONT ? "front":"back" + " getCameraDisplayRotation:"+result);
        return result;
    }

    public static int getCameraDisplayRotation(int rotation) {
        int cameraId = MyApplication.getCurrentCameraId() ;
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        Log.i("CameraUtil","DefaultDisplay rotation:"+rotation);
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (cameraId == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        Log.i("CameraUtil",cameraId == Camera.CameraInfo.CAMERA_FACING_FRONT ? "front":"back" + " getCameraDisplayRotation:"+result);
        return result;
    }
}
