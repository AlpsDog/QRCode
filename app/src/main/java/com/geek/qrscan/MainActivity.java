package com.geek.qrscan;

import android.Manifest;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import com.geek.qrscan.qr.CreateQRActivity;
import com.geek.qrscan.qr.QRScanActivity;
import com.yanzhenjie.permission.Action;
import com.yanzhenjie.permission.AndPermission;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {

    @BindView(R.id.qr_scan_tv)
    TextView qrScanTv;
    @BindView(R.id.qr_create_tv)
    TextView qrCreateTv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
    }

    @OnClick(R.id.qr_scan_tv)
    public void onQrScanTvClicked() {
        requestCameraPermission();
    }

    @OnClick(R.id.qr_create_tv)
    public void onQrCreateTvClicked() {
        CreateQRActivity.start(this);
    }

    /**
     * 请求相机权限
     * Manifest.permission.READ_EXTERNAL_STORAGE此权限不给讲将无法获取图片bitmap
     */
    private void requestCameraPermission() {
        AndPermission.with(this)
                .runtime()
                .permission(Manifest.permission.CAMERA)
                .onGranted(new Action<List<String>>() {
                    @Override
                    public void onAction(List<String> data) {
                        if (data != null && !data.isEmpty()) {
                            QRScanActivity.start(MainActivity.this);
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
}
