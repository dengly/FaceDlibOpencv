package com.zzwtec.facedlibopencv.jni;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.zzwtec.facedlibopencv.util.Constants;
import com.zzwtec.facedlibopencv.util.FileUtils;

import java.io.File;

public class Face {
    /**
     * 初始化模型
     * @param modelpath
     * @param type 0 - 5点人脸标记模型、1 - 68点人脸标记模型、2 - 人脸模型、3 - 人脸识别模型
     * @return
     */
    public native static int initModel(String modelpath, int type);

    public native static int setFaceDownsampleRatio(int faceDownsampleRatio);

    //设置是否显示人脸框
    public native static int showBox(int show);

    //设置是否显示人脸特征线
    public native static int showLandMarks(int show);

    //设置是否只对最大的人脸做识别
    public native static int getMaxFace(int maxFace);

    //设置人脸识别的决策阈值 值越小越像
    private native static int setMyThreshold(float myThreshold);

    //设置人脸识别的决策阈值 值越大越像 取值在0到1间
    public static void setThreshold(float myThreshold){
        if(myThreshold<0 || myThreshold>1)return ;
        setMyThreshold(1.0f - myThreshold);
    }

    //人脸特征标记
    public native static String landMarks(long rgbaAAddr, long grayAddr, long bgrAddr, long rgbAddr, long displayAddr);


    /**
     * 人脸特征标记
     * @param srcAAddr 源
     * @param format 源类型 1 - rgb、2 - bgr、3 - gray
     * @param displayAddr 要显示
     * @return
     */
    public native static String landMarks1(long srcAAddr, int format, long displayAddr);

    //人脸特征标记
    public native static String landMarks2(long input,long output);

    //人脸识别
    public native static int faceRecognitionForPicture(long input,long output);


    /**
     * 人脸检测
     * @param srcAAddr 源
     * @param format 源类型 1 - rgb、2 - bgr、3 - gray
     * @param displayAddr 要显示
     * @return
     */
    public native static int faceDetector(long srcAAddr, int format, long displayAddr);

    /**
     * 人脸检测
     * @param srcAAddr 源
     * @param format 源类型 1 - rgb、2 - bgr、3 - gray
     * @param displayAddr 要显示
     * @return
     */
    public native static int faceDetectorByDNN(long srcAAddr, int format, long displayAddr);

    /**
     * 人脸识别
     * @param srcAAddr 源
     * @param format 源类型 1 - rgb、2 - bgr、3 - gray
     * @param displayAddr 要显示
     * @return
     */
    public native static int faceRecognition(long srcAAddr, int format, long displayAddr, String facesPath);

    /**
     * 初始化脸部描述符
     * 用于要识别的人
     */
    public native static int initFaceDescriptors(String facesPath);

    public static class FaceModelFileUtils {
        private static String TAG = "FaceModelFileUtils";

        @NonNull
        public static void copyFacePicFile(@NonNull final Context context) {
            final String targetPath = Constants.getFacePicPath();
            try{
                File file = new File(targetPath);
                if(!file.getParentFile().exists()){
                    file.getParentFile().mkdir();
                }
                if (!file.exists()) {
                    file.createNewFile();
                    FileUtils.copyFileFromAssetsToOthers(context.getApplicationContext(), Constants.facePicName, targetPath);
                }
            }catch (Exception e){
                Log.e(TAG,"copyFaceShape5ModelFile "+ targetPath +" 有异常",e);
            }
        }

        @NonNull
        public static void copyFaceShape5ModelFile(@NonNull final Context context) {
            final String targetPath = Constants.getFaceShape5ModelPath();
            try{
                File file = new File(targetPath);
                if(!file.getParentFile().exists()){
                    file.getParentFile().mkdir();
                }
                if (!file.exists()) {
                    file.createNewFile();
                    FileUtils.copyFileFromAssetsToOthers(context.getApplicationContext(), Constants.faceShape5ModelName, targetPath);
                }
            }catch (Exception e){
                Log.e(TAG,"copyFaceShape5ModelFile "+ targetPath +" 有异常",e);
            }
        }

        @NonNull
        public static void copyFaceShape68ModelFile(@NonNull final Context context){
            final String targetPath = Constants.getFaceShape68ModelPath();
            try{
                File file = new File(targetPath);
                if(!file.getParentFile().exists()){
                    file.getParentFile().mkdir();
                }
                if (!file.exists()) {
                    file.createNewFile();
                    FileUtils.copyFileFromAssetsToOthers(context.getApplicationContext(), Constants.faceShape68ModelName, targetPath);
                }
            }catch (Exception e){
                Log.e(TAG,"copyFaceShape68ModelFile "+ targetPath +" 有异常",e);
            }
        }

        @NonNull
        public static void copyHumanFaceModelFile(@NonNull final Context context){
            final String targetPath = Constants.getHumanFaceModelPath();
            try{
                File file = new File(targetPath);
                if(!file.getParentFile().exists()){
                    file.getParentFile().mkdir();
                }
                if (!file.exists()) {
                    file.createNewFile();
                    FileUtils.copyFileFromAssetsToOthers(context.getApplicationContext(), Constants.humanFaceModelName, targetPath);
                }
            }catch (Exception e){
                Log.e(TAG,"copyHumanFaceModelFile "+ targetPath +" 有异常",e);
            }
        }

        @NonNull
        public static void copyFaceRecognitionV1ModelFile(@NonNull final Context context){
            final String targetPath = Constants.getFaceRecognitionV1ModelPath();
            try{
                File file = new File(targetPath);
                if(!file.getParentFile().exists()){
                    file.getParentFile().mkdir();
                }
                if (!file.exists()) {
                    file.createNewFile();
                    FileUtils.copyFileFromAssetsToOthers(context.getApplicationContext(), Constants.faceRecognitionV1ModelName, targetPath);
                }
            }catch (Exception e){
                Log.e(TAG,"copyFaceRecognitionV1ModelFile "+ targetPath +" 有异常",e);
            }
        }
    }
}
