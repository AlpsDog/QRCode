/*
 * Copyright (C) 2008 ZXing authors
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

package com.geek.qrcode;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

/**
 * This object wraps the Camera service object and expects to be the only one
 * talking to it. The implementation encapsulates the steps needed to take
 * preview-sized images, which are used for both preview and decoding.
 */
public final class CameraManager {

    private static final String TAG = CameraManager.class.getSimpleName();
    public static final String RESULT_TYPE = "result_type";
    public static final String RESULT_STRING = "result_string";
    public static final int RESULT_SUCCESS = 1;
    public static final int RESULT_FAILED = 2;
    public static final String LAYOUT_ID = "layout_id";
    private final BeepManager beepManager;
    private static final float BEEP_VOLUME = 0.10f;
    private static final long VIBRATE_DURATION = 500L;
    public int maxFrameSize = 475;

    private MediaPlayer mediaPlayer;
    private boolean playBeep;
    private boolean vibrate;
    private static CameraManager cameraManager;

    private final Context context;
    private final CameraConfigurationManager configManager;


    private Camera camera;
    private AutoFocusManager autoFocusManager;
    private Rect framingRect;
    private Rect framingRectInPreview;
    private boolean initialized;
    private boolean previewing;
    private int requestedCameraId = -1;
    private int requestedFramingRectWidth;
    private int requestedFramingRectHeight;
    /**
     * Preview frames are delivered here, which we pass on to the registered
     * handler. Make sure to clear the handler so it will only receive one
     * message.
     */
    private final PreviewCallback previewCallback;

    public CameraManager(Context context, Activity activity) {
        this.context = context;
        this.configManager = new CameraConfigurationManager(context);
        beepManager = new BeepManager(activity);
        previewCallback = new PreviewCallback(configManager);
    }

    public Camera getCamera() {
        return camera;
    }

    /**
     * Opens the camera driver and initializes the hardware parameters.
     *
     * @param holder The surface object which the camera will draw preview frames
     *               into.
     * @throws IOException Indicates the camera driver failed to open.
     */
    public synchronized void openDriver(SurfaceHolder holder)
            throws IOException {
        Camera theCamera = camera;
        if (theCamera == null) {

            if (requestedCameraId >= 0) {
                theCamera = OpenCameraInterface.open(requestedCameraId);
            } else {
                theCamera = OpenCameraInterface.open();
            }

            if (theCamera == null) {
                throw new IOException();
            }
            camera = theCamera;
        }
        theCamera.setPreviewDisplay(holder);

        if (!initialized) {
            initialized = true;
            configManager.initFromCameraParameters(theCamera);
            if (requestedFramingRectWidth > 0 && requestedFramingRectHeight > 0) {
                setManualFramingRect(requestedFramingRectWidth,
                        requestedFramingRectHeight);
                requestedFramingRectWidth = 0;
                requestedFramingRectHeight = 0;
            }
        }

        Camera.Parameters parameters = theCamera.getParameters();
        String parametersFlattened = parameters == null ? null : parameters
                .flatten(); // Save these, temporarily
        try {
            configManager.setDesiredCameraParameters(theCamera, false);
        } catch (RuntimeException re) {
            // Driver failed
            Log.w(TAG,
                    "Camera rejected parameters. Setting only minimal safe-mode parameters");
            Log.i(TAG, "Resetting to saved camera params: "
                    + parametersFlattened);
            // Reset:
            if (parametersFlattened != null) {
                parameters = theCamera.getParameters();
                parameters.unflatten(parametersFlattened);
                try {
                    theCamera.setParameters(parameters);
                    configManager.setDesiredCameraParameters(theCamera, true);
                } catch (RuntimeException re2) {
                    // Well, darn. Give up
                    Log.w(TAG,
                            "Camera rejected even safe-mode parameters! No configuration");
                }
            }
        }

    }

    //手机灯光控制
    public void isLightEnable(boolean isEnable) {
        Camera.Parameters parameter;
        if (isEnable) {

            if (camera != null) {
                parameter = camera.getParameters();
                parameter.setFlashMode("torch");
                camera.setParameters(parameter);
            }
        } else {
            if (camera != null) {
                parameter = camera.getParameters();
                parameter.setFlashMode("off");
                camera.setParameters(parameter);
            }
        }

    }

    /**
     * 开启响铃和震动
     */
    public void playBeepSoundAndVibrate() {
        beepManager.playBeepSoundAndVibrate();
    }

    /**
     * 判断是否需要响铃
     *
     * @param prefs
     * @param activity
     * @return
     */
    private static boolean shouldBeep(SharedPreferences prefs, Context activity) {
        boolean shouldPlayBeep = prefs.getBoolean(
                PreferencesActivity.KEY_PLAY_BEEP, true);
        if (shouldPlayBeep) {
            // See if sound settings overrides this
            AudioManager audioService = (AudioManager) activity
                    .getSystemService(Context.AUDIO_SERVICE);
            if (audioService.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
                shouldPlayBeep = false;
            }
        }
        return shouldPlayBeep;
    }

    public void updatePrefs() {
        beepManager.updatePrefs();
    }

    public void closeMediaPlayer() {
        beepManager.close();
    }

    /**
     * 根据Uri获取图片绝对路径，解决Android4.4以上版本Uri转换
     *
     * @param context
     * @param imageUri
     */
    @TargetApi(19)
    public static String getImageAbsolutePath(Context context, Uri imageUri) {
        if (context == null || imageUri == null)
            return null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT && DocumentsContract.isDocumentUri(context, imageUri)) {
            if (DocumentUtils.isExternalStorageDocument(imageUri)) {
                String docId = DocumentsContract.getDocumentId(imageUri);
                String[] split = docId.split(":");
                String type = split[0];
                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }
            } else if (DocumentUtils.isDownloadsDocument(imageUri)) {
                String id = DocumentsContract.getDocumentId(imageUri);
                Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
                return DocumentUtils.getDataColumn(context, contentUri, null, null);
            } else if (DocumentUtils.isMediaDocument(imageUri)) {
                String docId = DocumentsContract.getDocumentId(imageUri);
                String[] split = docId.split(":");
                String type = split[0];
                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }
                String selection = MediaStore.Images.Media._ID + "=?";
                String[] selectionArgs = new String[]{split[1]};
                return DocumentUtils.getDataColumn(context, contentUri, selection, selectionArgs);
            }
        } // MediaStore (and general)
        else if ("content".equalsIgnoreCase(imageUri.getScheme())) {
            // Return the remote address
            if (DocumentUtils.isGooglePhotosUri(imageUri))
                return imageUri.getLastPathSegment();
            return DocumentUtils.getDataColumn(context, imageUri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(imageUri.getScheme())) {
            return imageUri.getPath();
        }
        return null;
    }

    //解析相册图片，完成图片二维码扫描
    public static void analyzeBitmap(String path, AnalyzeCallback analyzeCallback) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        options.inJustDecodeBounds = false;
        int sampleSize = (int) ((float) options.outHeight / 400.0F);
        if (sampleSize <= 0) {
            sampleSize = 1;
        }

        options.inSampleSize = sampleSize;
        Bitmap mBitmap = BitmapFactory.decodeFile(path, options);
        MultiFormatReader multiFormatReader = new MultiFormatReader();
        Hashtable<DecodeHintType, Object> hints = new Hashtable(2);
        Vector<BarcodeFormat> decodeFormats = new Vector();
        if (decodeFormats == null || decodeFormats.isEmpty()) {
            decodeFormats = new Vector();
            decodeFormats.addAll(DecodeFormatManager.INDUSTRIAL_FORMATS);
            decodeFormats.addAll(DecodeFormatManager.QR_CODE_FORMATS);
            decodeFormats.addAll(DecodeFormatManager.DATA_MATRIX_FORMATS);
        }

        hints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);
        multiFormatReader.setHints(hints);
        Result rawResult = null;

        try {
            rawResult = multiFormatReader.decodeWithState(new BinaryBitmap(new HybridBinarizer(new BitmapLuminanceSource(mBitmap))));
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (rawResult != null) {
            if (analyzeCallback != null) {
                analyzeCallback.onAnalyzeSuccess(mBitmap, rawResult.getText());
            }
        } else if (analyzeCallback != null) {
            analyzeCallback.onAnalyzeFailed();
        }

    }

    /**
     * 生成带logo二维码图片或不带logo
     *
     * @param text 二维码内容
     * @param w    二维码图片宽
     * @param h    二维码图片高
     * @param logo
     * @return
     */
    public static Bitmap createImage(String text, int w, int h, Bitmap logo) {
        if (TextUtils.isEmpty(text)) {
            return null;
        } else {
            try {
                Bitmap scaleLogo = getScaleLogo(logo, w, h);
                int offsetX = w / 2;
                int offsetY = h / 2;
                int scaleWidth = 0;
                int scaleHeight = 0;
                if (scaleLogo != null) {
                    scaleWidth = scaleLogo.getWidth();
                    scaleHeight = scaleLogo.getHeight();
                    offsetX = (w - scaleWidth) / 2;
                    offsetY = (h - scaleHeight) / 2;
                }

                Hashtable<EncodeHintType, Object> hints = new Hashtable();
                hints.put(EncodeHintType.CHARACTER_SET, "utf-8");
                hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
                hints.put(EncodeHintType.MARGIN, Integer.valueOf(0));
                BitMatrix bitMatrix = (new QRCodeWriter()).encode(text, BarcodeFormat.QR_CODE, w, h, hints);
                int[] pixels = new int[w * h];

                for (int y = 0; y < h; ++y) {
                    for (int x = 0; x < w; ++x) {
                        if (x >= offsetX && x < offsetX + scaleWidth && y >= offsetY && y < offsetY + scaleHeight) {
                            int pixel = scaleLogo.getPixel(x - offsetX, y - offsetY);
                            if (pixel == 0) {
                                if (bitMatrix.get(x, y)) {
                                    pixel = -16777216;
                                } else {
                                    pixel = -1;
                                }
                            }

                            pixels[y * w + x] = pixel;
                        } else if (bitMatrix.get(x, y)) {
                            pixels[y * w + x] = -16777216;
                        } else {
                            pixels[y * w + x] = -1;
                        }
                    }
                }

                Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
                return bitmap;
            } catch (WriterException var15) {
                var15.printStackTrace();
                return null;
            }
        }
    }

    private static Bitmap getScaleLogo(Bitmap logo, int w, int h) {
        if (logo == null) {
            return null;
        } else {
            Matrix matrix = new Matrix();
            float scaleFactor = Math.min((float) w * 1.0F / 5.0F / (float) logo.getWidth(), (float) h * 1.0F / 5.0F / (float) logo.getHeight());
            matrix.postScale(scaleFactor, scaleFactor);
            Bitmap result = Bitmap.createBitmap(logo, 0, 0, logo.getWidth(), logo.getHeight(), matrix, true);
            return result;
        }
    }

    public synchronized boolean isOpen() {
        return camera != null;
    }


    /**
     * Closes the camera driver if still in use.
     */
    public synchronized void closeDriver() {
        if (camera != null) {
            camera.release();
            camera = null;
            // Make sure to clear these each time we close the camera, so that
            // any scanning rect
            // requested by intent is forgotten.
            framingRect = null;
            framingRectInPreview = null;
        }
    }

    /**
     * Asks the camera hardware to begin drawing preview frames to the screen.
     */
    public synchronized void startPreview() {
        Camera theCamera = camera;
        if (theCamera != null && !previewing) {
            theCamera.startPreview();
            previewing = true;
            autoFocusManager = new AutoFocusManager(context, camera);
        }
    }

    /**
     * Tells the camera to stop drawing preview frames.
     */
    public synchronized void stopPreview() {
        if (autoFocusManager != null) {
            autoFocusManager.stop();
            autoFocusManager = null;
        }
        if (camera != null && previewing) {
            camera.stopPreview();
            previewCallback.setHandler(null, 0);
            previewing = false;
        }
    }

    /**
     * A single preview frame will be returned to the handler supplied. The data
     * will arrive as byte[] in the message.obj field, with width and height
     * encoded as message.arg1 and message.arg2, respectively.
     *
     * @param handler The handler to send the message to.
     * @param message The what field of the message to be sent.
     */
    public synchronized void requestPreviewFrame(Handler handler, int message) {
        Camera theCamera = camera;
        if (theCamera != null && previewing) {
            previewCallback.setHandler(handler, message);
            theCamera.setOneShotPreviewCallback(previewCallback);
        }
    }

    /**
     * 计算这个条形码的扫描框；便于声明的同时，也强制用户通过改变距离来扫描到整个条形码
     */
    public synchronized Rect getFramingRect() {
        if (framingRect == null) {
            if (camera == null) {
                return null;
            }
            Point screenResolution = configManager.getScreenResolution();
            if (screenResolution == null) {
                // Called early, before init even finished
                return null;
            }

//			int width = findDesiredDimensionInRange(screenResolution.x,
//					MIN_FRAME_WIDTH, MAX_FRAME_WIDTH);
//			int height = findDesiredDimensionInRange(screenResolution.y,
//					MIN_FRAME_HEIGHT, MAX_FRAME_HEIGHT);

//            int width = findDesiredDimensionInRange(screenResolution.x,
//                    MIN_FRAME_WIDTH, maxFrameWidth);
//            int height = findDesiredDimensionInRange(screenResolution.y,
//                    MIN_FRAME_HEIGHT, maxFrameHeight);

            int leftOffset = (screenResolution.x - maxFrameSize) / 2;
            int topOffset = (screenResolution.y - maxFrameSize) / 2 - 120;
            framingRect = new Rect(leftOffset, topOffset, leftOffset + maxFrameSize,
                    topOffset + maxFrameSize);
        }
        return framingRect;
    }

    private static int findDesiredDimensionInRange(int resolution, int hardMin,
                                                   int hardMax) {
        int dim = 5 * resolution / 8; // Target 5/8 of each dimension
        if (dim < hardMin) {
            return hardMin;
        }
        if (dim > hardMax) {
            return hardMax;
        }
        return dim;
    }

    /**
     * Like {@link #getFramingRect} but coordinates are in terms of the preview
     * frame, not UI / screen.
     *
     * @return {@link Rect} expressing barcode scan area in terms of the preview
     * size
     */
    public synchronized Rect getFramingRectInPreview() {
        if (framingRectInPreview == null) {
            Rect framingRect = getFramingRect();
            if (framingRect == null) {
                return null;
            }
            Rect rect = new Rect(framingRect);
            Point cameraResolution = configManager.getCameraResolution();
            Point screenResolution = configManager.getScreenResolution();
            if (cameraResolution == null || screenResolution == null) {
                // Called early, before init even finished
                return null;
            }

            /******************** 竖屏更改1(cameraResolution.x/y互换) ************************/
            rect.left = rect.left * cameraResolution.y / screenResolution.x;
            rect.right = rect.right * cameraResolution.y / screenResolution.x;
            rect.top = rect.top * cameraResolution.x / screenResolution.y;
            rect.bottom = rect.bottom * cameraResolution.x / screenResolution.y;
            framingRectInPreview = rect;
        }
        return framingRectInPreview;
    }

    /**
     * Allows third party apps to specify the camera ID, rather than determine
     * it automatically based on available cameras and their orientation.
     *
     * @param cameraId camera ID of the camera to use. A negative value means
     *                 "no preference".
     */
    public synchronized void setManualCameraId(int cameraId) {
        requestedCameraId = cameraId;
    }

    /**
     * Allows third party apps to specify the scanning rectangle dimensions,
     * rather than determine them automatically based on screen resolution.
     *
     * @param width  The width in pixels to scan.
     * @param height The height in pixels to scan.
     */
    public synchronized void setManualFramingRect(int width, int height) {
        if (initialized) {
            Point screenResolution = configManager.getScreenResolution();
            if (width > screenResolution.x) {
                width = screenResolution.x;
            }
            if (height > screenResolution.y) {
                height = screenResolution.y;
            }
            int leftOffset = (screenResolution.x - width) / 2;
            int topOffset = (screenResolution.y - height) / 2;
            framingRect = new Rect(leftOffset, topOffset, leftOffset + width,
                    topOffset + height);
            Log.d(TAG, "Calculated manual framing rect: " + framingRect);
            framingRectInPreview = null;
        } else {
            requestedFramingRectWidth = width;
            requestedFramingRectHeight = height;
        }
    }

    /**
     * A factory method to build the appropriate LuminanceSource object based on
     * the format of the preview buffers, as described by Camera.Parameters.
     *
     * @param data   A preview frame.
     * @param width  The width of the image.
     * @param height The height of the image.
     * @return A PlanarYUVLuminanceSource instance.
     */
    public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data, int width, int height) {
        Rect rect = getFramingRectInPreview();
        if (rect == null) {
            return null;
        }
        int exwidth = (width - rect.left - rect.width()) / 2;
        int sw = exwidth * 2 / 3;
        if (sw > 0) {
            int left = rect.left - sw;
            int top = rect.top - sw;
            int rwidth = rect.width() + 2 * sw;
            int rheight = rect.height() + 2 * sw;
            return new PlanarYUVLuminanceSource(data, width, height, left,
                    top, rwidth, rheight, false);
        } else {
            return new PlanarYUVLuminanceSource(data, width, height, rect.left,
                    rect.top, rect.width(), rect.height(), false);
        }
    }


    public interface AnalyzeCallback {
        void onAnalyzeSuccess(Bitmap var1, String var2);

        void onAnalyzeFailed();
    }
}
