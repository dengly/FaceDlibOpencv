package com.zzwtec.facedlibopencv.util;

import android.os.Environment;

import java.io.File;

public class Constants {

    public static String faceShape5ModelName = "shape_predictor_5_face_landmarks.dat";
    public static String faceShape68ModelName = "shape_predictor_68_face_landmarks.dat";
    public static String humanFaceModelName = "mmod_human_face_detector.dat";
    public static String faceRecognitionV1ModelName = "dlib_face_recognition_resnet_model_v1.dat";
    private static String directory = "model";

    private static String face_directory = "faces";
    public static String facePicName = "dly.jpg";

    public static String getFacePicPath() {
        File sdcard = Environment.getExternalStorageDirectory();
        String targetPath = sdcard.getAbsolutePath() + File.separator + face_directory + File.separator + facePicName;
        return targetPath;
    }

    public static String getFacePicDirectoryPath() {
        File sdcard = Environment.getExternalStorageDirectory();
        String targetPath = sdcard.getAbsolutePath() + File.separator + face_directory ;
        return targetPath;
    }

    /**
     * 68个人脸特征的人脸检测算法模型
     * @return
     */
    public static String getFaceShape68ModelPath() {
        File sdcard = Environment.getExternalStorageDirectory();
        String targetPath = sdcard.getAbsolutePath() + File.separator + directory + File.separator + faceShape68ModelName;
        return targetPath;
    }

    /**
     * 5个人脸特征的人脸检测算法模型
     * @return
     */
    public static String getFaceShape5ModelPath() {
        File sdcard = Environment.getExternalStorageDirectory();
        String targetPath = sdcard.getAbsolutePath() + File.separator + directory + File.separator + faceShape5ModelName;
        return targetPath;
    }

    /**
     * 人脸检测算法模型
     * @return
     */
    public static String getHumanFaceModelPath() {
        File sdcard = Environment.getExternalStorageDirectory();
        String targetPath = sdcard.getAbsolutePath() + File.separator + directory + File.separator + humanFaceModelName;
        return targetPath;
    }

    /**
     * 人脸识别算法模型
     * @return
     */
    public static String getFaceRecognitionV1ModelPath() {
        File sdcard = Environment.getExternalStorageDirectory();
        String targetPath = sdcard.getAbsolutePath() + File.separator + directory + File.separator + faceRecognitionV1ModelName;
        return targetPath;
    }

}
