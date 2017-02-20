package com.carrobot.android.socketconnect.utils;

/**
 * Created by fuwei.jiang on 16/12/29.
 */
public class Config {
    public static final boolean isDebugMode = true;

    public static final int FILE_MAX_SIZE = 104*60*7;



    public static final boolean ENCODE_BASE64 = true;

    /**
     * socket心跳包检测常量值
     */
    public static final int HEARTBREAK_TIME = 1000;
    public static final int HEARTBREAK_NOREPLY_TIME = 10*1000;


    /**
     * socket通信类型
     */
    public  static final String TCP_TYPE_APP ="tcp_type_app";
    public  static final String TCP_TYPE_HEART ="ping";
    public  static final String TCP_TYPE_FILE = "file";


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
     * 文本发送错误码
     */
    public static final int TCP_SEND_SOCKET_NULL_ERROR = 400;
    public static final int TCP_SEND_SOCKET_FAIL_ERROR = 401;


    /**
     * socket连接方式常量值
     */
    public static final String CONTECT_WAY_WIRELESS = "CONTECT_WAY_WIRELESS";
    public static final String CONTECT_WAY_USB = "CONTECT_WAY_WIRED";

    /**
     * 文件传输相关回调
     */
    public static final int MAIN_MSG_WHAT_FILE_TRANSFER_SUCESS = 100;
    public static final int MAIN_MSG_WHAT_FILE_TRANSFER_FAIL = 101;
    public static final int MAIN_MSG_WHAT_FILE_INSTALL_SUCESS = 102;
    public static final int MAIN_MSG_WHAT_FILE_INSTALL_FAIL = 103;
    public static final int MAIN_MSG_WHAT_FILE_INSTALLING = 104;


    public static final int ERROR_CODE_SOCKET_LOST = 0;
    public static final int ERROR_CODE_SOCKET_TRANSFER = 1;




}
