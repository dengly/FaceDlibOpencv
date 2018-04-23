package com.zzwtec.facedlibopencv;

public class Face {
    //初始化模型
    public native static int initModel(String path);

    //设置是否显示人脸框
    public native static int showBox(int show);

    //设置是否显示人脸特征线
    public native static int showLandMarks(int show);

    //人脸检测
    public native static String landMarks(long rgbaAAddr, long grayAddr, long bgrAddr, long rgbAddr, long displayAddr);


    /**
     * 人脸检测
     * @param srcAAddr 源
     * @param format 源类型 1 rgb、2 bgr、3 gray
     * @param displayAddr 要显示
     * @return
     */
    public native static String landMarks1(long srcAAddr, int format, long displayAddr);

    //人脸检测
    public native static String landMarks2(long input,long output);
}
