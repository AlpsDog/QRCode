package com.geek.qrcode;

import android.graphics.Bitmap;

import com.google.zxing.LuminanceSource;

/**
 * @Author: HSL
 * @Time: 2018/12/13 16:18
 * @E-mail: xxx@163.com
 * @Description: 自定义解析Bitmap LuminanceSource
 */
public class BitmapLuminanceSource extends LuminanceSource {
    private byte[] bitmapPixels;

    public BitmapLuminanceSource(Bitmap bitmap) {
        super(bitmap.getWidth(), bitmap.getHeight());
        int[] data = new int[bitmap.getWidth() * bitmap.getHeight()];
        this.bitmapPixels = new byte[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(data, 0, this.getWidth(), 0, 0, this.getWidth(), this.getHeight());

        for (int i = 0; i < data.length; ++i) {
            this.bitmapPixels[i] = (byte) data[i];
        }

    }

    public byte[] getMatrix() {
        return this.bitmapPixels;
    }

    public byte[] getRow(int y, byte[] row) {
        System.arraycopy(this.bitmapPixels, y * this.getWidth(), row, 0, this.getWidth());
        return row;
    }
}
