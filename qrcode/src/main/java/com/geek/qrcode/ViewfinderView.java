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


import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.google.zxing.ResultPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author: HSL
 * @Time: 2018/12/13 16:21
 * @E-mail: xxx@163.com
 * @Description: 这是一个位于相机顶部的预览view, 它增加了一个外部部分透明的取景框，以及激光扫描动画和结果组件~
 */
public final class ViewfinderView extends View {

    private static final long ANIMATION_DELAY = 80L;
    private static final int CURRENT_POINT_OPACITY = 0xA0;
    private static final int MAX_RESULT_POINTS = 20;
    private static final int POINT_SIZE = 8;
    private final Paint paint;

    private CameraManager cameraManager;
    //取景框宽度
    private int mFbWidth;
    //取景框颜色
    private int mFbColor;
    //取景框拐角颜色
    private int mCorColor;
    //拐角宽度
    private int mCorWidth;
    //拐角长度
    private int mCorLength;
    //取景框外的背景颜色
    private int mMaskColor;
    //result Bitmap的颜色
    private int mResultColor;
    //特征点的颜色
    private int mResultPointColor;
    //扫描线移动的y
    private int scanLineTop;
    //扫描线移动速度
    private int mScanVelocity = 6;
    //扫描线
    private Bitmap scanLight;
    //扫描线宽度
    private int mScanLineW;
    //扫码结果
    private Bitmap resultBitmap;
    //取景框尺寸
    private int mScanSize = 0;
    private List<ResultPoint> possibleResultPoints;
    private List<ResultPoint> lastPossibleResultPoints;

    public ViewfinderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mFbWidth = 1;
        mCorWidth = 8;
        mCorLength = 40;
        mScanLineW = 22;
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Resources resources = getResources();
        mFbColor = resources.getColor(R.color.framing_line);
        mCorColor = resources.getColor(R.color.framing_corner);
        mMaskColor = resources.getColor(R.color.viewfinder_mask);
        mResultColor = resources.getColor(R.color.result_view);
        mResultPointColor = resources.getColor(R.color.possible_result_points);
        possibleResultPoints = new ArrayList<>(5);
        lastPossibleResultPoints = null;
        scanLight = BitmapFactory.decodeResource(resources, R.drawable.scan_light);
    }

    public void setCameraManager(CameraManager cameraManager) {
        this.cameraManager = cameraManager;
    }

    /**
     * 设置UI样式
     *
     * @param uiOption
     */
    public void setViewFinderUI(FramingUIOption uiOption) {
        if (uiOption.scanSize != null && uiOption.scanSize > 240) {
            mScanSize = uiOption.scanSize;
        }
        if (uiOption.fbWidth != null
                && uiOption.fbWidth > 0
                && uiOption.fbWidth < mScanSize / 2) {
            mFbWidth = uiOption.fbWidth;
        }
        if (uiOption.fbColor != null) {
            mFbColor = uiOption.fbColor;
        }
        if (uiOption.corWidth != null) {
            mCorWidth = uiOption.corWidth;
        }
        if (uiOption.corLength != null) {
            mCorLength = uiOption.corLength;
        }
        if (uiOption.corColor != null) {
            mCorColor = uiOption.corColor;
        }
        if (uiOption.maskColor != null) {
            mMaskColor = uiOption.maskColor;
        }
        if (uiOption.resultColor != null) {
            mResultColor = uiOption.resultColor;
        }
        if (uiOption.resultPointColor != null) {
            mResultPointColor = uiOption.resultPointColor;
        }
        if (uiOption.scanLineW != null) {
            mScanLineW = uiOption.scanLineW;
        }
        if (uiOption.scanVelocity != null) {
            mScanVelocity = uiOption.scanVelocity;
        }
        if (uiOption.scanLineRes != null) {
            scanLight = BitmapFactory.decodeResource(getResources(), uiOption.scanLineRes);
        }
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (cameraManager == null) {
            // not ready yet, early draw before done configuring
            return;
        }
        cameraManager.maxFrameSize = mScanSize;
        //获取取景框frame
        Rect frame = cameraManager.getFramingRect();
        Rect previewFrame = cameraManager.getFramingRectInPreview();
        if (frame == null || previewFrame == null) {
            return;
        }
        //绘制取景框之外的背景
        drawOutFrameRect(canvas, frame);
        if (resultBitmap != null) {
            // 如果有二维码结果的Bitmap
            // 在取景框内绘制不透明的result Bitmap
            paint.setAlpha(CURRENT_POINT_OPACITY);
            canvas.drawBitmap(resultBitmap, null, frame, paint);
        } else {
            //绘制边框
            drawFrameBounds(canvas, frame);
            //绘制扫描线
            drawScanLight(canvas, frame);
            //绘制特征点
            drawPossibleResultPoint(canvas, frame, previewFrame);
            // Request another update at the animation interval, but only
            // repaint the laser line,
            // not the entire viewfinder mask.
            postInvalidateDelayed(ANIMATION_DELAY, frame.left - POINT_SIZE,
                    frame.top - POINT_SIZE, frame.right + POINT_SIZE,
                    frame.bottom + POINT_SIZE);
        }
    }

    /**
     * 绘制取景框之外的背景
     *
     * @param canvas
     * @param frame
     */
    private void drawOutFrameRect(Canvas canvas, Rect frame) {
        int width = canvas.getWidth();
        int height = canvas.getHeight();
        // 绘制取景框外的暗灰色的表面，分四个区域绘制
        paint.setColor(resultBitmap != null ? mResultColor : mMaskColor);
        //取景框上部区域
        canvas.drawRect(0, 0, width, frame.top, paint);
        //取景框左边区域
        canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
        //取景框右边区域
        canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1,
                paint);
        //取景框底部区域
        canvas.drawRect(0, frame.bottom + 1, width, height, paint);
    }


    /**
     * 绘制取景框边框
     *
     * @param canvas
     * @param frame
     */
    private void drawFrameBounds(Canvas canvas, Rect frame) {
        paint.setColor(mFbColor);
        paint.setStrokeWidth(mFbWidth);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(frame, paint);
        paint.setColor(mCorColor);
        paint.setStyle(Paint.Style.FILL);
        // 左上角
        canvas.drawRect(frame.left + mFbWidth / 2,
                frame.top + mFbWidth / 2,
                frame.left + mFbWidth / 2 + mCorWidth,
                frame.top + mFbWidth / 2 + mCorLength,
                paint);
        canvas.drawRect(frame.left + mFbWidth / 2 + mCorWidth,
                frame.top + mFbWidth / 2,
                frame.left + mFbWidth / 2 + mCorLength,
                frame.top + mFbWidth / 2 + mCorWidth,
                paint);
        // 右上角
        canvas.drawRect(frame.right - mCorWidth - mFbWidth / 2,
                frame.top + mFbWidth / 2,
                frame.right - mFbWidth / 2,
                frame.top + mCorLength + mFbWidth / 2,
                paint);
        canvas.drawRect(frame.right - mCorLength - mFbWidth / 2,
                frame.top + mFbWidth / 2,
                frame.right - mFbWidth / 2,
                frame.top + mCorWidth + mFbWidth / 2,
                paint);
        // 左下角
        canvas.drawRect(frame.left + mFbWidth / 2,
                frame.bottom - mCorLength - mFbWidth / 2,
                frame.left + mCorWidth + mFbWidth / 2,
                frame.bottom - mFbWidth / 2,
                paint);
        canvas.drawRect(frame.left + mFbWidth / 2,
                frame.bottom - mCorWidth - mFbWidth / 2,
                frame.left + mCorLength + mFbWidth / 2,
                frame.bottom - mFbWidth / 2,
                paint);
        // 右下角
        canvas.drawRect(frame.right - mCorWidth - mFbWidth / 2,
                frame.bottom - mCorLength - mFbWidth / 2,
                frame.right - mFbWidth / 2,
                frame.bottom - mFbWidth / 2,
                paint);
        canvas.drawRect(frame.right - mCorLength - mFbWidth / 2,
                frame.bottom - mCorWidth - mFbWidth / 2,
                frame.right - mFbWidth / 2,
                frame.bottom - mFbWidth / 2,
                paint);
    }

    /**
     * 绘制移动扫描线
     *
     * @param canvas
     * @param frame
     */
    private void drawScanLight(Canvas canvas, Rect frame) {
        int extraW = mFbWidth / 2 + mScanLineW / 2;
        if (scanLineTop == 0) {
            scanLineTop = frame.top + extraW;
        }
        if (scanLineTop >= frame.bottom - extraW) {
            scanLineTop = frame.top + extraW;
        } else {
            scanLineTop += mScanVelocity;
        }
        Rect scanRect = new Rect(frame.left + mFbWidth / 2,
                scanLineTop - mScanLineW / 2,
                frame.right - mFbWidth / 2,
                scanLineTop + mScanLineW / 2);
        canvas.drawBitmap(scanLight, null, scanRect, paint);
    }

    /**
     * 绘制特征点
     *
     * @param canvas
     * @param frame
     * @param previewFrame
     */
    private void drawPossibleResultPoint(Canvas canvas, Rect frame, Rect previewFrame) {
        float scaleX = frame.width() / (float) previewFrame.width();
        float scaleY = frame.height() / (float) previewFrame.height();
        // 绘制扫描线周围的特征点
        List<ResultPoint> currentPossible = possibleResultPoints;
        List<ResultPoint> currentLast = lastPossibleResultPoints;
        int frameLeft = frame.left;
        int frameTop = frame.top;
        if (currentPossible.isEmpty()) {
            lastPossibleResultPoints = null;
        } else {
            possibleResultPoints = new ArrayList<ResultPoint>(5);
            lastPossibleResultPoints = currentPossible;
            paint.setAlpha(CURRENT_POINT_OPACITY);
            paint.setColor(mResultPointColor);
            synchronized (currentPossible) {
                for (ResultPoint point : currentPossible) {
                    canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX),
                            frameTop + (int) (point.getY() * scaleY),
                            POINT_SIZE,
                            paint);
                }
            }
        }
        if (currentLast != null) {
            paint.setAlpha(CURRENT_POINT_OPACITY / 2);
            paint.setColor(mResultPointColor);
            synchronized (currentLast) {
                float radius = POINT_SIZE / 2.0f;
                for (ResultPoint point : currentLast) {
                    canvas.drawCircle(frameLeft
                            + (int) (point.getX() * scaleX), frameTop
                            + (int) (point.getY() * scaleY), radius, paint);
                }
            }
        }

    }


    public void drawViewfinder() {
        Bitmap resultBitmap = this.resultBitmap;
        this.resultBitmap = null;
        if (resultBitmap != null) {
            resultBitmap.recycle();
        }
        invalidate();
    }

    /**
     * 设置扫描结果bitmap
     *
     * @param barcode
     */
    public void drawResultBitmap(Bitmap barcode) {
        resultBitmap = barcode;
        invalidate();
    }

    public void addPossibleResultPoint(ResultPoint point) {
        List<ResultPoint> points = possibleResultPoints;
        synchronized (points) {
            points.add(point);
            int size = points.size();
            if (size > MAX_RESULT_POINTS) {
                points.subList(0, size - MAX_RESULT_POINTS / 2).clear();
            }
        }
    }

    /**
     * 取景框UI设置
     */
    public static class FramingUIOption {
        //取景框大小
        public Integer scanSize;
        //取景边框框宽度
        public Integer fbWidth;
        //取景边框颜色
        public Integer fbColor;
        //取景框拐角颜色
        public Integer corColor;
        //拐角宽度
        public Integer corWidth;
        //拐角长度
        public Integer corLength;
        //取景框外的背景颜色
        public Integer maskColor;
        //result Bitmap的颜色
        public Integer resultColor;
        //特征点的颜色
        public Integer resultPointColor;
        //扫描线移动速度
        public Integer scanVelocity = 6;
        //扫描线
        public Integer scanLineRes;
        //扫描线宽度
        public Integer scanLineW;

    }

}
