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
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.graphics.Bitmap;
import android.graphics.Canvas;

public class Dose2 extends Activity {
	private MediaPlayer music = null;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		copyAssets();
		setContentView(new Dose2View(this));
		deleteAssets();

		// music_play("italo128.ogg");
	}

	private void deleteAssets() {
		File folder = getCacheDir();
		for (File f : folder.listFiles()) {
			// Log.d("files", "deleting: " + f);
			f.delete();
		}
	}

	/* load our native library */
	static {
		System.loadLibrary("dose2");
	}

	public void music_play(String fname) {
		AssetManager am = Dose2.this.getAssets();
		try {
			AssetFileDescriptor fd = am.openFd(fname);
			music = new MediaPlayer();
			// music.setAudioStreamType(AudioManager.STREAM_MUSIC);
			music.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
			Log.d("player", "" + fd.getFileDescriptor() + " " + fd.getStartOffset() + " " + fd.getLength());
			fd.close();
			music.setLooping(true);
			music.prepare();
			music.start();
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
					// Log.d("assets", "" + filename + " -> " + folder + "/" +
					// filename);
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

class Dose2View extends View {
	private Bitmap mBitmap;
	private long mStartTime;

	private static native void renderDemo(Bitmap bitmap, long time_ms);
	private static native void initDemo(String dataFolder, int w, int h);

	public Dose2View(Context context) {
		super(context);

		final int W;
		final int H;

		DisplayMetrics displaymetrics = new DisplayMetrics();
        ((Activity) context).getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        W = displaymetrics.widthPixels/1;
        H = displaymetrics.heightPixels/2;
		
        Log.e("ui", "Creating a " + W + "x" + H + " bitmap");
        
		mBitmap = Bitmap.createBitmap(W, H, Bitmap.Config.RGB_565);
		initDemo(context.getCacheDir().toString(), W, H);
		mStartTime = System.currentTimeMillis();
	}

	@Override
	protected void onDraw(Canvas canvas) {

		//canvas.drawColor(0xFFCCCCCC);
		renderDemo(mBitmap, System.currentTimeMillis() - mStartTime);

        //Log.e("ui", "Using a " + canvas.getWidth() + "x" + canvas.getHeight() + " canvas");

		
		canvas.drawBitmap(mBitmap, 0, 0, null);
		// force a redraw, with a different time-based pattern.
		invalidate();
	}
}
