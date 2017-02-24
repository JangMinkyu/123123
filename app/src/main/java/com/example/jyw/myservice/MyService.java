package com.example.jyw.myservice;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GestureDetectorCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class MyService extends Service {

    GestureDetectorCompat mDetector;

    private class MyGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            Log.e(TAG, "mDetector.onSingleTapConfirmed1");
            if (data == null) {
                Intent intent = new Intent(MyService.this, TransparentActivity.class);
                intent.putExtra("data", mProjectionManager.createScreenCaptureIntent());
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                Log.e(TAG, "mDetector.onSingleTapConfirmed2");
            } else {
                Log.e(TAG, "mDetector.onSingleTapConfirmed3");
                startProjection();
            }
            Log.e(TAG, "mDetector.onSingleTapConfirmed4");
            return true;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            Log.e(TAG, "mDetector.onTouchEvent.onDown");
            mTouchX = e.getRawX();
            mTouchY = e.getRawY();
            mViewX = mParams.x;
            mViewY = mParams.y;
            Log.e(TAG, "mDetector.onTouchEvent.onDown");
            return true;
        }
    }


    View mView;
    WindowManager mWindowManager;
    WindowManager.LayoutParams mParams;
    float mTouchX, mTouchY;
    int mViewX, mViewY;
    LocalBroadcastManager mLBM;
    Intent data;
    BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            data = intent.getParcelableExtra("data");
            startProjection();
        }
    };
    private static int num;
    private static final String TAG = MyService.class.getName();
    private static String STORE_DIRECTORY;
    private static int IMAGES_PRODUCED;
    private static final String SCREENCAP_NAME = "screencap";
    private static final int VIRTUAL_DISPLAY_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
    private static MediaProjection sMediaProjection;

    private MediaProjectionManager mProjectionManager;
    private ImageReader mImageReader;
    private Handler mHandler;
    private Display mDisplay;
    private VirtualDisplay mVirtualDisplay;
    private int mDensity;
    private int mWidth;
    private int mHeight;
    private int mRotation;
    private OrientationChangeCallback mOrientationChangeCallback;

    @Override
    public void onCreate() {
        super.onCreate();
        mDetector = new GestureDetectorCompat(this, new MyGestureListener());
        mView = LayoutInflater.from(this).inflate(R.layout.view_custom, null);

        mView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_MOVE:
                        int x = (int) (motionEvent.getRawX() - mTouchX);
                        int y = (int) (motionEvent.getRawY() - mTouchY);
                        mParams.x = mViewX + x;
                        mParams.y = mViewY + y;
                        mWindowManager.updateViewLayout(mView, mParams);
                        break;
//                    case MotionEvent.ACTION_UP:
//                        Log.e(TAG, "action Up");
////                        mDetector.onTouchEvent(motionEvent);
////                        Log.e(TAG, "mDetector action Up");
//                        break;
                    default:
                        Log.e(TAG, "mDetector.onTouchEvent1");
                        mDetector.onTouchEvent(motionEvent);
                        Log.e(TAG, "mDetector.onTouchEvent2");
                        break;
                }
                return true;
            }
        });
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mParams = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        mParams.gravity = Gravity.TOP | Gravity.LEFT;

        mWindowManager.addView(mView, mParams);


        // MediaProjection
        mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
//        new Thread() {
//            @Override
//            public void run() {
//                Looper.prepare();
//                mHandler = new Handler();
//                Looper.loop();
//            }
//        }.start();
        mHandler = new Handler();

        mLBM = LocalBroadcastManager.getInstance(this);
        mLBM.registerReceiver(mReceiver, new IntentFilter(TransparentActivity.class.getName()));
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mLBM.unregisterReceiver(mReceiver);
        stopProjection();
        if (mView != null) {
            mWindowManager.removeView(mView);
            mView = null;
        }
    }


    private class ImageAvailableListener implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
            FileOutputStream fos = null;
            Bitmap bitmap = null;

            try {
                image = mImageReader.acquireLatestImage();
                if (image != null) {
                    Image.Plane[] planes = image.getPlanes();
                    ByteBuffer buffer = planes[0].getBuffer();
                    int pixelStride = planes[0].getPixelStride();
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - pixelStride * mWidth;

                    // create bitmap
                    bitmap = Bitmap.createBitmap(mWidth + rowPadding / pixelStride, mHeight, Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(buffer);
                    num = (int)(Math.random()*100);
                    // write bitmap to a file
                    fos = new FileOutputStream(STORE_DIRECTORY + "/myscreen"+num+".jpg");
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                    Toast.makeText(MyService.this, "성공", Toast.LENGTH_SHORT).show();
                    IMAGES_PRODUCED++;
                    Log.e(TAG, "captured image: " + IMAGES_PRODUCED);
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }

                if (bitmap != null) {
                    bitmap.recycle();
                }

                if (image != null) {
                    image.close();
                }
                reader.close();
                stopProjection();
            }
        }
    }

    private class OrientationChangeCallback extends OrientationEventListener {
        public OrientationChangeCallback(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            synchronized (this) {
                final int rotation = mDisplay.getRotation();
                if (rotation != mRotation) {
                    mRotation = rotation;
                    try {
                        // clean up
                        if (mVirtualDisplay != null) mVirtualDisplay.release();
                        if (mImageReader != null)
                            mImageReader.setOnImageAvailableListener(null, null);

                        // re-create virtual display depending on device width / height
                        createVirtualDisplay();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private class MediaProjectionStopCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            Log.e("ScreenCapture", "stopping projection.");
//            mHandler.post(new Runnable() {
//                @Override
//                public void run() {
            if (mVirtualDisplay != null) mVirtualDisplay.release();
            if (mImageReader != null) mImageReader.setOnImageAvailableListener(null, null);
            if (mOrientationChangeCallback != null) mOrientationChangeCallback.disable();
            sMediaProjection.unregisterCallback(MediaProjectionStopCallback.this);
//                }
//            });
        }
    }

    private void startProjection() {
        sMediaProjection = mProjectionManager.getMediaProjection(-1, data);

        if (sMediaProjection != null) {
            File externalFilesDir = getExternalFilesDir(null);
            if (externalFilesDir != null) {
                STORE_DIRECTORY = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath();
                Log.e(TAG, "STORE_DIRECTORY = " + STORE_DIRECTORY);
                File storeDirectory = new File(STORE_DIRECTORY);
                if (!storeDirectory.exists()) {
                    boolean success = storeDirectory.mkdirs();
                    if (!success) {
                        Log.e(TAG, "failed to create file storage directory.");
                        return;
                    }
                }
            } else {
                Log.e(TAG, "failed to create file storage directory, getExternalFilesDir is null.");
                return;
            }

            // display metrics
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            mDensity = metrics.densityDpi;
            mDisplay = mWindowManager.getDefaultDisplay();

            // create virtual display depending on device width / height
            createVirtualDisplay();

            // register orientation change callback
            mOrientationChangeCallback = new OrientationChangeCallback(this);
            if (mOrientationChangeCallback.canDetectOrientation()) {
                mOrientationChangeCallback.enable();
            }

            // register media projection stop callback
            sMediaProjection.registerCallback(new MediaProjectionStopCallback(), mHandler);
        }
    }

    private void stopProjection() {
        if (sMediaProjection != null) {
            sMediaProjection.stop();
        }
    }

    /******************************************
     * Factoring Virtual Display creation
     ****************/
    private void createVirtualDisplay() {
        // get width and height
        Point size = new Point();
        mDisplay.getSize(size);
        mWidth = size.x;
        mHeight = size.y;

        // 이대표 바람둥이

        ////////////////////

        // start capture reader
        mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 2);
        mVirtualDisplay = sMediaProjection.createVirtualDisplay(SCREENCAP_NAME, mWidth, mHeight, mDensity, VIRTUAL_DISPLAY_FLAGS, mImageReader.getSurface(), null, mHandler);
        mImageReader.setOnImageAvailableListener(new ImageAvailableListener(), mHandler);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {

            }
        }, 100);
    }

}
