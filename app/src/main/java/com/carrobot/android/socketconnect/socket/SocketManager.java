package com.carrobot.android.socketconnect.socket;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import com.carrobot.android.socketconnect.listener.DataReceiveListener;
import com.carrobot.android.socketconnect.listener.DataSendListener;
import com.carrobot.android.socketconnect.listener.onSocketFileListener;
import com.carrobot.android.socketconnect.listener.onSocketStatusListener;
import com.carrobot.android.socketconnect.utils.Config;
import com.carrobot.android.socketconnect.utils.LogController;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

/**
 * Created by fuwei.jiang on 17/1/6.
 */

public class SocketManager {

    public static String TAG = SocketManager.class.getSimpleName();

    private Context mContext;
    private static SocketManager instance;
    // 无线wifi方式socket通信，使用socket实现收发
    private UDPSocket mUDPSocket;
    private TCPWifiSocket mTCPClient;
    private Thread mUDPReceiveThread;
    private Thread mUDPSendThread;
    private boolean isStartUDPBroadcast = true;

    // 有线连接方式socket通信，使用serversocket实现收发
    private TCPUsbSocket mTCPUsbSocket = null;
    private MyServiceConnection myServiceConnection;

    // 存储当前有效的连接方式
    private HashSet<String> mConnTypeSet = new HashSet<>();

    // 设置当前默认使用的TCP端口号
    private String mPortType = Config.TCP_PORT_FACTORY;

    // socket连接监听
    private List<onSocketStatusListener> mListenerList = new ArrayList<>();
    // socket文件传输监听
    private onSocketFileListener mOnSocketFileListener = null;
    // 数据接受监听
    private List<DataReceiveListener> mDataReceivedListenerList = new ArrayList<DataReceiveListener>();

    //start 文件传输使用的变量 start
    private boolean isUSBTransfer = false;
    private boolean isWIFITransfer = false;
    private boolean isUSBConn = false;
    private boolean isWIFIConn = false;
    private boolean isRomInstalling = false;
    private ArrayList<File> mFileLists = new ArrayList<>();
    private Hashtable<File, Boolean> mHt_FileStatus = new Hashtable<>();
    //end 文件传输使用的变量 end

    private SocketManager(Context context) {
        this.mContext = context.getApplicationContext();
    }

    public static SocketManager getInstance(Context context) {
        if (instance == null) {
            if (context != null) {
                instance = new SocketManager(context);
            }
        }
        return instance;
    }

    /**
     * 开始建立Socket连接（有线和无线同时监听）
     */
    public void startSocketConnection() {
        mConnTypeSet.clear();
        isUSBConn = false;
        isWIFIConn = false;
        destoryFile();
        startWifiSocketConnection();
        startUsbSocketConnection();

    }

    /**
     * 开始建立无线连接
     */
    private void startWifiSocketConnection() {
        //建立UPD链接，获取萝卜的ip地址和端口号,获取成功后直接建立TCP链接
        mUDPSocket = new UDPSocket();
        mUDPSocket.addDataReceiverListener(new DataReceiveListener() {
            @Override
            public void onMessageReceived(int type, String message) {
                if (type == Config.TYPE_RECEIVE_UDP) {
                    //将收到的消息发给主界面
                    notifySocketUdpInfo(message);
                    //建立TCP链接
                    int OBDPort = 8888;
                    String ip = "";
                    int factoryPort = 1234;

                    try {
                        JSONObject jsonObject = new JSONObject(message);
                        if (jsonObject.has("OBDPort"))
                            OBDPort = jsonObject.getInt("OBDPort");
                        if (jsonObject.has("ip"))
                            ip = jsonObject.getString("ip");
                        if (jsonObject.has("factoryPort"))
                            factoryPort = jsonObject.getInt("factoryPort");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    if (mTCPClient == null) {
                        mTCPClient = new TCPWifiSocket();
                        mTCPClient.addSocketConnStatusListener(new WirelessSocketConnListener());
                        mTCPClient.addDataReceivedListener(new DataReceiveListener() {
                            @Override
                            public void onMessageReceived(int type, final String message) {
                                notifyMessageReceived(type, message);
                                LogController.i(TAG, "wifi received msg:" + message);
                            }

                            @Override
                            public void onCommandReceived(int command) {

                            }
                        });
                        mTCPClient.setSocketFileListerner(new SocketFileStatusListener(Config.TCP_CONTECT_WAY_WIFI));
                        mTCPClient.connectTcpWifiSocket(ip, factoryPort);
                    }
                    //停止UDP的广播请求
                    isStartUDPBroadcast = false;
                    LogController.i(TAG, "udp accept server message:" + message);
                }
            }

            @Override
            public void onCommandReceived(int command) {

            }
        });
        //建立链接
        mUDPSocket.connectUDPSocket();

        //发送udp广播
        isStartUDPBroadcast = true;
        if (mUDPSendThread != null)
            mUDPSendThread.interrupt();
        mUDPSendThread = new Thread(new UdpBroadcastRunnable("{\n" +
                "     \"msg\":\"reqConn\"\n" +
                "     }"));
        mUDPSendThread.start();
    }

    /**
     * 开始建立有线连接
     */
    private void startUsbSocketConnection() {

        //建立有线连接的服务,启动service解决端口重新绑定的问题
        myServiceConnection = new MyServiceConnection();
        mContext.bindService(new Intent(mContext, TCPUsbSocket.class), myServiceConnection, Context.BIND_AUTO_CREATE);

    }

    /**
     * 实现文件传输监听
     */
    private class SocketFileStatusListener implements onSocketFileListener {
        private String mConnType = Config.TCP_CONTECT_WAY_WIFI;

        private SocketFileStatusListener(String connType) {
            this.mConnType = connType;
        }

        @Override
        public void onFileTransferSucess(String filePath) {
        }

        @Override
        public void onFileListTransferSucess() {
        }

        @Override
        public void onFileTransferFail(String filePath, String message) {
        }

        @Override
        public void onRomInstallSucess() {
            if (mOnSocketFileListener != null && isRomInstalling) {
                destoryFile();
                mOnSocketFileListener.onRomInstallSucess();
            }
        }

        @Override
        public void onRomInstallFail(String error) {
            isUSBTransfer = false;
            isWIFITransfer = false;
            isRomInstalling = false;
            if (mOnSocketFileListener != null) {
                mOnSocketFileListener.onRomInstallFail(error);
            }
        }

        @Override
        public void onRomInstalling(String progress) {

            isUSBTransfer = false;
            isWIFITransfer = false;
            isRomInstalling = true;
            if (mOnSocketFileListener != null) {
                mOnSocketFileListener.onRomInstalling(progress);
            }
        }
    }

    /**
     * 实现有线连接监听
     */
    private class UsbSocketConnListener implements onSocketStatusListener {
        private int mPort;

        public UsbSocketConnListener(int port) {
            this.mPort = port;
        }

        @Override
        public void onSocketUdpInfo(String message) {
        }

        @Override
        public void onSocketConnectSucess(final String connWay) {

            // 如果wifi已经开始传输了，不进行usb传输
            isUSBConn = true;
            mConnTypeSet.add(connWay);

            LogController.i(TAG, "UsbSocketConnListener sucess isUSBTransfer:" + isUSBTransfer + ",isWIFITransfer:" + isWIFITransfer);

            if (isUSBTransfer) {
                continueTransferFile();
            }

            notifySocketConnectSucess(connWay);

        }

        @Override
        public void onSocketConnectFail(final String message) {
            isUSBConn = false;
            isUSBTransfer = false;
            isRomInstalling = false;
            mConnTypeSet.remove(Config.TCP_CONTECT_WAY_USB);
            // usb如果断开了，检测wifi是否连接成功且是否在wifi传输
            LogController.i(TAG, "UsbSocketConnListener fail isUSBTransfer:" + isUSBTransfer + ",isWIFITransfer:" + isWIFITransfer);

            if (!isWIFITransfer) {
                continueTransferFile();
            }

            notifySocketConnectFail(message);
        }

        @Override
        public void onSocketConnectLost(final String connWay) {
            isUSBConn = false;
            isUSBTransfer = false;
            isRomInstalling = false;
            mConnTypeSet.remove(connWay);
            // usb如果断开了，检测wifi是否连接成功且是否在wifi传输
            if (!isWIFITransfer) {
                continueTransferFile();
            }
            notifySocketConnectLost(connWay);
            LogController.i(TAG, "UsbSocketConnListener lost isUSBTransfer:" + isUSBTransfer + ",isWIFITransfer:" + isWIFITransfer);
        }
    }

    /**
     * 无线方式连接监听
     */
    private class WirelessSocketConnListener implements onSocketStatusListener {
        @Override
        public void onSocketUdpInfo(String message) {
            notifySocketUdpInfo(message);
        }

        @Override
        public void onSocketConnectSucess(final String connWay) {
            isWIFIConn = true;
            mConnTypeSet.add(connWay);
            if (isWIFITransfer) {
                continueTransferFile();
            }
            notifySocketConnectSucess(connWay);
            LogController.i(TAG, "WirelessSocketConnListener sucess isUSBTransfer:" + isUSBTransfer + ",isWIFITransfer:" + isWIFITransfer);

        }

        @Override
        public void onSocketConnectFail(final String message) {
            isWIFIConn = false;
            isWIFITransfer = false;
            isRomInstalling = false;
            mConnTypeSet.remove(Config.TCP_CONTECT_WAY_WIFI);
            // 若如果usb已经建立连接，使用usb开始传输文件
            if (!isUSBTransfer) {
                continueTransferFile();
            }
            notifySocketConnectFail(message);
            //重新开始UDP的广播请求
            stopWifiSocketConnection();
            // 重新广播获取服务端的ip地址
            startWifiSocketConnection();
            LogController.i(TAG, "WirelessSocketConnListener fail isUSBTransfer:" + isUSBTransfer + ",isWIFITransfer:" + isWIFITransfer);

        }

        @Override
        public void onSocketConnectLost(final String connWay) {
            isWIFIConn = false;
            isWIFITransfer = false;
            isRomInstalling = false;
            mConnTypeSet.remove(connWay);
            // 若如果usb已经建立连接，使用usb开始传输文件
            if (!isUSBTransfer) {
                continueTransferFile();
            }
            notifySocketConnectLost(connWay);
            //重新开始UDP的广播请求
            stopWifiSocketConnection();
            // 重新广播获取服务端的ip地址
            startWifiSocketConnection();
            LogController.i(TAG, "WirelessSocketConnListener lost isUSBTransfer:" + isUSBTransfer + ",isWIFITransfer:" + isWIFITransfer);
        }
    }

    /**
     * 有线连接使用service进行绑定
     */
    private class MyServiceConnection implements ServiceConnection {
        /**
         * @param name
         * @param service
         */
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

            TCPUsbSocket.LocalBinder binder = (TCPUsbSocket.LocalBinder) service;

            TCPUsbSocket serverService = (TCPUsbSocket) binder.getService();
            mTCPUsbSocket = serverService;

            serverService.addSocketConnStatusListener(new UsbSocketConnListener(Config.TCP_SERVER_SOCKET_PORT));
            serverService.setSocketFileListerner(new SocketFileStatusListener(Config.TCP_CONTECT_WAY_USB));
            serverService.addDataReceivedListener(new DataReceiveListener() {
                @Override
                public void onMessageReceived(int type, String message) {
                    notifyMessageReceived(type, message);
                    LogController.i(TAG, "usb received msg:" + message);
                }

                @Override
                public void onCommandReceived(int command) {

                }
            });
            serverService.connectTcpUsbSocket();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    }


    /**
     * 获取当前的连接方式
     *
     * @return
     */
    private String getConnType() {
        String type = "";
        if (mConnTypeSet != null && mConnTypeSet.size() > 0) {
            Iterator<String> typeIterator = mConnTypeSet.iterator();
            while (typeIterator.hasNext()) {
                type = typeIterator.next();
                break;
            }
        }
        LogController.i(TAG, "request conntype:" + type);
        return type;
    }


    /**
     * 请求文本数据
     *
     * @param message
     * @return true 请求成功 false 请求失败
     */
    public void requestDataByJson(String message, DataSendListener listener) {
        requestDataByJson(message, getConnType(), listener);
    }


    /**
     * socket是否连接成功
     *
     * @return
     */
    public boolean isSocketConnSucess() {
        return isUSBConn || isWIFIConn;
    }


    /**
     * 请求文本数据
     *
     * @param message json格式
     * @param type    有线还是无线连接方式
     * @return true 请求成功 false 请求失败
     */
    public void requestDataByJson(final String message, final String type, final DataSendListener listener) {

        if (Config.TCP_CONTECT_WAY_WIFI.equals(type)) {
            if (mTCPClient != null) {
                mTCPClient.sendTcpData(message, new DataSendListener() {

                    @Override
                    public void onSuccess(final String message) {
                        if (listener != null)
                            listener.onSuccess(message);
                        LogController.i(TAG, "wifi json send sucess,thread:" + Thread.currentThread().getName());
                    }

                    @Override
                    public void onError(final String error) {
                        if (listener != null)
                            listener.onError(error);
                        LogController.i(TAG, "wifi json send fail,thread:" + Thread.currentThread().getName());
                    }

                });
            } else {
                if (listener != null)
                    listener.onError("disconnect wifi tcp socket.");
            }
        } else if (Config.TCP_CONTECT_WAY_USB.equals(type)) {
            if (mTCPUsbSocket != null) {
                mTCPUsbSocket.sendTcpData(message, new DataSendListener() {
                    @Override
                    public void onSuccess(final String message) {
                        if (listener != null)
                            listener.onSuccess(message);
                        LogController.i(TAG, "usb json send sucess,thread:" + Thread.currentThread().getName());

                    }

                    @Override
                    public void onError(final String error) {
                        if (listener != null)
                            listener.onError(error);
                        LogController.i(TAG, "usb json send fail,thread:" + Thread.currentThread().getName());

                    }
                });
            } else {
                if (listener != null)
                    listener.onError("disconnect usb tcp socket.");
            }
        } else {
            if (listener != null)
                listener.onError("disconnect wifi or usb tcp socket.");
        }

        LogController.i(TAG, "request data by json,thread:" + Thread.currentThread().getName());
    }


    /**
     * 传送文件列表
     *
     * @param filePathList
     * @return true 开始上传 false 无法开启上传
     * @throws FileNotFoundException
     */
    public boolean transferFileList(ArrayList<String> filePathList) throws FileNotFoundException {

        Log.i(TAG, "isUSBTransfer:" + isUSBTransfer + ",isWIFITransfer:" + isWIFITransfer);

        if (isUSBTransfer || isWIFITransfer) {
            LogController.i(TAG, "文件正在上传中，请稍后...");
            if (mOnSocketFileListener != null) {
                mOnSocketFileListener.onFileTransferFail("", "transfering");
            }
            return false;
        }
        if (isRomInstalling) {
            LogController.i(TAG, "ROM正在安装中，请稍后...");
            if (mOnSocketFileListener != null) {
                mOnSocketFileListener.onFileTransferFail("", "installing");
            }
            return false;
        }
        // 初始化开始上传文件状态信息
        mHt_FileStatus.clear();
        mFileLists.clear();
        for (String path : filePathList) {
            File file = new File(path);

            mFileLists.add(file);
            mHt_FileStatus.put(file, false);
        }

        // 开始传送文件
        if (isWIFIConn || isUSBConn) {
            if (mFileLists.size() > 0) {
                // 初始化文件安装状态
                isRomInstalling = false;
                boolean isFirstFile = true;
                boolean isLastFile = mFileLists.size() > 1 ? false : true;
                if (isUSBConn) {
                    isUSBTransfer = true;
                    isWIFITransfer = false;
                    transferFile(mFileLists.get(0), isFirstFile, isLastFile);
                } else if (isWIFIConn) {
                    isWIFITransfer = true;
                    isUSBTransfer = false;
                    transferFile(mFileLists.get(0), isFirstFile, isLastFile);
                } else {
                    mHt_FileStatus.clear();
                    mFileLists.clear();
                    return false;
                }
                return true;
            }
        } else {
            mHt_FileStatus.clear();
            mFileLists.clear();
        }

        if (mOnSocketFileListener != null) {
            mOnSocketFileListener.onFileTransferFail("", "disconnect");
        }
        return false;
    }


    /**
     * 当前文件是否在传输
     *
     * @return
     */
    public boolean isFileTransfering() {
        return isUSBTransfer || isWIFITransfer;
    }


    /**
     * 当前rom是否在安装中
     *
     * @return
     */
    public boolean isRomInstalling() {
        return isRomInstalling;
    }


    /**
     * 发送文件
     *
     * @param file 文件
     * @return
     */
    private void transferFile(final File file, final boolean isFirstFile, final boolean isLastFile) {
        // 1.如果有线和无线同时连接，使用有线传输
        // 2.如果仅有一种连接方式，使用当前的连接方式传输，且不主动断开
        if (isUSBTransfer) {
            if (mTCPUsbSocket != null) {
                mTCPUsbSocket.sendTcpFileData(file, isFirstFile, isLastFile, new DataSendListener() {
                    @Override
                    public void onSuccess(String message) {
                        mHt_FileStatus.put(file, true);
                        boolean isFinish = continueTransferFile();
                        if (mOnSocketFileListener != null) {
                            mOnSocketFileListener.onFileTransferSucess(file.getPath());
                        }
                        if (mOnSocketFileListener != null && isFinish) {
                            mOnSocketFileListener.onFileListTransferSucess();
                        }
                        LogController.i(TAG, "usb send file sucess.file:" + file.getName());
                    }

                    @Override
                    public void onError(String error) {
                        //如果发送失败，通知上层
                        mHt_FileStatus.put(file, false);
                        destoryFile();
                        if (mOnSocketFileListener != null) {
                            mOnSocketFileListener.onFileTransferFail(file.getPath(), "failed");
                        }
                        LogController.i(TAG, "usb send file failed.file:" + file.getName());
                    }
                });
            }
        } else if (isWIFITransfer) {
            if (mTCPClient != null) {
                mTCPClient.sendTcpFileData(file, isFirstFile, isLastFile, new DataSendListener() {
                    @Override
                    public void onSuccess(String message) {
                        // 文件发送成功，检测所有的文件是否都上传成功
                        mHt_FileStatus.put(file, true);
                        boolean isFinish = continueTransferFile();
                        if (mOnSocketFileListener != null) {
                            mOnSocketFileListener.onFileTransferSucess(file.getPath());
                        }
                        if (mOnSocketFileListener != null && isFinish) {
                            mOnSocketFileListener.onFileListTransferSucess();
                        }
                        LogController.i(TAG, "wifi send file sucess.file:" + file.getName());
                    }

                    @Override
                    public void onError(String error) {
                        //如果发送失败，通知上层
                        mHt_FileStatus.put(file, false);
                        destoryFile();
                        if (mOnSocketFileListener != null) {
                            mOnSocketFileListener.onFileTransferFail(file.getPath(), "failed");
                        }
                        LogController.i(TAG, "wifi send file failed.file:" + file.getName());
                    }
                });


            }
        } else {
            //如果发送失败，通知上层
            if (mOnSocketFileListener != null) {
                mOnSocketFileListener.onFileTransferFail(file.getPath(), "failed");
            }
        }
    }


    /**
     * 继续上传文件列表中没有上传的文件
     *
     * @return true 文件已全部上传 false 继续上传未传送文件
     */
    private boolean continueTransferFile() {
        if (mFileLists == null || mHt_FileStatus == null) {
            if (!isSocketConnSucess() && mOnSocketFileListener != null) {
                isRomInstalling = false;
                isWIFITransfer = false;
                isUSBTransfer = false;
                mOnSocketFileListener.onFileTransferFail("", "disconnect");
            }
            return false;
        }
        int fileSize = mFileLists.size();
        for (int i = 0; i < fileSize; i++) {
            File myFile = mFileLists.get(i);
            boolean flag = mHt_FileStatus.get(myFile);
            //寻找list中下一个开始上传的文件
            if (!flag) {
                boolean isLastFile = false;
                boolean isFirstFile = false;
                if (i == 0 || fileSize == 1) {
                    isFirstFile = true;
                }
                if (i == fileSize - 1) {
                    isLastFile = true;
                }
                if (isUSBConn) {
                    isUSBTransfer = true;
                    isWIFITransfer = false;
                    transferFile(myFile, isFirstFile, isLastFile);
                } else if (isWIFIConn) {
                    isWIFITransfer = true;
                    isUSBTransfer = false;
                    transferFile(myFile, isFirstFile, isLastFile);
                } else {
                    if (mOnSocketFileListener != null) {
                        mOnSocketFileListener.onFileTransferFail(myFile.getPath(), "disconnect");
                    }
                }
                return false;
            }
        }
        return true;
    }


    /**
     * 销毁有线和无线socket连接
     */
    public void stopSocketConnection() {
        mConnTypeSet.clear();
        isUSBConn = false;
        isWIFIConn = false;
        destoryFile();
        stopWifiSocketConnection();
        stopUsbSocketConnection();
    }

    /**
     * 销毁文件上传变量
     */
    private void destoryFile() {
        mFileLists.clear();
        isUSBTransfer = false;
        isWIFITransfer = false;
        isRomInstalling = false;
    }

    /**
     * 销毁无线socket连接
     */
    private void stopWifiSocketConnection() {
        if (mUDPSocket != null) {
            isStartUDPBroadcast = false;
            mUDPSocket.disconnectUDPSocket();
            mUDPSocket = null;
        }

        if (mUDPReceiveThread != null) {
            mUDPReceiveThread.interrupt();
            mUDPReceiveThread = null;

        }
        if (mUDPSendThread != null) {
            mUDPSendThread.interrupt();
            mUDPSendThread = null;
        }

        if (mTCPClient != null) {
            mTCPClient.disconnectTcpWifiSocket();
            mTCPClient = null;
        }
    }

    /**
     * 销毁有线socket连接
     */
    private void stopUsbSocketConnection() {
        if (mTCPUsbSocket != null) {
            mTCPUsbSocket.disconnectTcpUsbSocket();
            mContext.unbindService(myServiceConnection);
            mTCPUsbSocket = null;
        }
    }

    /**
     * 设置监听端口
     *
     * @param type
     */
    public void setPortType(String type) {
        this.mPortType = type;
    }

    /**
     * UDP 广播线程，获取服务端ip地址和端口
     */
    private class UdpBroadcastRunnable implements Runnable {
        private String mRequestUdpMsg;

        public UdpBroadcastRunnable(String msg) {
            this.mRequestUdpMsg = msg;
        }

        @Override
        public void run() {
            while (isStartUDPBroadcast) {
                mUDPSocket.sendUdpData(mRequestUdpMsg, null);
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void setSocketFileListerner(onSocketFileListener listener) {
        this.mOnSocketFileListener = listener;
    }

    public void addOnSocketStatusListener(onSocketStatusListener listener) {
        mListenerList.clear();
        mListenerList.add(listener);
    }

    public void removeOnSocketStatusListener(onSocketStatusListener listener) {
        mListenerList.remove(listener);
    }


    public void addDataReceivedListener(DataReceiveListener listener) {
        mDataReceivedListenerList.clear();
        mDataReceivedListenerList.add(listener);
    }

    public void removeDataReceivedListener(DataReceiveListener listener) {
        mDataReceivedListenerList.remove(listener);
    }

    private void notifySocketConnectSucess(final String connWay) {
        for (int i = 0; i < mListenerList.size(); i++) {
            mListenerList.get(i).onSocketConnectSucess(connWay);
        }
    }

    private void notifySocketConnectFail(final String message) {
        for (int i = 0; i < mListenerList.size(); i++) {
            mListenerList.get(i).onSocketConnectFail(message);
        }
    }

    private void notifySocketConnectLost(final String connWay) {
        for (int i = 0; i < mListenerList.size(); i++) {
            mListenerList.get(i).onSocketConnectLost(connWay);
        }
    }

    private void notifyMessageReceived(final int type, final String msg) {
        for (int i = 0; i < mDataReceivedListenerList.size(); i++) {
            mDataReceivedListenerList.get(i).onMessageReceived(type, msg);
        }
    }

    private void notifySocketUdpInfo(final String msg) {
        for (int i = 0; i < mListenerList.size(); i++) {
            mListenerList.get(i).onSocketUdpInfo(msg);
        }
    }
}
