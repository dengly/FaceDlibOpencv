package com.zzwtec.facedlibopencv;

import android.os.Environment;

import java.io.File;

public class Constants {

    public static String getFaceShapeModelPath() {
        File sdcard = Environment.getExternalStorageDirectory();
        String targetPath = sdcard.getAbsolutePath() + File.separator + "model" + File.separator + "shape_predictor_68_face_landmarks.dat";
        return targetPath;
    }

}
