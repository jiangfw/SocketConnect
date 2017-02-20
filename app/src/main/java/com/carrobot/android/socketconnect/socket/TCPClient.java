package com.carrobot.android.socketconnect.socket;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Base64;

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

public class TCPClient {


    public static String TAG = TCPClient.class.getSimpleName();

    private HeartBreakTimer heartBreakTimer;//心跳包计时器
    private long lastRecvTimeStamp = 0;//用于判断检测心跳包时间戳


    private OutputStream outputStream;//输出流向服务端发送数据
    private PrintWriter printWriter;
    private InputStream inputStream;
    private BufferedReader bufferedReader;//输入流获取服务端的数据

    private Socket mSocket;

    private ArrayList<onSocketStatusListener> mListenerList;

    private onSocketFileListener mOnSocketFileListener;

    private String mServerIP;
    private int mServerPort;


    private Thread mThread = null;

    private HandlerThread wifiHandlerThread = new HandlerThread("wifi_thread");
    private Handler wifiController;


    public TCPClient(Context context, String ip, int port) {
        this.mServerIP = ip;
        this.mServerPort = port;

        mListenerList = new ArrayList<onSocketStatusListener>();
        wifiHandlerThread.start();
        wifiController = new Handler(wifiHandlerThread.getLooper());
    }


    public void executeServerSocket() {

        if (mThread != null)
            mThread.interrupt();
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    startConn(mServerIP, mServerPort);
                    String line = "";
                    while ((line = bufferedReader.readLine()) != null) {
                        // 用于检测心跳包
                        lastRecvTimeStamp = System.currentTimeMillis();
                        try {
                            LogController.i(TAG, "wireless read sucess, line:" + line + ",file:" + mTransferFile);
                            JSONObject jsonObject = new JSONObject(line);
                            String msg = jsonObject.optString("msg");
                            String data = jsonObject.optString("data");
                            try {
                                // 开始传送文件
                                if ("upgrade".equalsIgnoreCase(msg)) {
                                    //开始上传升级包 {“msg”:”sof”, “name”:”filename”}
                                    JSONObject sofObject = new JSONObject();
                                    if ("ready".equalsIgnoreCase(data)) {
                                        sofObject.put("msg", "sof");
                                        if (mTransferFile != null) {
                                            sofObject.put("name", mTransferFile.getName());
                                        }
                                        mTransferFileTime = System.currentTimeMillis();
                                        printWriter.println(sofObject.toString());
                                    } else if ("success".equalsIgnoreCase(data)) {
                                        //校验成功
                                        mHandler.sendEmptyMessage(Config.MAIN_MSG_WHAT_FILE_INSTALL_SUCESS);
                                    } else if ("md5error".equalsIgnoreCase(data)) {
                                        //md5error
                                        mHandler.sendMessage(mHandler.obtainMessage(Config.MAIN_MSG_WHAT_FILE_INSTALL_FAIL, data));
                                    } else if ("failed".equalsIgnoreCase(data)) {
                                        //升级失败
                                        mHandler.sendMessage(mHandler.obtainMessage(Config.MAIN_MSG_WHAT_FILE_INSTALL_FAIL, data));
                                    } else if ("installing".equalsIgnoreCase(data)) {
                                        String progress = jsonObject.optString("process");
                                        mHandler.sendMessage(mHandler.obtainMessage(Config.MAIN_MSG_WHAT_FILE_INSTALLING, progress));
                                    } else if ("install not started".equalsIgnoreCase(data)) {

                                    } else {
                                        // 如果服务端未返回有效的字段，则关闭之前的并重新上传
                                        JSONObject eof = new JSONObject();
                                        eof.put("msg", "eof");
                                        if (mTransferFile != null) {
                                            eof.put("name", mTransferFile.getName());
                                            eof.put("len", mTransferFile.length());
                                        }
                                        LogController.i(TAG, "wireless write ready again msg:" + eof.toString());
                                        printWriter.println(eof.toString());
                                        //结束后重新上传
                                        transferFile(mTransferFile, false, isFinishTransfer);
                                    }
                                    LogController.i(TAG, "wireless write msg:" + sofObject.toString() + ",isFinishTransfer:" + isFinishTransfer);
                                } else if ("sof".equalsIgnoreCase(msg) && mTransferFile != null) {
                                    //传输数据 {“msg”:”downloading”, “offset”:1234, “payload”:5678, “data”:[……二进制数据........]}
                                    byte[] buffer = new byte[Config.FILE_MAX_SIZE];
                                    RandomAccessFile fileOutStream = new RandomAccessFile(mTransferFile, "r");
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
                                } else if ("downloading".equalsIgnoreCase(msg) && mTransferFile != null) {
                                    //传输数据 {“msg”:”downloading”, “offset”:1234, “payload”:5678, “data”:[……二进制数据........]}
                                    int offset = jsonObject.optInt("offset");
                                    int payload = jsonObject.optInt("payload");

                                    JSONObject downloadJson = new JSONObject();
                                    byte[] buffer = new byte[Config.FILE_MAX_SIZE];
                                    RandomAccessFile fileOutStream = new RandomAccessFile(mTransferFile, "r");
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
                                        if (mTransferFile != null) {
                                            eof.put("name", mTransferFile.getName());
                                            eof.put("len", mTransferFile.length());
                                        }
                                        printWriter.println(eof.toString());
                                        LogController.i(TAG, "wireless write downloading again msg:" + eof.toString());
                                        //结束后重新上传
                                        transferFile(mTransferFile, isFinishTransfer, false);
                                        return;
                                    }
                                    if (len > 0) {
                                        printWriter.println(downloadJson.toString());
                                    } else {
                                        //升级包传输完成 {“msg”:”eof”, “name”:”filename”}
                                        JSONObject eof = new JSONObject();
                                        eof.put("msg", "eof");
                                        if (mTransferFile != null) {
                                            eof.put("name", mTransferFile.getName());
                                            eof.put("len", mTransferFile.length());
                                        }
                                        printWriter.println(eof.toString());
                                        LogController.i(TAG, "wireless write msg:" + eof.toString() + "/n alltime:" + (System.currentTimeMillis() - mTransferFileTime));

                                    }
                                } else if ("eof".equalsIgnoreCase(msg)) {
                                    // 升级包传输完成
                                    if ("ok".equalsIgnoreCase(data)) {
                                        //如果当前文件是最后一个文件，则通知服务端升级包全部传输完成
                                        if (isFinishTransfer) {

                                            //升级包更新完成 通知 {“msg”:”upgrade”, “data”:”end”}
                                            JSONObject sofObject = new JSONObject();
                                            sofObject.put("msg", "upgrade");
                                            sofObject.put("data", "end");
                                            printWriter.println(sofObject.toString());
                                            LogController.i(TAG, "wireless write msg:" + sofObject.toString());
                                            isAllowRequestMsgByJson = true;
                                        }
                                        LogController.i(TAG, "wireless transfer isFinishTransfer:" + isFinishTransfer);
                                        mHandler.sendEmptyMessage(Config.MAIN_MSG_WHAT_FILE_TRANSFER_SUCESS);

                                    } else if ("error".equalsIgnoreCase(data)) {
                                        // 就重传文件
                                        mHandler.sendMessage(mHandler.obtainMessage(Config.MAIN_MSG_WHAT_FILE_TRANSFER_FAIL, data));
                                    }
                                } else {
                                    mHandler.sendMessage(mHandler.obtainMessage(0, line));
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                mHandler.sendMessage(mHandler.obtainMessage(Config.MAIN_MSG_WHAT_FILE_TRANSFER_FAIL, e.toString()));
                                LogController.i(TAG, "wireless file ex:" + e.toString());
                            }

                        } catch (JSONException e) {
                            e.printStackTrace();
                            LogController.i(TAG, "wireless read json error:" + e.toString());
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    LogController.i(TAG, "wireless read ex:" + e.toString());
                }
            }
        });
        mThread.start();
    }

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0: {
                    String str = (String) msg.obj;
                    String type = "";
                    try {
                        JSONObject jsonObject = new JSONObject(str);
                        if (jsonObject.has("msg"))
                            type = jsonObject.getString("msg");

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    if (!"pong".equalsIgnoreCase(type)) {

                        notifyMessageReceived(str);
                    }
                    LogController.i(TAG, "wireless accept server message：" + str);

                    break;
                }
                case 1: {
                    notifySocketConnectSucess(Config.CONTECT_WAY_WIRELESS);
                    LogController.i(TAG, "wireless build tcp socket sucess.");
                    break;
                }
                case 2: {
                    notifySocketConnectFail((String) msg.obj);
                    LogController.i(TAG, "wireless build tcp socket fail.");
                    break;
                }
                case 3: {
                    if (heartBreakTimer != null) {
                        heartBreakTimer.exit();
                    }
                    notifySocketConnectLost(Config.CONTECT_WAY_WIRELESS);
                    LogController.i(TAG, "wireless check heart time ,socket connect lost");
                    break;
                }
                case 4: {
                    LogController.i(TAG, "wireless send to heart message");
                    break;
                }
                case 5: {
                    if (!Config.TCP_TYPE_HEART.equalsIgnoreCase((String) msg.obj))
                        notifySendSucess();
                    break;
                }
                case 6: {
                    Bundle bundle = msg.getData();
                    if (bundle != null) {
                        String type = bundle.getString("type");
                        int error = bundle.getInt("error");
                        if (!Config.TCP_TYPE_HEART.equalsIgnoreCase(type)) ;
                        notifySendError(error);
                        LogController.i(TAG, "wireless send to fail errocode=" + error);
                    }
                    break;
                }

                case Config.MAIN_MSG_WHAT_FILE_INSTALL_SUCESS: {
                    if (mOnSocketFileListener != null) {
                        mOnSocketFileListener.onRomInstallSucess();
                        mTransferFile = null;
                    }
                    LogController.i(TAG, "wireless file install sucess!");
                    break;
                }
                case Config.MAIN_MSG_WHAT_FILE_INSTALL_FAIL: {
                    if (mOnSocketFileListener != null) {
                        mOnSocketFileListener.onRomInstallFail((String) msg.obj);
                        mTransferFile = null;
                    }
                    LogController.i(TAG, "wireless file install fail! ex:" + msg.obj);
                    break;
                }
                case Config.MAIN_MSG_WHAT_FILE_TRANSFER_SUCESS: {
                    if (mOnSocketFileListener != null) {
                        String path = "";
                        if (mTransferFile != null)
                            path = mTransferFile.getPath();
                        mOnSocketFileListener.onFileTransferSucess(path);
                    }

                    LogController.i(TAG, "wireless file transfer sucess!");
                    break;
                }
                case Config.MAIN_MSG_WHAT_FILE_TRANSFER_FAIL: {
                    if (mOnSocketFileListener != null) {
                        String path = "";
                        if (mTransferFile != null)
                            path = mTransferFile.getPath();
                        mOnSocketFileListener.onFileTransferFail(path, (String) msg.obj);
                    }
                    isAllowRequestMsgByJson = true;
                    LogController.i(TAG, "wireless file transfer fail! ex:" + msg.obj);
                    break;
                }

                case Config.MAIN_MSG_WHAT_FILE_INSTALLING: {
                    if (mOnSocketFileListener != null) {
                        mOnSocketFileListener.onRomInstalling((String) msg.obj);
                    }
                    LogController.i(TAG, "wireless file installing...");
                    break;
                }
                default:
                    break;

            }
        }
    };

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
                mHandler.sendEmptyMessage(3);
            } else if (duration > Config.HEARTBREAK_TIME) {
                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("msg", Config.TCP_TYPE_HEART);
                    jsonObject.put("data", "wifi");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                sendTcpMsg(jsonObject.toString(), Config.TCP_TYPE_HEART);
            }
        }

        @Override
        public void doTimeOutWork() {

        }
    }


    private File mTransferFile = null;
    private boolean isFinishTransfer = false;


    public void notifyStartUpgrade() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("msg", "upgrade");
            jsonObject.put("data", "start");
            printWriter.println(jsonObject.toString());
            LogController.i(TAG, "wireless write msg:" + jsonObject.toString());
        } catch (JSONException e) {
            e.printStackTrace();
            LogController.i(TAG, "wireless file upgrade json start error!");
        } catch (Exception e) {
            e.printStackTrace();
            LogController.i(TAG, "wireless file upgrade start error! ex:" + e.toString());
        }
    }


    public boolean transferFile(File file, boolean isStart, boolean isFinish) {
        //1.通知开始升级 {“msg”:”upgrade”, “data”:”start"}
        this.mTransferFile = file;
        this.isFinishTransfer = isFinish;
        JSONObject jsonObject = new JSONObject();
        try {
            isAllowRequestMsgByJson = false;
            if (isStart) {
                jsonObject.put("msg", "upgrade");
                jsonObject.put("data", "start");
            } else {
                jsonObject.put("msg", "sof");
                jsonObject.put("name", file.getName());
            }
            printWriter.println(jsonObject.toString());
            LogController.i(TAG, "wireless write msg:" + jsonObject.toString());
            return true;
        } catch (JSONException e) {
            e.printStackTrace();
            LogController.i(TAG, "wireless file upgrade json start error!");
        } catch (Exception e) {
            e.printStackTrace();
            LogController.i(TAG, "wireless file upgrade start error! ex:" + e.toString());
        }
        LogController.i(TAG, "wireless file upgrade start ! isStart:" + isStart);

        return false;
    }

    public void notifyStartTransferFile() {

    }


    public void notifyEndTransferFile() {
        try {
            JSONObject eof = new JSONObject();
            eof.put("msg", "eof");
            eof.put("name", mTransferFile.getName());
            eof.put("len", mTransferFile.length());
            LogController.i(TAG, "wireless write ready again msg:" + eof.toString());
            printWriter.println(eof.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private volatile boolean isAllowRequestMsgByJson = true;


    /**
     * 发送TCP数据
     *
     * @param msg
     */
    public boolean sendTcpMsg(String msg, String type) {

        if (!isAllowRequestMsgByJson) {
            return false;
        }
        try {
            if (mSocket != null) {
                printWriter.println(msg.toString());
                mHandler.sendMessage(mHandler.obtainMessage(5, type));
                LogController.i(TAG,"wireless send to sucess msg:"+msg.toString()+",threadName:"+ Thread.currentThread().getName()+",threadId:"+ Thread.currentThread().getId());
                return true;
            } else {
                Message message = new Message();
                message.what = 6;
                message.getData().putString("type", type);
                message.getData().putInt("error", Config.TCP_SEND_SOCKET_NULL_ERROR);
                mHandler.sendMessage(message);
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            Message message = new Message();
            message.what = 6;
            message.getData().putString("type", type);
            message.getData().putInt("error", Config.TCP_SEND_SOCKET_FAIL_ERROR);
            mHandler.sendMessage(message);
            return false;
        }
    }


    /**
     * 关闭连接
     */
    public void stopConn() {

        if (mThread != null)
            mThread.interrupt();

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
            mHandler.sendEmptyMessage(1);
            // 开启心跳包检测服务 暂时屏蔽
            if (heartBreakTimer != null)
                heartBreakTimer.exit();
            heartBreakTimer = new HeartBreakTimer();
            heartBreakTimer.start(-1, Config.HEARTBREAK_TIME);
//            LogController.i(TAG,"========start connection=====bufferedReader=="+bufferedReader+"===");
        } catch (Exception e) {
            e.printStackTrace();
            mHandler.sendMessage(mHandler.obtainMessage(2, e.getMessage()));
        }

    }


    private long mTransferFileTime = 0L;


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

}
