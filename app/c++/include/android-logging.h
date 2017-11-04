#ifndef _ANDROID_LOGGING_H_
#define _ANDROID_LOGGING_H_

#ifdef __ANDROID__
#include <android/log.h>

//#define ANDROID_LOG_TAG "SomeTag" //define before include
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, ANDROID_LOG_TAG, __VA_ARGS__)
#define ALOGW(...) __android_log_print(ANDROID_LOG_WARN, ANDROID_LOG_TAG, __VA_ARGS__)
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, ANDROID_LOG_TAG, __VA_ARGS__)
#define ALOGD(...) __android_log_print(ANDROID_LOG_DEBUG, ANDROID_LOG_TAG, __VA_ARGS__)
#if DEBUG
#define ALOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, ANDROID_LOG_TAG, __VA_ARGS__)
#else
#define ALOGV(...)
#endif
#else
#define ALOGE(...) fprintf(stderr, "%s ERROR: ", ANDROID_LOG_TAG); fprintf(stderr,  __VA_ARGS__); fprintf(stderr, "\n")
#define ALOGW(...) fprintf(stdout, "%s WARNING: ", ANDROID_LOG_TAG);  fprintf(stdout, __VA_ARGS__); fprintf(stderr, "\n")
#define ALOGI(...) fprintf(stdout, "%s INFO: ", ANDROID_LOG_TAG);  fprintf(stdout, __VA_ARGS__); fprintf(stderr, "\n")
#if DEBUG
#define ALOGV(...) fprintf(stdout, "%s DEBUG: ", ANDROID_LOG_TAG);  fprintf(stdout, __VA_ARGS__); fprintf(stderr, "\n")
#else
#define ALOGV(...)
#endif
#endif

#endif //_ANDROID_LOGGING_H_
