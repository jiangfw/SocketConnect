package com.carrobot.android.socketconnect.socket;

import android.os.Handler;
import android.os.Message;

import com.carrobot.android.socketconnect.utils.LogController;
import com.carrobot.android.socketconnect.utils.Utils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

/**
 * Created by fuwei.jiang on 16/12/27.
 */
public class UDPClient implements Runnable {
    
    private static String TAG = UDPClient.class.getSimpleName();
    
    private static int SEND_UDP_PORT = 1000;
    private static int RECEIVE_UDP_PORT = 1026;
    private static String BROADCAST_IP = "192.168.49.255";
    
    private static DatagramSocket mDatagramSocket = null;
    private static DatagramPacket mDatagramPacketSend, mDatagramPacketReceive;
    private boolean mUdpLife = true;

    private Handler mHandler;

    public UDPClient(Handler handler) {
        super();
        this.mHandler = handler;
    }


    /**
     * 设置UDP是否获取数据
     *
     * @param b
     */
    public void setUdpLife(boolean b) {
        mUdpLife = b;
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
            BROADCAST_IP = Utils.getBroadcastAddress();
            if(BROADCAST_IP == null){
                LogController.i(TAG,"broadcast ip address error.");
                return "broadcast ip address error.";
            }
            LogController.i(TAG, "udp broadcast address：" + BROADCAST_IP);
            hostAddress = InetAddress.getByName(BROADCAST_IP);
        } catch (UnknownHostException e) {
            LogController.i(TAG, "udp can not find address.");
            e.printStackTrace();
        }

        mDatagramPacketSend = new DatagramPacket(msgSend.getBytes(), msgSend.getBytes().length, hostAddress, SEND_UDP_PORT);

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
     * 接受UDP数据
     */
    @Override
    public void run() {

        try {
            if (mDatagramSocket == null) {
                mDatagramSocket = new DatagramSocket(null);
                mDatagramSocket.setReuseAddress(true);
                mDatagramSocket.bind(new InetSocketAddress(RECEIVE_UDP_PORT));
                LogController.i(TAG, "udp init");

            }
            byte[] msgRcv = new byte[1024];

            mDatagramPacketReceive = new DatagramPacket(msgRcv, msgRcv.length);
            while (mUdpLife) {
                LogController.i(TAG, "udp read message start");
                mDatagramSocket.receive(mDatagramPacketReceive);
                String RcvMsg = new String(mDatagramPacketReceive.getData(), mDatagramPacketReceive.getOffset(), mDatagramPacketReceive.getLength());
                //将收到的消息发给主界面
                Message msg = new Message();
                msg.what = 0;
                msg.obj = RcvMsg;
                mHandler.sendMessage(msg);
                LogController.i(TAG, "udp receive messag:"+RcvMsg);

                mUdpLife = false;

            }

            LogController.i(TAG, "udp read message end.");
            mDatagramSocket.close();
            mDatagramSocket = null;
        }  catch (Exception e) {
            e.printStackTrace();
            if(mDatagramSocket!=null){
                mDatagramSocket.close();
                mDatagramSocket = null;
            }
            LogController.i(TAG, "udp connect fail,message:"+e.getMessage());

        }
    }
}
