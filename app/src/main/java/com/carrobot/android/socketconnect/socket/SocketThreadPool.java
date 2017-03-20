package com.carrobot.android.socketconnect.socket;

import android.os.Handler;
import android.os.HandlerThread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by fuwei.jiang on 17/3/2下午7:16.
 */

public class SocketThreadPool {

    private static final int POOL_SIZE = 10; // 单个CPU线程池大小

    private ExecutorService executor;
    private static SocketThreadPool socketThreadPool;

    private HandlerThread socketHandlerThread = new HandlerThread("socket_thread_pool");
    private Handler socketController = null;

    public static  SocketThreadPool getSocketThreadPool(){
        if(socketThreadPool ==null){
            socketThreadPool = new SocketThreadPool();
        }
        return  socketThreadPool;
    }


    public void post(Runnable runnable){
        initExecuter();
        socketController.post(runnable);
    }

//    public void execute(Runnable runnable){
//        initExecuter();
//        if(executor!=null){
//            executor.execute(runnable);
//        }
//    }

    public void shutdown(){
        initExecuter();
        if(executor!=null){
            executor.shutdown();
        }
    }

    private SocketThreadPool(){
        initExecuter();
    }


    private void initExecuter(){
        if(executor==null){
            int cpuNums = Runtime.getRuntime().availableProcessors();
            executor = Executors.newFixedThreadPool(cpuNums * POOL_SIZE); // 根据CPU数目初始化线程池
        }

        if(socketController==null){
            socketHandlerThread.start();
            socketController = new Handler(socketHandlerThread.getLooper());
        }
    }
}
