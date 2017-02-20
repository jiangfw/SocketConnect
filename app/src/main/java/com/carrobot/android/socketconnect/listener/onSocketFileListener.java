package com.carrobot.android.socketconnect.listener;

/**
 * Created by fuwei.jiang on 17/1/13上午11:25.
 */

public interface onSocketFileListener {

    // 单个文件传送成功
    void onFileTransferSucess(String filePath);
    // 所有文件传输成功
    void onFileListTransferSucess();
    // 文件传输失败回调
    void onFileTransferFail(String filePath, String message);
    // Rom安装成功
    void onRomInstallSucess();
    // Rom安装失败
    void onRomInstallFail(String error);
    // Rom正在安装中
    void onRomInstalling(String progress);

}
