/*
 * Dose2 for Android by Gautier Portet
 * Main for Android
 * */

#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <math.h>
#include "dose2/m2d.h"

#define HEAPSIZE 10000000

int WIDTH = 320;
int HEIGHT = 240;

volatile unsigned frame;
int stopnow = 0;

int time0, timex;
int demo_frames = 0;

static uint8_t* graffa;

unsigned char *teepal1();

void fix_fread(void *dest, int dummy, int count, FILE *f)
{
	fread(dest, dummy, count, f);
}

static unsigned hiippi[HEAPSIZE];
static unsigned *hptrs[4096], hpind = 1;
void *getmem(int bytes)
{
	/* Let's keep it aligned */
	int wds = (bytes + 4) / 4;
	return (hptrs[hpind] += wds) - wds;
}
void mark()
{
	hptrs[hpind + 1] = hptrs[hpind];
	hpind++;
}
void release()
{
	hpind--;
}

M2d *new_m2_roto(float x0, float y0, float r, float i)
{
	M2d *m = getmem(sizeof(M2d));
	m->x1 = x0;
	m->y1 = y0;
	r = exp(r);
	m->xx = m->yy = cos(i) * r;
	m->xy = -(m->yx = sin(i) * r);
	return m;
}

M2d *new_m2_mul(M2d *a, M2d *b)
{
	M2d *m = getmem(sizeof(M2d));
	m->xx = a->xx * b->xx + a->yx * b->xy;
	m->xy = a->xy * b->xx + a->yy * b->xy;
	m->x1 = a->x1 * b->xx + a->y1 * b->xy + b->x1;
	m->yx = a->xx * b->yx + a->yx * b->yy;
	m->yy = a->xy * b->yx + a->yy * b->yy;
	m->y1 = a->x1 * b->yx + a->y1 * b->yy + b->y1;
	return m;
}

const char *PATH;

void demo_init(const char * path, int width, int height)
{
	PATH = path;
	WIDTH = width;
	HEIGHT = height;
	hptrs[0] = hptrs[1] = hiippi;
	mark();
	graffa = malloc(WIDTH * HEIGHT);
	initdemo();
}

static uint16_t palette[256];

static uint16_t make565(int red, int green, int blue)
{
	return (uint16_t)(((red << 8) & 0xf800) | ((green << 3) & 0x07e0) | ((blue >> 3) & 0x001f));
}

void fillcopy(uint16_t *screen, uint8_t* graffa, int fill)
{
	int x, y;
	uint8_t *q8 = graffa;
	uint16_t *p16 = screen;

	for (x = 1; x < WIDTH; x++) {
		q8[x] ^= q8[x - 1];
	}

	for (y = 0; y < HEIGHT - 1; y++) {
		for (x = 0; x < WIDTH; x++) {
			q8[x + WIDTH] ^= q8[x];
			p16[x] = palette[q8[x]];
		}
		p16 += WIDTH;
		q8 += WIDTH;
	}
}

// 4 pixels at a time, unused because it needs width to be multiple of 4.
void fillcopy_(uint16_t *screen, uint8_t* graffa, int fill)
{
	int x, y;
	uint8_t *q8 = graffa;
	uint16_t *p16 = screen;
	uint32_t *q32 = (uint32_t*) graffa;

	for (x = 1; x < WIDTH; x++) {
		q8[x] ^= q8[x - 1];
	}

	for (y = 0; y < HEIGHT - 1; y++) {
		for (x = 0; x < WIDTH / 4; x++) {
			q32[x + WIDTH / 4] ^= q32[x];
			p16[4 * x + 0] = palette[q8[4 * x + 0]];
			p16[4 * x + 1] = palette[q8[4 * x + 1]];
			p16[4 * x + 2] = palette[q8[4 * x + 2]];
			p16[4 * x + 3] = palette[q8[4 * x + 3]];
		}
		p16 += WIDTH;
		q32 += WIDTH / 4;
		q8 += WIDTH;
	}
}

void rundemo(float t);

int demo_frame(void* pixels, float time)
{
	float f;

	//timex = time /*- time0*/;
	mark();

	init_layers((char*) graffa, new_col(0, 0, 0, 0));

	memset(graffa, 0, WIDTH * HEIGHT);

	f = (time / 1000.0 / 60.0) * 132.3412 * 4.0;
	rundemo(f);

	unsigned char *p = teepal1();
	int i;
	for (i = 0; i < 256; i++) {
		palette[i] = make565(p[i * 3], p[i * 3 + 1], p[i * 3 + 2]);
	}

	fillcopy(pixels, graffa, 1);

	release();
	demo_frames++;

	return stopnow;
}

