# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.
# 需求的Cmake最小版本号
cmake_minimum_required(VERSION 3.4.1)

# 设置变量
set(CMAKE_VERBOSE_MAKEFILE on) # opencv
set(jniLibs "${CMAKE_SOURCE_DIR}/src/main/jniLibs") # opencv

# 包含别的CmakeLists.txt
include_directories("${CMAKE_SOURCE_DIR}/src/main/cpp/include") # opencv

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library(libopencv_java3 SHARED IMPORTED ) # 编译成library，参数是库的名字、库的类型、源码s(自己写的JNI文件，可以有多个)
set_target_properties(libopencv_java3 PROPERTIES IMPORTED_LOCATION "${jniLibs}/${ANDROID_ABI}/libopencv_java3.so") # 设置输出库文件的位置

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=gnu++11 -fexceptions -frtti") # opencv

include("${CMAKE_SOURCE_DIR}/src/main/cpp/dlib/cmake") # dlib

add_library(native-lib SHARED src/main/cpp/native-lib.cpp )
#set_target_properties(native-lib PROPERTIES LIBRARY_OUTPUT_DIRECTORY ${jniLibs}/${ANDROID_ABI})

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

# 库需要链接（依赖）的其他的库，一起打包成第一个参数的名字下面都是链接的库
target_link_libraries( # Specifies the target library.
                       native-lib
                       android log

                       # opencv
                       libopencv_java3  #used for java sdk

                       # Dlib
                       dlib::dlib

                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib} )

set(CMAKE_BUILD_TYPE "RELEASE")
set(CMAKE_CXX_FLAGS_RELEASE "$ENV{CXXFLAGS} -O3 -Wall")
SET(CMAKE_BUILE_TYPE "DEBUG")
SET(CMAKE_CXX_FLAGS_DEBUG "-g -Wall")