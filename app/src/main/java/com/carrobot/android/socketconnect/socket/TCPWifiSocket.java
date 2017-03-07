package com.carrobot.android.socketconnect.socket;

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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.util.ArrayList;

/**
 * Created by fuwei.jiang on 16/12/27.
 */

public class TCPWifiSocket implements Runnable {


    public static String TAG = TCPWifiSocket.class.getSimpleName();

    private long lastRecvTimeStamp = 0;//用于判断检测心跳包时间戳
    private HeartBreakTimer heartBreakTimer;//心跳包计时器

    private OutputStream outputStream;//输出流向服务端发送数据
    private PrintWriter printWriter;
    private InputStream inputStream;
    private BufferedReader bufferedReader;//输入流获取服务端的数据

    private Socket mSocket;
    private ArrayList<onSocketStatusListener> mListenerList;
    private onSocketFileListener mOnSocketFileListener;
    private ArrayList<DataReceiveListener> mDataReceiveListenerList;

    private Thread receiveTcpWifiThread = null;

    public TCPWifiSocket() {
        mListenerList = new ArrayList<onSocketStatusListener>();
        mDataReceiveListenerList = new ArrayList<DataReceiveListener>();
    }

    /**
     * 建立TCP链接
     *
     * @param ip
     * @param port
     */
    public void connectTcpWifiSocket(String ip, int port) {
        //建立链接
        startConn(ip, port);
        //开启接收tcp消息线程
        startTCPWifiSocketThread();

    }

    /**
     * 关闭TCP链接
     */
    public void disconnectTcpWifiSocket() {
        //关闭接受tcp消息线程
        stopTCPWifiSocketThread();
        //关闭链接
        stopConn();

    }

    /**
     * 开始监听线程
     **/
    private void startTCPWifiSocketThread() {
        if (receiveTcpWifiThread == null) {
            receiveTcpWifiThread = new Thread(this);
            receiveTcpWifiThread.start();
            LogController.i(TAG, "startTCPWifiSocketThread() 线程启动成功.");
        }
    }

    /**
     * 暂停监听线程
     **/
    private void stopTCPWifiSocketThread() {
        if (receiveTcpWifiThread != null)
            receiveTcpWifiThread.interrupt();
        receiveTcpWifiThread = null;
        LogController.i(TAG, "stopTCPWifiSocketThread() 线程停止成功.");
    }

    /**
     * 建立tcp连接
     *
     * @param ip
     * @param port
     */
    private void startConn(String ip, int port) {
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
            // 开启心跳包检测服务 暂时屏蔽
            if (heartBreakTimer != null)
                heartBreakTimer.exit();
            heartBreakTimer = new HeartBreakTimer();
            heartBreakTimer.start(-1, Config.HEARTBREAK_TIME);

            LogController.i(TAG, "startConn(ip:" + ip + ",port:" + port + ") 开始建立WIFI的TCP链接成功.");
        } catch (Exception e) {
            e.printStackTrace();
            notifySocketConnectFail(e.getMessage());
            LogController.i(TAG, "startConn(ip:" + ip + ",port:" + port + ") 开始建立WIFI的TCP链接失败.");
        }
    }

    /**
     * 关闭tcp连接
     */
    private void stopConn() {

        if (heartBreakTimer != null)
            heartBreakTimer.exit();

        try {
            if (mSocket != null && !mSocket.isClosed())
                mSocket.close();
            inputStream.close();
            bufferedReader.close();
            printWriter.close();
            outputStream.close();
            mSocket = null;
            inputStream = null;
            bufferedReader = null;
            printWriter = null;
            outputStream = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
        LogController.i(TAG, "stopConn() 关闭WIFI的tcp链接成功.");
    }


    @Override
    public void run() {
        try {
            String line = "";
            while ((line = bufferedReader.readLine()) != null) {
                // 用于检测心跳包
                lastRecvTimeStamp = System.currentTimeMillis();
                // 处理返回的消息
                handleReceivedMessage(line, mTransferFile, mFileSendListener);
            }
        } catch (Exception e) {
            e.printStackTrace();
            LogController.i(TAG, "wifi read ex:" + e.toString());
        }
    }


    /**
     * 心跳包检测
     */
    private class HeartBreakTimer extends TimerCheck {

        public HeartBreakTimer() {
            lastRecvTimeStamp = System.currentTimeMillis();
        }

        @Override
        public void doTimerCheckWork() {

            long duration = System.currentTimeMillis() - lastRecvTimeStamp;
            if (duration > Config.HEARTBREAK_NOREPLY_TIME) {
                isAllowRequestMsgByJson = true;
                notifySocketConnectLost(Config.TCP_CONTECT_WAY_WIFI);
            } else if (duration > Config.HEARTBREAK_TIME) {
                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("msg", Config.TCP_TYPE_HEART);
                    jsonObject.put("data", "wifi");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                // 发送心跳包消息
                sendTcpData(jsonObject.toString(), null);
            }
        }

        @Override
        public void doTimeOutWork() {

        }
    }

    private File mTransferFile = null;
    private boolean isFinishTransfer = false;
    private DataSendListener mFileSendListener = null;

    /**
     * 发送文件
     *
     * @param file
     * @param isFirst
     * @param isFinish
     * @param listener
     */
    public void sendTcpFileData(final File file, final boolean isFirst, final boolean isFinish, final DataSendListener listener) {

        SocketThreadPool.getSocketThreadPool().post(new Runnable() {
            @Override
            public void run() {
                //1.通知开始升级 {“msg”:”upgrade”, “data”:”start"}
                TCPWifiSocket.this.mTransferFile = file;
                TCPWifiSocket.this.isFinishTransfer = isFinish;
                TCPWifiSocket.this.mFileSendListener = listener;
                JSONObject jsonObject = new JSONObject();
                try {
                    isAllowRequestMsgByJson = false;
                    if (isFirst) {
                        jsonObject.put("msg", "upgrade");
                        jsonObject.put("data", "start");
                    } else {
                        jsonObject.put("msg", "sof");
                        jsonObject.put("name", file.getName());
                    }
                    printWriter.println(jsonObject.toString());

                    if (isFirst) {
                        LogController.i(TAG, "wifi write upgrade start msg:" + jsonObject.toString());
                    } else {
                        LogController.i(TAG, "wifi write file start msg:" + jsonObject.toString());
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    if (listener != null) {
                        listener.onError("json error");
                    }
                    LogController.i(TAG, "wifi file upgrade start json error!");
                } catch (Exception e) {
                    e.printStackTrace();
                    if (listener != null) {
                        listener.onError("wifi write file upgrade start failed error");
                    }
                    LogController.i(TAG, "wifi file upgrade start failed error! \n ex:" + e.toString());
                }
            }
        });
    }

    /**
     * 处理收到的消息
     *
     * @param receiveMsg
     * @param file
     * @param listener
     */
    private void handleReceivedMessage(String receiveMsg, File file, DataSendListener listener) {

        try {
            String fileName = (file == null) ? "" : file.getName();
            LogController.i(TAG, "wifi read sucess, line:" + receiveMsg + "\n" + fileName);
            JSONObject jsonObject = new JSONObject(receiveMsg);
            String msg = jsonObject.optString("msg");
            String data = jsonObject.optString("data");
            try {
                // 开始传送文件
                if ("upgrade".equalsIgnoreCase(msg)) {
                    //开始上传升级包 {“msg”:”sof”, “name”:”filename”}
                    JSONObject sofObject = new JSONObject();
                    if ("ready".equalsIgnoreCase(data) || "upgrade already started".equalsIgnoreCase(data)) {
                        sofObject.put("msg", "sof");
                        sofObject.put("name", fileName);
                        if (file != null)
                            sofObject.put("len", file.length());
                        printWriter.println(sofObject.toString());
                        LogController.i(TAG, "wifi write file start msg:" + sofObject.toString());
                    } else if ("success".equalsIgnoreCase(data)) {
                        //校验成功
                        if (mOnSocketFileListener != null)
                            mOnSocketFileListener.onRomInstallSucess();
                    } else if ("installing".equalsIgnoreCase(data)) {
                        String progress = jsonObject.optString("process");
                        if (mOnSocketFileListener != null)
                            mOnSocketFileListener.onRomInstalling(progress);
                    } else {
                        // md5error,failed...
                        if (mOnSocketFileListener != null)
                            mOnSocketFileListener.onRomInstallFail(data);
                    }
                } else if ("sof".equalsIgnoreCase(msg) && file != null) {
                    //传输数据 {“msg”:”downloading”, “offset”:1234, “payload”:5678, “data”:[……二进制数据........]}
                    byte[] buffer = new byte[Config.FILE_MAX_SIZE];
                    RandomAccessFile fileOutStream = new RandomAccessFile(file, "r");
                    fileOutStream.seek(0);
                    int len = fileOutStream.read(buffer);
                    //重置上传二进制流的起始位置
                    int offset = 0;
                    int payload = len;

                    JSONObject downloadJson = new JSONObject();
                    downloadJson.put("msg", "downloading");
                    downloadJson.put("offset", offset);
                    downloadJson.put("payload", payload);
                    if (Config.ENCODE_BASE64) {
                        String str = new String(Base64.encode(buffer, 0, len, Base64.DEFAULT));
                        str = str.replaceAll("\n", "");
                        downloadJson.put("data", str);
                    } else {
                        JSONArray array = new JSONArray();
                        for (int i = 0; i < len; i++) {
                            array.put(buffer[i]);
                        }
                        downloadJson.put("data", array);
                    }
                    printWriter.println(downloadJson.toString());
                } else if ("downloading".equalsIgnoreCase(msg) && file != null) {
                    //传输数据 {“msg”:”downloading”, “offset”:1234, “payload”:5678, “data”:[……二进制数据........]}
                    int offset = jsonObject.optInt("offset");
                    int payload = jsonObject.optInt("payload");

                    JSONObject downloadJson = new JSONObject();
                    byte[] buffer = new byte[Config.FILE_MAX_SIZE];
                    RandomAccessFile fileOutStream = new RandomAccessFile(file, "r");
                    int len = -1;

                    if ("ok".equalsIgnoreCase(data)) {
                        //重置上传二进制流的起始位置
                        offset = offset + payload;
                        fileOutStream.seek(offset);
                        len = fileOutStream.read(buffer);
                        payload = len;
                        downloadJson.put("msg", "downloading");
                        downloadJson.put("offset", offset);
                        downloadJson.put("payload", payload);
                        if (Config.ENCODE_BASE64) {
                            String str = new String(Base64.encode(buffer, 0, len, Base64.DEFAULT));
                            str = str.replaceAll("\n", "");
                            downloadJson.put("data", str);
                        } else {
                            JSONArray array = new JSONArray();
                            for (int i = 0; i < len; i++) {
                                array.put(buffer[i]);
                            }
                            downloadJson.put("data", array);
                        }
                    } else if ("error".equalsIgnoreCase(data)) {
                        //重新上传刚才的上传的数据
                        fileOutStream.seek(offset);
                        len = fileOutStream.read(buffer);
                        payload = len;
                        downloadJson.put("msg", "downloading");
                        downloadJson.put("offset", offset);
                        downloadJson.put("payload", payload);
                        if (Config.ENCODE_BASE64) {
                            String str = new String(Base64.encode(buffer, 0, len, Base64.DEFAULT));
                            str = str.replaceAll("\n", "");
                            downloadJson.put("data", str);
                        } else {
                            JSONArray array = new JSONArray();
                            for (int i = 0; i < len; i++) {
                                array.put(buffer[i]);
                            }
                            downloadJson.put("data", array);
                        }
                    } else {
                        // 如果服务端未返回有效的字段，则关闭之前的并重新上传
                        JSONObject eof = new JSONObject();
                        eof.put("msg", "eof");
                        eof.put("name", fileName);
                        if (file != null) {
                            eof.put("len", file.length());
                        }
                        printWriter.println(eof.toString());
                        LogController.i(TAG, "wifi write file downloading failed msg:" + eof.toString());
                        //结束后重新上传
                        sendTcpFileData(file, false, isFinishTransfer, listener);
                        return;
                    }
                    if (len > 0) {
                        printWriter.println(downloadJson.toString());
                    } else {
                        //升级包传输完成 {“msg”:”eof”, “name”:”filename”}
                        JSONObject eof = new JSONObject();
                        eof.put("msg", "eof");
                        eof.put("name", fileName);
                        if (file != null) {
                            eof.put("len", file.length());
                        }
                        printWriter.println(eof.toString());
                        LogController.i(TAG, "wifi write file msg:" + eof.toString());
                    }
                } else if ("eof".equalsIgnoreCase(msg)) {
                    String filePath = (file == null) ? "" : file.getPath();
                    // 升级包传输完成
                    if ("ok".equalsIgnoreCase(data)) {
                        if (listener != null)
                            listener.onSuccess(filePath);
                        //如果当前文件是最后一个文件，则通知服务端升级包全部传输完成
                        if (isFinishTransfer) {
                            //升级包更新完成 通知 {“msg”:”upgrade”, “data”:”end”}
                            JSONObject sofObject = new JSONObject();
                            sofObject.put("msg", "upgrade");
                            sofObject.put("data", "end");
                            printWriter.println(sofObject.toString());
                            isAllowRequestMsgByJson = true;
                            LogController.i(TAG, "wifi write upgrade end msg:" + sofObject.toString());
                        }
                        LogController.i(TAG, "wifi wirte file msg sucess! isFinishTransfer:" + isFinishTransfer);
                    } else if ("error".equalsIgnoreCase(data)) {
                        // 通知上层文件传输失败
                        if (listener != null)
                            listener.onError(filePath);
                        isAllowRequestMsgByJson = true;
                    }
                } else {
                    notifyDataReceived(receiveMsg);
                }
            } catch (Exception e) {
                e.printStackTrace();
                // 通知上层文件传输失败
                String filePath = (file == null) ? "" : file.getPath();
                if (listener != null)
                    listener.onError(filePath);
                isAllowRequestMsgByJson = true;
                LogController.i(TAG, "wifi file ex:" + e.toString());
            }
        } catch (JSONException e) {
            e.printStackTrace();
            LogController.i(TAG, "wifi read json error:" + e.toString());
        }

    }

    private volatile boolean isAllowRequestMsgByJson = true;


    /**
     * 发送tcp数据
     *
     * @param listener
     */
    public void sendTcpData(final String msg, final DataSendListener listener) {
        SocketThreadPool.getSocketThreadPool().post(new Runnable() {
            @Override
            public void run() {

                if (!isAllowRequestMsgByJson) {
                    if (listener != null)
                        listener.onError("wifi file transfering...");
                    LogController.i(TAG, "wifi file transfering:" + msg.toString() + ",threadName:" + Thread.currentThread().getName());
                    return;
                }
                try {

                    printWriter.println(msg.toString());
                    if (listener != null) {
                        listener.onSuccess(msg);
                    }
                    LogController.i(TAG, "wifi send to sucess msg:" + msg.toString() + ",threadName:" + Thread.currentThread().getName());
                } catch (Exception e) {
                    e.printStackTrace();
                    if (listener != null) {
                        listener.onError("wifi send fail error.");
                    }
                    LogController.i(TAG, "wifi send to fail msg:" + e.getMessage() + ",threadName:" + Thread.currentThread().getName());
                }
            }
        });
    }

    public void setSocketFileListerner(onSocketFileListener listener) {
        this.mOnSocketFileListener = listener;
    }


    public void addSocketConnStatusListener(onSocketStatusListener listener) {
        this.mListenerList.clear();
        this.mListenerList.add(listener);
    }

    public void removeSocketConnStatusListener(onSocketStatusListener listener) {
        this.mListenerList.remove(listener);
    }

    public void addDataReceivedListener(DataReceiveListener listener) {
        this.mDataReceiveListenerList.clear();
        this.mDataReceiveListenerList.add(listener);
    }

    public void removeDataReceivedListener(DataReceiveListener listener) {
        this.mDataReceiveListenerList.remove(listener);
    }

    private void notifyDataReceived(String message) {
        // 心跳包的消息传递到上层
        String type = "";
        try {
            JSONObject jsonObject = new JSONObject(message);
            type = jsonObject.optString("msg");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (!"pong".equalsIgnoreCase(type)) {
            for (int i = 0; i < mDataReceiveListenerList.size(); i++) {
                mDataReceiveListenerList.get(i).onMessageReceived(Config.TYPE_RECEIVE_TCP, message);
            }
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

        if (heartBreakTimer != null) {
            heartBreakTimer.exit();
        }
        for (int i = 0; i < mListenerList.size(); i++) {
            mListenerList.get(i).onSocketConnectLost(connWay);
        }
    }
}
