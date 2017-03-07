package com.carrobot.android.socketconnect.socket;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.carrobot.android.socketconnect.listener.DataReceiveListener;
import com.carrobot.android.socketconnect.listener.DataSendListener;
import com.carrobot.android.socketconnect.utils.Config;
import com.carrobot.android.socketconnect.utils.LogController;
import com.carrobot.android.socketconnect.utils.Utils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by fuwei.jiang on 16/12/27.
 */
public class UDPSocket implements Runnable {

    private static String TAG = UDPSocket.class.getSimpleName();
    private static final int BUFFERLENGTH = 1024; // 缓冲大小
    private static byte[] receiveBuffer = new byte[BUFFERLENGTH];

    private ArrayList<DataReceiveListener> mDataReceiveListenerList;

    //接口回调更新到主线程处理
    private Handler mHandler = new Handler(Looper.getMainLooper());

    private DatagramSocket mDatagramSocket = null;
    private DatagramPacket mDatagramPacketSend;
    private DatagramPacket mDatagramPacketReceive;

    private Thread receiveUDPThread;
    private boolean isThreadRunning;


    public UDPSocket() {
        mDataReceiveListenerList = new ArrayList<DataReceiveListener>();
    }

    /**
     * 发送UDP消息
     *
     * @param sendMsg
     * @param dataSendListener
     */
    public void sendUdpData(final String sendMsg, final DataSendListener dataSendListener) {
        SocketThreadPool.getSocketThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                InetAddress hostAddress = null;
                try {
                    Config.UDP_BROADCAST_IP = Utils.getBroadcastAddress();
                    if (Config.UDP_BROADCAST_IP == null) {
                        onSendError(dataSendListener, "broadcast ip address error");
                        LogController.i(TAG, "broadcast ip address error.");
                    }
                    hostAddress = InetAddress.getByName(Config.UDP_BROADCAST_IP);
                    LogController.i(TAG, "udp broadcast address：" + Config.UDP_BROADCAST_IP);
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                    onSendError(dataSendListener, "unknown host not find address");
                    LogController.i(TAG, "udp can not find address.");
                }

                mDatagramPacketSend = new DatagramPacket(sendMsg.getBytes(), sendMsg.getBytes().length, hostAddress, Config.UDP_SEND_PORT);
                try {
                    if (mDatagramSocket != null) {
                        mDatagramSocket.send(mDatagramPacketSend);
                        onSendSucess(dataSendListener, sendMsg);
                        LogController.i(TAG, "upd send to message sucess.");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    onSendError(dataSendListener, "send msg fail");
                    LogController.i(TAG, "upd send to message fail.");
                }
            }
        });
    }


    /**
     * 接受UDP数据
     */
    @Override
    public void run() {

        while (isThreadRunning) {
            LogController.i(TAG, "==udp receive message start.==");
            try {
                mDatagramSocket.receive(mDatagramPacketReceive);
            } catch (IOException e) {
                e.printStackTrace();
                isThreadRunning = false;
                mDatagramPacketReceive = null;
                if (mDatagramSocket != null) {
                    mDatagramSocket.close();
                    mDatagramSocket = null;
                }
                receiveUDPThread = null;
                LogController.i(TAG, "udp receive message fail.");
                e.printStackTrace();
                return;
            }
            String receiveMsg = new String(mDatagramPacketReceive.getData(), mDatagramPacketReceive.getOffset(), mDatagramPacketReceive.getLength());
            //向外界抛出收到的消息
            notifyMessageReceived(receiveMsg);
            // 每次接收完UDP数据后，重置长度。否则可能会导致下次收到数据包被截断。
            if (mDatagramPacketReceive != null) {
                mDatagramPacketReceive.setLength(BUFFERLENGTH);
            }
            LogController.i(TAG, "udp receive messag:" + receiveMsg);
        }
        LogController.i(TAG, "==udp receive message end.==");
        if (mDatagramSocket != null)
            mDatagramSocket.close();
        mDatagramSocket = null;
    }


    /**
     * 连接udp
     */
    public void connectUDPSocket() {
        try {
            // 绑定端口
            if (mDatagramSocket == null) {
                mDatagramSocket = new DatagramSocket(null);
                mDatagramSocket.setReuseAddress(true);
                mDatagramSocket.bind(new InetSocketAddress(Config.UDP_RECEIVE_PORT));
                LogController.i(TAG, "udp init");
            }
            // 创建数据接受包
            if (mDatagramPacketReceive == null)
                mDatagramPacketReceive = new DatagramPacket(receiveBuffer, BUFFERLENGTH);

            // 开启接受消息线程
            startUDPSocketThread();
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    public void disconnectUDPSocket() {

        stopUDPSocketThread();
        if (mDatagramSocket != null) {
            mDatagramSocket.close();
            mDatagramSocket = null;
        }
    }

    /**
     * 开始监听线程
     **/
    private void startUDPSocketThread() {
        if (receiveUDPThread == null) {
            receiveUDPThread = new Thread(this);

            isThreadRunning = true;
            receiveUDPThread.start();

            LogController.i(TAG, "startUDPSocketThread() 线程启动成功");
        }
    }

    /**
     * 暂停监听线程
     **/
    private void stopUDPSocketThread() {
        isThreadRunning = false;
        if (receiveUDPThread != null)
            receiveUDPThread.interrupt();
        receiveUDPThread = null;
        LogController.i(TAG, "stopUDPSocketThread() 线程停止成功");
    }

    public void addDataReceiverListener(DataReceiveListener dataReceiveListener) {
        if (mDataReceiveListenerList == null) {
            mDataReceiveListenerList = new ArrayList<DataReceiveListener>();
        }
        this.mDataReceiveListenerList.add(dataReceiveListener);
        LogController.d(TAG, "addDataReceiverListener");
    }

    public void removeDataReceiverListener(DataReceiveListener dataReceiveListener) {
        if (mDataReceiveListenerList == null) {
            mDataReceiveListenerList = new ArrayList<DataReceiveListener>();
        }
        this.mDataReceiveListenerList.remove(dataReceiveListener);
        LogController.d(TAG, "removeDataReceiverListener");
    }


    private void onSendError(final DataSendListener listener, final String error) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null)
                    listener.onError(error);
            }
        });

    }

    private void onSendSucess(final DataSendListener listener, final String message) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onSuccess(message);
                }
            }
        });
    }


    private void notifyMessageReceived(final String msg) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; mDataReceiveListenerList != null && i < mDataReceiveListenerList.size(); i++) {
                    DataReceiveListener listener = mDataReceiveListenerList.get(i);
                    if (listener != null) {
                        listener.onMessageReceived(Config.TYPE_RECEIVE_UDP, msg);
                    }
                }
                LogController.d(TAG, " notifyMessageReceived = " + (mDataReceiveListenerList == null ? 0 : mDataReceiveListenerList.size()));
            }
        });
    }
}
