package com.carrobot.android.socketconnect.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.telephony.TelephonyManager;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

/**
 * Created by fuwei.jiang on 16/12/29.
 */

public class Utils {


    /**
     * 获取设备唯一ID，用来添加通信hook
     * */
    public static String getUdid(Context context){
        String deviceID = ((TelephonyManager) context
                .getSystemService(Context.TELEPHONY_SERVICE))
                .getDeviceId();
        return deviceID;
    }


    public static String getBroadcastAddress(){
        String broadcast = getBroadcastAddress("p2p");
        if(broadcast == null){
            return getBroadcastAddress("wlan0");
        }
        return broadcast;
    }

    /**
     * 获取P2P的广播地址
     * */
    public static String getBroadcastAddress(String netCardName) {
        try {

            Enumeration<NetworkInterface> eni = NetworkInterface
                    .getNetworkInterfaces();
            while (eni.hasMoreElements()) {

                NetworkInterface networkCard = eni.nextElement();

                if(networkCard.getDisplayName().startsWith(netCardName)){
                    List<InterfaceAddress> ncAddrList = networkCard
                            .getInterfaceAddresses();
                    Iterator<InterfaceAddress> ncAddrIterator = ncAddrList.iterator();
                    while (ncAddrIterator.hasNext()) {
                        InterfaceAddress networkCardAddress = ncAddrIterator.next();
                        InetAddress address = networkCardAddress.getAddress();
                        if (!address.isLoopbackAddress()) {
                            String hostAddress = address.getHostAddress();
                            if (hostAddress.indexOf(":") > 0) {
                                // case : ipv6
                                continue;
                            } else {
                                // case : ipv4
                                String maskAddress = calcMaskByPrefixLength(networkCardAddress.getNetworkPrefixLength());
                                String subnetAddress = calcSubnetAddress(hostAddress, maskAddress);
                                String broadcastAddress = networkCardAddress.getBroadcast().getHostAddress();

                                LogController.i("UDPClient",netCardName+" subnetmask =  "+ maskAddress);
                                LogController.i("UDPClient",netCardName+" subnet     =  "+ subnetAddress);
                                LogController.i("UDPClient",netCardName+" broadcast  =  "+ broadcastAddress+"\n");

                                return  broadcastAddress;
                            }
                        } else {
                            String loopback = networkCardAddress.getAddress().getHostAddress();
                            LogController.i("UDPClient","loopback addr  =   " + loopback +"\n");

                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private static String calcMaskByPrefixLength(int length) {
        int mask = -1 << (32 - length);
        int partsNum = 4;
        int bitsOfPart = 8;
        int maskParts[] = new int[partsNum];
        int selector = 0x000000ff;

        for (int i = 0; i < maskParts.length; i++) {
            int pos = maskParts.length - 1 - i;
            maskParts[pos] = (mask >> (i * bitsOfPart)) & selector;
        }

        String result = "";
        result = result + maskParts[0];
        for (int i = 1; i < maskParts.length; i++) {
            result = result + "." + maskParts[i];
        }
        return result;
    }

    private static String calcSubnetAddress(String ip, String mask) {
        String result = "";
        try {
            // calc sub-net IP
            InetAddress ipAddress = InetAddress.getByName(ip);
            InetAddress maskAddress = InetAddress.getByName(mask);

            byte[] ipRaw = ipAddress.getAddress();
            byte[] maskRaw = maskAddress.getAddress();

            int unsignedByteFilter = 0x000000ff;
            int[] resultRaw = new int[ipRaw.length];
            for (int i = 0; i < resultRaw.length; i++) {
                resultRaw[i] = (ipRaw[i] & maskRaw[i] & unsignedByteFilter);
            }

            // make result string
            result = result + resultRaw[0];
            for (int i = 1; i < resultRaw.length; i++) {
                result = result + "." + resultRaw[i];
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        return result;
    }


    /**
     * 用来判断服务是否运行.
     * @param context
     * @param className 判断的服务名字
     * @return true 在运行 false 不在运行
     */
    public static boolean isServiceRunning(Context context, String className) {
        boolean isRunning = false;
        ActivityManager activityManager = (ActivityManager)
                context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> serviceList
                = activityManager.getRunningServices(3000);
        if (!(serviceList.size()>0)) {
            return false;
        }
        for (int i=0; i<serviceList.size(); i++) {
            LogController.i("Utils","className:"+serviceList.get(i).service.getClassName());

            if (serviceList.get(i).service.getClassName().equals(className) == true) {
                isRunning = true;
                break;
            }
        }
        LogController.i("Utils","className:"+className+",isRunning:"+isRunning);
        return isRunning;
    }
}
