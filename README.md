# FaceDlibOpencv
本项目基于 [OpenCV](https://opencv.org) 和 [Dlib](http://dlib.net) 的图片、视频人脸检测和人脸识别

## 版本
* [OpenCV 3.4.1](https://opencv.org/opencv-3-4-1.html) 的 Android SDK
* [Dlib 19.10](http://dlib.net/files/dlib-19.10.tar.bz2)

## 说明
* 算法模型存放在SD卡的```model```文件夹下，可以通过修改```com.zzwtec.facedlibopencv.Constants```
* 人脸库存放在SD卡的```faces```文件夹下，可以通过修改```com.zzwtec.facedlibopencv.Constants```
* 目前 OpenCV 只是用于摄像头的管理和显示，人脸检测、特征标记、识别都是使用 Dlib
* 经测试 Dlib 的人脸特征提取耗时比较大，如在锤子的[坚果手机 U1](https://www.smartisan.com/jianguo/#/specs)上，原图是1280x960，经压缩处理是320x240,一次人脸检测耗时是280毫秒左右，一次一个人脸特征提取耗时是6800毫秒左右，一次人脸特征比对耗时是0.03毫秒左右