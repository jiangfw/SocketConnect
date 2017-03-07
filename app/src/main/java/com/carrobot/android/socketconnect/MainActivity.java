package com.carrobot.android.socketconnect;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.carrobot.android.socketconnect.listener.DataReceiveListener;
import com.carrobot.android.socketconnect.listener.DataSendListener;
import com.carrobot.android.socketconnect.listener.onSocketFileListener;
import com.carrobot.android.socketconnect.listener.onSocketStatusListener;
import com.carrobot.android.socketconnect.socket.SocketManager;
import com.carrobot.android.socketconnect.utils.Config;
import com.carrobot.android.socketconnect.utils.FileCache;
import com.carrobot.android.socketconnect.utils.LogController;
import com.carrobot.android.socketconnect.utils.Utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;


public class MainActivity extends AppCompatActivity implements onSocketStatusListener, onSocketFileListener, DataReceiveListener {

    private final String TAG = MainActivity.class.getSimpleName();

    private TextView id_tv_info;
    private Button id_btn_send_wireless, id_btn_send_wired;
    private TextView id_tv_recevie;

    private Button id_btn_send_file, id_btn_get_version;

    private SocketManager mSocketManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSocketManager = SocketManager.getInstance(this);
        mSocketManager.addOnSocketStatusListener(this);
        mSocketManager.setSocketFileListerner(this);
        mSocketManager.addDataReceivedListener(this);
        mSocketManager.startSocketConnection();

        id_btn_send_wireless = (Button) findViewById(R.id.id_btn_send_wireless);

        id_btn_send_wireless.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String msg = "{\"msg\":\"wifi\"}";
                mSocketManager.requestDataByJson(msg, Config.TCP_CONTECT_WAY_WIFI, new DataSendListener() {
                    @Override
                    public void onSuccess(String message) {

                    }

                    @Override
                    public void onError(String error) {
                        Toast.makeText(MainActivity.this,"wifi error:"+error,Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        id_btn_send_wired = (Button) findViewById(R.id.id_btn_send_wired);

        id_btn_send_wired.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String msg = "{\"msg\":\"usb\"}";
                mSocketManager.requestDataByJson(msg, Config.TCP_CONTECT_WAY_USB, new DataSendListener() {
                    @Override
                    public void onSuccess(String message) {

                    }

                    @Override
                    public void onError(String error) {
                        Toast.makeText(MainActivity.this,"usb error:"+error,Toast.LENGTH_SHORT).show();
                    }
                });

            }
        });

        //请求版本号
        id_btn_get_version = (Button) findViewById(R.id.id_btn_get_version);
        id_btn_get_version.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = "{\"msg\":\"version\"}";
                mSocketManager.requestDataByJson(msg, new DataSendListener() {
                    @Override
                    public void onSuccess(String message) {

                    }

                    @Override
                    public void onError(String error) {
                        Toast.makeText(MainActivity.this,"error:"+error,Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        //发送文件
        id_btn_send_file = (Button) findViewById(R.id.id_btn_send_file);
        id_btn_send_file.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String romPath = FileCache.getInstance().getRomPath();

                File fileDirs = new File(romPath);

                if (fileDirs != null) {
                    String[] array = fileDirs.list();
                    ArrayList<String> pathLists = new ArrayList<String>();
                    for (int i = 0; array != null && i < array.length; i++) {
                        pathLists.add(romPath + "/" + array[i]);
                        LogController.i(TAG, "file path:" + array[i]);
                    }
                    try {
                        mSocketManager.transferFileList(pathLists);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                        Toast.makeText(MainActivity.this, "file path is not exits.", Toast.LENGTH_LONG).show();
                        LogController.i(TAG, "file path is not exits");
                    }
                } else {
                    Toast.makeText(MainActivity.this, "fileDirs is null.", Toast.LENGTH_LONG).show();
                    LogController.i(TAG, "fileDirs is null.");
                }
            }
        });

        id_tv_info = (TextView) findViewById(R.id.id_tv_info);
        id_tv_recevie = (TextView) findViewById(R.id.id_tv_recevie);

        Button id_btn_miracast = (Button) findViewById(R.id.id_btn_miracast);
        id_btn_miracast.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startWifiDisplayActivity(MainActivity.this);
            }
        });

    }


    /**
     * 开启屏幕投射设置
     *
     * @param context
     */
    public static void startWifiDisplayActivity(Context context) {

        System.out.println("brand:" + Build.BRAND + ",board:" + Build.BOARD + ",display:" + Build.DISPLAY + ",model:" + Build.MODEL);
        try {
            Intent intent = new Intent("android.settings.WIFI_DISPLAY_SETTINGS");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
            try {
                // sdk 6.0 设置中无线投屏的action
                Intent intent = new Intent("android.settings.CAST_SETTINGS");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (ActivityNotFoundException e0) {
                e0.printStackTrace();
                try {
                    Intent intent = new Intent("mediatek.settings.WFD_SINK_SETTINGS");
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (ActivityNotFoundException e1) {
                    e1.printStackTrace();
                    try {
                        Intent intent = new Intent(Settings.ACTION_SETTINGS);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    } catch (ActivityNotFoundException e2) {
                        e2.printStackTrace();
                    }
                }

            }
        } catch (SecurityException se) {
            try {
                Intent intent = new Intent(Settings.ACTION_SETTINGS);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (ActivityNotFoundException e2) {
                e2.printStackTrace();
            }
            se.printStackTrace();
        }
    }

    /**
     * 获取upd广播的IP地址和端口号
     *
     * @param message
     */
    @Override
    public void onSocketUdpInfo(String message) {
        Log.i("udpClient", "onSocketUdpInfo" + message);

        /**
         * 响应：
         {
         “msg”:”reqConn”,
         “ip”:”xx.xx.xx.xx”,
         “factoryPort”: 1234,   // 工模测试服务的端口号
         “OBDPort”: 5678      // OBD消息服务的端口号
         }
         */

        try {
            JSONObject jsonObject = new JSONObject(message);
            String ip = jsonObject.optString("ip");
            int factoryPort = jsonObject.optInt("factoryPort");
            int OBDPort = jsonObject.optInt("OBDPort");

            id_tv_info.setText("服务端IP地址:" + ip + "  工模端口:" + factoryPort + " OBD端口:" + OBDPort);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Socket通信与TCP连接建立成功
     */
    @Override
    public void onSocketConnectSucess(String connWay) {
        Toast.makeText(this, connWay, Toast.LENGTH_SHORT).show();
        id_tv_recevie.setText("建立TCP连接成功..." + "\n");

    }

    /**
     * Socket通信TCP连接建立失败
     */
    @Override
    public void onSocketConnectFail(String message) {
        Toast.makeText(this, "onSocketConnectFail msg:" + message, Toast.LENGTH_SHORT).show();
        id_tv_recevie.setText("建立TCP连接中..." + message + "\n");
    }

    /**
     * Socket通信TCP连接断开连接
     */
    @Override
    public void onSocketConnectLost(String connWay) {

        Toast.makeText(this, "onSocketConnectLost:" + connWay, Toast.LENGTH_SHORT).show();
        id_tv_recevie.setText("建立TCP连接中..." + "\n");
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSocketManager.stopSocketConnection();
        mSocketManager.removeOnSocketStatusListener(this);
    }

    @Override
    public void onFileTransferSucess(String filePath) {

        LogController.d(TAG, "file transfer sucess,filePath:" + filePath);
        Toast.makeText(this, "file transfer sucess.", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onFileListTransferSucess() {
        LogController.d(TAG, "all file transfer sucess");
        Toast.makeText(this, "all file transfer sucess.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onFileTransferFail(String filePath, String message) {
        LogController.d(TAG, "file transfer error:" + message + ",path:" + filePath);
        Toast.makeText(this, "file transfer error:" + message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRomInstallSucess() {

    }

    @Override
    public void onRomInstallFail(String error) {

    }

    @Override
    public void onRomInstalling(String progress) {

    }

    @Override
    public void onMessageReceived(int type, String message) {
        LogController.d(TAG, "onMessageReceived,type:" + type + ",message:" + message);
        id_tv_recevie.setText(id_tv_recevie.getText().toString() + message.toString() + "\n");
    }

    @Override
    public void onCommandReceived(int command) {

    }
}
