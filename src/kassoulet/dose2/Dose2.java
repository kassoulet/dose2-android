/* 
 * Dose2 for Android by Gautier Portet
 * Java Application 
 * */
package kassoulet.dose2;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;

public class Dose2 extends Activity implements OnClickListener {
	private MediaPlayer mediaPlayer = null;
	Dose2View view;
	private PowerManager.WakeLock wakeLock;

	// Load our native library
	static {
		System.loadLibrary("dose2");
	}

	// Implement the OnClickListener callback
	public void onClick(View v) {
		view.toggleFPS();
	}
	
	// Copy APK assets to cache folder
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

	// Remove all files from cache
	private void deleteAssets() {
		File folder = getCacheDir();
		for (File f : folder.listFiles()) {
			f.delete();
		}
	}

	// Play our music file. Uses Android MediaPlayer
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

	// Called when the activity is first created.
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Prevent screen to be dimmed
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "DoNotDimScreen");

		// Copy assets to cache so native code can access them
		copyAssets();

		// Create view, load native data
		view = new Dose2View(this);
		setContentView(view);
		view.setClickable(true);
		view.setOnClickListener(this);
		
		// Delete cached files
		deleteAssets();

		// And start the music!
		music_play("italo128.ogg");
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mediaPlayer.stop();
		mediaPlayer.release();
	}

	@Override
	protected void onPause() {
		super.onPause();
		wakeLock.release();
		finish();
	}

	@Override
	protected void onResume() {
		super.onResume();
		wakeLock.acquire();
	}
}

class Dose2View extends SurfaceView implements SurfaceHolder.Callback {
	private static native void initDemo(String dataFolder, int w, int h);
	private static native int renderDemo(Bitmap bitmap, long time_ms);

	private Bitmap bitmap;
	private long startTime = 0;
	private int frames = 0;
	boolean displayFPS = false;

	DrawingThread thread;

	Activity activity;
	private Paint paint;

	public Dose2View(Context context) {
		super(context);
		getHolder().addCallback(this);
		this.activity = (Activity) context;

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

		// For FPS display
		paint = new Paint();
		paint.setColor(Color.WHITE);
		this.setClickable(true);
	}

	public void toggleFPS() {
		displayFPS = !displayFPS;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (canvas == null) {
			// Something bad happened.
			return;
		}
		if (startTime == 0) {
			// Set our time reference now, when the display start, so the synchro is correct.
			startTime = System.currentTimeMillis();
		}
		frames++;

		// Call native renderer
		int stop = renderDemo(bitmap, System.currentTimeMillis() - startTime);

		if (stop > 0) {
			Log.i("ui", "Exiting.");
			activity.finish();
		}

		// And draw bitmap buffer
		int padding = (canvas.getWidth() - bitmap.getWidth()) / 2;
		canvas.drawBitmap(bitmap, padding, 0, null);

		if (displayFPS) {
			String fps = "" + (int) (1000.0 * frames / (System.currentTimeMillis() - startTime)) + " fps";
			canvas.drawText(fps, padding + 10, 10, paint);
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int arg1, int arg2, int arg3) {
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		thread = new DrawingThread(getHolder(), this);
		thread.setRunning(true);
		thread.start();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		boolean retry = true;
		thread.setRunning(false);
		while (retry) {
			try {
				thread.join();
				retry = false;
			} catch (InterruptedException e) {
				// we will try it again and again...
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

	public void setRunning(boolean run) {
		this.run = run;
	}
}
