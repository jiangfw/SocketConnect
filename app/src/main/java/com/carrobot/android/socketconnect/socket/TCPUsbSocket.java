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

public class TCPUsbSocket extends Service implements Runnable {

    public static String TAG = TCPUsbSocket.class.getSimpleName();

    private HeartBreakTimer heartBreakTimer;//心跳包计时器
    private long lastRecvTimeStamp = 0;//用于判断检测心跳包时间戳

    //接口回调更新到主线程处理
    private Handler mHandler = new Handler(Looper.getMainLooper());

    private ServerSocket serverSocket = null;
    private ArrayList<onSocketStatusListener> mListenerList = new ArrayList<>();
    private onSocketFileListener mOnSocketFileListener;

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
        SocketThreadPool.getSocketThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                //1.开启serversocket监听
                startConn();
                //2.启动接受消息的线程
                startTCPUsbSocketThread();
            }
        });
    }

    public void disconnectTcpUsbSocket() {
        //1.关闭serversocket监听
        stopConn();
        //2.关闭接受消息的线程
        stopTCPUsbSocketThread();
    }

    /**
     * 开启server端监听
     */
    private void startConn() {
        try {
            if (serverSocket == null) {
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(Config.TCP_SERVER_SOCKET_PORT));
            }
            LogController.i(TAG, "startConn() 开始建立USB的TCP链接成功.");
        } catch (IOException e) {
            e.printStackTrace();
            LogController.i(TAG, "startConn() 开始建立USB的TCP链接失败.");
        }
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
        LogController.i(TAG, "stopConn() 关闭USB的tcp链接成功.");
    }


    /**
     * 开始监听线程
     **/
    private void startTCPUsbSocketThread() {
        if (receiveTcpUsbThread == null) {
            receiveTcpUsbThread = new Thread(this);
            receiveTcpUsbThread.start();
            LogController.i(TAG, "startTCPUsbSocketThread() 线程启动成功.");
        }
    }

    /**
     * 暂停监听线程
     **/
    private void stopTCPUsbSocketThread() {
        if (receiveTcpUsbThread != null)
            receiveTcpUsbThread.interrupt();
        receiveTcpUsbThread = null;
        LogController.i(TAG, "stopTCPUsbSocketThread() 线程停止成功.");
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
                // 开启心跳包检测服务
                if (heartBreakTimer != null)
                    heartBreakTimer.exit();
                heartBreakTimer = new HeartBreakTimer();
                heartBreakTimer.start(-1, Config.HEARTBREAK_TIME);

                LogController.i(TAG, "accept tcp socket:" + socket);
            }
        } catch (Exception e) {
            e.printStackTrace();
            notifySocketConnectFail(e.getMessage());
            LogController.i(TAG, "e2:" + e.toString());
        } finally {
            stopConn();
        }
    }


    public class LocalBinder extends Binder {

        TCPUsbSocket getService() {

            return TCPUsbSocket.this;

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

        @Override
        public void run() {
            try {
                String line = null;
                while ((line = this.bufferedReader.readLine()) != null) {
                    // 用于检测心跳包
                    lastRecvTimeStamp = System.currentTimeMillis();
                    // 处理返回的消息
                    handleReceivedMessage(line, mTransferFile, mFileSendListener);
                }
            } catch (Exception e) {
                e.printStackTrace();
                LogController.i(TAG, "usb read ex:" + e.toString());
            }

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
                LogController.i(TAG, "usb read sucess, line:" + receiveMsg + "\n" + fileName);
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
                            LogController.i(TAG, "usb write file start msg:" + sofObject.toString());
                        } else if ("success".equalsIgnoreCase(data)) {
                            //ROM升级成功
                            onRomInstallSucess(mOnSocketFileListener);
                        } else if ("installing".equalsIgnoreCase(data)) {
                            //ROM安装进度
                            String progress = jsonObject.optString("process");
                            onRomInstalling(mOnSocketFileListener,progress);
                        } else {
                            // ROM安装失败 md5error,failed...
                            onRomInstallFail(mOnSocketFileListener,data);
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
                            LogController.i(TAG, "usb write file downloading failed msg:" + eof.toString());
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
                            LogController.i(TAG, "usb write file msg:" + eof.toString());
                        }
                    } else if ("eof".equalsIgnoreCase(msg)) {
                        String filePath = (file == null) ? "" : file.getPath();
                        // 升级包传输完成
                        if ("ok".equalsIgnoreCase(data)) {
                            //如果当前文件是最后一个文件，则通知服务端升级包全部传输完成
                            if (isFinishTransfer) {
                                //升级包更新完成 通知 {“msg”:”upgrade”, “data”:”end”}
                                JSONObject sofObject = new JSONObject();
                                sofObject.put("msg", "upgrade");
                                sofObject.put("data", "end");
                                printWriter.println(sofObject.toString());
                                isAllowRequestMsgByJson = true;
                                LogController.i(TAG, "usb write upgrade end msg:" + sofObject.toString());
                            }
                            //文件发送成功回调
                            onSendSucess(listener, filePath);
                            LogController.i(TAG, "usb wirte file msg sucess! isFinishTransfer:" + isFinishTransfer);
                        } else if ("error".equalsIgnoreCase(data)) {
                            // 通知上层文件传输失败
                            onSendError(listener, filePath);
                            isAllowRequestMsgByJson = true;
                        }
                    } else {
                        notifyMessageReceived(receiveMsg);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    // 通知上层文件传输失败
                    String filePath = (file == null) ? "" : file.getPath();
                    onSendError(listener, filePath);
                    isAllowRequestMsgByJson = true;
                    LogController.i(TAG, "usb file ex:" + e.toString());
                }
            } catch (JSONException e) {
                e.printStackTrace();
                LogController.i(TAG, "usb read json error:" + e.toString());
            }

        }
    }

    private File mTransferFile = null;
    private boolean isFinishTransfer = false;
    private DataSendListener mFileSendListener;


    private volatile boolean isAllowRequestMsgByJson = true;

    /**
     * 发送文件
     *
     * @param file
     * @param isStart
     * @param isFinish
     * @param listener
     */
    public void sendTcpFileData(final File file, final boolean isStart, final boolean isFinish, final DataSendListener listener) {
        TCPUsbSocket.this.mTransferFile = file;
        TCPUsbSocket.this.isFinishTransfer = isFinish;
        TCPUsbSocket.this.mFileSendListener = listener;
        isAllowRequestMsgByJson = false;
        //1.通知开始升级 {“msg”:”upgrade”, “data”:”start"}
        SocketThreadPool.getSocketThreadPool().post(new Runnable() {
            @Override
            public void run() {
                for (Socket socket : mSocketLists) {
                    JSONObject jsonObject = new JSONObject();
                    try {
                        PrintWriter pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                        if (isStart) {
                            jsonObject.put("msg", "upgrade");
                            jsonObject.put("data", "start");
                        } else {
                            jsonObject.put("msg", "sof");
                            jsonObject.put("name", file.getName());
                        }
                        pw.println(jsonObject.toString());
                        LogController.i(TAG, "usb file upgrade start :" + jsonObject.toString());
                    } catch (Exception e) {
                        e.printStackTrace();
                        onSendError(listener, "usb file upgrade start error.");
                        LogController.i(TAG, "usb file upgrade start error! ex:" + e.toString());
                    }
                }
            }
        });

    }


    /**
     * 发送文本消息
     *
     * @param json
     * @param listener
     */
    public void sendTcpData(final String json, final DataSendListener listener) {

        SocketThreadPool.getSocketThreadPool().post(new Runnable() {
            @Override
            public void run() {
                if (!isAllowRequestMsgByJson) {
                    onSendError(listener, "usb file transfering...");
                    LogController.i(TAG, "usb file transfering:" + json.toString() + ",threadName:" + Thread.currentThread().getName());
                    return;
                }

                if (mSocketLists == null || mSocketLists.size() == 0) {
                    onSendError(listener, "disconnect usb tcp socket.");
                    return;
                }

                LogController.d(TAG, "usb send tcp data socketSize:" + mSocketLists.size());
                for (Socket socket : mSocketLists) {
                    PrintWriter pw = null;
                    if (socket != null) {
                        try {
                            pw = new PrintWriter(new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true));
                            pw.println(json);
                            pw.flush();
                            onSendSucess(listener, json);
                            LogController.i(TAG, "usb send to sucess msg:" + json.toString() + ",threadName:" + Thread.currentThread().getName());
                        } catch (Exception e) {
                            e.printStackTrace();
                            onSendError(listener, e.getMessage());
                            mSocketLists.remove(socket);
                            LogController.i(TAG, "usb send to sucess msg:" + json.toString() + ",threadName:" + Thread.currentThread().getName());
                        }
                    }
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


    private void onRomInstalling(final onSocketFileListener listener, final String progress) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null)
                    listener.onRomInstalling(progress);
            }
        });
    }

    private void onRomInstallFail(final onSocketFileListener listener, final String error) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null)
                    listener.onRomInstallFail(error);
            }
        });
    }

    private void onRomInstallSucess(final onSocketFileListener listener) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null)
                    listener.onRomInstallSucess();
            }
        });
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


    private void notifyMessageReceived(final String message) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
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

    private void notifySocketConnectLost(final String connWay) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (heartBreakTimer != null) {
                    heartBreakTimer.exit();
                }
                for (int i = 0; i < mListenerList.size(); i++) {
                    mListenerList.get(i).onSocketConnectLost(connWay);
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
                stopConn();
                return;
            }

            long duration = System.currentTimeMillis() - lastRecvTimeStamp;
            if (duration > Config.HEARTBREAK_NOREPLY_TIME) {
                isAllowRequestMsgByJson = true;
                notifySocketConnectLost(Config.TCP_CONTECT_WAY_USB);
            } else if (duration > Config.HEARTBREAK_TIME) {
                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("msg", Config.TCP_TYPE_HEART);
                    jsonObject.put("data", "usb");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                sendTcpData(jsonObject.toString(), null);
            }
        }

        @Override
        public void doTimeOutWork() {

        }
    }

    @Override
    public void onDestroy() {
        isDestroy = true;
        stopConn();
        super.onDestroy();
        LogController.i(TAG, "onDestroy");
    }
}
