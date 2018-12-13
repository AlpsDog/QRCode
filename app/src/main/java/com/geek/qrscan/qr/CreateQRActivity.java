package com.geek.qrscan.qr;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.geek.qrcode.QRCodeGenerate;
import com.geek.qrscan.R;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
/**
 * @Author: HSL
 * @Time: 2018/12/13 16:34
 * @E-mail: xxx@163.com
 * @Description: 生成二维码~
 */
public class CreateQRActivity extends AppCompatActivity {

    @BindView(R.id.back_iv)
    ImageView backIv;
    @BindView(R.id.normal_qr_tv)
    TextView normalQrTv;
    @BindView(R.id.icon_qr_tv)
    TextView iconQrTv;
    @BindView(R.id.result_iv)
    ImageView resultIv;

    private static final String URL = "https://github.com/clearloveforyou";

    public static void start(Context context) {
        Intent starter = new Intent(context, CreateQRActivity.class);
        context.startActivity(starter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_qr);
        ButterKnife.bind(this);
    }

    /**
     * 返回
     */
    @OnClick(R.id.back_iv)
    public void onBackIvClicked() {
        finish();
    }

    /**
     * 正常二维码
     */
    @OnClick(R.id.normal_qr_tv)
    @SuppressLint("all")
    public void onNormalQrTvClicked() {
        final QRCodeGenerate generate = new QRCodeGenerate();
        Observable.create(new ObservableOnSubscribe<Bitmap>() {
            @Override
            public void subscribe(ObservableEmitter<Bitmap> e) throws Exception {
                Bitmap bitmap = generate.createCode(CreateQRActivity.this, URL, 0);
                if (bitmap != null) {
                    e.onNext(bitmap);
                    e.onComplete();
                } else {
                    e.onError(new Throwable());
                }
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Bitmap>() {
                    @Override
                    public void accept(Bitmap bitmap) throws Exception {
                        resultIv.setImageBitmap(bitmap);
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        Toast.makeText(CreateQRActivity.this, "二维码生成失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * 带icon二维码
     */
    @OnClick(R.id.icon_qr_tv)
    @SuppressLint("all")
    public void onIconQrTvClicked() {
        final QRCodeGenerate generate = new QRCodeGenerate();
        Observable.create(new ObservableOnSubscribe<Bitmap>() {
            @Override
            public void subscribe(ObservableEmitter<Bitmap> e) throws Exception {
                Bitmap bitmap = generate.createCode(
                        CreateQRActivity.this,
                        URL,
                        R.mipmap.jinli_icon,
                        0,
                        80);
                if (bitmap != null) {
                    e.onNext(bitmap);
                    e.onComplete();
                } else {
                    e.onError(new Throwable());
                }
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Bitmap>() {
                    @Override
                    public void accept(Bitmap bitmap) throws Exception {
                        resultIv.setImageBitmap(bitmap);
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        Toast.makeText(CreateQRActivity.this, "二维码生成失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
