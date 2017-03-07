package com.carrobot.android.socketconnect.utils;

/**
 * Created by fuwei.jiang on 16/12/29.
 */
public class Config {
    public static final boolean isDebugMode = true;

    public static final int FILE_MAX_SIZE = 104*60*7;
    public static final boolean ENCODE_BASE64 = true;

    /**
     * UDP相关常量
     */
    public static final int UDP_SEND_PORT = 1000;
    public static final int UDP_RECEIVE_PORT = 1026;
    public static String UDP_BROADCAST_IP = "192.168.49.255";

    /**
     * socket接受数据类型
     */
    public static final int TYPE_RECEIVE_UDP = 0;
    public static final int TYPE_RECEIVE_TCP =1;

    /**
     * socket心跳包检测常量值
     */
    public static final int HEARTBREAK_TIME = 1000;
    public static final int HEARTBREAK_NOREPLY_TIME = 10*1000;


    /**
     * socket通信类型
     */
    public  static final String TCP_TYPE_HEART ="ping";

    /**
     * 端口类型 工模or OBD
     */
    public static  final String TCP_PORT_OBD = "tcp_port_obd";
    public static  final String TCP_PORT_FACTORY = "tcp_port_factory";

    /**
     * usb方式连接的监听接口
     */
    public static final int TCP_SERVER_SOCKET_PORT = 9100;


    /**
     * socket连接方式常量值
     */
    public static final String TCP_CONTECT_WAY_WIFI = "TCP_CONTECT_WAY_WIFI";
    public static final String TCP_CONTECT_WAY_USB = "TCP_CONTECT_WAY_USB";

}
