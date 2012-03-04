/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kassoulet.dose2;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import android.app.Activity;
import android.app.AlertDialog;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;

public class Dose2 extends Activity {
	private MediaPlayer mediaPlayer = null;

	private PowerManager.WakeLock wakeLock;

	/* load our native library */
	static {
		System.loadLibrary("dose2");
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Prevent screen to be dimmed
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "DoNotDimScreen");

		// Copy assets to cache so native code can access them
		copyAssets();

		// Create view, load native data
		setContentView(new Dose2View(this));

		// Delete cached files
		deleteAssets();

		// And starts the music!
		music_play("italo128.ogg");
	}

	@Override
	protected void onPause() {
		super.onPause();
		wakeLock.release();
		Log.i("activity", "OnPause");
		finish();
	}

	@Override
	protected void onResume() {
		super.onResume();
		wakeLock.acquire();
		Log.i("activity", "OnResume");
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mediaPlayer.stop();
		mediaPlayer.release();
		Log.i("activity", "OnDestroy");
	}

	private void deleteAssets() {
		File folder = getCacheDir();
		for (File f : folder.listFiles()) {
			f.delete();
		}
	}

	public void music_play(String fname) {
		// Load and play an audio file from assets
		AssetManager am = Dose2.this.getAssets();
		try {
			AssetFileDescriptor fd = am.openFd(fname);
			mediaPlayer = new MediaPlayer();
			mediaPlayer.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
			fd.close();
			mediaPlayer.setLooping(false);
			mediaPlayer.prepare();
			mediaPlayer.start();
		} catch (IOException e) {
		}
	}

	private void copyAssets() {
		File folder = getCacheDir();
		AssetManager assetManager = getAssets();
		String[] files = null;
		try {
			files = assetManager.list("");
		} catch (IOException e) {
			Log.e("assets", e.getMessage());
		}
		for (String filename : files) {
			if (filename.endsWith(".vec")) {
				InputStream in = null;
				OutputStream out = null;
				try {
					in = assetManager.open(filename);
					out = new FileOutputStream(folder + "/" + filename);
					copyFile(in, out);
					in.close();
					in = null;
					out.flush();
					out.close();
					out = null;
				} catch (Exception e) {
					Log.e("assets", e.getMessage());
				}
			}
		}
	}

	private void copyFile(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[1024];
		int read;
		while ((read = in.read(buffer)) != -1) {
			out.write(buffer, 0, read);
		}
	}
}

class Dose2View extends SurfaceView implements SurfaceHolder.Callback {
	private Bitmap bitmap;
	private long startTime;
	private int frames = 0;
	DrawingThread thread;

	private static native void renderDemo(Bitmap bitmap, long time_ms);

	private static native void initDemo(String dataFolder, int w, int h);

	public Dose2View(Context context) {
		super(context);
		getHolder().addCallback(this);

		// Get screen size
		int width, height;
		DisplayMetrics displaymetrics = new DisplayMetrics();
		((Activity) context).getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
		width = displaymetrics.widthPixels;
		height = displaymetrics.heightPixels;

		// but we want a 4:3 aspect ratio
		width = height * 4 / 3;

		Log.i("ui", "Creating a " + width + "x" + height + " bitmap");

		// Create buffer
		bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
		// And init demo
		initDemo(context.getCacheDir().toString(), width, height);
		startTime = System.currentTimeMillis();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		frames++;

		// Call native renderer
		renderDemo(bitmap, System.currentTimeMillis() - startTime);

		// And draw bitmap buffer 
		int padding = (canvas.getWidth() - bitmap.getWidth()) / 2;
		canvas.drawBitmap(bitmap, padding, 0, null);

		/*String fps = "" + (int) (1000.0 * frames / (System.currentTimeMillis() - startTime)) + " fps";
		Paint paint = new Paint();
		paint.setColor(Color.WHITE);
		canvas.drawText(fps, padding + 10, 10, paint);*/
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int arg1, int arg2, int arg3) {
		Log.i("sv", "surfaceChanged");
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		Log.i("sv", "surfaceCreated");
		thread = new DrawingThread(getHolder(), this);
		thread.setRunning(true);
		thread.start();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		Log.i("sv", "surfaceDestroyed");

		boolean retry = true;
		thread.setRunning(false);
		while (retry) {
			try {
				thread.join();
				retry = false;
			} catch (InterruptedException e) {
				// we will try it again and again...
				Log.i("sv", "retry join");
			}
		}
	}
}

class DrawingThread extends Thread {
	private SurfaceHolder surfaceHolder;
	private Dose2View panel;
	private boolean run = false;

	public DrawingThread(SurfaceHolder surfaceHolder, Dose2View panel) {
		this.surfaceHolder = surfaceHolder;
		this.panel = panel;
	}

	public void setRunning(boolean run) {
		this.run = run;
	}

	public SurfaceHolder getSurfaceHolder() {
		return surfaceHolder;
	}

	@Override
	public void run() {
		Canvas canvas;
		while (run) {
			canvas = null;
			try {
				canvas = surfaceHolder.lockCanvas(null);
				synchronized (surfaceHolder) {
					panel.onDraw(canvas);
				}
			} finally {
				if (canvas != null) {
					surfaceHolder.unlockCanvasAndPost(canvas);
				}
			}
		}
	}
}
