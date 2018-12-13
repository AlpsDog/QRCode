package com.geek.qrcode;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.text.TextUtils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.Hashtable;

/**
 * @Author: HSL
 * @Time: 2018/12/13 16:21
 * @E-mail: xxx@163.com
 * @Description:  二维码生成~
 */
public class QRCodeGenerate {

    private static QRCodeGenerate qrCodeGenerate = null;

    public static QRCodeGenerate getInstance() {
        return qrCodeGenerate == null ? qrCodeGenerate = new QRCodeGenerate() : qrCodeGenerate;
    }

    /**
     * 单位：dp
     */
    private int IMAGE_HALFWIDTH = 0;

    private int dip2px(Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    /**
     * 生成二维码
     *
     * @param content    二维码中包含的文本信息
     * @param icon       中间的icon图标
     * @param qrcodeSize 二维码图片大小(单位：dp,内部已转成像素)
     * @param logoSize   logo图标大小(单位：dp,内部已转成像素)
     * @return
     */
    public Bitmap createCode(Context context, String content, Bitmap icon,
                             int qrcodeSize, int logoSize) {
        try {
            if (TextUtils.isEmpty(content)) {
                return null;
            }
            if (qrcodeSize <= 0) {
                qrcodeSize = 300;
            }
            qrcodeSize = dip2px(context, qrcodeSize);
            IMAGE_HALFWIDTH = dip2px(context, logoSize);
            // 配置参数
            Hashtable<EncodeHintType, Object> hst = new Hashtable<EncodeHintType, Object>();
            hst.put(EncodeHintType.CHARACTER_SET, "utf-8");
            hst.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hst.put(EncodeHintType.MARGIN, 0);
            QRCodeWriter writer = new QRCodeWriter();
            // 生成二维码矩阵信息
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE,
                    qrcodeSize, qrcodeSize, hst);
            // 定义数组长度为矩阵高度*矩阵宽度，用于记录矩阵中像素信息
            int[] pixels = new int[qrcodeSize * qrcodeSize];
            for (int y = 0; y < qrcodeSize; y++) {
                for (int x = 0; x < qrcodeSize; x++) {
                    if (matrix.get(x, y)) {
                        // 记录黑块信息
                        pixels[y * qrcodeSize + x] = 0xff000000;
                    } else {
                        pixels[y * qrcodeSize + x] = 0xffffffff;
                    }
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(qrcodeSize, qrcodeSize,
                    Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, qrcodeSize, 0, 0, qrcodeSize,
                    qrcodeSize);
            if (icon != null) {
                try {
                    Matrix m = new Matrix();
                    float sx = (float) IMAGE_HALFWIDTH / icon.getWidth();
                    float sy = (float) IMAGE_HALFWIDTH / icon.getHeight();
                    m.setScale(sx, sy);// 设置缩放信息
                    // 将logo图片按martix设置的信息缩放
                    icon = Bitmap.createBitmap(icon, 0, 0, icon.getWidth(),
                            icon.getHeight(), m, false);
                    Bitmap mbt = Bitmap.createBitmap(qrcodeSize, qrcodeSize,
                            Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(mbt);
                    canvas.drawBitmap(bitmap, 0, 0, null);
                    canvas.scale(sx, sy, qrcodeSize / 2, qrcodeSize / 2);
                    canvas.drawBitmap(icon, (qrcodeSize - IMAGE_HALFWIDTH) / 2,
                            (qrcodeSize - IMAGE_HALFWIDTH) / 2, null);
                    canvas.save();
                    canvas.restore();
                    return mbt;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 生成二维码
     *
     * @param context
     * @param content    二维码中包含的文本信息
     * @param logoResid  中间的logo图标
     * @param qrcodeSize 二维码图片大小(单位：dp,内部已转成像素)
     * @param logoSize   logo图标大小(单位：dp,内部已转成像素)
     * @return
     */
    public Bitmap createCode(Context context, String content, int logoResid,
                             int qrcodeSize, int logoSize) {
        try {
            Resources res = context.getResources();
            Bitmap bt = BitmapFactory.decodeResource(res, logoResid);
            return createCode(context, content, bt, qrcodeSize, logoSize);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 生成二维码
     *
     * @param content    二维码中包含的文本信息
     * @param qrcodeSize 二维码图片大小(单位：dp,内部已转成像素)
     * @return
     */
    public Bitmap createCode(Context context, String content, int qrcodeSize) {
        return createCode(context, content, null, qrcodeSize, 0);
    }
}
