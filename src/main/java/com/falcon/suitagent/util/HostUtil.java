/*
 * www.msxf.com Inc.
 * Copyright (c) 2017 All Rights Reserved
 */
package com.falcon.suitagent.util;
//             ,%%%%%%%%,
//           ,%%/\%%%%/\%%
//          ,%%%\c "" J/%%%
// %.       %%%%/ o  o \%%%
// `%%.     %%%%    _  |%%%
//  `%%     `%%%%(__Y__)%%'
//  //       ;%%%%`\-/%%%'
// ((       /  `%%%%%%%'
//  \\    .'          |
//   \\  /       \  | |
//    \\/攻城狮保佑) | |
//     \         /_ | |__
//     (___________)))))))                   `\/'
/*
 * 修订记录:
 * long.qian@msxf.com 2017-05-25 10:15 创建
 */

import lombok.extern.slf4j.Slf4j;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * @author long.qian@msxf.com
 */
@Slf4j
public class HostUtil {

    /**
     * 获取本机主机名
     * @return
     */
    public static String getHostName() {
        String osName = System.getProperty("os.name").toLowerCase();
        String hostName = "localhost";
        try {
            Enumeration allNetInterfaces = NetworkInterface.getNetworkInterfaces();
            InetAddress ip;
            while (allNetInterfaces.hasMoreElements())
            {
                NetworkInterface netInterface = (NetworkInterface) allNetInterfaces.nextElement();
                Enumeration addresses = netInterface.getInetAddresses();
                while (addresses.hasMoreElements())
                {
                    ip = (InetAddress) addresses.nextElement();
                    if (ip != null && ip instanceof Inet4Address)
                    {
                        if (!ip.getHostName().startsWith("localhost") && !ip.getHostName().startsWith("1") && !ip.getHostName().startsWith("2")){
                            hostName = ip.getHostName();
                        }
                    }
                }
            }
            if ("localhost".equals(hostName)){
                InetAddress addr = InetAddress.getLocalHost();
                hostName = addr.getHostName();
            }
            if ("localhost".equals(hostName)){
                log.warn("本机的主机名获取可能失败（返回默认的localhost），当前系统环境：{}",osName);
            }
        } catch (Exception e) {
           log.error("",e);
        }

        return hostName;
    }

    /**
     * 获取本机IP地址
     * @return
     */
    public static String getHostIp() {
        String ipAddress = "127.0.0.1";
        String osName = System.getProperty("os.name").toLowerCase();
        try {
            Enumeration allNetInterfaces = NetworkInterface.getNetworkInterfaces();
            InetAddress ip;
            while (allNetInterfaces.hasMoreElements())
            {
                NetworkInterface netInterface = (NetworkInterface) allNetInterfaces.nextElement();
                Enumeration addresses = netInterface.getInetAddresses();
                while (addresses.hasMoreElements())
                {
                    ip = (InetAddress) addresses.nextElement();
                    if (ip != null && ip instanceof Inet4Address)
                    {
                        if (!ip.getHostAddress().startsWith("127.0.0.1") && !ip.getHostAddress().startsWith("localhost")){
                            ipAddress = ip.getHostAddress();
                        }
                    }
                }
            }
            if ("127.0.0.1".equals(ipAddress)){
                InetAddress addr = InetAddress.getLocalHost();
                ipAddress = addr.getHostAddress();
            }
            if ("127.0.0.1".equals(ipAddress)){
                log.warn("本机IP的局域网地址获取可能失败（返回默认的127.0.0.1），当前系统环境：{}",osName);
            }
        } catch (Exception e) {
            log.error("",e);
        }
        return ipAddress;
    }

    /**
     * 获取本机所有的IP地址（不包括127.0.0.1等IP地址）
     * @return
     */
    public static List<String> getHostIps() {
        List<String> ipAddress = new ArrayList<>();
        try {
            Enumeration allNetInterfaces = NetworkInterface.getNetworkInterfaces();
            InetAddress ip;
            while (allNetInterfaces.hasMoreElements())
            {
                NetworkInterface netInterface = (NetworkInterface) allNetInterfaces.nextElement();
                Enumeration addresses = netInterface.getInetAddresses();
                while (addresses.hasMoreElements())
                {
                    ip = (InetAddress) addresses.nextElement();
                    if (ip != null && ip instanceof Inet4Address)
                    {
                        if (!ip.getHostAddress().startsWith("127.0.0.1") && !ip.getHostAddress().startsWith("localhost")){
                            ipAddress.add(ip.getHostAddress());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("",e);
        }

        return ipAddress;
    }
}
