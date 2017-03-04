package com.carrobot.android.socketconnect.socket;

import android.os.Handler;
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
public class UDPClient implements Runnable {

    private static String TAG = "";
    private static final int POOL_SIZE = 5; // 单个CPU线程池大小
    private static final int BUFFERLENGTH = 1024; // 缓冲大小
    private static byte[] sendBuffer = new byte[BUFFERLENGTH];
    private static byte[] receiveBuffer = new byte[BUFFERLENGTH];

    private ArrayList<DataReceiveListener> mDataReceiveListenerList;


    private ExecutorService executor;
    private DatagramSocket mDatagramSocket = null;
    private DatagramPacket mDatagramPacketSend;
    private DatagramPacket mDatagramPacketReceive;

    private Thread receiveUDPThread;
    private boolean isThreadRunning;


    public UDPClient() {
        int cpuNums = Runtime.getRuntime().availableProcessors();
        executor = Executors.newFixedThreadPool(cpuNums * POOL_SIZE); // 根据CPU数目初始化线程池
        mDataReceiveListenerList = new ArrayList<DataReceiveListener>();
    }


    /**
     * 发送UDP消息
     *
     * @param msgSend
     * @return
     */
    public String sendUdpData(String msgSend) {
        InetAddress hostAddress = null;
        try {
            Config.UDP_BROADCAST_IP = Utils.getBroadcastAddress();
            if (Config.UDP_BROADCAST_IP == null) {
                LogController.i(TAG, "broadcast ip address error.");
                return "broadcast ip address error.";
            }
            hostAddress = InetAddress.getByName(Config.UDP_BROADCAST_IP);
            LogController.i(TAG, "udp broadcast address：" + Config.UDP_BROADCAST_IP);
        } catch (UnknownHostException e) {
            LogController.i(TAG, "udp can not find address.");
            e.printStackTrace();
        }

        mDatagramPacketSend = new DatagramPacket(msgSend.getBytes(), msgSend.getBytes().length, hostAddress, Config.UDP_SEND_PORT);
        try {
            if (mDatagramSocket != null)
                mDatagramSocket.send(mDatagramPacketSend);
        } catch (IOException e) {
            e.printStackTrace();
            LogController.i(TAG, "upd send to message fail.");
        }
        return msgSend;
    }

    /**
     * 发送UDP消息
     *
     * @param sendMsg
     * @param dataSendListener
     */
    public void sendUdpData(final String sendMsg, final DataSendListener dataSendListener) {
        if (executor == null) {
            int cpuNums = Runtime.getRuntime().availableProcessors();
            executor = Executors.newFixedThreadPool(cpuNums * POOL_SIZE);
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                InetAddress hostAddress = null;
                try {
                    Config.UDP_BROADCAST_IP = Utils.getBroadcastAddress();
                    if (Config.UDP_BROADCAST_IP == null) {
                        if (dataSendListener != null) {
                            dataSendListener.onError();
                        }
                        LogController.i(TAG, "broadcast ip address error.");
                    }
                    hostAddress = InetAddress.getByName(Config.UDP_BROADCAST_IP);
                    LogController.i(TAG, "udp broadcast address：" + Config.UDP_BROADCAST_IP);
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                    if (dataSendListener != null) {
                        dataSendListener.onError();
                    }
                    LogController.i(TAG, "udp can not find address.");
                }

                mDatagramPacketSend = new DatagramPacket(sendMsg.getBytes(), sendMsg.getBytes().length, hostAddress, Config.UDP_SEND_PORT);
                try {
                    if (mDatagramSocket != null) {
                        mDatagramSocket.send(mDatagramPacketSend);
                        if (dataSendListener != null) {
                            dataSendListener.onSuccess();
                            LogController.i(TAG, "upd send to message sucess.");
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    if (dataSendListener != null) {
                        dataSendListener.onError();
                    }
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

    private void notifyMessageReceived(String msg) {
        for (int i = 0; mDataReceiveListenerList != null && i < mDataReceiveListenerList.size(); i++) {
            DataReceiveListener listener = mDataReceiveListenerList.get(i);
            if (listener != null) {
                listener.onMessageReceived(Config.TYPE_RECEIVE_UDP, msg);
            }
        }
        LogController.d(TAG, " notifyMessageReceived = " + (mDataReceiveListenerList == null ? 0 : mDataReceiveListenerList.size()));
    }
}
