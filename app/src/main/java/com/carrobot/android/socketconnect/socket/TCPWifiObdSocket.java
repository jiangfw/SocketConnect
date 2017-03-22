package com.carrobot.android.socketconnect.socket;

import android.os.Handler;
import android.os.Looper;

import com.carrobot.android.socketconnect.listener.DataReceiveListener;
import com.carrobot.android.socketconnect.listener.onSocketStatusListener;
import com.carrobot.android.socketconnect.utils.Config;
import com.carrobot.android.socketconnect.utils.LogController;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;

/**
 * Created by fuwei.jiang on 16/12/27.
 */

public class TCPWifiObdSocket implements Runnable {


    public static String TAG = TCPWifiObdSocket.class.getSimpleName();

    private OutputStream outputStream;//输出流向服务端发送数据
    private PrintWriter printWriter;
    private InputStream inputStream;
    private BufferedReader bufferedReader;//输入流获取服务端的数据

    private Socket mSocket;
    private ArrayList<onSocketStatusListener> mListenerList;
    private ArrayList<DataReceiveListener> mDataReceiveListenerList;

    private Thread receiveTcpWifiThread = null;

    //接口回调更新到主线程处理
    private Handler mHandler = new Handler(Looper.getMainLooper());

    public TCPWifiObdSocket() {
        mListenerList = new ArrayList<>();
        mDataReceiveListenerList = new ArrayList<>();
    }

    /**
     * 建立TCP链接
     *
     * @param ip
     * @param port
     */
    public void connectTcpWifiSocket(final String ip, final int port) {

        SocketThreadPool.getSocketThreadPool().post(new Runnable() {
            @Override
            public void run() {
                //建立链接
                if (startConn(ip, port)) {
                    //开启接收tcp消息线程
                    startTCPWifiObdSocketThread();
                }
            }
        });
    }

    /**
     * 关闭TCP链接
     */
    public void disconnectTcpWifiSocket() {

        SocketThreadPool.getSocketThreadPool().post(new Runnable() {
            @Override
            public void run() {
                //关闭接受tcp消息线程
                stopTCPWifiObdSocketThread();
                //关闭链接
                stopConn();
            }
        });
    }

    /**
     * 开始监听线程
     **/
    private void startTCPWifiObdSocketThread() {
        if (receiveTcpWifiThread == null) {
            receiveTcpWifiThread = new Thread(this);
            receiveTcpWifiThread.start();
            LogController.i(TAG, "startTCPWifiObdSocketThread() 线程启动成功.");
        }
    }

    /**
     * 暂停监听线程
     **/
    private void stopTCPWifiObdSocketThread() {
        if (receiveTcpWifiThread != null)
            receiveTcpWifiThread.interrupt();
        receiveTcpWifiThread = null;
        LogController.i(TAG, "stopTCPWifiObdSocketThread() 线程停止成功.");
    }

    /**
     * 建立tcp连接
     *
     * @param ip
     * @param port
     */
    private boolean startConn(final String ip, final int port) {
        try {
            if (mSocket == null) {
                mSocket = new Socket(ip, port);
                mSocket.setKeepAlive(true);
                mSocket.setTcpNoDelay(true);
                mSocket.setReuseAddress(true);
            }
            inputStream = mSocket.getInputStream();
            bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            outputStream = mSocket.getOutputStream();
            printWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(outputStream)), true);
            //通知socket连接成功
            notifySocketConnectSucess(Config.TCP_CONTECT_WAY_WIFI);

            LogController.i(TAG, "wifi obd startConn(ip:" + ip + ",port:" + port + ") 开始建立WIFI的TCP链接成功.");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            notifySocketConnectFail(e.getMessage());
            LogController.i(TAG, "wif obd startConn(ip:" + ip + ",port:" + port + ") 开始建立WIFI的TCP链接失败.\nex:" + e.getMessage());
        }
        return false;
    }

    /**
     * 关闭tcp连接
     */
    private void stopConn() {

        try {
            if (mSocket != null && !mSocket.isClosed())
                mSocket.close();
            if (inputStream != null)
                inputStream.close();
            if (bufferedReader != null)
                bufferedReader.close();
            if (printWriter != null)
                printWriter.close();
            if (outputStream != null)
                outputStream.close();
            mSocket = null;
            inputStream = null;
            bufferedReader = null;
            printWriter = null;
            outputStream = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
        LogController.i(TAG, "wifi obd stopConn() 关闭WIFI的tcp链接成功.");
    }


    @Override
    public void run() {
        try {
            String line = "";
            while ((line = bufferedReader.readLine()) != null) {
                // 处理返回的消息
                notifyDataReceived(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
            notifySocketConnectFail(e.getMessage());
            LogController.i(TAG, "wifi obd read ex:" + e.toString());
        }
    }

    public void addSocketConnStatusListener(onSocketStatusListener listener) {
        this.mListenerList.add(listener);
    }

    public void removeSocketConnStatusListener(onSocketStatusListener listener) {
        this.mListenerList.remove(listener);
    }

    public void addDataReceivedListener(DataReceiveListener listener) {
        this.mDataReceiveListenerList.add(listener);
    }

    public void removeDataReceivedListener(DataReceiveListener listener) {
        this.mDataReceiveListenerList.remove(listener);
    }


    private void notifyDataReceived(final String message) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < mDataReceiveListenerList.size(); i++) {
                    mDataReceiveListenerList.get(i).onMessageReceived(Config.TYPE_RECEIVE_TCP, message);
                }
            }
        });
    }

    private void notifySocketConnectSucess(final String connWay) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < mListenerList.size(); i++) {
                    mListenerList.get(i).onSocketConnectSucess(connWay);
                }
            }
        });
    }

    private void notifySocketConnectFail(final String message) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < mListenerList.size(); i++) {
                    mListenerList.get(i).onSocketConnectFail(message);
                }
            }
        });
    }
}
