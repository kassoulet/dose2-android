/*
 * Dose2 for Android by Gautier Portet
 * JNI Wrapper
 * */

#include <jni.h>
#include <time.h>
#include <android/log.h>
#include <android/bitmap.h>

#include <stdio.h>
#include <stdlib.h>
#include <math.h>

#define  LOG_TAG    "dose2"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

/* Set to 1 to enable debug log traces. */
#define DEBUG 0

/* Return current time in milliseconds */
static double now_ms(void)
{
	struct timeval tv;
	gettimeofday(&tv, NULL);
	return tv.tv_sec * 1000. + tv.tv_usec / 1000.;
}

/* simple stats management */
typedef struct
{
	double renderTime;
	double frameTime;
} FrameStats;

#define  MAX_FRAME_STATS  200
#define  MAX_PERIOD_MS    1500

typedef struct
{
	double firstTime;
	double lastTime;
	double frameTime;

	int firstFrame;
	int numFrames;
	FrameStats frames[MAX_FRAME_STATS];
} Stats;

static void stats_init(Stats* s)
{
	s->lastTime = now_ms();
	s->firstTime = 0.;
	s->firstFrame = 0;
	s->numFrames = 0;
}

static void stats_startFrame(Stats* s)
{
	s->frameTime = now_ms();
}

static void stats_endFrame(Stats* s)
{
	double now = now_ms();
	double renderTime = now - s->frameTime;
	double frameTime = now - s->lastTime;
	int nn;

	if (now - s->firstTime >= MAX_PERIOD_MS) {
		if (s->numFrames > 0) {
			double minRender, maxRender, avgRender;
			double minFrame, maxFrame, avgFrame;
			int count;

			nn = s->firstFrame;
			minRender = maxRender = avgRender = s->frames[nn].renderTime;
			minFrame = maxFrame = avgFrame = s->frames[nn].frameTime;
			for (count = s->numFrames; count > 0; count--) {
				nn += 1;
				if (nn >= MAX_FRAME_STATS) nn -= MAX_FRAME_STATS;
				double render = s->frames[nn].renderTime;
				if (render < minRender) minRender = render;
				if (render > maxRender) maxRender = render;
				double frame = s->frames[nn].frameTime;
				if (frame < minFrame) minFrame = frame;
				if (frame > maxFrame) maxFrame = frame;
				avgRender += render;
				avgFrame += frame;
			}
			avgRender /= s->numFrames;
			avgFrame /= s->numFrames;

			//LOGI(	"frame/s (avg,min,max) = (%.1f,%.1f,%.1f) "
			//		"render time ms (avg,min,max) = (%.1f,%.1f,%.1f)\n", 1000./avgFrame, 1000./maxFrame, 1000./minFrame, avgRender, minRender, maxRender);
		}
		s->numFrames = 0;
		s->firstFrame = 0;
		s->firstTime = now;
	}

	nn = s->firstFrame + s->numFrames;
	if (nn >= MAX_FRAME_STATS) nn -= MAX_FRAME_STATS;

	s->frames[nn].renderTime = renderTime;
	s->frames[nn].frameTime = frameTime;

	if (s->numFrames < MAX_FRAME_STATS) {
		s->numFrames += 1;
	} else {
		s->firstFrame += 1;
		if (s->firstFrame >= MAX_FRAME_STATS) s->firstFrame -= MAX_FRAME_STATS;
	}

	s->lastTime = now;
}

void demo_init();
int demo_frame(void* pixels, float time);

JNIEXPORT void JNICALL Java_kassoulet_dose2_Dose2View_initDemo(JNIEnv * env, jobject obj, jobject path, jint width, jint height)
{
	const char *nativeString = (*env)->GetStringUTFChars(env, path, 0);

	demo_init(nativeString, width, height);

	(*env)->ReleaseStringUTFChars(env, path, nativeString);
}

JNIEXPORT jint JNICALL Java_kassoulet_dose2_Dose2View_renderDemo(JNIEnv * env, jobject obj, jobject bitmap,
		jlong time_ms)
{
	AndroidBitmapInfo info;
	void* pixels;
	int ret;
	static Stats stats;
	static int init;

	if (!init) {
		stats_init(&stats);
		init = 1;
	}

	if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
		LOGE("AndroidBitmap_getInfo() failed ! error=%d", ret);
		return;
	}

	if (info.format != ANDROID_BITMAP_FORMAT_RGB_565) {
		LOGE("Bitmap format is not RGB_565 !");
		return;
	}

	if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
		LOGE("AndroidBitmap_lockPixels() failed ! error=%d", ret);
	}

	stats_startFrame(&stats);

	//float f = (time_ms / 1000.0 / 60.0) * 132.3412 * 4.0;
	//LOGI("time= %lld %f %f", time_ms, f, f/32.0);
	int stop = demo_frame(pixels, (float) time_ms);

	AndroidBitmap_unlockPixels(env, bitmap);

#if DEBUG
	stats_endFrame(&stats);
#endif

	return stop;
}
