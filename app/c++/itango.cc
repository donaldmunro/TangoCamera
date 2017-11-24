/*
Copyright (c) 2017 Donald Munro

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

#include <string>
#include <cmath>
#include <vector>
#include <memory>
#include <atomic>
#include <chrono>
#include <thread>

#include <string.h>
#include <sys/time.h>
#include <jni.h>
#include <tango_client_api.h>

#include "tango_client_api.h"
#include "tango_support_api.h"

#define ANDROID_LOG_TAG "itango"
#include "android-logging.h"
#include "Renderer.h" // Renderer.h should use this ANDROID_LOG_TAG if included after android-logging.h

static std::atomic_bool connected{false}, taking_photo{false},  has_image{false}, has_pointcloud{false};
static volatile bool depth_enabled = false;
static bool is_depth = false;
static uint64_t start_time = 0;
static JavaVM* vm = nullptr;
static jobject activity = nullptr;
static jmethodID java_request_render = nullptr, java_on_photo = nullptr, java_on_pointcloud = nullptr,
                 java_post_process = nullptr, java_toast = nullptr, java_error = nullptr;

static std::unique_ptr<Renderer> renderer;
static double pointcloud_timestamp = -1;
static int pose_retries = 0, pointcloud_retries = 0;

const int kTangoCoreMinimumVersion = 9377;
const int TOAST_LENGTH_SHORT = -1;
const int TOAST_LENGTH_LONG = 0;
const int TOAST_LENGTH_INDEFINITE = -2;
const float MIN_CONFIDENCE = 0.3;
const float MAX_RANGE = 4;
const int MAX_POSE_RETRIES = 5, MAX_POINTCLOUD_RETRIES = 10;

static inline uint64_t uptime()
//-----------------------------
{
   struct timespec t;
   t.tv_sec = t.tv_nsec = 0;
   clock_gettime(CLOCK_BOOTTIME, &t);
   return (uint64_t)(t.tv_sec)*1000000000LL + t.tv_nsec;
}

extern "C"
jint JNI_OnLoad(JavaVM* vm_, void*) { vm = vm_; return JNI_VERSION_1_6; }

extern "C"
JNIEXPORT jboolean JNICALL
Java_to_ar_tango_tangocamera_ITango_create(JNIEnv *env, jclass klass, jobject activity_)
//------------------------------------------------------------------------------------
{
   if (connected.exchange(false))
      TangoService_disconnect();
   int version = 0;
   TangoErrorType err = TangoSupport_GetTangoVersion(env, activity_, &version);
   if (err != TANGO_SUCCESS || version < kTangoCoreMinimumVersion)
   {
      ALOGE("itango::create: Tango Core version is out of date.");
      return JNI_FALSE;
   }
   if (vm == nullptr)
   {
      ALOGE("itango::create: Java VM not set.");
      return JNI_FALSE;
   }
   activity = env->NewGlobalRef(activity_);
   if (activity == nullptr)
   {
      ALOGE("itango::create: Could not obtain activity ref");
      return JNI_FALSE;
   }
   jclass cls = env->GetObjectClass(activity_);
   java_request_render = env->GetMethodID(cls, "requestRender", "()V");
   if (java_request_render == nullptr)
   {
      ALOGE("itango::create: Could not obtain requestRender method ref");
      return JNI_FALSE;
   }
   java_on_photo = env->GetMethodID(cls, "onPhoto", "(I[BIIIDDDDDDDD)V");
   if (java_on_photo == nullptr)
   {
      ALOGE("itango::create: Could not obtain onPhoto method ref");
      return JNI_FALSE;
   }
   java_post_process = env->GetMethodID(cls, "postProcess", "()V");
   if (java_post_process == nullptr)
   {
      ALOGE("itango::create: Could not obtain postProcess method ref");
      return JNI_FALSE;
   }
   java_error = env->GetMethodID(cls, "onTakePhotoError", "(Ljava/lang/String;)V");

   java_on_pointcloud = env->GetMethodID(cls, "onPointCloud", "([FD)V");
   if (java_on_pointcloud == nullptr)
   {
      ALOGE("itango::create: Could not obtain onPointCloud method ref");
      return JNI_FALSE;
   }
   java_toast = env->GetMethodID(cls, "asyncNotification", "(Ljava/lang/String;IZ)V");
   if (java_toast == nullptr)
      ALOGW("itango::create: Could not obtain asyncNotification method ref");
   return JNI_TRUE;
}

extern "C"
JNIEXPORT void JNICALL
Java_to_ar_tango_tangocamera_ITango_destroy(JNIEnv *env, jclass type)
//-------------------------------------------------------------------
{
  if (connected.exchange(false))
     TangoService_disconnect();
  env->DeleteGlobalRef(activity);
  activity = nullptr;
  java_request_render = java_on_photo = java_post_process = nullptr;
}

static void on_pointcloud(void* context, const TangoPointCloud* point_cloud)
//--------------------------------------------------------------------------
{
   if ( (depth_enabled) && (is_depth) && (taking_photo) && (! has_pointcloud) )
   {
      JNIEnv* env;
      vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
      int no = point_cloud->num_points;
      if (no == 0)
      {
         if (pointcloud_retries++ < MAX_POINTCLOUD_RETRIES)
            return;
      }

      float *ptr = (float *) point_cloud->points;
      int good = 0;
      std::vector<float> good_data;
      for (int i=0; i<no; i++)
      {
         float x = *ptr++;
         float y = *ptr++;
         float z = *ptr++;
         float confidence = *ptr++;
         if ( ( confidence >= MIN_CONFIDENCE) && (z <= MAX_RANGE) )
         {
            good++;
            good_data.push_back(x);
            good_data.push_back(y);
            good_data.push_back(z);
            good_data.push_back(confidence);
         }
      }
      if ( (no > 0) && (static_cast<double>(good)/static_cast<double>(no) <= 0.5) )
      {
         ALOGW("Not enough good pointcloud points (%d/%d)", good, no);
         if (pointcloud_retries++ < MAX_POINTCLOUD_RETRIES)
            return;
      }

      ALOGD("Copying pointcloud to Java array");
      pointcloud_timestamp = point_cloud->timestamp;
      no = static_cast<int>(good_data.size());
      jfloatArray data = env->NewFloatArray(no);
      if (data == nullptr)
         ALOGE("itango::on_pointcloud: Memory allocation error allocating Java image data buffer of size sizeof(jfloat)*4*%d", no);
      else
      {
         env->SetFloatArrayRegion(data, 0, static_cast<jsize>(good_data.size()), (const jfloat *)&good_data[0]);
         env->CallVoidMethod(activity, java_on_pointcloud, data, pointcloud_timestamp);
         env->DeleteLocalRef(data);
      }
      has_pointcloud = true;
      ALOGD("Got pointcloud %ld %f", start_time, pointcloud_timestamp);
   }
}

void on_image(void *context, TangoCameraId cameraid, const TangoImageBuffer *buffer)
//------------------------------------------------------------------------------------------
{
   if ( (taking_photo) && (! has_image) )
   {
      JNIEnv* env;
      vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);

      int image_format = 0x11; // NV21 from android.graphics.ImageFormat.NV21
      int len;
      switch (buffer->format)
      {
         case TANGO_HAL_PIXEL_FORMAT_YV12:
            image_format = 0x23; // android.graphics.ImageFormat.YUV_420_888;
            len = (buffer->width * buffer->height * 12) / 8;
            break;
         case TANGO_HAL_PIXEL_FORMAT_YCrCb_420_SP:
            image_format = 0x11; // NV21 from android.graphics.ImageFormat.NV21;
            len = (buffer->width * buffer->height * 12) / 8; //NV21 or YUV_420_888
            break;
         case TANGO_HAL_PIXEL_FORMAT_RGBA_8888:
            image_format = 0x2A; // from android.graphics.ImageFormat.FLEX_RGBA_8888 but not really
                                 // as FLEX_RGBA_8888 has R, G, etc in different buffers as opposed
                                 // to successive bytes.
            len = buffer->width*buffer->height*4;
            break;
      }

      jbyteArray data = env->NewByteArray(len);
      if (data == NULL)
      {
         ALOGE("itango::on_image: Memory allocation error allocating Java image data buffer");
         return;
      }
      env->SetByteArrayRegion(data, 0, len, (jbyte *)buffer->data);

      if ( (depth_enabled) && (is_depth) && (! has_pointcloud) )
      {
         if (java_toast != nullptr)
         {
            const char* str = "Hold Camera Steady. Gathering point cloud";
            jstring jstr = env->NewStringUTF(str);
            env->CallVoidMethod(activity, java_toast, jstr, TOAST_LENGTH_LONG, JNI_FALSE);
            env->DeleteLocalRef(jstr);
         }
      }

      TangoCoordinateFramePair frames_of_reference;
      frames_of_reference.base = TANGO_COORDINATE_FRAME_START_OF_SERVICE;
      frames_of_reference.target = TANGO_COORDINATE_FRAME_DEVICE;
      TangoPoseData pose;
      TangoErrorType ret = TangoService_getPoseAtTime(buffer->timestamp, frames_of_reference, &pose);
      if (ret != TANGO_SUCCESS)
      {
         if (pose_retries < MAX_POSE_RETRIES)
         {
            ALOGW("itango::on_image: Could not get pose at timestamp %.5f (%d). Retrying", buffer->timestamp, ret);
            return;
         }
         ret = TangoService_getPoseAtTime(0, frames_of_reference, &pose);
         if (ret != TANGO_SUCCESS)
         {
            ALOGE("itango::on_image: Could not get pose at timestamp 0.0 (%d)", ret);
            pose.orientation[0] = pose.orientation[1] = pose.orientation[2] = pose.orientation[3] =
            pose.translation[0] = pose.translation[1] = pose.translation[2] = 0;
         }
      }
      ALOGD("Calling onPhoto");
      env->CallVoidMethod(activity, java_on_photo, image_format, data, buffer->width, buffer->height,
                          buffer->stride, pose.orientation[3], pose.orientation[0], pose.orientation[1],
                          pose.orientation[2], pose.translation[0], pose.translation[1], pose.translation[2],
                          buffer->timestamp);
      has_image = true;
      ALOGD("Got image %ld %f %f", start_time, buffer->timestamp, pose.timestamp);
   }
}

void on_frame(void* context, TangoCameraId id)
//--------------------------------------------
{
   if ( (id == TANGO_CAMERA_COLOR) && (vm != nullptr) && (activity != nullptr) && (java_request_render != nullptr) )
   {
       JNIEnv* env;
       vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
       env->CallVoidMethod(activity, java_request_render);
   }
   if (taking_photo)
   {
      if ( (has_image) && (has_pointcloud) )
      {
         taking_photo = false;
         depth_enabled = false;
         ALOGD("Calling postProcess %ld", start_time);
         JNIEnv* env;
         vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
         env->CallVoidMethod(activity, java_post_process);
         pose_retries = pointcloud_retries = 0;
         has_image = false;
         has_pointcloud = false;
      }
      else if ((uptime() - start_time) > 1000000000LL)
      {
         taking_photo = false;
         ALOGD("Calling onTakePhotoError");
         if (java_error != nullptr)
         {
            JNIEnv* env;
            vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
            std::stringstream errs;
            errs << "Timed out waiting on";
            if (! has_image)
               errs << " Image";
            if ( (depth_enabled) && (is_depth) && (! has_pointcloud) )
               errs << " Point Cloud";
            depth_enabled = false;
            jstring message = env->NewStringUTF(errs.str().c_str());
            env->CallVoidMethod(activity, java_error, message);
            env->DeleteLocalRef(message);
         }
         pose_retries = pointcloud_retries = 0;
         has_image = false;
         has_pointcloud = false;
      }
   }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_to_ar_tango_tangocamera_ITango_startTakingPhoto(JNIEnv *env, jclass type, jboolean isPointCloud)
//---------------------------------------------------------------------------------------------------
{
   if ( (taking_photo) || (! connected) )
      return (jboolean) JNI_FALSE;
   if (is_depth)
      depth_enabled = isPointCloud;
   else
      depth_enabled = false;
   has_pointcloud = (! isPointCloud);
   start_time = uptime();
   taking_photo = true;
   return (jboolean) JNI_TRUE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_to_ar_tango_tangocamera_ITango_serviceConnected(JNIEnv *env, jclass klass, jobject binder)
//---------------------------------------------------------------------------------------------
{
   try
   {
      if (TangoService_setBinder((void *) env, (void *) binder) != TANGO_SUCCESS)
      {
         ALOGE("HelloVideoApp::OnTangoServiceConnected, TangoService_setBinder error");
         return JNI_FALSE;
      }

      TangoConfig tango_config = TangoService_getConfig(TANGO_CONFIG_DEFAULT);
      if (tango_config == nullptr)
      {
         ALOGE("serviceConnected. Failed to get default config form");
         return JNI_FALSE;
      }

      TangoErrorType ret = TangoConfig_setBool(tango_config, "config_enable_color_camera", true);
      if (ret  != TANGO_SUCCESS)
      {
         ALOGE("serviceConnected: config_enable_color_camera() failed (%d)", ret);
         return JNI_FALSE;
      }
      ret = TangoConfig_setBool(tango_config, "config_enable_auto_recovery", true);
      if (ret != TANGO_SUCCESS)
         ALOGE("serviceConnected: Failed to set config_enable_auto_recovery (%d). Continuing", ret);
      ret = TangoConfig_setBool(tango_config, "config_enable_drift_correction", true);
      if (ret != TANGO_SUCCESS)
         ALOGE("serviceConnected: Failed to set config_enable_drift_correction (%d). Continuing", ret);
      int temp = 0;
      is_depth = ( (TangoConfig_setBool(tango_config, "config_enable_depth", true) == TANGO_SUCCESS) &&
                   (TangoConfig_setInt32(tango_config, "config_depth_mode", TANGO_POINTCLOUD_XYZC)  == TANGO_SUCCESS) &&
                   (TangoConfig_getInt32(tango_config, "max_point_cloud_elements", &temp) == TANGO_SUCCESS)
                 );
      if (is_depth)
      {
         uint32_t max_vertex_count = static_cast<uint32_t>(temp);
         ret = TangoService_connectOnPointCloudAvailable(on_pointcloud);
         if (ret != TANGO_SUCCESS)
         {
            is_depth = false;
            ALOGE("serviceConnected: Failed to set point cloud callback (%d). Point clouds will not be available", ret);
         }
      }
      else
         ALOGE("serviceConnected: Failed to set point cloud mode. Point clouds will not be available");

      ret = TangoService_connectOnFrameAvailable(TANGO_CAMERA_COLOR, nullptr, on_image);
      if (ret != TANGO_SUCCESS)
      {
         ALOGE("serviceConnected: Error connecting frame callback (%d)", ret);
         return JNI_FALSE;
      }
      ret = TangoService_connectOnTextureAvailable(TANGO_CAMERA_COLOR, nullptr, on_frame);
      if (ret != TANGO_SUCCESS)
      {
         ALOGE("serviceConnected: Failed to connect texture callback (%d)", ret);
         return JNI_FALSE;
      }

      ret = TangoService_connect(nullptr, tango_config);
      if (ret != TANGO_SUCCESS)
      {
         ALOGE("serviceConnected: Failed to connect to the Tango service (%d)", ret);
         TangoService_disconnectCamera(TANGO_CAMERA_COLOR);
         return JNI_FALSE;
      }

      // Initialize TangoSupport context.
      TangoSupport_initialize(TangoService_getPoseAtTime, TangoService_getCameraIntrinsics);

      connected = true;
      return JNI_TRUE;
   }
   catch (...)
   {
      ALOGE("Exception in Java_to_ar_tango_tangocamera_ITango_serviceConnected");
   }
   return JNI_FALSE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_to_ar_tango_tangocamera_ITango_isServiceConnected(JNIEnv *env, jclass type) { return (connected) ? JNI_TRUE : JNI_FALSE; }

extern "C"
JNIEXPORT jboolean JNICALL
Java_to_ar_tango_tangocamera_ITango_resetPoseStart(JNIEnv *env, jclass type)
//--------------------------------------------------------------------------
{

   if (connected)
   {
      TangoService_resetMotionTracking();
      uint64_t  starttime = uptime();
      TangoCoordinateFramePair frames_of_reference;
      frames_of_reference.base = TANGO_COORDINATE_FRAME_START_OF_SERVICE;
      frames_of_reference.target = TANGO_COORDINATE_FRAME_DEVICE;
      TangoPoseData pose;
      while ((uptime() - starttime) <= 5000000000LL)
      {
         TangoErrorType ret = TangoService_getPoseAtTime(0, frames_of_reference, &pose);
         if ( (ret == TANGO_SUCCESS) && (pose.status_code == TANGO_POSE_VALID) )
            return JNI_TRUE;
         std::this_thread::sleep_for(std::chrono::milliseconds(100));
      }
      ALOGW("resetPoseStart timed out");
   }
   return JNI_FALSE;
}

extern "C"
JNIEXPORT jdouble JNICALL
Java_to_ar_tango_tangocamera_ITango_lastTimestamp(JNIEnv *env, jclass type)
//------------------------------------------------------------------------
{
   TangoCoordinateFramePair frames_of_reference;
   frames_of_reference.base = TANGO_COORDINATE_FRAME_START_OF_SERVICE;
   frames_of_reference.target = TANGO_COORDINATE_FRAME_DEVICE;
   TangoPoseData pose;
   if (TangoService_getPoseAtTime(0, frames_of_reference, &pose) == TANGO_SUCCESS)
      return pose.timestamp;
   else
      return -1;
}


extern "C"
JNIEXPORT void JNICALL
Java_to_ar_tango_tangocamera_ITango_disconnect(JNIEnv *env, jclass type)
//----------------------------------------------------------------------
{
   if (connected.exchange(false))
      TangoService_disconnect();
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_to_ar_tango_tangocamera_ITango_isDepth(JNIEnv *env, jclass type)
//--------------------------------------------------------------------
{
   return ( (is_depth) ? JNI_TRUE : JNI_FALSE);

}

//extern "C"
//JNIEXPORT void JNICALL
//Java_to_ar_tango_tangocamera_ITango_enableDepth(JNIEnv *env, jclass type, jboolean b)
////-----------------------------------------------------------------------------------
//{
//   if (b)
//      depth_enabled = true;
//   else
//      depth_enabled = false;
//}

extern "C"
JNIEXPORT jboolean JNICALL
Java_to_ar_tango_tangocamera_ITango_intrinsics(JNIEnv *env, jclass klass, jint cameraId,
                                               jdoubleArray fx_, jdoubleArray fy_, jdoubleArray cx_,
                                               jdoubleArray cy_, jintArray height_,
                                               jintArray width_, jdoubleArray hFOV_,
                                               jdoubleArray vFOV_, jdoubleArray distortion_)
//--------------------------------------------------------------------------------------------------------
{
   jboolean ret = JNI_FALSE;
   if (! connected)
      return ret;
   jdouble *fx = env->GetDoubleArrayElements(fx_, NULL);
   jdouble *fy = env->GetDoubleArrayElements(fy_, NULL);
   jdouble *cx = env->GetDoubleArrayElements(cx_, NULL);
   jdouble *cy = env->GetDoubleArrayElements(cy_, NULL);
   jint *height = env->GetIntArrayElements(height_, NULL);
   jint *width = env->GetIntArrayElements(width_, NULL);
   jdouble *hFOV = env->GetDoubleArrayElements(hFOV_, NULL);
   jdouble *vFOV = env->GetDoubleArrayElements(vFOV_, NULL);
   jdouble *distortion = env->GetDoubleArrayElements(distortion_, NULL);

   TangoCameraIntrinsics intrinsics;
   TangoErrorType err = TangoService_getCameraIntrinsics((TangoCameraId) cameraId, &intrinsics);
   if (err == TANGO_SUCCESS)
   {
      *fx = intrinsics.fx;
      *fy = intrinsics.fy;
      *cx = intrinsics.cx;
      *cy = intrinsics.cy;
      *height = intrinsics.height;
      *width = intrinsics.width;
      memcpy(distortion, intrinsics.distortion, 5*sizeof(jdouble));
      *hFOV = 2*atan2(0.5**width, *fx);
      *vFOV = 2*atan2(0.5**height, *fy);
      ret = JNI_TRUE;
   }

   env->ReleaseDoubleArrayElements(fx_, fx, 0);
   env->ReleaseDoubleArrayElements(fy_, fy, 0);
   env->ReleaseDoubleArrayElements(cx_, cx, 0);
   env->ReleaseDoubleArrayElements(cy_, cy, 0);
   env->ReleaseIntArrayElements(height_, height, 0);
   env->ReleaseIntArrayElements(width_, width, 0);
   env->ReleaseDoubleArrayElements(hFOV_, hFOV, 0);
   env->ReleaseDoubleArrayElements(vFOV_, vFOV, 0);
   env->ReleaseDoubleArrayElements(distortion_, distortion, 0);
   return ret;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_to_ar_tango_tangocamera_ITango_IMU2CameraPose(JNIEnv *env, jclass type, jint cameraId,
                                                   jdoubleArray rotation_, jdoubleArray translation_)
//--------------------------------------------------------------------------------------
{
   TangoCoordinateFramePair frames_of_reference;
   frames_of_reference.base = TANGO_COORDINATE_FRAME_IMU;
   switch (cameraId)
   {
      case TANGO_CAMERA_COLOR:   frames_of_reference.target = TANGO_COORDINATE_FRAME_CAMERA_COLOR; break;
      case TANGO_CAMERA_DEPTH:   frames_of_reference.target = TANGO_COORDINATE_FRAME_CAMERA_DEPTH; break;
      case TANGO_CAMERA_FISHEYE: frames_of_reference.target = TANGO_COORDINATE_FRAME_CAMERA_FISHEYE; break;
      default: return JNI_FALSE;
   }
   jdouble *rotation = nullptr, *translation = nullptr;
   if ( (! env->IsSameObject(rotation_, nullptr)) && (env -> GetArrayLength(rotation_) >= 4) )
      rotation = env->GetDoubleArrayElements(rotation_, NULL);
   if ( (! env->IsSameObject(translation_, nullptr)) && (env -> GetArrayLength(translation_) >= 3) )
      translation = env->GetDoubleArrayElements(translation_, NULL);
   TangoPoseData pose;
   TangoErrorType ret = TangoService_getPoseAtTime(0.0, frames_of_reference, &pose);
   if (ret == TANGO_SUCCESS)
   {
      if (rotation != nullptr)
      {
         rotation[0] = pose.orientation[3];
         rotation[1] = pose.orientation[0];
         rotation[2] = pose.orientation[1];
         rotation[3] = pose.orientation[2];
      }
      if (translation != nullptr)
      {
         translation[0] = pose.translation[0];
         translation[1] = pose.translation[1];
         translation[2] = pose.translation[2];
      }
   }
   if (rotation != nullptr)
      env->ReleaseDoubleArrayElements(rotation_, rotation, 0);
   if (translation != nullptr)
      env->ReleaseDoubleArrayElements(translation_, translation, 0);
   return (ret == TANGO_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}


extern "C"
JNIEXPORT void JNICALL
Java_to_ar_tango_tangocamera_ITango_renderInit(JNIEnv *env, jclass type)
//----------------------------------------------------------------------
{
   renderer.reset(new OGLRenderer(TANGO_CAMERA_COLOR, connected));
   std::stringstream errs;
   if (! renderer->init(errs))
      renderer.reset();
}
extern "C"
JNIEXPORT void JNICALL
Java_to_ar_tango_tangocamera_ITango_renderResize(JNIEnv *env, jclass type, jint w, jint h)
//----------------------------------------------------------------------------------------
{
   if (renderer)
      renderer->resize(w, h);
}
extern "C"
JNIEXPORT void JNICALL
Java_to_ar_tango_tangocamera_ITango_render(JNIEnv *env, jclass type)
//------------------------------------------------------------------
{
   if (renderer)
      renderer->render();
}
