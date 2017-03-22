package com.carrobot.android.socketconnect.socket;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Base64;

import com.carrobot.android.socketconnect.listener.DataReceiveListener;
import com.carrobot.android.socketconnect.listener.DataSendListener;
import com.carrobot.android.socketconnect.listener.onSocketFileListener;
import com.carrobot.android.socketconnect.listener.onSocketStatusListener;
import com.carrobot.android.socketconnect.utils.Config;
import com.carrobot.android.socketconnect.utils.LogController;
import com.carrobot.android.socketconnect.utils.TimerCheck;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by fuwei.jiang on 17/1/7.
 */

public class TCPUsbObdSocket extends Service implements Runnable {

    public static String TAG = TCPUsbObdSocket.class.getSimpleName();

    //接口回调更新到主线程处理
    private Handler mHandler = new Handler(Looper.getMainLooper());

    private ServerSocket serverSocket = null;
    private ArrayList<onSocketStatusListener> mListenerList = new ArrayList<>();

    private ArrayList<DataReceiveListener> mDataReceiveListenerList = new ArrayList<>();

    private Thread receiveTcpUsbThread = null;

    private List<Socket> mSocketLists = new ArrayList<Socket>();

    private boolean isDestroy = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogController.i(TAG, "onStartCommand");
        return START_NOT_STICKY;
    }


    public void connectTcpUsbSocket() {
        SocketThreadPool.getSocketThreadPool().post(new Runnable() {
            @Override
            public void run() {
                //1.开启serversocket监听
                if (startConn()) {
                    //2.启动接受消息的线程
                    startTCPUsbSocketThread();
                }
            }
        });
    }

    public void disconnectTcpUsbSocket() {
        SocketThreadPool.getSocketThreadPool().post(new Runnable() {
            @Override
            public void run() {
                //1.关闭serversocket监听
                stopConn();
                //2.关闭接受消息的线程
                stopTCPUsbSocketThread();
            }
        });
    }

    /**
     * 开启server端监听
     */
    private boolean startConn() {
        try {
            if (serverSocket == null) {
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(Config.TCP_SERVER_SOCKET_OBD_PORT));
            }
            LogController.i(TAG, "usb obd startConn(" + Config.TCP_SERVER_SOCKET_OBD_PORT + ") 开始建立USB的TCP链接成功.");
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            LogController.i(TAG, "usb obd startConn(" + Config.TCP_SERVER_SOCKET_OBD_PORT + ") 开始建立USB的TCP链接失败.");
        }
        return false;
    }

    /**
     * 关闭socket链接
     */
    public void stopConn() {
        //断开serversocket链接
        if (serverSocket != null) {
            if (!serverSocket.isClosed()) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                serverSocket = null;
            }
        }
        //断开socket链接
        for (Socket socket : mSocketLists) {
            if (socket != null) {
                try {
                    if (!socket.isClosed())
                        socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                socket = null;
            }
        }
        mSocketLists.clear();
        LogController.i(TAG, "usb obd stopConn() 关闭USB的tcp链接成功.");
    }


    /**
     * 开始监听线程
     **/
    private void startTCPUsbSocketThread() {
        if (receiveTcpUsbThread == null) {
            receiveTcpUsbThread = new Thread(this);
            receiveTcpUsbThread.start();
            LogController.i(TAG, "usb obd startTCPUsbSocketThread() 线程启动成功.");
        }
    }

    /**
     * 暂停监听线程
     **/
    private void stopTCPUsbSocketThread() {
        if (receiveTcpUsbThread != null)
            receiveTcpUsbThread.interrupt();
        receiveTcpUsbThread = null;
        LogController.i(TAG, "usb obd stopTCPUsbSocketThread() 线程停止成功.");
    }


    private final IBinder mBinder = new LocalBinder();

    @Override
    public void run() {
        try {
            Socket socket = null;
            while (true) {
                if (isDestroy)
                    break;
                socket = serverSocket.accept();
                mSocketLists.add(socket);
                //握手通信
                new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true).println("{\"msg\":\"version\"}");
                //建立通信线程
                new Thread(new TCPServerCommunicationThread(socket)).start();
                //通知上层
                notifySocketConnectSucess(Config.TCP_CONTECT_WAY_USB);

                LogController.i(TAG, "usb obd accept tcp socket:" + socket);
            }
        } catch (Exception e) {
            e.printStackTrace();
            notifySocketConnectFail(e.getMessage());
            LogController.i(TAG, "usb obd ex:" + e.toString());
        } finally {
            stopConn();
        }
    }


    public class LocalBinder extends Binder {

        TCPUsbObdSocket getService() {

            return TCPUsbObdSocket.this;

        }

    }

    @Override
    public IBinder onBind(Intent intent) {
        LogController.i(TAG, "usb obd onBind");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        LogController.i(TAG, "usb obd onUnbind");
        return super.onUnbind(intent);
    }

    class TCPServerCommunicationThread implements Runnable {

        Socket mSocket;
        private BufferedReader bufferedReader;
        private PrintWriter printWriter;


        public TCPServerCommunicationThread(Socket socket) {
            this.mSocket = socket;
            try {
                this.bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                this.printWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                String line = null;
                while ((line = this.bufferedReader.readLine()) != null) {
                    // 处理返回的消息
                    notifyMessageReceived(line);
                }
            } catch (Exception e) {
                e.printStackTrace();
                LogController.i(TAG, "usb obd read ex:" + e.toString());
            }

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


    private void notifyMessageReceived(final String message) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < mDataReceiveListenerList.size(); i++) {
                    mDataReceiveListenerList.get(i).onMessageReceived(Config.TYPE_RECEIVE_USB_OBD, message);
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

    @Override
    public void onDestroy() {
        isDestroy = true;
        stopConn();
        super.onDestroy();
        LogController.i(TAG, "usb obd onDestroy");
    }
}
