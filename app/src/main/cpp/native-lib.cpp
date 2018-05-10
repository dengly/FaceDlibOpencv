#include <native-lib.h>
#include <android/log.h>

#include <string.h>

#include <opencv2/opencv.hpp>
#include <opencv2/highgui/highgui.hpp>

#include <dlib/image_processing/frontal_face_detector.h>
#include <dlib/image_processing.h>
#include <dlib/image_io.h>
#include <dlib/opencv/cv_image.h>
#include <dlib/dnn.h>
#include <dlib/clustering.h>

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

frontal_face_detector detector = dlib::get_frontal_face_detector(); // 不使用 GPU ,依赖 CPU
int detectorTimes = 0; // 上采样次数 dlib的人脸检测器只能检测80x80和更大的人脸，如果需要检测比它小的人脸，需要对图像上采样，一次上采样图像尺寸放大一倍
shape_predictor pose_model;//shape_predictor的作用是：以图像的某块区域为输入，输出一系列的点（point location）以表示此图像region里object的姿势pose。所以，shape_predictor主要用于表示object的姿势

net_type net_humanFace; // 用于人脸检测
anet_type net_faceRecognition; // 用户人脸识别

bool initflag_faceLandmarks68 = false; // 68点人脸标记模型初始化标记
bool initflag_faceLandmarks5 = false; // 5点人脸标记模型初始化标记
bool initflag_humanFace = false; // 人脸模型初始化标记
bool initflag_faceRecognitionV1 = false; // 人脸识别模型初始化标记
bool initflag_faceDB = false; // 人脸库初始化标记
bool showBox = true; // 是否显示人脸框
bool showLine = false; // 是否显示人脸特征线
bool maxFace = false; // 是否只对最大的人脸做识别
bool useCNN = false; // 是否使用卷积神经网络（CNN）
float myThreshold = 0.5 ; //人脸识别的决策阈值 值越小越像

int checkFace = 0;

int FACE_DOWNSAMPLE_RATIO = 4;
int SKIP_FRAMES = 2;

struct FaceDB{
    matrix<float, 0, 1> face_descriptors;
    string label;
};

std::vector<FaceDB> faceDBs;

// ----------------------------------------------------------------------------------------

// 初始化
void initFaceLandmarks68Model(string faceLandmarks68Modelpath){
    dlib::deserialize(faceLandmarks68Modelpath) >> pose_model; // 68点人脸标记模型 shape_predictor_68_face_landmarks.dat
}
void initFaceLandmarks5Model(string faceLandmarks5Modelpath){
    dlib::deserialize(faceLandmarks5Modelpath) >> pose_model; // 5点人脸标记模型 shape_predictor_5_face_landmarks.dat
}
void initHumanFaceModel(string humanFaceModelpath){
    dlib::deserialize(humanFaceModelpath) >> net_humanFace; // 人脸检测模型 mmod_human_face_detector.dat
}
void initFaceRecognitionV1Model(string faceRecognitionV1ModelPath){
    dlib::deserialize(faceRecognitionV1ModelPath) >> net_faceRecognition;
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

/**
 * 人脸检测
 * @tparam T
 * @param img
 * @param img_small
 * @param mDisplay
 * @return
 */
template <typename T>
int detector_face(matrix<T>& img, matrix<T>& img_small, Mat& mDisplay){
    long start_detector=0, finish=0;
    double totaltime_detector=0.0;
    int found = 0;
    start_detector = clock();

    for (auto face_small_rectangle : detector(img_small, detectorTimes)){ //检测人脸，获得边界框
//        if(face_small_rectangle.width() * face_small_rectangle.height() * FACE_DOWNSAMPLE_RATIO * FACE_DOWNSAMPLE_RATIO < 150 * 150){
//            continue;
//        }
        found++;
        auto shape_small = pose_model(img_small, face_small_rectangle);  // 提取人脸特征

        if(showBox){
            cv::Rect box(0,0,0,0);
            box.x = face_small_rectangle.left() * FACE_DOWNSAMPLE_RATIO;
            box.y = face_small_rectangle.top() * FACE_DOWNSAMPLE_RATIO;
            box.width = face_small_rectangle.width() * FACE_DOWNSAMPLE_RATIO;
            box.height = face_small_rectangle.height() * FACE_DOWNSAMPLE_RATIO;

            int top = box.y - box.height / 3 ;
            top = top < 0 ? 0 : top;
            int height = box.height * 4 / 3 ;
            height = height + top > mDisplay.size().height ? mDisplay.size().height - top : height ;
            box.y = top;
            box.height = height;

            cv::rectangle(mDisplay, box, Scalar(255, 0, 0), 2, 8, 0);
        }
        if(showLine) {
            render_face(mDisplay, shape_small); //描线
        }
    }


    finish = clock();
    totaltime_detector = (double)(finish - start_detector) / CLOCKS_PER_SEC;
    LOGI("\nface detector time = %f ms\n", totaltime_detector*1000);

    return found;
}

int loadDB(const char * facesPath){
    //获取绝对路径
    if(facesPath == NULL) {
        return 0;
    }

    try{
        std::vector<matrix<rgb_pixel>> faces; // 人脸 裁剪后的人脸

        DIR *pDir = NULL;
        struct dirent *dmsg;
        char szFileName[128];
        char szFolderName[128];

        strcpy(szFolderName, facesPath);
        strcat(szFolderName, "/%s");
        if ((pDir = opendir(facesPath)) != NULL) {
            LOGI("打开 %s 文件夹",facesPath);
            // 遍历目录
            while ((dmsg = readdir(pDir)) != NULL) {
                if (strcmp(dmsg->d_name, ".") != 0 && strcmp(dmsg->d_name, "..") != 0) {
                    sprintf(szFileName, szFolderName, dmsg->d_name);

                    if (!strcmp(strstr(szFileName, ".") + 1 , "jpg")){
                        matrix<rgb_pixel> img;
//                        char path[260];
                        string fileName = szFileName;
                        LOGI("文件名 %s",szFileName);
                        load_image(img, fileName);

                        for (auto face : detector(img, detectorTimes)) {
                            auto shape = pose_model(img, face); // 一个人的人脸特征
                            matrix<rgb_pixel> face_chip;
                            extract_image_chip(img, get_face_chip_details(shape,150,0.25), face_chip);
                            faces.push_back(move(face_chip));
                        }

                        LOGI("人脸数 %d", faces.size());

                        if (faces.size() > 0){
                            std::vector<matrix<float, 0, 1>> face_descriptors = net_faceRecognition(faces);

                            for(auto item : face_descriptors) {
                                *(strrchr(dmsg->d_name,'.')) = '\0';
                                struct FaceDB faceDB;
                                faceDB.face_descriptors = item;
                                faceDB.label = dmsg->d_name;
                                LOGI("faceDB.label: %s" , &faceDB.label);
                                faceDBs.push_back(faceDB);
                            }

                            if (faceDBs.size() < 1) {
                                LOGI("initFaceDescriptors 获取人脸特征失败");
                            } else {
                                LOGI("initFaceDescriptors 获取到人脸特征数:%d",faceDBs.size());
                            }
                        }
                    }
                }
            }
        }

        if (pDir != NULL) {
            closedir(pDir);
        }
        initflag_faceDB = true;
        return 1;
    } catch (const std::exception &e) {

    } catch (...) {

    }
    return 0;
}

//初始化dlib
JNIEXPORT jint JNICALL Java_com_zzwtec_facedlibopencv_jni_Face_initModel
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

JNIEXPORT jint JNICALL Java_com_zzwtec_facedlibopencv_jni_Face_showBox
        (JNIEnv * env, jclass jobject, jint show) {
    showBox = (int)show == 1 ;
    return 1;
}

JNIEXPORT jint JNICALL Java_com_zzwtec_facedlibopencv_jni_Face_getMaxFace
        (JNIEnv * env, jclass jobject, jint maxFace) {
    maxFace = (int)maxFace == 1 ;
    return 1;
}

JNIEXPORT jint JNICALL Java_com_zzwtec_facedlibopencv_jni_Face_showLandMarks
        (JNIEnv * env, jclass jobject, jint show) {
    showLine = (int)show == 1 ;
    return 1;
}

JNIEXPORT jint JNICALL Java_com_zzwtec_facedlibopencv_jni_Face_setFaceDownsampleRatio
        (JNIEnv * env, jclass jobject, jint faceDownsampleRatio){
    FACE_DOWNSAMPLE_RATIO = faceDownsampleRatio;
    return 1;
}

JNIEXPORT jint JNICALL Java_com_zzwtec_facedlibopencv_jni_Face_setMyThreshold
        (JNIEnv * env, jclass jobject, jfloat threshold){
    myThreshold = threshold;
    return 1;
}

JNIEXPORT jstring JNICALL Java_com_zzwtec_facedlibopencv_jni_Face_landMarks2
        (JNIEnv * env, jclass jobject, jlong intPtr, jlong outPtr){
    string str = "Fail";
    if(!initflag_faceLandmarks68 && !initflag_faceLandmarks5){
        LOGE("没有初始化 68点人脸标记模型 或 5点人脸标记模型");
        const char* ret = str.c_str();
        return env->NewStringUTF(ret);
    }
    showLine = true;
    Mat& inMat = *(Mat*)intPtr;
    Mat& outMat = *(Mat*)outPtr;
    outMat = inMat;

    LOGD("landMarks2");

    long start, finish;
    double totaltime;
    start = clock();

    cv::Rect box(0, 0, 0, 0);
    std::vector<cv::Point2d> pts2d;

    try{
        Mat result ;
        matrix<bgr_pixel> img;
        cvtColor(outMat, result, CV_RGBA2BGR);
        assign_image(img, cv_image<bgr_pixel>(result));

        std::vector<dlib::rectangle> dets = detector(img, detectorTimes);

        int Max = 0;
        int area = 0; // 获取最大面值的人脸
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

            int top = box.y - box.height / 3 ;
            top = top < 0 ? 0 : top;
            int height = box.height * 4 / 3 ;
            height = height + top > outMat.size().height ? outMat.size().height - top : height ;
            box.y = top;
            box.height = height;
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

JNIEXPORT jstring JNICALL Java_com_zzwtec_facedlibopencv_jni_Face_landMarks1
        (JNIEnv *env, jclass jobject, jlong srcAAddr, jint format, jlong displayAddr ) {
    string str = "Fail";
    if(!initflag_faceLandmarks68 && !initflag_faceLandmarks5){
        LOGE("没有初始化 68点人脸标记模型 或 5点人脸标记模型");
        const char* ret = str.c_str();
        return env->NewStringUTF(ret);
    }
    showLine = true;
    Mat& mSrc = *(Mat*) srcAAddr;
    int formatType = (int)format;
    Mat& mDisplay = *(Mat*) displayAddr;

    LOGD("landMarks1");

    long start, finish;
    double totaltime;
    start = clock();
    try{
        // Resize image for face detection
        cv::Mat mSrc_small;
        cv::resize(mSrc, mSrc_small, cv::Size(), 1.0/FACE_DOWNSAMPLE_RATIO, 1.0/FACE_DOWNSAMPLE_RATIO);

        matrix<unsigned char> img, img_small; // greyscale
        if(formatType == 1){ // rgb
            assign_image(img_small, cv_image<rgb_pixel>(mSrc_small));
            assign_image(img, cv_image<rgb_pixel>(mSrc));
        } else if(formatType == 2){ // bgr
            assign_image(img_small, cv_image<bgr_pixel>(mSrc_small));
            assign_image(img, cv_image<bgr_pixel>(mSrc));
        }else if(formatType == 3){ // gray
            assign_image(img_small, cv_image<unsigned char>(mSrc_small));
            assign_image(img, cv_image<unsigned char>(mSrc));
        }
        detector_face( img, img_small, mDisplay);
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

JNIEXPORT jstring JNICALL Java_com_zzwtec_facedlibopencv_jni_Face_landMarks
        (JNIEnv *env, jclass jobject, jlong rgbaAAddr, jlong grayAddr, jlong bgrAddr, jlong rgbAddr, jlong displayAddr ) {
    string str = "Fail";
    if(!initflag_faceLandmarks68 && !initflag_faceLandmarks5){
        LOGE("没有初始化 68点人脸标记模型 或 5点人脸标记模型");
        const char* ret = str.c_str();
        return env->NewStringUTF(ret);
    }
    showLine = true;
//    Mat& mRgba = *(Mat*) rgbaAAddr;
//    Mat& mGray = *(Mat*) grayAddr;
//    Mat& mBgr = *(Mat*) bgrAddr;
    Mat& mRgb = *(Mat*) rgbAddr;
    Mat& mDisplay = *(Mat*) displayAddr;

    LOGD("landMarks");

    long start, finish;
    double totaltime;
    start = clock();
    try{
        matrix<rgb_pixel> img;
        assign_image(img, cv_image<rgb_pixel>(mRgb));

        std::vector<dlib::rectangle> faces = detector(img, detectorTimes); //检测人脸，获得边界框

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

                int top = box.y - box.height / 3 ;
                top = top < 0 ? 0 : top;
                int height = box.height * 4 / 3 ;
                height = height + top > mDisplay.size().height ? mDisplay.size().height - top : height ;
                box.y = top;
                box.height = height;

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

JNIEXPORT jint JNICALL Java_com_zzwtec_facedlibopencv_jni_Face_faceDetector
        (JNIEnv *env, jclass jobject, jlong srcAAddr, jint format, jlong displayAddr ) {
    Mat& mSrc = *(Mat*) srcAAddr;
    int formatType = (int)format;
    Mat& mDisplay = *(Mat*) displayAddr;

    LOGD("landMarks");
    showBox = true;
    showLine = false;

    long start, finish;
    double totaltime;
    start = clock();
    try{
        // Resize image for face detection
        cv::Mat mSrc_small;
        cv::resize(mSrc, mSrc_small, cv::Size(), 1.0/FACE_DOWNSAMPLE_RATIO, 1.0/FACE_DOWNSAMPLE_RATIO);

        matrix<unsigned char> img, img_small; // greyscale
        if(formatType == 1){ // rgb
            assign_image(img_small, cv_image<rgb_pixel>(mSrc_small));
            assign_image(img, cv_image<rgb_pixel>(mSrc));
        } else if(formatType == 2){ // bgr
            assign_image(img_small, cv_image<bgr_pixel>(mSrc_small));
            assign_image(img, cv_image<bgr_pixel>(mSrc));
        }else if(formatType == 3){ // gray
            assign_image(img_small, cv_image<unsigned char>(mSrc_small));
            assign_image(img, cv_image<unsigned char>(mSrc));
        }
        detector_face( img, img_small, mDisplay);
    } catch (const std::exception &e) {

    } catch (...) {

    }

    finish = clock();
    totaltime = (double)(finish - start) / CLOCKS_PER_SEC;
    LOGD("detector face time = %f ms\n", totaltime*1000);

    return 1;
}

JNIEXPORT jint JNICALL Java_com_zzwtec_facedlibopencv_jni_Face_faceDetectorByDNN
        (JNIEnv *env, jclass jobject, jlong srcAAddr, jint format, jlong displayAddr ) {
    Mat& mSrc = *(Mat*) srcAAddr;
    int formatType = (int)format;
    Mat& mDisplay = *(Mat*) displayAddr;

    LOGD("faceDetectorByDNN");
    showBox = true;
    showLine = false;

    long start, finish;
    double totaltime;
    start = clock();
    try{
        // Resize image for face detection
        int zoom = 3;
        cv::Mat mSrc_small;
        cv::resize(mSrc, mSrc_small, cv::Size(), 1.0/FACE_DOWNSAMPLE_RATIO/zoom, 1.0/FACE_DOWNSAMPLE_RATIO/zoom);

        matrix<rgb_pixel> img, img_small; // greyscale
        if(formatType == 1){ // rgb
            assign_image(img_small, cv_image<rgb_pixel>(mSrc_small));
            assign_image(img, cv_image<rgb_pixel>(mSrc));
        } else if(formatType == 2){ // bgr
            assign_image(img_small, cv_image<bgr_pixel>(mSrc_small));
            assign_image(img, cv_image<bgr_pixel>(mSrc));
        }else if(formatType == 3){ // gray
            assign_image(img_small, cv_image<unsigned char>(mSrc_small));
            assign_image(img, cv_image<unsigned char>(mSrc));
        }

        auto dets = net_humanFace(img_small);
        int size = 0;
        for (auto&& d : dets){
            cv::Rect box(0,0,0,0);
            box.x = d.rect.left() * FACE_DOWNSAMPLE_RATIO*zoom;
            box.y = d.rect.top() * FACE_DOWNSAMPLE_RATIO*zoom;
            box.width = d.rect.width() * FACE_DOWNSAMPLE_RATIO*zoom;
            box.height = d.rect.height() * FACE_DOWNSAMPLE_RATIO*zoom;

            int top = box.y - box.height / 3 ;
            top = top < 0 ? 0 : top;
            int height = box.height * 4 / 3 ;
            height = height + top > mDisplay.size().height ? mDisplay.size().height - top : height ;
            box.y = top;
            box.height = height;

            cv::rectangle(mDisplay, box, Scalar(255, 0, 0), 2, 8, 0);
            size++;
        }
        LOGI("faces size:%d",size);

    } catch (const std::exception &e) {

    } catch (...) {

    }

    finish = clock();
    totaltime = (double)(finish - start) / CLOCKS_PER_SEC;
    LOGD("detector face time = %f ms\n", totaltime*1000);

    return 1;
}

JNIEXPORT jint JNICALL Java_com_zzwtec_facedlibopencv_jni_Face_faceRecognitionForPicture
        (JNIEnv *, jclass, jlong srcAAddr, jlong displayAddr){
    if(!initflag_faceLandmarks68 && !initflag_faceLandmarks5){
        LOGE("没有初始化 68点人脸标记模型 或 5点人脸标记模型");
        return 0;
    }
    if(!initflag_faceRecognitionV1){
        LOGE("没有初始化 人脸识别模型");
        return 0;
    }
    showBox=true;
    Mat& mSrc = *(Mat*) srcAAddr;
    Mat& mDisplay = *(Mat*) displayAddr;
    mDisplay = mSrc;

    LOGD("faceRecognitionForPicture");

    int found=0;
    try{
        Mat result ;
        matrix<rgb_pixel> img;
        cvtColor(mSrc, result, CV_RGBA2RGB);
        assign_image(img, cv_image<rgb_pixel>(result));

        long start_detector=0, start_recognition=0, finish=0;
        double totaltime_detector=0.0,totaltime_recognition=0.0;

        start_detector = clock();
        std::vector<matrix<rgb_pixel>> faces; // 人脸 裁剪后的人脸
//        std::vector<full_object_detection> shapes; // 人脸特征
        std::vector<cv::Rect> boxes; // 人脸位置框
        std::vector<dlib::rectangle> detector_faces = detector(img, detectorTimes);
        for (auto face : detector_faces) { // 人脸检测
//            if(face.width() * face.height() < 150 * 150){
//                continue;
//            }
            auto shape = pose_model(img, face); // 提取人脸特征
            matrix<rgb_pixel> face_chip;

            long _time = clock();
            chip_details chipDetails = get_face_chip_details(shape,150,0.25);
            LOGI("\nget_face_chip_details time = %f ms\n", ((double)(clock() - _time) / CLOCKS_PER_SEC)*1000);

            _time = clock();
            extract_image_chip(img, chipDetails, face_chip); // 提取裁剪 直立旋转和缩放至标准尺寸的每张脸的副本
            LOGI("\nextract_image_chip time = %f ms\n", ((double)(clock() - _time) / CLOCKS_PER_SEC)*1000);

            faces.push_back(move(face_chip));

            if(showBox) {
                cv::Rect box(0,0,0,0);

                box.x = face.left();
                box.y = face.top();
                box.width = face.width();
                box.height = face.height();

                int top = box.y - box.height / 3 ;
                top = top < 0 ? 0 : top;
                int height = box.height * 4 / 3 ;
                height = height + top > mDisplay.size().height ? mDisplay.size().height - top : height ;
                box.y = top;
                box.height = height;

                boxes.push_back(box);
                cv::rectangle(mDisplay, box, Scalar(255, 0, 0), 2, 8, 0);
            }
        }
        finish = clock();
        totaltime_detector = (double)(finish - start_detector) / CLOCKS_PER_SEC;
        LOGI("\nface detector time = %f ms\n", totaltime_detector*1000);
        if (faces.size() > 0){
            start_recognition = clock();
            std::vector<matrix<float, 0, 1>> face_descriptors = net_faceRecognition(faces);
            long _start = clock();
            LOGI("人脸特征耗时 %f ms\n", (double)(_start - start_recognition) / CLOCKS_PER_SEC * 1000);

            if (face_descriptors.size() < 1) {
                LOGI("获取人脸特征失败 \n");
            } else {
                LOGI("获取到人脸特征数:%d \n",face_descriptors.size());
            }

            for (size_t i = 0; i < face_descriptors.size(); ++i) {
                for (size_t j = i+1; j < face_descriptors.size(); ++j) {
                    _start = clock();
                    auto thisThreshold = length(face_descriptors[i]-face_descriptors[j]); // 比对结果是一个距离值
                    LOGI("--------------- 比对值:%f  比对耗时 %f ms\n",thisThreshold, (double)(clock() - _start) / CLOCKS_PER_SEC * 1000);
                    cv::putText(mDisplay, "threshold:"+to_string(thisThreshold), cv::Point(boxes[i].x,boxes[i].y-3), cv::FONT_HERSHEY_SIMPLEX, 1, Scalar(181, 127, 255), 2, cv::LINE_AA);
                    if (thisThreshold < myThreshold){
                        found=1;
                        break;
                    }
                }
            }
            finish = clock();
            totaltime_recognition = (double)(finish - start_recognition) / CLOCKS_PER_SEC;
            LOGI("face recognition time = %f ms\n", totaltime_recognition*1000);
        }

        LOGI("all time = %f ms\n", (totaltime_detector + totaltime_recognition)*1000);

    } catch (const std::exception &e) {

    } catch (...) {

    }

    return found;
}

JNIEXPORT jint JNICALL Java_com_zzwtec_facedlibopencv_jni_Face_faceRecognition
        (JNIEnv *env, jclass jobject, jlong srcAAddr, jint format, jlong displayAddr ,jstring path){
    if(!initflag_faceLandmarks68 && !initflag_faceLandmarks5){
        LOGE("没有初始化 68点人脸标记模型 或 5点人脸标记模型");
        return 0;
    }
    if(!initflag_faceRecognitionV1){
        LOGE("没有初始化 人脸识别模型");
        return 0;
    }
    if(!initflag_faceDB){
        LOGE("没有初始化 人脸库");
        return 0;
    }
    if(faceDBs.size() == 0){
        //获取绝对路径
        const char * facesPath = env->GetStringUTFChars(path, 0);
        if(facesPath == NULL) {
            return 0;
        }
        int r = loadDB(facesPath);
        env->ReleaseStringUTFChars(path, facesPath);
        return r;
    }
    showBox=true;
    Mat& mSrc = *(Mat*) srcAAddr;
    int formatType = (int)format;
    Mat& mDisplay = *(Mat*) displayAddr;

    LOGD("faceRecognition");

    int found=0;
    try{
        // Resize image for face detection
        cv::Mat mSrc_small;
        cv::resize(mSrc, mSrc_small, cv::Size(), 1.0/FACE_DOWNSAMPLE_RATIO, 1.0/FACE_DOWNSAMPLE_RATIO);

        matrix<rgb_pixel> img, img_small; // greyscale
        if(formatType == 1){ // rgb
            assign_image(img_small, cv_image<rgb_pixel>(mSrc_small));
            assign_image(img, cv_image<rgb_pixel>(mSrc));
        } else if(formatType == 2){ // bgr
            assign_image(img_small, cv_image<bgr_pixel>(mSrc_small));
            assign_image(img, cv_image<bgr_pixel>(mSrc));
        }else if(formatType == 3){ // gray
            assign_image(img_small, cv_image<unsigned char>(mSrc_small));
            assign_image(img, cv_image<unsigned char>(mSrc));
        }

        long start=0,start_detector=0, start_recognition=0, finish=0;
        double totaltime=0.0,totaltime_recognition=0.0;

        start = clock();

        std::vector<matrix<rgb_pixel>> faces; // 人脸 裁剪后的人脸
//        std::vector<full_object_detection> shapes; // 人脸特征
        std::vector<cv::Rect> boxes; // 人脸位置框

        start_detector = clock();

        std::vector<dlib::rectangle> detector_faces = detector(img_small, detectorTimes);

        int Max = 0;
        if(maxFace){
            int area = 0; // 获取最大面值的人脸
            if (detector_faces.size() != 0) {
                for (unsigned long t = 0; t < detector_faces.size(); ++t) {
                    if (area < detector_faces[t].width()*detector_faces[t].height()) {
                        area = detector_faces[t].width()*detector_faces[t].height();
                        Max = t;
                    }
                }
            }
            dlib::rectangle max_face_small = detector_faces[Max];
            detector_faces.clear();
            detector_faces.push_back(max_face_small);
        }
        for (auto face_small : detector_faces) { // 人脸检测
//            if(face_small.width() * face_small.height() * FACE_DOWNSAMPLE_RATIO * FACE_DOWNSAMPLE_RATIO < 150 * 150){
//                continue;
//            }
            auto shape_small = pose_model(img_small, face_small); // 提取人脸特征
            matrix<rgb_pixel> face_chip;
            extract_image_chip(img_small, get_face_chip_details(shape_small,150,0.25), face_chip); // 提取裁剪 直立旋转和缩放至标准尺寸的每张脸的副本
            faces.push_back(move(face_chip));

            if(showBox) {
                cv::Rect box(0,0,0,0);
                box.x = face_small.left() * FACE_DOWNSAMPLE_RATIO;
                box.y = face_small.top() * FACE_DOWNSAMPLE_RATIO;
                box.width = face_small.width() * FACE_DOWNSAMPLE_RATIO;
                box.height = face_small.height() * FACE_DOWNSAMPLE_RATIO;

                int top = box.y - box.height / 3 ;
                top = top < 0 ? 0 : top;
                int height = box.height * 4 / 3 ;
                height = height + top > mDisplay.size().height ? mDisplay.size().height - top : height ;
                box.y = top;
                box.height = height;

                boxes.push_back(box);
                cv::rectangle(mDisplay, box, Scalar(255, 0, 0), 2, 8, 0);
            }
        }

        finish = clock();
        LOGI("\nface detector time = %f ms\n", ((double)(finish - start_detector) / CLOCKS_PER_SEC)*1000);

        LOGI("faces.size: %d  faceDBs.size: %d\n", faces.size(), faceDBs.size() );

        if (faces.size() > 0 && faceDBs.size() > 0){
            start_recognition = clock();
            std::vector<matrix<float, 0, 1>> face_descriptors = net_faceRecognition(faces);
            long _start = clock();
            LOGI("人脸特征耗时 %f ms\n", (double)(_start - start_recognition) / CLOCKS_PER_SEC * 1000);

            if (face_descriptors.size() < 1) {
                LOGI("获取人脸特征失败 \n");
            } else {
                LOGI("获取到人脸特征数:%d \n",face_descriptors.size());
            }

            LOGI("--------------- 库的人脸特征数:%d \n",faceDBs.size());

            size_t i = 0, j = 0;
            for (; i < face_descriptors.size(); ++i) {
                for (; j < faceDBs.size(); ++j) {
                    _start = clock();
                    auto thisThreshold = length(face_descriptors[i]-faceDBs[j].face_descriptors); // 比对结果是一个距离值
                    LOGI("--------------- 比对值:%f  比对耗时 %f ms\n",thisThreshold, (double)(clock() - _start) / CLOCKS_PER_SEC * 1000);
                    if (thisThreshold < myThreshold){
                        found=1;
                        cv::putText(mDisplay, faceDBs[j].label + " - " + to_string(thisThreshold), cv::Point(boxes[i].x,boxes[i].y-3), cv::FONT_HERSHEY_SIMPLEX, 1, Scalar(181, 127, 255), 2, cv::LINE_AA);
                        break;
                    }else{
                        cv::putText(mDisplay, to_string(thisThreshold), cv::Point(boxes[i].x,boxes[i].y-3), cv::FONT_HERSHEY_SIMPLEX, 1, Scalar(181, 127, 255), 2, cv::LINE_AA);
                    }
                }
            }
            finish = clock();
            totaltime_recognition = (double)(finish - start_recognition) / CLOCKS_PER_SEC;
            LOGI("face recognition time = %f ms\n", totaltime_recognition*1000);
        }
        finish = clock();
        totaltime = (double)(finish - start) / CLOCKS_PER_SEC;
        LOGD("recognition face all time = %f ms\n", totaltime*1000);

    } catch (const std::exception &e) {

    } catch (...) {

    }

    return found;
}

JNIEXPORT jint JNICALL Java_com_zzwtec_facedlibopencv_jni_Face_initFaceDescriptors
        (JNIEnv *env, jclass jobject, jstring path){
    if(!initflag_faceLandmarks68 && !initflag_faceLandmarks5){
        LOGE("没有初始化 68点人脸标记模型 或 5点人脸标记模型");
        return 0;
    }
    if(!initflag_faceRecognitionV1){
        LOGE("没有初始化 人脸识别模型");
        return 0;
    }
    //获取绝对路径
    const char * facesPath = env->GetStringUTFChars(path, 0);
    if(facesPath == NULL) {
        return 0;
    }

    int r = loadDB(facesPath);
    env->ReleaseStringUTFChars(path, facesPath);
    return r;
}