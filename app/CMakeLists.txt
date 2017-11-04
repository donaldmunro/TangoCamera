# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)
project(itango CXX)
set(CMAKE_CXX_STANDARD 11)

set(CMAKE_ANDROID_NDK "/opt/android-sdk/ndk-bundle")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( itango SHARED c++/itango.cc c++/OGLShaderUtils.h c++/OGLShaderUtils.cc
                           c++/Renderer.h c++/Renderer.cc )

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

set(LOCAL_NDK_LIBS "${CMAKE_SOURCE_DIR}/c++/lib/${ANDROID_ABI}")
#set(LOCAL_NDK_LIBS "${CMAKE_SOURCE_DIR}/libs/${ANDROID_ABI}")

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

# See for eg .externalNativeBuild/cmake/debug/arm64-v8a/cmake_build_output.txt or
# whatever your ABI is
message(STATUS "CMAKE_SOURCE_DIR: " ${CMAKE_SOURCE_DIR})
message(STATUS "Tango: " "${LOCAL_NDK_LIBS}/libtango_client_api.so")
message(STATUS "log-lib: " ${log-lib})

target_include_directories(itango PUBLIC "c++/include")

target_link_libraries( itango ${log-lib} GLESv3 android EGL
                       "${LOCAL_NDK_LIBS}/libtango_client_api.so"
                       "${LOCAL_NDK_LIBS}/libtango_support_api.so"
                       )
