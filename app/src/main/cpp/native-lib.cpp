#include <native-lib.h>
#include <android/log.h>

#include <opencv2/opencv.hpp>
#include <opencv2/highgui/highgui.hpp>

#include <dlib/image_processing/frontal_face_detector.h>
#include <dlib/image_processing.h>
#include <dlib/image_io.h>
#include <dlib/opencv/cv_image.h>
#include <dlib/dnn.h>
#include <dlib/clustering.h>

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

// ----------------------------------------------------------------------------------------

template <template <int, template<typename>class, int, typename> class block, int N, template<typename>class BN, typename SUBNET>
using residual = add_prev1<block<N, BN, 1, tag1<SUBNET>>>;

template <template <int, template<typename>class, int, typename> class block, int N, template<typename>class BN, typename SUBNET>
using residual_down = add_prev2<avg_pool<2, 2, 2, 2, skip1<tag2<block<N, BN, 2, tag1<SUBNET>>>>>>;

template <int N, template <typename> class BN, int stride, typename SUBNET>
using block = BN<con<N, 3, 3, 1, 1, relu<BN<con<N, 3, 3, stride, stride, SUBNET>>>>>;

template <int N, typename SUBNET> using ares = relu<residual<block, N, affine, SUBNET>>;
template <int N, typename SUBNET> using ares_down = relu<residual_down<block, N, affine, SUBNET>>;

template <typename SUBNET> using alevel0 = ares_down<256, SUBNET>;
template <typename SUBNET> using alevel1 = ares<256, ares<256, ares_down<256, SUBNET>>>;
template <typename SUBNET> using alevel2 = ares<128, ares<128, ares_down<128, SUBNET>>>;
template <typename SUBNET> using alevel3 = ares<64, ares<64, ares<64, ares_down<64, SUBNET>>>>;
template <typename SUBNET> using alevel4 = ares<32, ares<32, ares<32, SUBNET>>>;

using anet_type = loss_metric<fc_no_bias<128, avg_pool_everything<alevel0<alevel1<alevel2<alevel3<alevel4<max_pool<3, 3, 2, 2, relu<affine<con<32, 7, 7, 2, 2,input_rgb_image_sized<150>>>>>>>>>>>>>;

template <long num_filters, typename SUBNET> using con5d = con<num_filters, 5, 5, 2, 2, SUBNET>;
template <long num_filters, typename SUBNET> using con5  = con<num_filters, 5, 5, 1, 1, SUBNET>;

template <typename SUBNET> using downsampler  = relu<affine<con5d<32, relu<affine<con5d<32, relu<affine<con5d<16, SUBNET>>>>>>>>>;
template <typename SUBNET> using rcon5  = relu<affine<con5<45, SUBNET>>>;

using net_type = loss_mmod<con<1, 9, 9, 1, 1, rcon5<rcon5<rcon5<downsampler<input_rgb_image_pyramid<pyramid_down<6>>>>>>>>;

// ----------------------------------------------------------------------------------------

frontal_face_detector detector = get_frontal_face_detector(); // 不使用 GPU ,依赖 CPU
shape_predictor pose_model;//定义个shape_predictor类的实例

net_type net_humanFace; // 用于人脸检测
anet_type net_faceRecognition; // 用户人脸识别

bool initflag_faceLandmarks68 = false; // 68点人脸标记模型初始化标记
bool initflag_faceLandmarks5 = false; // 5点人脸标记模型初始化标记
bool initflag_humanFace = false; // 人脸模型初始化标记
bool initflag_faceRecognitionV1 = false; // 人脸识别模型初始化标记
bool showBox = true; // 是否显示人脸框
bool showLine = false; // 是否显示人脸特征线
bool useCNN = false; // 是否使用卷积神经网络（CNN）
float myThreshold = 0.6 ; //人脸识别的决策阈值

int checkFace = 0;

std::vector<matrix<float, 0, 1>> face_descriptors_db;

// ----------------------------------------------------------------------------------------

// 初始化
void initFaceLandmarks68Model(string faceLandmarks68Modelpath){
    deserialize(faceLandmarks68Modelpath) >> pose_model; // 68点人脸标记模型 shape_predictor_68_face_landmarks.dat
}
void initFaceLandmarks5Model(string faceLandmarks5Modelpath){
    deserialize(faceLandmarks5Modelpath) >> pose_model; // 5点人脸标记模型 shape_predictor_5_face_landmarks.dat
}
void initHumanFaceModel(string humanFaceModelpath){
    deserialize(humanFaceModelpath) >> net_humanFace; // 人脸检测模型 mmod_human_face_detector.dat
}
void initFaceRecognitionV1Model(string faceRecognitionV1ModelPath){
    deserialize(faceRecognitionV1ModelPath) >> net_faceRecognition;
}

// 人脸识别的抖动计算 可以提高面部识别精度 但会影响速度
std::vector<matrix<rgb_pixel>> jitter_image(const matrix<rgb_pixel>& img) {
    thread_local dlib::rand rnd;

    std::vector<matrix<rgb_pixel>> crops;
    for (int i = 0; i < 100; ++i)
        crops.push_back(jitter_image(img,rnd));

    return crops;
}

// 画线
void draw_polyline(cv::Mat &img, const dlib::full_object_detection& d, const int start, const int end, bool isClosed = false) {
    std::vector<cv::Point> points;
    for (int i = start; i <= end; ++i) {
        points.push_back(cv::Point(d.part(i).x() * FACE_DOWNSAMPLE_RATIO * 1.0 , d.part(i).y() * FACE_DOWNSAMPLE_RATIO * 1.0 ));
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

// 人脸检测
template <typename T>
void detector_face(array2d<T>& img, array2d<T>& img_small, Mat& mDisplay){
    std::vector<dlib::rectangle> faces ;
    faces = detector(img_small, 1); //检测人脸，获得边界框

    LOGI("faces size:%d",faces.size());

    // Find the pose of each face.
//    std::vector<full_object_detection> shapes;
    for (unsigned long i = 0; i < faces.size(); ++i){
        full_object_detection shape = pose_model(img, faces[i]);  // 一个人的人脸特征
//        shapes.push_back(shape);

        if(showBox){
            cv::Rect box(0, 0, 0, 0);
            box.x = faces[i].left() * FACE_DOWNSAMPLE_RATIO;
            box.y = faces[i].top() * FACE_DOWNSAMPLE_RATIO;
            box.width = faces[i].width() * FACE_DOWNSAMPLE_RATIO;
            box.height = faces[i].height() * FACE_DOWNSAMPLE_RATIO;
            cv::rectangle(mDisplay, box, Scalar(255, 0, 0), 2, 8, 0);
        }
        if(showLine) {
            // Custom Face Render
            render_face(mDisplay, shape); //描线
        }

    }
//    if(faces.size() > 0) {
//        net_faceRecognition(shapes);
//    }
}

//初始化dlib
JNIEXPORT jint JNICALL Java_com_zzwtec_facedlibopencv_Face_initModel
        (JNIEnv *env, jclass jobject, jstring path, jint type) {
    //获取绝对路径
    const char * modelpath = env->GetStringUTFChars(path, 0);
    if(modelpath == NULL) {
        return 0;
    }
    string modelpathStr = modelpath;

    LOGD("initModel");
    try {
        if(type == 0){ // 5点人脸标记模型
            if(!initflag_faceLandmarks5){
                LOGD("initFaceLandmarks5Model");
                initFaceLandmarks5Model(modelpathStr);
                initflag_faceLandmarks5 = true;
                return 1;
            }
        }else if(type == 1){ // 68点人脸标记模型
            if(!initflag_faceLandmarks68){
                LOGD("initFaceLandmarks68Model");
                initFaceLandmarks68Model(modelpathStr);
                initflag_faceLandmarks68 = true;
                return 1;
            }
        }else if(type == 2){ // 人脸模型
            if(!initflag_humanFace){
                LOGD("initHumanFaceModel");
                initHumanFaceModel(modelpathStr);
                initflag_humanFace = true;
                return 1;
            }
        }else if(type == 3){ // 人脸识别模型
            if(!initflag_faceRecognitionV1){
                LOGD("initFaceRecognitionV1Model");
                initFaceRecognitionV1Model(modelpathStr);
                initflag_faceRecognitionV1 = true;
                return 1;
            }
        }
    } catch (const std::exception &e) {
    } catch (...) {
    }
    env->ReleaseStringUTFChars(path, modelpath);
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
    string str = "Fail";
    if(!initflag_faceLandmarks68 && !initflag_faceLandmarks5){
        LOGE("没有初始化 68点人脸标记模型 或 5点人脸标记模型");
        const char* ret = str.c_str();
        return env->NewStringUTF(ret);
    }
    Mat& inMat = *(Mat*)intPtr;
    Mat& outMat = *(Mat*)outPtr;
    outMat = inMat;

    LOGD("jnidetect");

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

        std::vector<dlib::rectangle> dets = detector(img, 1);

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
    LOGD("detector face time = %f ms\n", totaltime*1000);
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
    string str = "Fail";
    if(!initflag_faceLandmarks68 && !initflag_faceLandmarks5){
        LOGE("没有初始化 68点人脸标记模型 或 5点人脸标记模型");
        const char* ret = str.c_str();
        return env->NewStringUTF(ret);
    }
    Mat& mSrc = *(Mat*) srcAAddr;
    int formatType = (int)format;
    Mat& mDisplay = *(Mat*) displayAddr;

    LOGD("jnidetect");

    long start, finish;
    double totaltime;
    start = clock();
    try{
        // Resize image for face detection
        cv::Mat mSrc_small;
        cv::resize(mSrc, mSrc_small, cv::Size(), 1.0/FACE_DOWNSAMPLE_RATIO, 1.0/FACE_DOWNSAMPLE_RATIO);

        if(formatType == 1){ // rgb
            array2d<rgb_pixel> img, img_small;
            assign_image(img_small, cv_image<rgb_pixel>(mSrc_small));
            assign_image(img, cv_image<rgb_pixel>(mSrc));

            detector_face( img, img_small, mDisplay);

        } else if(formatType == 2){ // bgr
            array2d<bgr_pixel> img, img_small;
            assign_image(img_small, cv_image<bgr_pixel>(mSrc_small));
            assign_image(img, cv_image<bgr_pixel>(mSrc));

            detector_face( img, img_small, mDisplay);

        }else if(formatType == 3){ // gray
            array2d<rgb_pixel> img, img_small;
            Mat result, result_small ;

            cvtColor(mSrc_small, result_small, CV_GRAY2RGB);
            assign_image(img_small, cv_image<rgb_pixel>(result_small));
            cvtColor(mSrc, result, CV_GRAY2RGB);
            assign_image(img, cv_image<rgb_pixel>(result));

            detector_face( img, img_small, mDisplay);

        }
        str = "Success";
    } catch (const std::exception &e) {

    } catch (...) {

    }

    finish = clock();
    totaltime = (double)(finish - start) / CLOCKS_PER_SEC;
    LOGD("detector face time = %f ms\n", totaltime*1000);

    const char* ret = str.c_str();
    return env->NewStringUTF(ret);
}

JNIEXPORT jstring JNICALL Java_com_zzwtec_facedlibopencv_Face_landMarks
        (JNIEnv *env, jclass jobject, jlong rgbaAAddr, jlong grayAddr, jlong bgrAddr, jlong rgbAddr, jlong displayAddr ) {
    string str = "Fail";
    if(!initflag_faceLandmarks68 && !initflag_faceLandmarks5){
        LOGE("没有初始化 68点人脸标记模型 或 5点人脸标记模型");
        const char* ret = str.c_str();
        return env->NewStringUTF(ret);
    }
    Mat& mRgba = *(Mat*) rgbaAAddr;
    Mat& mGray = *(Mat*) grayAddr;
    Mat& mBgr = *(Mat*) bgrAddr;
    Mat& mRgb = *(Mat*) rgbAddr;
    Mat& mDisplay = *(Mat*) displayAddr;

    LOGD("jnidetect");

    long start, finish;
    double totaltime;
    start = clock();
    try{
        array2d<rgb_pixel> img;
        assign_image(img, cv_image<rgb_pixel>(mRgb));

        std::vector<dlib::rectangle> faces = detector(img, 1); //检测人脸，获得边界框

        LOGI("faces size:%d",faces.size());

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
    LOGD("detector face time = %f ms\n", totaltime*1000);

    const char* ret = str.c_str();
    return env->NewStringUTF(ret);
}

JNIEXPORT jint JNICALL Java_com_zzwtec_facedlibopencv_Face_faceRecognition
        (JNIEnv *env, jclass jobject, jlong srcAAddr, jint format, jlong displayAddr ){
    if(!initflag_faceLandmarks68 && !initflag_faceLandmarks5){
        LOGE("没有初始化 68点人脸标记模型 或 5点人脸标记模型");
        return 0;
    }
    Mat& mSrc = *(Mat*) srcAAddr;
    int formatType = (int)format;
    Mat& mDisplay = *(Mat*) displayAddr;

    LOGD("jnidetect");

    int found=0;
    try{
        // Resize image for face detection
        cv::Mat mSrc_small;
        cv::resize(mSrc, mSrc_small, cv::Size(), 1.0/FACE_DOWNSAMPLE_RATIO, 1.0/FACE_DOWNSAMPLE_RATIO);

        matrix<rgb_pixel> img, img_small;
        if(formatType == 1){ // rgb
            assign_image(img_small, cv_image<rgb_pixel>(mSrc_small));
            assign_image(img, cv_image<rgb_pixel>(mSrc));
        } else if(formatType == 2){ // bgr
            Mat result, result_small ;
            cvtColor(mSrc_small, result_small, CV_BGR2RGB);
            assign_image(img_small, cv_image<rgb_pixel>(result_small));
            cvtColor(mSrc, result, CV_BGR2RGB);
            assign_image(img, cv_image<rgb_pixel>(result));
        }else if(formatType == 3){ // gray
            Mat result, result_small ;
            cvtColor(mSrc_small, result_small, CV_GRAY2RGB);
            assign_image(img_small, cv_image<rgb_pixel>(result_small));
            cvtColor(mSrc, result, CV_GRAY2RGB);
            assign_image(img, cv_image<rgb_pixel>(result));
        }

        long start_detector, start_recognition, finish;
        double totaltime_detector,totaltime_recognition;

        start_detector = clock();
        cv::Rect box(0, 0, 0, 0);
        std::vector<matrix<rgb_pixel>> faces;
        for (auto face : detector(img_small, 1)) {
            auto shape = pose_model(img, face); // 一个人的人脸特征
            matrix<rgb_pixel> face_chip;
            extract_image_chip(img, get_face_chip_details(shape,150,0.25), face_chip);
            faces.push_back(move(face_chip));

            if(showBox) {
                box.x = face.left() * FACE_DOWNSAMPLE_RATIO;
                box.y = face.top() * FACE_DOWNSAMPLE_RATIO;
                box.width = face.width() * FACE_DOWNSAMPLE_RATIO;
                box.height = face.height() * FACE_DOWNSAMPLE_RATIO;
                cv::rectangle(mDisplay, box, Scalar(255, 0, 0), 2, 8, 0);
            }
        }
        finish = clock();
        totaltime_detector = (double)(finish - start_detector) / CLOCKS_PER_SEC;
        LOGI("\nface detector time = %f ms\n", totaltime_detector*1000);
        start_recognition = clock();
        if (faces.size() > 0){
            std::vector<matrix<float, 0, 1>> face_descriptors = net_faceRecognition(faces);

            if (face_descriptors.size() < 1) {
                LOGI("获取人脸特征失败");
            } else {
                LOGI("获取到人脸特征数:%d",face_descriptors.size());
            }

            LOGI("--------------- 库的人脸特征数:%d",face_descriptors_db.size());

            size_t i = 0, j = 0;
            float thisThreshold;
            for (; i < face_descriptors.size(); ++i) {
                for (; j < face_descriptors_db.size(); ++j) {
                    thisThreshold = length(face_descriptors[i]-face_descriptors_db[j]);
                    LOGI("--------------- 比对值:%f",thisThreshold);
                    if (thisThreshold < myThreshold){
                        found=1;
                        break;
                    }
                }
            }
            if(found==0){
                cv::putText(mDisplay, to_string(thisThreshold), cv::Point(box.x,box.y-2), cv::FONT_HERSHEY_SIMPLEX, 1, Scalar(181, 127, 255), 2, cv::LINE_AA);
                LOGI("找不到库里的人");
            }else{
                // putText( InputOutputArray img, const String& text, Point org, int fontFace, double fontScale, Scalar color, int thickness = 1, int lineType = LINE_8, bool bottomLeftOrigin = false )
                cv::putText(mDisplay, "dly " + to_string(thisThreshold), cv::Point(box.x,box.y-2), cv::FONT_HERSHEY_SIMPLEX, 1, Scalar(181, 127, 255), 2, cv::LINE_AA);
                LOGI("找到库里的人 哈哈哈");
            }
        }
        finish = clock();
        totaltime_recognition = (double)(finish - start_recognition) / CLOCKS_PER_SEC;
        LOGI("face recognition time = %f ms\n", totaltime_recognition*1000);

        LOGI("all time = %f ms\n", (totaltime_detector + totaltime_recognition)*1000);

    } catch (const std::exception &e) {

    } catch (...) {

    }

    return found;
}

JNIEXPORT jint JNICALL Java_com_zzwtec_facedlibopencv_Face_initFaceDescriptors
        (JNIEnv *env, jclass jobject, jstring path){
    //获取绝对路径
    const char * facesPath = env->GetStringUTFChars(path, 0);
    if(facesPath == NULL) {
        return 0;
    }

    try{
        std::vector<matrix<rgb_pixel>> faces;

        DIR *pDir = NULL;
        struct dirent *dmsg;
        char szFileName[128];
        char szFolderName[128];

        strcpy(szFolderName, facesPath);
        strcat(szFolderName, "/%s");
        if ((pDir = opendir(facesPath)) != NULL) {
            // 遍历目录并删除文件
            while ((dmsg = readdir(pDir)) != NULL) {
                if (strcmp(dmsg->d_name, ".") != 0 && strcmp(dmsg->d_name, "..") != 0) {
                    sprintf(szFileName, szFolderName, dmsg->d_name);

                    if (!strcmp(strstr(szFileName, ".") + 1 , "jpg")){
                        matrix<rgb_pixel> img;
                        char path[260];
                        string fileName = szFileName;
                        load_image(img, fileName);

                        for (auto face : detector(img, 1)) {
                            auto shape = pose_model(img, face); // 一个人的人脸特征
                            matrix<rgb_pixel> face_chip;
                            extract_image_chip(img, get_face_chip_details(shape,150,0.25), face_chip);
                            faces.push_back(move(face_chip));
                        }

                        if (faces.size() > 0){
                            std::vector<matrix<float, 0, 1>> face_descriptors = net_faceRecognition(faces);

                            for(auto item : face_descriptors) {
                                face_descriptors_db.push_back(item);
                            }

                            if (face_descriptors_db.size() < 1) {
                                LOGI("initFaceDescriptors 获取人脸特征失败");
                            } else {
                                LOGI("initFaceDescriptors 获取到人脸特征数:%d",face_descriptors_db.size());
                            }
                        }
                    }
                }
            }
        }

        if (pDir != NULL) {
            closedir(pDir);
        }
        env->ReleaseStringUTFChars(path, facesPath);
        return 1;
    } catch (const std::exception &e) {

    } catch (...) {

    }
    return 0;
}