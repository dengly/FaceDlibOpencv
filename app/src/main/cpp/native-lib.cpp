#include <native-lib.h>
#include <android/log.h>

#include <opencv2/opencv.hpp>
#include <opencv2/highgui/highgui.hpp>

#include <dlib/image_processing/frontal_face_detector.h>
#include <dlib/image_processing.h>
#include <dlib/opencv/cv_image.h>

#define TAG "Dlib-jni" // 这个是自定义的LOG的标识
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, TAG, __VA_ARGS__)  // 定义LOGV类型
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG,TAG ,__VA_ARGS__) // 定义LOGD类型
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,TAG ,__VA_ARGS__) // 定义LOGI类型
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,TAG ,__VA_ARGS__) // 定义LOGW类型
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,TAG ,__VA_ARGS__) // 定义LOGE类型
#define LOGF(...) __android_log_print(ANDROID_LOG_FATAL,TAG ,__VA_ARGS__) // 定义LOGF类型

#define FACE_DOWNSAMPLE_RATIO 4
#define SKIP_FRAMES 2

using namespace dlib;
using namespace std;
using namespace cv;

frontal_face_detector detector = get_frontal_face_detector();
shape_predictor pose_model;//定义个shape_predictor类的实例

bool initflag = false; // 初始化标记
bool showBox = true; // 是否显示人脸框
bool showLine = false; // 是否显示人脸特征线

// 初始化
void init(string modelpath){
    LOGE("init");
    // modelpath 是 shape_predictor_68_face_landmarks.dat 的文件路径
//    string model = modelpath + "/model/shape_predictor_68_face_landmarks.dat";
    deserialize(modelpath) >> pose_model; //读入标记点文件
}

// 画线
void draw_polyline(cv::Mat &img, const dlib::full_object_detection& d, const int start, const int end, bool isClosed = false) {
    std::vector<cv::Point> points;
    for (int i = start; i <= end; ++i) {
        points.push_back(cv::Point(d.part(i).x(), d.part(i).y()));
    }
    cv::polylines(img, points, isClosed, cv::Scalar(255,0,0), 2, 16);

}

// 脸部渲染器
void render_face(cv::Mat &img, const dlib::full_object_detection& d) {
    DLIB_CASSERT
            (
                    d.num_parts() == 68,
                    "\n\t Invalid inputs were given to this function. "
                            << "\n\t d.num_parts():  " << d.num_parts()
            );

    draw_polyline(img, d, 0, 16);           // Jaw line 下颌线
    draw_polyline(img, d, 17, 21);          // Left eyebrow 左眼眉
    draw_polyline(img, d, 22, 26);          // Right eyebrow 右眼眉
    draw_polyline(img, d, 27, 30);          // Nose bridge 鼻梁
    draw_polyline(img, d, 30, 35, true);    // Lower nose 降低鼻子
    draw_polyline(img, d, 36, 41, true);    // Left eye 左眼
    draw_polyline(img, d, 42, 47, true);    // Right Eye 右眼
    draw_polyline(img, d, 48, 59, true);    // Outer lip 外唇
    draw_polyline(img, d, 60, 67, true);    // Inner lip 内唇

}

//初始化dlib
JNIEXPORT jint JNICALL Java_com_zzwtec_facedlibopencv_Face_initModel
        (JNIEnv *env, jclass jobject,jstring path) {
    //获取绝对路径
    const char* modelPath;
    modelPath = env->GetStringUTFChars(path, 0);
    if(modelPath == NULL) {
        return 0;
    }
    string MPath = modelPath;

    LOGE("initModel");
    try {
        if(!initflag){
            init(MPath);
            initflag = true;
            return 1;
        }

    } catch (const std::exception &e) {

    } catch (...) {

    }
    return 0;
}

JNIEXPORT jint JNICALL Java_com_zzwtec_facedlibopencv_Face_showBox
        (JNIEnv * env, jclass jobject, jint show) {
    showBox = (int)show == 1 ;
    return 1;
}

JNIEXPORT jint JNICALL Java_com_zzwtec_facedlibopencv_Face_showLandMarks
        (JNIEnv * env, jclass jobject, jint show) {
    showLine = (int)show == 1 ;
    return 1;
}

JNIEXPORT jstring JNICALL Java_com_zzwtec_facedlibopencv_Face_landMarks2
        (JNIEnv * env, jclass jobject, jlong intPtr, jlong outPtr){
    Mat& inMat = *(Mat*)intPtr;
    Mat& outMat = *(Mat*)outPtr;
    outMat = inMat;

    LOGE("jnidetect");

    long start, finish;
    double totaltime;
    start = clock();

    cv::Rect box(0, 0, 0, 0);
    std::vector<cv::Point2d> pts2d;

    try{
        Mat result ;
        array2d<bgr_pixel> img;
        cvtColor(outMat, result, CV_RGBA2BGR);
        assign_image(img, cv_image<bgr_pixel>(result));

        std::vector<dlib::rectangle> dets = detector(img);

        int Max = 0;
        int area = 0; // 指定话大于一定面值的人脸
        if (dets.size() != 0) {
            for (unsigned long t = 0; t < dets.size(); ++t) {
                if (area < dets[t].width()*dets[t].height()) {
                    area = dets[t].width()*dets[t].height();
                    Max = t;
                }
            }
        }

        full_object_detection shape = pose_model(img, dets[Max]); // 一个人的人脸特征
        if(showBox) {
            box.x = dets[Max].left();
            box.y = dets[Max].top();
            box.width = dets[Max].width();
            box.height = dets[Max].height();
        }

        pts2d.clear();

        for (size_t k = 0; k < shape.num_parts(); k++) {
            Point2d p(shape.part(k).x(), shape.part(k).y());
            pts2d.push_back(p);
        }
    } catch (const std::exception &e) {

    } catch (...) {

    }

    finish = clock();
    totaltime = (double)(finish - start) / CLOCKS_PER_SEC;
    LOGE("time = %f\n", totaltime*1000);
    string str = "Fail";
    if(pts2d.size() == 68){
        if(showBox){
            cv::rectangle(outMat, box, Scalar(255, 0, 0), 2, 8, 0);
        }

        if(showLine){
            for (int i = 0; i < 17; i++)	circle(outMat, (pts2d)[i], 4, cv::Scalar(255, 0, 0), -1, 8, 0);
            for (int i = 17; i < 27; i++)	circle(outMat, (pts2d)[i], 4, cv::Scalar(255, 0, 0), -1, 8, 0);
            for (int i = 27; i < 31; i++)	circle(outMat, (pts2d)[i], 4, cv::Scalar(255, 0, 0), -1, 8, 0);
            for (int i = 31; i < 36; i++)	circle(outMat, (pts2d)[i], 4, cv::Scalar(255, 0, 0), -1, 8, 0);
            for (int i = 36; i < 48; i++)	circle(outMat, (pts2d)[i], 4, cv::Scalar(255, 0, 0), -1, 8, 0);
            for (int i = 48; i < 60; i++)	circle(outMat, (pts2d)[i], 4, cv::Scalar(255, 0, 0), -1, 8, 0);
            for (int i = 60; i < 68; i++)	circle(outMat, (pts2d)[i], 4, cv::Scalar(255, 0, 0), -1, 8, 0);
        }
        str = "Success";
    }

    const char* ret = str.c_str();
    return env->NewStringUTF(ret);
}

JNIEXPORT jstring JNICALL Java_com_zzwtec_facedlibopencv_Face_landMarks1
        (JNIEnv *env, jclass jobject, jlong srcAAddr, jint format, jlong displayAddr ) {
    Mat& mSrc = *(Mat*) srcAAddr;
    int formatType = (int)format;
    Mat& mDisplay = *(Mat*) displayAddr;

    LOGE("jnidetect");

    long start, finish;
    double totaltime;
    string str = "Fail";
    start = clock();
    try{
        std::vector<dlib::rectangle> faces ;
        if(formatType == 1){ // rgb
            array2d<rgb_pixel> img;
            assign_image(img, cv_image<rgb_pixel>(mSrc));
            faces = detector(img); //检测人脸，获得边界框

            LOGE("faces size:%d",faces.size());

            // Find the pose of each face.
//        std::vector<full_object_detection> shapes;
            for (unsigned long i = 0; i < faces.size(); ++i){
                full_object_detection shape = pose_model(img, faces[i]);  // 一个人的人脸特征
//            shapes.push_back(shape);

                if(showBox){
                    cv::Rect box(0, 0, 0, 0);
                    box.x = faces[i].left();
                    box.y = faces[i].top();
                    box.width = faces[i].width();
                    box.height = faces[i].height();
                    cv::rectangle(mDisplay, box, Scalar(255, 0, 0), 2, 8, 0);
                }
                if(showLine) {
                    // Custom Face Render
                    render_face(mDisplay, shape); //描线
                }
            }
        } else if(formatType == 2){ // bgr
            array2d<bgr_pixel> img;
            assign_image(img, cv_image<bgr_pixel>(mSrc));
            faces = detector(img); //检测人脸，获得边界框

            LOGE("faces size:%d",faces.size());

            // Find the pose of each face.
//        std::vector<full_object_detection> shapes;
            for (unsigned long i = 0; i < faces.size(); ++i){
                full_object_detection shape = pose_model(img, faces[i]);  // 一个人的人脸特征
//            shapes.push_back(shape);

                if(showBox){
                    cv::Rect box(0, 0, 0, 0);
                    box.x = faces[i].left();
                    box.y = faces[i].top();
                    box.width = faces[i].width();
                    box.height = faces[i].height();
                    cv::rectangle(mDisplay, box, Scalar(255, 0, 0), 2, 8, 0);
                }
                if(showLine) {
                    // Custom Face Render
                    render_face(mDisplay, shape); //描线
                }
            }

        }else if(formatType == 3){ // gray
            array2d<rgb_pixel> img;
            Mat result ;
            cvtColor(mSrc, result, CV_GRAY2RGB);
            assign_image(img, cv_image<rgb_pixel>(result));

            faces = detector(img); //检测人脸，获得边界框

            LOGE("faces size:%d",faces.size());

            // Find the pose of each face.
//        std::vector<full_object_detection> shapes;
            for (unsigned long i = 0; i < faces.size(); ++i){
                full_object_detection shape = pose_model(img, faces[i]);  // 一个人的人脸特征
//            shapes.push_back(shape);

                if(showBox){
                    cv::Rect box(0, 0, 0, 0);
                    box.x = faces[i].left();
                    box.y = faces[i].top();
                    box.width = faces[i].width();
                    box.height = faces[i].height();
                    cv::rectangle(mDisplay, box, Scalar(255, 0, 0), 2, 8, 0);
                }
                if(showLine) {
                    // Custom Face Render
                    render_face(mDisplay, shape); //描线
                }
            }
        }
        str = "Success";
    } catch (const std::exception &e) {

    } catch (...) {

    }

    finish = clock();
    totaltime = (double)(finish - start) / CLOCKS_PER_SEC;
    LOGE("time = %f\n", totaltime*1000);

    const char* ret = str.c_str();
    return env->NewStringUTF(ret);
}

JNIEXPORT jstring JNICALL Java_com_zzwtec_facedlibopencv_Face_landMarks
        (JNIEnv *env, jclass jobject, jlong rgbaAAddr, jlong grayAddr, jlong bgrAddr, jlong rgbAddr, jlong displayAddr ) {
    Mat& mRgba = *(Mat*) rgbaAAddr;
    Mat& mGray = *(Mat*) grayAddr;
    Mat& mBgr = *(Mat*) bgrAddr;
    Mat& mRgb = *(Mat*) rgbAddr;
    Mat& mDisplay = *(Mat*) displayAddr;

    LOGE("jnidetect");

    long start, finish;
    double totaltime;
    string str = "Fail";
    start = clock();
    try{
        array2d<rgb_pixel> img;
        assign_image(img, cv_image<rgb_pixel>(mRgb));

        std::vector<dlib::rectangle> faces = detector(img); //检测人脸，获得边界框

        LOGE("faces size:%d",faces.size());

        // Find the pose of each face.
//        std::vector<full_object_detection> shapes;
        for (unsigned long i = 0; i < faces.size(); ++i){
            full_object_detection shape = pose_model(img, faces[i]);  // 一个人的人脸特征
//            shapes.push_back(shape);

            if(showBox) {
                cv::Rect box(0, 0, 0, 0);
                box.x = faces[i].left();
                box.y = faces[i].top();
                box.width = faces[i].width();
                box.height = faces[i].height();
                cv::rectangle(mDisplay, box, Scalar(255, 0, 0), 2, 8, 0);
            }
            if(showLine) {
                // Custom Face Render
                render_face(mDisplay, shape); //描线
            }
        }
        str = "Success";
    } catch (const std::exception &e) {

    } catch (...) {

    }

    finish = clock();
    totaltime = (double)(finish - start) / CLOCKS_PER_SEC;
    LOGE("time = %f\n", totaltime*1000);

    const char* ret = str.c_str();
    return env->NewStringUTF(ret);
}