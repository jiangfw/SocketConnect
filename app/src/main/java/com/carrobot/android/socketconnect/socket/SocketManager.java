package com.carrobot.android.socketconnect.socket;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by fuwei.jiang on 17/1/6.
 */

public class SocketManager {

    public static String TAG = SocketManager.class.getSimpleName();

    private Context mContext;
    private static SocketManager instance;
    // 线程池
    private ExecutorService mExecutorService;

    // 无线wifi方式socket通信，使用socket实现收发
    private UDPClient mUDPClient;
    private TCPClient mTCPClient;
    private boolean isStartUDPBroadcast = true;

    // 有线连接方式socket通信，使用serversocket实现收发
    private TCPServerService mTCPServerService = null;
    private MyServiceConnection myServiceConnection;

    // 存储当前有效的连接方式
    private HashSet<String> mConnTypeSet = new HashSet<>();

    // 保存当前mini的版本号
//    private String mMiniVersion = "";

    // 设置当前默认使用的TCP端口号
    private String mPortType = Config.TCP_PORT_FACTORY;

    // socket连接监听
    private List<onSocketStatusListener> mListenerList = new ArrayList<>();
    // socket文件传输监听
    private onSocketFileListener mOnSocketFileListener = null;

    //start 文件传输使用的变量 start
    private boolean isUSBTransfer = false;
    private boolean isWIFITransfer = false;
    private boolean isUSBConn = false;
    private boolean isWIFIConn = false;
    private boolean isRomInstalling = false;
    private ArrayList<File> mFileLists = new ArrayList<>();
    private Hashtable<File, Boolean> mHt_FileStatus = new Hashtable<>();
    //end 文件传输使用的变量 end


    private HandlerThread socketHandlerThread = new HandlerThread("socket_thread");
	private Handler socketController;

    private SocketManager(Context context) {
        this.mContext = context.getApplicationContext();
        mExecutorService = Executors.newFixedThreadPool(5);
        socketHandlerThread.start();
        socketController = new Handler(socketHandlerThread.getLooper());
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
//        mListenerList.clear();
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
        //建立UPD链接，获取萝卜的ip地址和端口号
        mUDPClient = new UDPClient(mHandler);
        mUDPClient.setUdpLife(true);
        //建立链接
        mExecutorService.execute(mUDPClient);
        //发送udp广播
        isStartUDPBroadcast = true;
        mExecutorService.execute(new UdpBroadcastRunnable("{\n" +
                "     \"msg\":\"reqConn\"\n" +
                "     }"));
    }

    /**
     * 开始建立有线连接
     */
    private void startUsbSocketConnection() {

        //建立有线连接的服务,启动service解决端口重新绑定的问题
        myServiceConnection = new MyServiceConnection();
        mContext.bindService(new Intent(mContext, TCPServerService.class), myServiceConnection, Context.BIND_AUTO_CREATE);

    }

    /**
     * 实现文件传输监听
     */
    private class SocketFileStatusListener implements onSocketFileListener {
        private String mConnType = Config.CONTECT_WAY_WIRELESS;

        private SocketFileStatusListener(String connType) {
            this.mConnType = connType;
        }

        @Override
        public void onFileTransferSucess(String filePath) {
            File file = new File(filePath);
            mHt_FileStatus.put(file, true);

            if (mOnSocketFileListener != null) {
                mOnSocketFileListener.onFileTransferSucess(filePath);
            }
            boolean isFinish = continueTransferFile();
            if (mOnSocketFileListener != null && isFinish) {
                mOnSocketFileListener.onFileListTransferSucess();
            }
        }

        @Override
        public void onFileListTransferSucess() {
            //此处逻辑在onFileTransferSucess中实现
        }

        @Override
        public void onFileTransferFail(String filePath, String message) {
            File file = new File(filePath);
            mHt_FileStatus.put(file, false);
            if (mOnSocketFileListener != null) {
                destoryFile();
                mOnSocketFileListener.onFileTransferFail(filePath, message);
            }
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
        public void onSocketConnectSucess(String connWay) {
            // 如果wifi已经开始传输了，不进行usb传输
            isUSBConn = true;
            mConnTypeSet.add(connWay);

            //连接成功请求版本号
//            String msg = "{\"msg\":\"version\"}";
//            requestDataByJson(msg, Config.CONTECT_WAY_USB);

            LogController.i(TAG, "UsbSocketConnListener sucess isUSBTransfer:" + isUSBTransfer + ",isWIFITransfer:" + isWIFITransfer);


            if (isUSBTransfer) {
                continueTransferFile();
            }

            notifySocketConnectSucess(connWay);
        }

        @Override
        public void onSocketConnectFail(String message) {
            isUSBConn = false;
            isUSBTransfer = false;
            isRomInstalling = false;
            mConnTypeSet.remove(Config.CONTECT_WAY_USB);
            // usb如果断开了，检测wifi是否连接成功且是否在wifi传输
            LogController.i(TAG, "UsbSocketConnListener fail isUSBTransfer:" + isUSBTransfer + ",isWIFITransfer:" + isWIFITransfer);

            if (!isWIFITransfer) {
                continueTransferFile();
            }

            notifySocketConnectFail(message);
        }

        @Override
        public void onSocketConnectLost(String connWay) {
            isUSBConn = false;
            isUSBTransfer = false;
            isRomInstalling = false;
            mConnTypeSet.remove(connWay);

            LogController.i(TAG, "UsbSocketConnListener lost isUSBTransfer:" + isUSBTransfer + ",isWIFITransfer:" + isWIFITransfer);

            // usb如果断开了，检测wifi是否连接成功且是否在wifi传输
            if (!isWIFITransfer) {
                continueTransferFile();
            }
            notifySocketConnectLost(connWay);
        }

        @Override
        public void onMessageReceive(String message) {
            notifyMessageReceived(message);
            // 每次连接成功重置版本号
//            parseMiniRomVersion(message);
        }

        @Override
        public void onSendSuccess() {
            notifySendSucess();
        }

        @Override
        public void onSendError(int code) {

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
        public void onSocketConnectSucess(String connWay) {
            isWIFIConn = true;
            mConnTypeSet.add(connWay);

            //连接成功请求版本号
//            String msg = "{\"msg\":\"version\"}";
//            requestDataByJson(msg, Config.CONTECT_WAY_WIRELESS);

            LogController.i(TAG, "WirelessSocketConnListener sucess isUSBTransfer:" + isUSBTransfer + ",isWIFITransfer:" + isWIFITransfer);


            if (isWIFITransfer) {
                continueTransferFile();
            }

            notifySocketConnectSucess(connWay);
        }

        @Override
        public void onSocketConnectFail(String message) {
            isWIFIConn = false;
            isWIFITransfer = false;
            isRomInstalling = false;
            mConnTypeSet.remove(Config.CONTECT_WAY_WIRELESS);
            LogController.i(TAG, "WirelessSocketConnListener fail isUSBTransfer:" + isUSBTransfer + ",isWIFITransfer:" + isWIFITransfer);

            // 若如果usb已经建立连接，使用usb开始传输文件
            if (!isUSBTransfer) {
                continueTransferFile();
            }
            notifySocketConnectFail(message);
            //重新开始UDP的广播请求
            stopWifiSocketConnection();
            // 重新广播获取服务端的ip地址
            startWifiSocketConnection();
        }

        @Override
        public void onSocketConnectLost(String connWay) {
            isWIFIConn = false;
            isWIFITransfer = false;
            isRomInstalling = false;
            mConnTypeSet.remove(connWay);
            LogController.i(TAG, "WirelessSocketConnListener lost isUSBTransfer:" + isUSBTransfer + ",isWIFITransfer:" + isWIFITransfer);

            // 若如果usb已经建立连接，使用usb开始传输文件
            if (!isUSBTransfer) {
                continueTransferFile();
            }

            notifySocketConnectLost(connWay);
            //重新开始UDP的广播请求
            stopWifiSocketConnection();
            // 重新广播获取服务端的ip地址
            startWifiSocketConnection();
        }

        @Override
        public void onMessageReceive(String message) {
            notifyMessageReceived(message);
            // 每次连接成功重置版本号
//            parseMiniRomVersion(message);
        }

        @Override
        public void onSendSuccess() {
            notifySendSucess();
        }

        @Override
        public void onSendError(int code) {
            notifySendError(code);
            //重新开始UDP的广播请求
            stopWifiSocketConnection();
            // 重新广播获取服务端的ip地址
            startWifiSocketConnection();
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

            TCPServerService.LocalBinder binder = (TCPServerService.LocalBinder) service;

            TCPServerService serverService = (TCPServerService) binder.getService();
            mTCPServerService = serverService;

            serverService.addSocketConnStatusListener(new UsbSocketConnListener(Config.TCP_SERVER_SOCKET_PORT));
            serverService.setSocketFileListerner(new SocketFileStatusListener(Config.CONTECT_WAY_USB));
            serverService.executeServerSocket();
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
    public void requestDataByJson(final String message) {
        if(socketController!=null)
        socketController.post(new Runnable() {
            @Override
            public void run() {
                String type = getConnType();
                if (Config.CONTECT_WAY_WIRELESS.equals(type)) {
                    if (mTCPClient != null) {
                         mTCPClient.sendTcpMsg(message, Config.TCP_TYPE_APP);
                    }
                } else if (Config.CONTECT_WAY_USB.equals(type)) {
                    if (mTCPServerService != null) {
                        mTCPServerService.sendServerSocketMessage(message, Config.TCP_TYPE_APP);
                    }
                }
                LogController.i(TAG,"thread:"+ Thread.currentThread().getName());

            }
        });
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
    public void requestDataByJson(final String message, final String type) {
        if(socketController!=null)
            socketController.post(new Runnable() {
            @Override
            public void run() {
                if (Config.CONTECT_WAY_WIRELESS.equals(type)) {
                    if (mTCPClient != null) {
                        mTCPClient.sendTcpMsg(message, Config.TCP_TYPE_APP);
                    }
                } else if (Config.CONTECT_WAY_USB.equals(type)) {
                    if (mTCPServerService != null) {
                        mTCPServerService.sendServerSocketMessage(message, Config.TCP_TYPE_APP);
                    }
                }

                LogController.i(TAG,"thread:"+ Thread.currentThread().getName());
            }
        });
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
//        boolean ret = true;

        socketController.post(new Runnable() {
            @Override
            public void run() {
                if (isUSBTransfer) {
                    if (!(mTCPServerService != null && mTCPServerService.transferFile(file, isFirstFile, isLastFile))) {
                        //如果发送失败，通知上层
                        if (mOnSocketFileListener != null) {
                            mOnSocketFileListener.onFileTransferFail(file.getPath(), "failed");
                        }
//                ret = false;
                    }
                } else if (isWIFITransfer) {
                    if (!(mTCPClient != null && mTCPClient.transferFile(file, isFirstFile, isLastFile))) {
                        //如果发送失败，通知上层
                        if (mOnSocketFileListener != null) {
                            mOnSocketFileListener.onFileTransferFail(file.getPath(), "failed");
                        }
//                ret = false;
                    }
                } else {
                    //如果发送失败，通知上层
                    if (mOnSocketFileListener != null) {
                        mOnSocketFileListener.onFileTransferFail(file.getPath(), "failed");
                    }
//            ret = false;
                }
//        return ret;
            }
        });

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

        //如何当前rom正在安装中和手机断开连接,提示上层
//        if (mOnSocketFileListener != null && isRomInstalling) {
//            isUSBTransfer = false;
//            isWIFITransfer = false;
//            mOnSocketFileListener.onFileTransferFail("", "disconnect");
//        }
        return true;
    }


    /**
     * 销毁有线和无线socket连接
     */
    public void stopSocketConnection() {
//        mListenerList.clear();
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
//        isUSBConn = false;
//        isWIFIConn = false;
        isRomInstalling = false;
    }

    /**
     * 销毁无线socket连接
     */
    private void stopWifiSocketConnection() {
        if (mUDPClient != null) {
            isStartUDPBroadcast = false;
            mUDPClient.setUdpLife(false);
            mUDPClient = null;
        }
        if (mTCPClient != null) {
            mTCPClient.stopConn();
            mTCPClient = null;
        }
    }

    /**
     * 销毁有线socket连接
     */
    private void stopUsbSocketConnection() {
        if (mTCPServerService != null) {
            mTCPServerService.close();
            mContext.unbindService(myServiceConnection);
            mTCPServerService = null;
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
                mUDPClient.sendUdpData(mRequestUdpMsg);
//                LogController.i(TAG, "udp send to server message:" + mRequestUdpMsg);
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 更新到主线程更新UI
     */
    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                // 接受udp发送的ip地址和端口号,并建立TCP链接
                case 0:
                    String addressAndPort = (String) msg.obj;
                    notifySocketUdpInfo(addressAndPort);
                    LogController.i(TAG, "udp accept server message:" + addressAndPort);
                    int OBDPort = 8888;
                    String ip = "";
                    int factoryPort = 1234;

                    try {
                        JSONObject jsonObject = new JSONObject(addressAndPort);
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
                        if (Config.TCP_PORT_FACTORY.equals(mPortType)) {
                            mTCPClient = new TCPClient(mContext, ip, factoryPort);
                        } else if (Config.TCP_PORT_OBD.equals(mPortType)) {
                            mTCPClient = new TCPClient(mContext, ip, factoryPort);
                        }
                        mTCPClient.addSocketConnStatusListener(new WirelessSocketConnListener());
                        mTCPClient.setSocketFileListerner(new SocketFileStatusListener(Config.CONTECT_WAY_WIRELESS));
                    }

                    mTCPClient.executeServerSocket();
//                    mExecutorService.execute(mTCPClient);
                    //停止UDP的广播请求
                    isStartUDPBroadcast = false;
                    break;
                default:
                    break;
            }
        }
    };


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

    private void notifySendSucess() {
        for (int i = 0; i < mListenerList.size(); i++) {
            mListenerList.get(i).onSendSuccess();
        }
    }

    private void notifySendError(int error) {
        for (int i = 0; i < mListenerList.size(); i++) {
            mListenerList.get(i).onSendError(error);
        }
    }

    private void notifySocketConnectSucess(String connWay) {
        for (int i = 0; i < mListenerList.size(); i++) {
            mListenerList.get(i).onSocketConnectSucess(connWay);
        }
    }

    private void notifySocketConnectFail(String message) {

        for (int i = 0; i < mListenerList.size(); i++) {
            mListenerList.get(i).onSocketConnectFail(message);
        }
    }

    private void notifySocketConnectLost(String connWay) {

        for (int i = 0; i < mListenerList.size(); i++) {
            mListenerList.get(i).onSocketConnectLost(connWay);
        }
    }

    private void notifyMessageReceived(String msg) {
        for (int i = 0; i < mListenerList.size(); i++) {
            mListenerList.get(i).onMessageReceive(msg);
        }
    }

    private void notifySocketUdpInfo(String msg) {
        for (int i = 0; i < mListenerList.size(); i++) {
            mListenerList.get(i).onSocketUdpInfo(msg);
        }
    }
}
