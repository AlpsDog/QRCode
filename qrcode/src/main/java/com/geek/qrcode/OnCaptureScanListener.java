package com.geek.qrcode;

import android.graphics.Bitmap;
import android.widget.RelativeLayout;

/**
 * @Author: HSL
 * @Time: 2018/12/13 16:21
 * @E-mail: xxx@163.com
 * @Description: 这个人太懒，没留下什么踪迹~
 */
public interface OnCaptureScanListener {

    public void onBuildTopView(RelativeLayout container);

    public void onBuildBottomView(RelativeLayout container);

    public void onAnalyzeSuccess(Bitmap mBitmap, String result);

    public void onAnalyzeFailed();
}
