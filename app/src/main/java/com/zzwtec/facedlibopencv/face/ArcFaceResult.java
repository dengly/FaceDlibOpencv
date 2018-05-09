package com.zzwtec.facedlibopencv.face;

import android.graphics.Rect;

import com.arcsoft.facerecognition.AFR_FSDKFace;

public class ArcFaceResult {
    float mScore = 0.0F; // 人脸比对结果
    AFR_FSDKFace face; // 人脸特征
    AFR_FSDKFace faceDB; // 人脸特征库中的匹配的人脸特征
    String faceTag; // 人脸特征库中的匹配的标记
    Rect faceRect; // 人脸所在位置
}
