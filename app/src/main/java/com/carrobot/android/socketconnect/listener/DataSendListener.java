package com.carrobot.android.socketconnect.listener;

/**
 * Created by fuwei.jiang on 17/3/1上午11:06.
 */

public interface DataSendListener {
    void onSuccess();

    void onError();

    void onProgress(int progress);
}
