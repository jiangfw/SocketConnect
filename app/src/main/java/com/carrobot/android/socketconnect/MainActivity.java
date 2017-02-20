package com.carrobot.android.socketconnect;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

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


public class MainActivity extends AppCompatActivity implements onSocketStatusListener {

    private final String TAG = MainActivity.class.getSimpleName();

    private TextView id_tv_info;
    private Button id_btn_send_wireless,id_btn_send_wired;
    private TextView id_tv_recevie;

    private Button id_btn_send_file, id_btn_get_version;

    private SocketManager mSocketManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSocketManager = SocketManager.getInstance(this);
        mSocketManager.addOnSocketStatusListener(this);
        mSocketManager.startSocketConnection();

        id_btn_send_wireless = (Button) findViewById(R.id.id_btn_send_wireless);

        id_btn_send_wireless.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String str = "send to client message";
                String type = Config.TCP_TYPE_APP;
                String hook = Utils.getUdid(MainActivity.this);

                /**
                 * tcp连接阶段，请求和响应的消息格式：
                 {
                 “msg”:”请求类型”,
                 “hook”:”客户端生成uuid，服务端会将次字符串原文回传，以区分请求和响应”,
                 ...
                 }
                 */

                try {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.putOpt("msg",type);
                    jsonObject.putOpt("hook",hook);
                    jsonObject.putOpt("resposeMsg",str);
                    mSocketManager.requestDataByJson(jsonObject.toString(),Config.CONTECT_WAY_WIRELESS);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
        });

        id_btn_send_wired = (Button) findViewById(R.id.id_btn_send_wired);

        id_btn_send_wired.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String str = "send to server message";
                String type = Config.TCP_TYPE_APP;
                String hook = Utils.getUdid(MainActivity.this);
                try {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.putOpt("msg",type);
                    jsonObject.putOpt("hook",hook);
                    jsonObject.putOpt("resposeMsg",str);
                    mSocketManager.requestDataByJson(jsonObject.toString(),Config.CONTECT_WAY_USB);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
        });

        //请求版本号
        id_btn_get_version = (Button) findViewById(R.id.id_btn_get_version);
        id_btn_get_version.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = "{\"msg\":\"version\"}";
                mSocketManager.requestDataByJson(msg);
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
                        boolean isSucess = mSocketManager.transferFileList(pathLists);
                        if (isSucess) {
                            Toast.makeText(MainActivity.this, "start transerfer file sucess.", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(MainActivity.this, "start transerfer file fail.", Toast.LENGTH_LONG).show();
                        }
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

    }

    /**
     * 获取upd广播的IP地址和端口号
     * @param message
     */
    @Override
    public void onSocketUdpInfo(String message) {
        Log.i("udpClient","onSocketUdpInfo"+message);

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
        Toast.makeText(this,connWay, Toast.LENGTH_LONG).show();
        id_tv_recevie.setText("建立TCP连接成功..."+"\n");

    }
    /**
     * Socket通信TCP连接建立失败
     */
    @Override
    public void onSocketConnectFail(String message) {

        id_tv_recevie.setText("建立TCP连接中..."+message+"\n");
    }
    /**
     * Socket通信TCP连接断开连接
     */
    @Override
    public void onSocketConnectLost(String connWay) {

        Toast.makeText(this,"onSocketConnectLost:"+connWay, Toast.LENGTH_LONG).show();

//        id_tv_info.setText("udp 广播获取服务端IP地址中...:");
        id_tv_recevie.setText("建立TCP连接中..."+"\n");
    }
    /**
     * 获取服务端发送的文本信息
     * @param message
     */
    @Override
    public void onMessageReceive(String message) {

        Log.i("UDPClient","onMessageReceive"+message);
        id_tv_recevie.setText(id_tv_recevie.getText().toString()+message.toString()+"\n");
    }
    /**
     * TCP发送成功回调
     */
    @Override
    public void onSendSuccess() {

    }
    /**
     * TCP发送失败
     * code ＝ 400 ：socket被回收
     * code ＝ 401 ：socket断开连接，发送失败
     * @param code
     */
    @Override
    public void onSendError(int code) {
        Toast.makeText(this,"未建立TCP连接，请稍后...",Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSocketManager.stopSocketConnection();
        mSocketManager.removeOnSocketStatusListener(this);
        //TODO test 2 3 4
    }
}
