package com.carrobot.android.socketconnect.socket;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
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
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

/**
 * Created by fuwei.jiang on 17/1/7.
 */

public class TCPServerService extends Service {

    public static String TAG = TCPServerService.class.getSimpleName();

    private HeartBreakTimer heartBreakTimer;//心跳包计时器
    private long lastRecvTimeStamp = 0;//用于判断检测心跳包时间戳

    private ServerSocket serverSocket = null;
    private ArrayList<onSocketStatusListener> mListenerList = new ArrayList<onSocketStatusListener>();
    private onSocketFileListener mOnSocketFileListener;

    private Thread mThread = null;


    private Socket mSocket = null;
    private boolean isDestroy = false;


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogController.i(TAG, "onStartCommand");
        return START_NOT_STICKY;
    }

    /**
     * 开启serversockt监听
     */
    public void executeServerSocket() {
        if (mThread != null)
            mThread.interrupt();
        mThread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    if (serverSocket == null) {
                        serverSocket = new ServerSocket();
                        serverSocket.setReuseAddress(true);
                        serverSocket.bind(new InetSocketAddress(Config.TCP_SERVER_SOCKET_PORT));
                    }

                    Socket socket = null;
                    while (true) {
                        if (isDestroy)
                            break;
                        socket = serverSocket.accept();

                        try {
                            if (mSocket != null && !mSocket.isClosed()) {
                                mSocket.close();
                                mSocket = null;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            LogController.i(TAG, "e1:" + e.toString());
                        }

                        mSocket = socket;
                        new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true).println("{\"msg\":\"version\"}");
                        new Thread(new TCPServerCommunicationThread(socket)).start();
                        mHandler.sendEmptyMessage(3);

                        // 开启心跳包检测服务

                        if (heartBreakTimer != null)
                            heartBreakTimer.exit();
                        heartBreakTimer = new HeartBreakTimer();
                        heartBreakTimer.start(-1, Config.HEARTBREAK_TIME);

                        LogController.i(TAG, "start timer");

                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Message msg = new Message();
                    msg.what = 4;
                    msg.obj = e.toString();
                    mHandler.sendMessage(msg);
                    LogController.i(TAG, "e2:" + e.toString());


                } finally {
                    if (serverSocket != null) {
                        try {
                            serverSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                            LogController.i(TAG, "e3:" + e.toString());
                        }
                    }
                }
            }
        });
        mThread.start();
    }

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg == null)
                return;
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
                    if (!"pong".equalsIgnoreCase(type))
                        notifyMessageReceived(str);
                    LogController.i(TAG, "usb tcp accept client message：" + str);
                    break;
                }
                case 1: {
                    notifySendSucess();
                    LogController.i(TAG, "usb tcp send to sucess," + "socket connect counts：");
                    break;
                }
                case 2: {
                    Bundle bundle = msg.getData();
                    if (bundle != null) {
                        String type = bundle.getString("type");
                        int error = bundle.getInt("error");
                        if (!Config.TCP_TYPE_HEART.equalsIgnoreCase(type)) ;
                        notifySendError(error);
                        LogController.i(TAG, "usb tcp send to fail, errocode:" + msg.obj);
                    }
                    break;
                }
                case 3: {
                    notifySocketConnectSucess(Config.CONTECT_WAY_USB);
                    LogController.i(TAG, "usb tcp build sucess.");
                    break;
                }
                case 4: {
                    notifySocketConnectFail((String) msg.obj);
                    LogController.i(TAG, "usb tcp fail ex:" + msg.obj);
                    break;
                }
                case 5: {
                    if (heartBreakTimer != null) {
                        heartBreakTimer.exit();
                    }
                    notifySocketConnectLost(Config.CONTECT_WAY_USB);
                    LogController.i(TAG, "usb check heart time ,socket connect lost");
                    break;
                }
                case 7: {
                    break;
                }

                case Config.MAIN_MSG_WHAT_FILE_INSTALL_SUCESS: {
                    if (mOnSocketFileListener != null) {
                        mOnSocketFileListener.onRomInstallSucess();
                    }

                    mTransferFile = null;
                    LogController.i(TAG, "usb file install sucess!");
                    break;
                }
                case Config.MAIN_MSG_WHAT_FILE_INSTALL_FAIL: {
                    if (mOnSocketFileListener != null) {
                        mOnSocketFileListener.onRomInstallFail((String) msg.obj);
                    }
                    mTransferFile = null;
                    LogController.i(TAG, "usb file install fail! ex:" + msg.obj);
                    break;
                }
                case Config.MAIN_MSG_WHAT_FILE_TRANSFER_SUCESS: {
                    if (mOnSocketFileListener != null) {
                        String path = "";
                        if (mTransferFile != null)
                            path = mTransferFile.getPath();
                        mOnSocketFileListener.onFileTransferSucess(path);
                    }
                    LogController.i(TAG, "usb file transfer sucess!");

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

                    LogController.i(TAG, "usb file transfer fail! ex:" + msg.obj);
                    break;
                }

                case Config.MAIN_MSG_WHAT_FILE_INSTALLING: {
                    if (mOnSocketFileListener != null) {
                        mOnSocketFileListener.onRomInstalling((String) msg.obj);
                    }
                    LogController.i(TAG, "usb file installing...");
                    break;
                }
                default:
                    break;

            }
        }
    };

    private final IBinder mBinder = new LocalBinder();


    public class LocalBinder extends Binder {

        TCPServerService getService() {

            return TCPServerService.this;

        }

    }

    @Override
    public IBinder onBind(Intent intent) {
        LogController.i(TAG, "onBind");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        LogController.i(TAG, "onUnbind");
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

        private long mTransferFileTime = 0L;


        @Override
        public void run() {
            try {
                String line = null;
                while ((line = this.bufferedReader.readLine()) != null) {

                    lastRecvTimeStamp = System.currentTimeMillis();

                    LogController.i(TAG, "usb read sucess, line:" + line + ",file:" + mTransferFile);
                    try {
                        JSONObject jsonObject = new JSONObject(line);
                        String msg = jsonObject.optString("msg");
                        String data = jsonObject.optString("data");
                        try {
                            // 开始传送文件
                            if ("upgrade".equalsIgnoreCase(msg)) {
                                //开始下载升级包 {“msg”:”sof”, “name”:”filename”, “len”:filelength}
                                JSONObject sofObject = new JSONObject();
                                if ("ready".equalsIgnoreCase(data)) {
                                    sofObject.put("msg", "sof");
                                    if (mTransferFile != null)
                                        sofObject.put("name", mTransferFile.getName());
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
                                    //正在安装中
                                    String progress = jsonObject.optString("process");
                                    mHandler.sendMessage(mHandler.obtainMessage(Config.MAIN_MSG_WHAT_FILE_INSTALLING, progress));
                                } else if ("install not started".equalsIgnoreCase(data)) {

                                } else {
                                    JSONObject eof = new JSONObject();
                                    eof.put("msg", "eof");

                                    if (mTransferFile != null) {
                                        eof.put("name", mTransferFile.getName());
                                        eof.put("len", mTransferFile.length());
                                    }

                                    LogController.i(TAG, "usb write again msg:" + eof.toString());
                                    printWriter.println(eof.toString());
                                    //结束后重新上传
                                    transferFile(mTransferFile, false, isFinishTransfer);
                                }
                                LogController.i(TAG, "usb write msg:" + sofObject.toString() + ",isFinishTransfer:" + isFinishTransfer);

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
                                LogController.i(TAG, "usb write sof start fileSize:" + mTransferFile.length() + "\n msg:" + downloadJson.toString());
                            } else if ("downloading".equalsIgnoreCase(msg) && mTransferFile != null) {
                                //传输数据 {“msg”:”downloading”, “offset”:1234, “payload”:5678, “data”:[……二进制数据........]}
                                int offset = jsonObject.optInt("offset");
                                int payload = jsonObject.optInt("payload");
                                JSONObject downloadJson = new JSONObject();
                                byte[] buffer = new byte[Config.FILE_MAX_SIZE];
                                RandomAccessFile fileOutStream = null;
                                fileOutStream = new RandomAccessFile(mTransferFile, "r");
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
                                    //重新上传刚才没有上传成功的数据
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
                                    LogController.i(TAG, "usb write downloading again msg:" + downloadJson.toString());

                                } else {
                                    // 如果服务端未返回有效的字段，则关闭之前的并重新上传
                                    JSONObject eof = new JSONObject();
                                    eof.put("msg", "eof");
                                    if (mTransferFile != null) {
                                        eof.put("name", mTransferFile.getName());
                                        eof.put("len", mTransferFile.length());
                                    }
                                    printWriter.println(eof.toString());
                                    LogController.i(TAG, "usb write downloading again msg:" + eof.toString());
                                    //结束后重新上传
                                    transferFile(mTransferFile, false, isFinishTransfer);
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
                                    LogController.i(TAG, "usb write msg:" + eof.toString() + "/n alltime:" + (System.currentTimeMillis() - mTransferFileTime));
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
                                        LogController.i(TAG, "usb write msg:" + sofObject.toString());
                                        isAllowRequestMsgByJson = true;
                                    }
                                    LogController.i(TAG, "usb transfer isFinishTransfer:" + isFinishTransfer);
                                    mHandler.sendEmptyMessage(Config.MAIN_MSG_WHAT_FILE_TRANSFER_SUCESS);
                                } else if ("error".equalsIgnoreCase(data)) {
                                    // 就重传文件,交给上层处理
//                                      transferFile(uploadFile);
                                    mHandler.sendMessage(mHandler.obtainMessage(Config.MAIN_MSG_WHAT_FILE_TRANSFER_FAIL, data));
                                }
                            } else {
                                mHandler.sendMessage(mHandler.obtainMessage(0, line));
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            mHandler.sendMessage(mHandler.obtainMessage(Config.MAIN_MSG_WHAT_FILE_TRANSFER_FAIL, e.toString()));
                            LogController.i(TAG, "usb file ex:" + e.toString());
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                        LogController.i(TAG, "usb read json error:" + e.toString());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                LogController.i(TAG, "usb read ex:" + e.toString());
            }

        }
    }

    private File mTransferFile = null;
    private boolean isFinishTransfer = false;


    private volatile boolean isAllowRequestMsgByJson = true;

    public boolean transferFile(File file, boolean isStart, boolean isFinish) {
        //1.通知开始升级 {“msg”:”upgrade”, “data”:”start"}
        if (file == null)
            return false;
        this.mTransferFile = file;
        this.isFinishTransfer = isFinish;
        if (mSocket == null) {
            LogController.i(TAG, "usb file upgrade socket is null!");
            return false;
        }
        JSONObject jsonObject = new JSONObject();
        try {
            isAllowRequestMsgByJson = false;
            PrintWriter pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(mSocket.getOutputStream())), true);
            if (isStart) {
                jsonObject.put("msg", "upgrade");
                jsonObject.put("data", "start");
            } else {
                jsonObject.put("msg", "sof");
                jsonObject.put("name", file.getName());
            }

            pw.println(jsonObject.toString());
            LogController.i(TAG, "usb file upgrade start :" + jsonObject.toString());
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            LogController.i(TAG, "usb file upgrade start error! ex:" + e.toString());
        }


        return false;
    }


    public boolean sendServerSocketMessage(String json, String type) {
        if (!isAllowRequestMsgByJson) {
            return false;
        }
        PrintWriter pw = null;
        if (mSocket != null) {
            {
                try {
                    pw = new PrintWriter(new PrintWriter(new BufferedWriter(new OutputStreamWriter(mSocket.getOutputStream())), true));
                    pw.println(json);
                    pw.flush();
                    mHandler.sendMessage(mHandler.obtainMessage(1, type));
                    return true;
                } catch (Exception e) {
                    LogController.i(TAG, "es1:" + e.toString());

                    try {
                        mSocket.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                        LogController.i(TAG, "es2:" + e1.toString());

                    }
                    e.printStackTrace();

                    Message message = new Message();
                    message.what = 2;
                    message.getData().putString("type", type);
                    message.getData().putInt("error", Config.TCP_SEND_SOCKET_FAIL_ERROR);
                    mHandler.sendMessage(message);
//                    mHandler.sendEmptyMessage(5);
                    return false;
                }
            }
        }
        return false;
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

    private void notifyMessageReceived(String msg) {
        for (int i = 0; i < mListenerList.size(); i++) {
            mListenerList.get(i).onMessageReceive(msg);
        }
    }

    private void notifySocketConnectSucess(String connWay) {
        for (int i = 0; i < mListenerList.size(); i++) {
            mListenerList.get(i).onSocketConnectSucess(connWay);
        }
    }

    private void notifySocketConnectLost(String connWay) {
        for (int i = 0; i < mListenerList.size(); i++) {
            mListenerList.get(i).onSocketConnectLost(connWay);
        }
    }

    private void notifySocketConnectFail(String message) {

        for (int i = 0; i < mListenerList.size(); i++) {
            mListenerList.get(i).onSocketConnectFail(message);
        }
    }

    public void close() {

        if (mThread != null) {
            mThread.interrupt();
        }

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
        if (mSocket != null && !mSocket.isClosed()) {
            try {
                mSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
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

            if (isDestroy) {
                if (heartBreakTimer != null)
                    heartBreakTimer.exit();
                close();
                return;
            }

            long duration = System.currentTimeMillis() - lastRecvTimeStamp;
            if (duration > Config.HEARTBREAK_NOREPLY_TIME) {
                isAllowRequestMsgByJson = true;
                mHandler.sendEmptyMessage(5);

                LogController.i(TAG, "time out lost");
            } else if (duration > Config.HEARTBREAK_TIME) {
                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("msg", Config.TCP_TYPE_HEART);
                    jsonObject.put("data", "usb");
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                LogController.i(TAG, jsonObject.toString());
                sendServerSocketMessage(jsonObject.toString(), Config.TCP_TYPE_HEART);
            }
        }

        @Override
        public void doTimeOutWork() {

        }
    }

    @Override
    public void onDestroy() {
        isDestroy = true;
        close();
        super.onDestroy();
//        Toast.makeText(TCPServerService.this," service onDestroy",Toast.LENGTH_SHORT).show();

        LogController.i(TAG, "onDestroy");
    }
}
