package com.geek.qrcode;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;

import java.util.Collection;
import java.util.Map;

/**
 * @Author: HSL
 * @Time: 2018/12/13 16:16
 * @E-mail: xxx@163.com
 * @Description: 扫描基类~
 */
public class BaseQRScanActivity extends Activity implements SurfaceHolder.Callback {

    private InactivityTimer inactivityTimer;
    private String resultStr;
    private PopupWindow popupWindow;
    private SurfaceView previewView = null;
    private RelativeLayout customCaptureTopRl = null;
    private RelativeLayout customCapturTopBottom = null;
    public ViewfinderView viewfinderView = null;
    public CameraManager cameraManager;
    private boolean hasSurface;
    private IntentSource source;
    private Collection<BarcodeFormat> decodeFormats;
    private String characterSet;
    public CaptureActivityHandler handler;
    private OnCaptureScanListener onCaptureScanListener = null;
    private Map<DecodeHintType, ?> decodeHints;
    private int mScanSize = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_base_qr_scan);
        initView();
        hasSurface = false;
        inactivityTimer = new InactivityTimer(this);
    }

    private void initView() {
        previewView = (SurfaceView) findViewById(R.id.preview_view);
        customCaptureTopRl = (RelativeLayout) findViewById(R.id.custom_capture_top_rl);
        customCapturTopBottom = (RelativeLayout) findViewById(R.id.custom_capture_top_bottom);
        viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
    }

    /**
     *
     * @param listener
     * @param scanSize 扫描框大小
     */
    protected void builder(OnCaptureScanListener listener, int scanSize) {
        ViewfinderView.FramingUIOption uiOption = new ViewfinderView.FramingUIOption();
        uiOption.scanSize = scanSize;
        builder(listener, uiOption);
    }

    /**
     *
     * @param listener
     * @param uiOption 扫描框参数配置
     */
    protected void builder(OnCaptureScanListener listener, ViewfinderView.FramingUIOption uiOption) {
        this.onCaptureScanListener = listener;
        if (onCaptureScanListener != null) {
            onCaptureScanListener.onBuildTopView(customCaptureTopRl);
            onCaptureScanListener.onBuildBottomView(customCapturTopBottom);
        }
        int scanSize = uiOption.scanSize;
        if (uiOption.scanSize <= 100) {
            float scale = getResources().getDisplayMetrics().density;
            scanSize = (int) (170 * scale + 0.5f);
        }
        mScanSize = scanSize;
        viewfinderView.setViewFinderUI(uiOption);
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics outMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(outMetrics);
        customCaptureTopRl.getLayoutParams().height = (outMetrics.heightPixels - scanSize) / 2;
        customCapturTopBottom.getLayoutParams().height = (outMetrics.heightPixels - scanSize) / 2;
    }

    /**
     * 打开闪光灯
     */
    protected void openFlash() {
        Camera camera = getCamera();
        if (camera != null) {
            Camera.Parameters parameters = camera.getParameters();
            if (parameters != null) {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                camera.setParameters(parameters);
            }
        }
    }

    /**
     * 关闭闪光灯
     */
    protected void closeFlash() {
        Camera camera = getCamera();
        if (camera != null) {
            Camera.Parameters parameters = camera.getParameters();
            if (parameters != null) {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                camera.setParameters(parameters);
            }
        }
    }

    /**
     * 打开相册
     */
    protected void openLocalAlbum() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_PICK);
        startActivityForResult(intent, CaptureCode.REQUEST_IMAGE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        //CameraManager必须在这里初始化，而不是在onCreate()中。
        //这是必须的，因为当我们第一次进入时需要显示帮助页，我们并不想打开Camera,测量屏幕大小
        //当扫描框的尺寸不正确时会出现bug
        reInitialize();
    }

    /**
     * 重新初始化
     */
    protected void reInitialize() {
        try {
            cameraManager = new CameraManager(this, this);
            cameraManager.maxFrameSize = mScanSize;
            viewfinderView.setCameraManager(cameraManager);
            handler = null;
            SurfaceHolder surfaceHolder = previewView.getHolder();
            if (hasSurface) {
                initCamera(surfaceHolder);
            } else {
                surfaceHolder.addCallback(this);
            }
            cameraManager.updatePrefs();
            inactivityTimer.onResume();
            source = IntentSource.NONE;
            decodeFormats = null;
            characterSet = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        inactivityTimer.onPause();
        cameraManager.closeMediaPlayer();
        cameraManager.closeDriver();
        if (!hasSurface) {
            SurfaceHolder surfaceHolder = previewView.getHolder();
            surfaceHolder.removeCallback(this);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        inactivityTimer.shutdown();
        super.onDestroy();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (!hasSurface) {
            hasSurface = true;
            initCamera(holder);
        }
    }

    protected Camera getCamera() {
        if (cameraManager != null) {
            return cameraManager.getCamera();
        } else {
            return null;
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;
        if (cameraManager != null) {
            cameraManager.stopPreview();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {

    }

    public void handleDecode(Result rawResult, Bitmap barcode, float scaleFactor) {
        inactivityTimer.onActivity();
        if (barcode != null) {
            cameraManager.playBeepSoundAndVibrate();
            String resultString = rawResult.getText();
            if (onCaptureScanListener != null) {
                onCaptureScanListener.onAnalyzeSuccess(barcode, resultString);
            }
        } else {
            if (onCaptureScanListener != null) {
                onCaptureScanListener.onAnalyzeFailed();
            }
        }
    }

    /**
     * 初始化Camera
     *
     * @param surfaceHolder
     */
    private void initCamera(SurfaceHolder surfaceHolder) {
        if (surfaceHolder == null) {
            return;
        }
        if (cameraManager.isOpen()) {
            return;
        }
        try {
            // 打开Camera硬件设备
            cameraManager.openDriver(surfaceHolder);
            // 创建一个handler来打开预览，并抛出一个运行时异常
            if (handler == null) {
                handler = new CaptureActivityHandler(this, decodeFormats,
                        decodeHints, characterSet, cameraManager);
            }
        } catch (Exception e) {
            Log.w(e.getMessage(), e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CaptureCode.REQUEST_IMAGE) {
            if (data != null) {
                Uri uri = data.getData();
                try {
                    cameraManager.analyzeBitmap(cameraManager.getImageAbsolutePath(this, uri), new CameraManager.AnalyzeCallback() {
                        @Override
                        public void onAnalyzeSuccess(Bitmap bitmap, String result) {
                            if (onCaptureScanListener != null) {
                                onCaptureScanListener.onAnalyzeSuccess(bitmap, result);
                            }
                        }

                        @Override
                        public void onAnalyzeFailed() {
                            if (onCaptureScanListener != null) {
                                onCaptureScanListener.onAnalyzeFailed();
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.w(e.getMessage(), e);
                }
            }
        }
    }

    /**
     * 获取bundle对象
     *
     * @return
     */
    public Bundle getBundle() {
        Intent intent = getIntent();
        if (intent == null) {
            return new Bundle();
        } else {
            Bundle bundle = intent.getExtras();
            return bundle == null ? new Bundle() : bundle;
        }
    }

    /**
     * 从bundle中获取字符串
     *
     * @param bundle       bundle对象
     * @param key
     * @param defaultValue 默认值
     * @return
     */
    public String getStringBundle(Bundle bundle, String key, String defaultValue) {
        if (bundle != null && bundle.containsKey(key)) {
            return bundle.getString(key, defaultValue);
        } else {
            return defaultValue;
        }
    }

    /**
     * 从bundle中获取字符串
     *
     * @param key
     * @param defaultValue 默认值
     * @return
     */
    public String getStringBundle(String key, String defaultValue) {
        Bundle bundle = getBundle();
        return getStringBundle(bundle, key, defaultValue);
    }

    /**
     * 从bundle中获取字符串
     *
     * @param key
     * @return
     */
    public String getStringBundle(String key) {
        return getStringBundle(key, "");
    }

    /**
     * 从bundle中获取int值
     *
     * @param bundle       bundle对象
     * @param key
     * @param defaultValue 默认值
     * @return
     */
    public int getIntBundle(Bundle bundle, String key, int defaultValue) {
        if (bundle != null && bundle.containsKey(key)) {
            return bundle.getInt(key, defaultValue);
        } else {
            return defaultValue;
        }
    }

    /**
     * 从bundle中获取int值
     *
     * @param key
     * @param defaultValue 默认值
     * @return
     */
    public int getIntBundle(String key, int defaultValue) {
        Bundle bundle = getBundle();
        return getIntBundle(bundle, key, defaultValue);
    }

    /**
     * 从bundle中获取int值
     *
     * @param key
     * @return
     */
    public int getIntBundle(String key) {
        return getIntBundle(key, 0);
    }

    /**
     * 从bundle中获取boolean值
     *
     * @param bundle       bundle对象
     * @param key
     * @param defaultValue 默认值
     * @return
     */
    public boolean getBooleanBundle(Bundle bundle, String key, boolean defaultValue) {
        if (bundle != null && bundle.containsKey(key)) {
            return bundle.getBoolean(key, defaultValue);
        } else {
            return defaultValue;
        }
    }

    /**
     * 从bundle中获取boolean值
     *
     * @param key
     * @param defaultValue 默认值
     * @return
     */
    public boolean getBooleanBundle(String key, boolean defaultValue) {
        Bundle bundle = getBundle();
        return getBooleanBundle(bundle, key, defaultValue);
    }

    /**
     * 从bundle中获取boolean值
     *
     * @param key
     * @return
     */
    public boolean getBooleanBundle(String key) {
        return getBooleanBundle(key, false);
    }

    /**
     * 从bundle中获取object值
     *
     * @param bundle       bundle对象
     * @param key
     * @param defaultValue 默认值
     * @return
     */
    public Object getObjectBundle(Bundle bundle, String key, Object defaultValue) {
        if (bundle != null && bundle.containsKey(key)) {
            return bundle.get(key);
        } else {
            return defaultValue;
        }
    }

    /**
     * 从bundle中获取object值
     *
     * @param key
     * @param defaultValue 默认值
     * @return
     */
    public Object getObjectBundle(String key, Object defaultValue) {
        Bundle bundle = getBundle();
        return getObjectBundle(bundle, key, defaultValue);
    }

    /**
     * 从bundle中获取object值
     *
     * @param key
     * @return
     */
    public Object getObjectBundle(String key) {
        return getObjectBundle(key, null);
    }

    /**
     * 从bundle中获取float值
     *
     * @param bundle       bundle对象
     * @param key
     * @param defaultValue 默认值
     * @return
     */
    public float getFloatBundle(Bundle bundle, String key, float defaultValue) {
        if (bundle != null && bundle.containsKey(key)) {
            return bundle.getFloat(key, defaultValue);
        } else {
            return defaultValue;
        }
    }

    /**
     * 从bundle中获取float值
     *
     * @param key
     * @param defaultValue 默认值
     * @return
     */
    public float getFloatBundle(String key, float defaultValue) {
        Bundle bundle = getBundle();
        return getFloatBundle(bundle, key, defaultValue);
    }

    /**
     * 从bundle中获取float值
     *
     * @param key
     * @return
     */
    public float getFloatBundle(String key) {
        return getFloatBundle(key, 0);
    }

    /**
     * 从bundle中获取double值
     *
     * @param bundle       bundle对象
     * @param key
     * @param defaultValue 默认值
     * @return
     */
    public double getDoubleBundle(Bundle bundle, String key, double defaultValue) {
        if (bundle != null && bundle.containsKey(key)) {
            return bundle.getDouble(key, defaultValue);
        } else {
            return defaultValue;
        }
    }

    /**
     * 从bundle中获取double值
     *
     * @param key
     * @param defaultValue 默认值
     * @return
     */
    public double getDoubleBundle(String key, double defaultValue) {
        Bundle bundle = getBundle();
        return getDoubleBundle(bundle, key, defaultValue);
    }

    /**
     * 从bundle中获取double值
     *
     * @param key
     * @return
     */
    public double getDoubleBundle(String key) {
        return getDoubleBundle(key, 0);
    }

    /**
     * 从bundle中获取long值
     *
     * @param bundle       bundle对象
     * @param key
     * @param defaultValue 默认值
     * @return
     */
    public long getLongBundle(Bundle bundle, String key, long defaultValue) {
        if (bundle != null && bundle.containsKey(key)) {
            return bundle.getLong(key, defaultValue);
        } else {
            return defaultValue;
        }
    }

    /**
     * 从bundle中获取long值
     *
     * @param key
     * @param defaultValue 默认值
     * @return
     */
    public long getLongBundle(String key, long defaultValue) {
        Bundle bundle = getBundle();
        return getLongBundle(bundle, key, defaultValue);
    }

    /**
     * 从bundle中获取long值
     *
     * @param key
     * @return
     */
    public long getLongBundle(String key) {
        return getLongBundle(key, 0);
    }

    public Activity getActivity() {
        return this;
    }
}
