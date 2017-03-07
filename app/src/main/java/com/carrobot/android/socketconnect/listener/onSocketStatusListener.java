package com.carrobot.android.socketconnect.listener;


/**
 * Created by GMM on 16/12/28.
 */
public interface onSocketStatusListener {

    void onSocketUdpInfo(String message);

    void onSocketConnectSucess(String connWay);

    void onSocketConnectFail(String message);

    void onSocketConnectLost(String connWay);

}
