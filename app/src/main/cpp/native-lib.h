#include <jni.h>

#ifndef _Included_com_zzwtec_facedlibopencv_Face
#define _Included_com_zzwtec_facedlibopencv_Face


#define TAG "Dlib-jni" // 这个是自定义的LOG的标识
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, TAG, __VA_ARGS__)  // 定义LOGV类型
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG,TAG ,__VA_ARGS__) // 定义LOGD类型
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,TAG ,__VA_ARGS__) // 定义LOGI类型
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,TAG ,__VA_ARGS__) // 定义LOGW类型
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,TAG ,__VA_ARGS__) // 定义LOGE类型
#define LOGF(...) __android_log_print(ANDROID_LOG_FATAL,TAG ,__VA_ARGS__) // 定义LOGF类型

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     com_zzwtec_facedlibopencv_Face
 * Method:    initModel
 * Signature: (Ljava/lang/String;I)I
 */
JNIEXPORT jint JNICALL Java_com_zzwtec_facedlibopencv_jni_Face_initModel
        (JNIEnv *, jclass, jstring, jint);

/*
 * Class:     com_zzwtec_facedlibopencv_Face
 * Method:    getMaxFace
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_com_zzwtec_facedlibopencv_jni_Face_getMaxFace
        (JNIEnv *, jclass, jint);

/*
 * Class:     com_zzwtec_facedlibopencv_Face
 * Method:    showBox
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_com_zzwtec_facedlibopencv_jni_Face_showBox
        (JNIEnv *, jclass, jint);

/*
 * Class:     com_zzwtec_facedlibopencv_Face
 * Method:    setFaceDownsampleRatio
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_com_zzwtec_facedlibopencv_jni_Face_setFaceDownsampleRatio
        (JNIEnv *, jclass, jint);

/*
 * Class:     com_zzwtec_facedlibopencv_Face
 * Method:    setMyThreshold
 * Signature: (F)I
 */
JNIEXPORT jint JNICALL Java_com_zzwtec_facedlibopencv_jni_Face_setMyThreshold
        (JNIEnv *, jclass, jfloat);

/*
 * Class:     com_zzwtec_facedlibopencv_Face
 * Method:    showLandMarks
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_com_zzwtec_facedlibopencv_jni_Face_showLandMarks
        (JNIEnv *, jclass, jint);

/*
 * Class:     com_zzwtec_facedlibopencv_Face
 * Method:    landMarks
 * Signature: (JJJJJ)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_zzwtec_facedlibopencv_jni_Face_landMarks
        (JNIEnv *, jclass, jlong, jlong, jlong, jlong, jlong);

/*
 * Class:     com_zzwtec_facedlibopencv_Face
 * Method:    landMarks
 * Signature: (JIJ)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_zzwtec_facedlibopencv_jni_Face_landMarks1
        (JNIEnv *, jclass, jlong, jint, jlong );

/*
 * Class:     com_zzwtec_facedlibopencv_Face
 * Method:    landMarks2
 * Signature: (JJ)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_zzwtec_facedlibopencv_jni_Face_landMarks2
        (JNIEnv *, jclass, jlong, jlong);

/*
 * Class:     com_zzwtec_facedlibopencv_Face
 * Method:    faceDetector
 * Signature: (JIJ)Ljava/lang/String;
 */
JNIEXPORT jint JNICALL Java_com_zzwtec_facedlibopencv_jni_Face_faceDetector
        (JNIEnv *, jclass, jlong, jint, jlong );

/*
 * Class:     com_zzwtec_facedlibopencv_Face
 * Method:    faceDetectorByDNN
 * Signature: (JIJ)Ljava/lang/String;
 */
JNIEXPORT jint JNICALL Java_com_zzwtec_facedlibopencv_jni_Face_faceDetectorByDNN
        (JNIEnv *, jclass, jlong, jint, jlong );

/*
 * Class:     com_zzwtec_facedlibopencv_Face
 * Method:    faceRecognitionForPicture
 * Signature: (JJ)I
 */
JNIEXPORT jint JNICALL Java_com_zzwtec_facedlibopencv_jni_Face_faceRecognitionForPicture
        (JNIEnv *, jclass, jlong, jlong );

/*
 * Class:     com_zzwtec_facedlibopencv_Face
 * Method:    faceRecognition
 * Signature: (JIJLjava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_com_zzwtec_facedlibopencv_jni_Face_faceRecognition
        (JNIEnv *, jclass, jlong, jint, jlong, jstring);

/*
 * Class:     com_zzwtec_facedlibopencv_Face
 * Method:    initFaceDescriptors
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_com_zzwtec_facedlibopencv_jni_Face_initFaceDescriptors
        (JNIEnv *, jclass, jstring);

#ifdef __cplusplus
}
#endif
#endif