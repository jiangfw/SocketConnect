package com.carrobot.android.socketconnect.listener;

/**
 * Created by fuwei.jiang on 17/3/1上午11:10.
 */

public interface DataReceiveListener {

    void onMessageReceived(int type,String message);

    void onCommandReceived(int command);
}
