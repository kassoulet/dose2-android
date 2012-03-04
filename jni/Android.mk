LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := dose2
LOCAL_SRC_FILES := jni-wrapper.c android-main.c dose2/alue.c dose2/argb.c dose2/blob.c dose2/demo.c dose2/layer.c dose2/line.c dose2/obu2d.c dose2/palette.c dose2/schaibe.c dose2/taso.c
LOCAL_CFLAGS    := -funsigned-char -O3 -ffast-math
LOCAL_LDLIBS    := -lm -llog -ljnigraphics

include $(BUILD_SHARED_LIBRARY)
