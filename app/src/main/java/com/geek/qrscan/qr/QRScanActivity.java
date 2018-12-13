package com.geek.qrscan.qr;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.geek.qrcode.BaseQRScanActivity;
import com.geek.qrcode.OnCaptureScanListener;
import com.geek.qrcode.ViewfinderView;
import com.geek.qrscan.R;
import com.geek.qrscan.utils.PixelUtils;
import com.geek.qrscan.utils.ScreenUtils;
import com.yanzhenjie.permission.Action;
import com.yanzhenjie.permission.AndPermission;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * @Author: HSL
 * @Time: 2018/12/13 16:33
 * @E-mail: xxx@163.com
 * @Description: 二维码扫码~
 */
public class QRScanActivity extends BaseQRScanActivity implements OnCaptureScanListener {

    public static void start(Context context) {
        Intent starter = new Intent(context, QRScanActivity.class);
        context.startActivity(starter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int screenWidth = ScreenUtils.getScreenWidth();
        int scanSize = screenWidth - 2 * PixelUtils.dip2px(this, 80);
        //一般构建
//        this.builder(this, scanSize);
        //参数构建
        ViewfinderView.FramingUIOption uiOption = new ViewfinderView.FramingUIOption();
        uiOption.scanSize = scanSize;
        //边框颜色
        uiOption.fbColor = Color.parseColor("#f46215");
        uiOption.fbWidth = 10;
        uiOption.corColor = Color.parseColor("#5e63fd");
        uiOption.corWidth = 16;
        uiOption.corLength = 50;
        builder(this, uiOption);
    }

    /**
     * 自定义顶部布局
     *
     * @param relativeLayout
     */
    @Override
    public void onBuildTopView(RelativeLayout relativeLayout) {
        ScanTopViewHolder holder = new ScanTopViewHolder(this);
        relativeLayout.addView(holder.getTopView());
    }

    /**
     * 自定义底部布局
     *
     * @param relativeLayout
     */
    @Override
    public void onBuildBottomView(RelativeLayout relativeLayout) {
        ScanBottomViewHolder holder = new ScanBottomViewHolder(this);
        relativeLayout.addView(holder.getBottomView());
    }

    /**
     * 完成
     *
     * @param bitmap
     * @param s
     */
    @Override
    public void onAnalyzeSuccess(Bitmap bitmap, String s) {
        Toast.makeText(this, "扫码结果：" + s, Toast.LENGTH_SHORT).show();
        Log.d("hsl", "onAnalyzeSuccess: ==" + bitmap + "==" + s);
//        finish();
        viewfinderView.drawResultBitmap(bitmap);
    }

    /**
     * 失败
     */
    @Override
    public void onAnalyzeFailed() {
        Toast.makeText(this, "解析失败!", Toast.LENGTH_SHORT).show();
    }

    /**
     * 读权限
     */
    private void requestReadExternalStoragePermission() {
        AndPermission.with(this)
                .runtime()
                .permission(Manifest.permission.READ_EXTERNAL_STORAGE)
                .onGranted(new Action<List<String>>() {
                    @Override
                    public void onAction(List<String> data) {
                        if (data != null && !data.isEmpty()) {
                            openLocalAlbum();
                        }
                    }
                })
                .onDenied(new Action<List<String>>() {
                    @Override
                    public void onAction(List<String> data) {

                    }
                })
                .start();
    }

    /**
     * 自定义顶部布局
     */
    public class ScanTopViewHolder {

        @BindView(R.id.return_iv)
        ImageView returnIv;
        @BindView(R.id.album_tv)
        TextView albumTv;
        @BindView(R.id.custom_title_ll)
        LinearLayout customTitleLl;
        @BindView(R.id.close_tv)
        TextView closeTv;

        private View mTopView;

        public ScanTopViewHolder(Context context) {
            mTopView = LayoutInflater.from(context).inflate(R.layout.rl_qr_scan_top, null);
            ButterKnife.bind(this, mTopView);
        }

        public View getTopView() {
            return mTopView;
        }

        /**
         * 返回
         */
        @OnClick(R.id.return_iv)
        public void onReturnIvClicked() {
            finish();
        }

        /**
         * 相册
         */
        @OnClick(R.id.album_tv)
        public void onAlbumTvClicked() {
            requestReadExternalStoragePermission();
//            openLocalAlbum();
        }
    }

    /**
     * 自定义尾部布局
     */
    public class ScanBottomViewHolder {

        @BindView(R.id.lamp_iv)
        ImageView lampIv;

        private View mBottomView;

        public ScanBottomViewHolder(Context context) {
            mBottomView = LayoutInflater.from(context).inflate(R.layout.rl_qr_scan_bottom, null);
            ButterKnife.bind(this, mBottomView);
        }

        public View getBottomView() {
            return mBottomView;
        }
    }
}
