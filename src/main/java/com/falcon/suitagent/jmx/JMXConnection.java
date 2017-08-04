/*
 * www.yiji.com Inc.
 * Copyright (c) 2016 All Rights Reserved
 */
package com.falcon.suitagent.jmx;

import com.falcon.suitagent.config.AgentConfiguration;
import com.falcon.suitagent.exception.JMXUnavailabilityType;
import com.falcon.suitagent.jmx.vo.JMXConnectionInfo;
import com.falcon.suitagent.util.HostUtil;
import com.falcon.suitagent.util.StringUtils;
import com.falcon.suitagent.vo.jmx.JMXExecuteCommandInfo;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.math.NumberUtils;

import javax.management.remote.JMXConnector;
import javax.management.remote.JMXServiceURL;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/*
 * 修订记录:
 * guqiu@yiji.com 2016-06-22 17:48 创建
 */

/**
 * @author guqiu@yiji.com
 */
@Slf4j
public class JMXConnection {
    private static final Map<String,JMXConnectionInfo> connectCacheLibrary = new HashMap<>();//JMX的连接缓存
    private static final Map<String,Integer> serverConnectCount = new HashMap<>();//记录服务应有的JMX连接数

    private String serverName;
    private List<JMXExecuteCommandInfo> commandInfos = new ArrayList<>();

    /**
     * 当serverName不为空时，只采集serverName的JMX
     * 当serverName为空时，只采集jmxExecuteCommandInfos中的JMX
     * @param serverName
     * @param jmxExecuteCommandInfos
     */
    public JMXConnection(String serverName, List<JMXExecuteCommandInfo> jmxExecuteCommandInfos) {
        this.serverName = serverName;
        this.commandInfos.addAll(jmxExecuteCommandInfos);
    }

    /**
     * 删除JMX连接池连接
     * @param serverName
     * JMX服务名
     * @param pid
     * 进程id
     */
    public static void removeConnectCache(String serverName,int pid){
        String key = serverName + pid;
        if(connectCacheLibrary.remove(key) != null){
            //删除成功,更新serverConnectCount
            int count = serverConnectCount.get(serverName);
            if (count -1 == 0){
                serverConnectCount.remove(serverName);
            }else {
                serverConnectCount.put(serverName,count - 1);
            }
            log.info("已清除JMX监控: {} , pid: {}",serverName,pid);
        }
    }

    /**
     * 根据服务名,返回该服务应有的JMX连接数
     * @param serverName
     * @return
     */
    public static int getServerConnectCount(String serverName){
        if (serverName == null){
            return 0;
        }
        return serverConnectCount.get(serverName);
    }

    /**
     * 获取本地是否已开启指定的JMX服务
     * @param serverName
     * @return
     */
    public static boolean hasJMXServerInLocal(String serverName){
        if(!StringUtils.isEmpty(serverName)){
            List<VirtualMachineDescriptor> vms = VirtualMachine.list();
            for (VirtualMachineDescriptor desc : vms) {
                File file = new File(desc.displayName());
                if(file.exists()){
                    //java -jar 形式启动的Java应用
                    if(file.toPath().getFileName().toString().equals(serverName)){
                        return true;
                    }
                }else if(hasContainsServerName(desc.displayName(),serverName)){
                    return true;
                }

            }
        }
        return false;
    }

    /**
     * 获取指定服务名的本地JMX VM 描述对象
     * @param serverName
     * @return
     */
    public static List<VirtualMachineDescriptor> getVmDescByServerName(String serverName){
        List<VirtualMachineDescriptor> vmDescList = new ArrayList<>();
        if (!StringUtils.isEmpty(serverName)){
            List<VirtualMachineDescriptor> vms = VirtualMachine.list();
            for (VirtualMachineDescriptor desc : vms) {
                File file = new File(desc.displayName());
                if(file.exists()){
                    //java -jar 形式启动的Java应用
                    if(file.toPath().getFileName().toString().equals(serverName)){
                        vmDescList.add(desc);
                    }
                }else if(hasContainsServerName(desc.displayName(),serverName)){
                    vmDescList.add(desc);
                }
            }
        }
        return vmDescList;
    }

    /**
     * 判断指定的目标服务名是否在探测的展示名中
     * @param displayName
     * @param serverName
     * @return
     */
    public static boolean hasContainsServerName(String displayName,String serverName){
        if (StringUtils.isEmpty(serverName)){
            return false;
        }
        boolean has = true;
        List<String> displaySplit = Arrays.asList(displayName.split("\\s+"));
        for (String s : serverName.split("\\s+")) {
            //boot  start
            if (!displaySplit.contains(s)){
                has = false;
                break;
            }
        }
        return has;
    }

    /**
     *
     * @throws IOException
     */
    public static void closeAll() {
        for (JMXConnectionInfo jmxConnectionInfo : connectCacheLibrary.values()) {
            jmxConnectionInfo.closeJMXConnector();
        }
    }

    /**
     * 获取JMX连接
     * @return
     * @throws IOException
     */
    public synchronized List<JMXConnectionInfo> getMBeanConnection(){
        if(StringUtils.isEmpty(serverName) && commandInfos != null && commandInfos.isEmpty()){
            log.error("获取JMX连接的serverName和JMXExecuteCommandInfo集合不能同时为空");
            return new ArrayList<>();
        }

        List<VirtualMachineDescriptor> vmDescList = getVmDescByServerName(serverName);

        List<JMXConnectionInfo> connections = new ArrayList<>();
        if (serverName != null){
            connections.addAll(connectCacheLibrary.entrySet().
                    stream().
                    filter(entry -> NumberUtils.isNumber(entry.getKey().replace(serverName,""))).
                    map(Map.Entry::getValue).
                    collect(Collectors.toList()));
        }
        if (commandInfos != null && !commandInfos.isEmpty()){
            for (JMXExecuteCommandInfo commandInfo : commandInfos) {
                connections.addAll(connectCacheLibrary.entrySet().
                        stream().
                        filter(entry -> entry.getKey().equals(commandInfo.getAppName())).
                        map(Map.Entry::getValue).
                        collect(Collectors.toList()));
            }
        }

        if(connections.isEmpty() || connections.size() < (vmDescList.size() + commandInfos.size())){ //JMX连接池为空或没达到该有的连接数
            connections.clear();
            clearCacheForServerName();
            clearCacheForAllCommandInfos();

            int serverNameCount = 0;
            for (VirtualMachineDescriptor desc : vmDescList) {
                JMXConnectUrlInfo jmxConnectUrlInfo = getConnectorAddress(desc);
                if (jmxConnectUrlInfo == null) {
                    log.error("应用 {} 的JMX连接URL获取失败",desc.displayName());
                    //对应的ServerName的JMX连接获取失败，返回该服务JMX连接失败，用于上报不可用记录
                    connections.add(initBadJMXConnect(desc));
                    serverNameCount++;
                    continue;
                }

                try {
                    connections.add(initJMXConnectionInfo(getJMXConnector(jmxConnectUrlInfo),desc));
                    log.debug("应用 {} JMX 连接已建立",serverName);

                } catch (Exception e) {
                    log.error("JMX 连接获取异常:{}",e.getMessage());
                    //JMX连接获取失败，添加该服务JMX的不可用记录，用于上报不可用记录
                    connections.add(initBadJMXConnect(desc));
                }
                //该服务应有的数量++
                serverNameCount++;
            }
            //一个serverName可能会有多个实例
            if(serverNameCount > 0){
                serverConnectCount.put(serverName,serverNameCount);
            }else{
                if (serverName != null){
                    //对应的ServerName的JMX连接获取失败，返回该服务JMX连接失败，用于上报不可用记录
                    JMXConnectionInfo jmxConnectionInfo = new JMXConnectionInfo();
                    jmxConnectionInfo.setValid(false, JMXUnavailabilityType.connectionFailed);
                    connections.add(jmxConnectionInfo);
                    serverConnectCount.put(serverName,1);
                }
            }


            //指定命令行方式的JMX连接，避免重复监控CommandInfo
            if (serverName == null){
                for (JMXExecuteCommandInfo commandInfo : this.commandInfos) {
                    JMXConnectUrlInfo jmxConnectUrlInfo = getConnectorAddress(commandInfo);
                    if (jmxConnectUrlInfo == null) {
                        log.error("应用 {} 的JMX连接URL获取失败",commandInfo);
                        //对应的ServerName的JMX连接获取失败，返回该服务JMX连接失败，用于上报不可用记录
                        connections.add(initBadJMXConnect(commandInfo));
                        serverConnectCount.put(commandInfo.getAppName(),1);
                        continue;
                    }

                    try {
                        connections.add(initJMXConnectionInfo(getJMXConnector(jmxConnectUrlInfo),commandInfo));
                        log.debug("应用 {} JMX 连接已建立",commandInfo);
                        serverConnectCount.put(commandInfo.getAppName(),1);
                    } catch (Exception e) {
                        log.error("JMX 连接获取异常:{}",e.getMessage());
                        //JMX连接获取失败，添加该服务JMX的不可用记录，用于上报不可用记录
                        connections.add(initBadJMXConnect(commandInfo));
                        serverConnectCount.put(commandInfo.getAppName(),1);
                    }
                    //该服务应有的数量++
                    serverNameCount++;
                }
            }

        }

        //若当前应有的服务实例记录值比获取到的记录值小，重新设置
        if (serverName != null){
            if(getServerConnectCount(serverName) < vmDescList.size()){
                serverConnectCount.put(serverName,vmDescList.size());
            }
        }

        return connections;
    }

    private JMXConnector getJMXConnector(JMXConnectUrlInfo jmxConnectUrlInfo) throws Exception {
        JMXServiceURL url = new JMXServiceURL(jmxConnectUrlInfo.getRemoteUrl());
        JMXConnector connector;
        if(jmxConnectUrlInfo.isAuthentication()){
            connector = JMXConnectWithTimeout.connectWithTimeout(url,jmxConnectUrlInfo.getJmxUser()
                    ,jmxConnectUrlInfo.getJmxPassword(),10, TimeUnit.SECONDS);
        }else{
            connector = JMXConnectWithTimeout.connectWithTimeout(url,null,null,10, TimeUnit.SECONDS);
        }
        return connector;
    }

    /**
     * 清楚连接缓存
     */
    private void clearCacheForServerName(){
        //清除当前连接池中的连接
        if (serverName != null){
            List<String> removeKey = connectCacheLibrary.keySet().stream().filter(key -> NumberUtils.isNumber(key.replace(serverName,""))).collect(Collectors.toList());
            removeKey.forEach(key -> {
                connectCacheLibrary.get(key).closeJMXConnector();
                connectCacheLibrary.remove(key);
            });
        }
    }

    /**
     * 清楚连接缓存
     */
    private void clearCacheForAllCommandInfos(){
        //清除当前连接池中的连接
        List<String> removeKey = new ArrayList<>();
        for (JMXExecuteCommandInfo commandInfo : this.commandInfos) {
            removeKey.addAll(connectCacheLibrary.keySet().stream().filter(key -> key.equals(commandInfo.getAppName())).collect(Collectors.toList()));
        }
        removeKey.forEach(key -> {
            connectCacheLibrary.get(key).closeJMXConnector();
            connectCacheLibrary.remove(key);
        });
    }

    /**
     * 清楚连接缓存
     */
    private void clearCacheForCommandInfo(JMXExecuteCommandInfo commandInfo){
        //清除当前连接池中的连接
        List<String> removeKey = new ArrayList<>();
        removeKey.addAll(connectCacheLibrary.keySet().stream().filter(key -> key.equals(commandInfo.getAppName())).collect(Collectors.toList()));
        removeKey.forEach(key -> {
            connectCacheLibrary.get(key).closeJMXConnector();
            connectCacheLibrary.remove(key);
        });
    }

    /**
     * 重置jmx连接
     * @throws IOException
     */
    public synchronized void resetMBeanConnection() {
        //本地JMX连接中根据指定的服务名命中的VirtualMachineDescriptor
        List<VirtualMachineDescriptor> targetDesc = getVmDescByServerName(serverName);

        //若命中的target数量大于或等于该服务要求的JMX连接数,则进行重置连接池中的连接
        if(targetDesc.size() >= getServerConnectCount(serverName)){

            //清除当前连接池中的连接
            clearCacheForServerName();

            //重新设置服务应有连接数
            int count = 0;
            //重新构建连接
            for (VirtualMachineDescriptor desc : targetDesc) {

                JMXConnectUrlInfo jmxConnectUrlInfo = getConnectorAddress(desc);
                if (jmxConnectUrlInfo == null) {
                    log.error("应用{}的JMX连接URL获取失败",serverName);
                    //对应的ServerName的JMX连接获取失败，返回该服务JMX连接失败，用于上报不可用记录
                    initBadJMXConnect(desc);
                    count++;
                    continue;
                }
                try {
                    initJMXConnectionInfo(getJMXConnector(jmxConnectUrlInfo),desc);
                    log.info("应用 {} JMX 连接已建立,将在下一周期获取Metrics值时生效",serverName);
                } catch (Exception e) {
                    log.error("JMX 连接获取异常:{}",e);
                    //JMX连接获取失败，添加该服务JMX的不可用记录，用于上报不可用记录
                    initBadJMXConnect(desc);
                }
                count++;
            }
            serverConnectCount.put(serverName,count);
        }

        //若当前应有的服务实例记录值比获取到的记录值小，重新设置
        if(getServerConnectCount(serverName) < targetDesc.size()){
            serverConnectCount.put(serverName,targetDesc.size());
        }

        //避免重复监控CommandInfo
        if (serverName == null){
            for (JMXExecuteCommandInfo commandInfo : this.commandInfos) {
                JMXConnectionInfo cached = connectCacheLibrary.get(commandInfo.getAppName());
                //未缓存或缓存中的对象失效
                if (cached == null || !cached.isValid()){
                    clearCacheForCommandInfo(commandInfo);
                    JMXConnectUrlInfo jmxConnectUrlInfo = getConnectorAddress(commandInfo);
                    if (jmxConnectUrlInfo == null) {
                        log.error("应用{}的JMX连接URL获取失败",commandInfo);
                        //对应的ServerName的JMX连接获取失败，返回该服务JMX连接失败，用于上报不可用记录
                        initBadJMXConnect(commandInfo);
                        serverConnectCount.put(commandInfo.getAppName(),1);
                        continue;
                    }
                    try {
                        initJMXConnectionInfo(getJMXConnector(jmxConnectUrlInfo),commandInfo);
                        log.info("应用 {} JMX 连接已建立,将在下一周期获取Metrics值时生效",commandInfo);
                    } catch (Exception e) {
                        log.error("JMX 连接获取异常:{}",e);
                        //JMX连接获取失败，添加该服务JMX的不可用记录，用于上报不可用记录
                        initBadJMXConnect(commandInfo);
                        serverConnectCount.put(commandInfo.getAppName(),1);
                    }
                }
            }
        }


    }



    private JMXConnectUrlInfo getConnectorAddress(VirtualMachineDescriptor desc){
        if(AgentConfiguration.INSTANCE.isAgentJMXLocalConnect()){
            String connectorAddress = AbstractJmxCommand.findJMXLocalUrlByProcessId(Integer.parseInt(desc.id()));
            if(connectorAddress != null){
                return new JMXConnectUrlInfo(connectorAddress);
            }
        }

        JMXConnectUrlInfo jmxConnectUrlInfo = null;
        try {
            jmxConnectUrlInfo = AbstractJmxCommand.findJMXRemoteUrlByProcessId(null,Integer.parseInt(desc.id()), HostUtil.getHostIp());
            if(jmxConnectUrlInfo != null){
                log.info("JMX Remote URL:{}",jmxConnectUrlInfo);
            }else if(!AgentConfiguration.INSTANCE.isAgentJMXLocalConnect()){
                log.warn("应用未配置JMX Remote功能,请给应用配置JMX Remote");
            }
        } catch (Exception e) {
            log.error("JMX连接本机地址获取失败",e);
        }
        return jmxConnectUrlInfo;
    }

    private JMXConnectUrlInfo getConnectorAddress(JMXExecuteCommandInfo commandInfo){
        JMXConnectUrlInfo jmxConnectUrlInfo = null;
        jmxConnectUrlInfo = AbstractJmxCommand.findJMXRemoteUrlByProcessId(commandInfo.getCommand(),0, commandInfo.getIp());
        if(jmxConnectUrlInfo != null){
            log.info("JMX Remote URL:{}",jmxConnectUrlInfo);
        }else if(!AgentConfiguration.INSTANCE.isAgentJMXLocalConnect()){
            log.warn("应用未配置JMX Remote功能,请给应用配置JMX Remote");
        }
        return jmxConnectUrlInfo;
    }

    /**
     * JMXConnectionInfo的初始化动作
     * @param connector
     * @param desc
     * @return
     * @throws IOException
     */
    private JMXConnectionInfo initJMXConnectionInfo(JMXConnector connector,VirtualMachineDescriptor desc) throws IOException {
        JMXConnectionInfo jmxConnectionInfo = new JMXConnectionInfo();
        jmxConnectionInfo.setJmxConnector(connector);
        jmxConnectionInfo.setCacheKeyId(desc.id());
        jmxConnectionInfo.setConnectionServerName(serverName);
        jmxConnectionInfo.setConnectionQualifiedServerName(desc.displayName());
        jmxConnectionInfo.setMBeanServerConnection(connector.getMBeanServerConnection());
        jmxConnectionInfo.setValid(true,null);
        jmxConnectionInfo.setPid(Integer.parseInt(desc.id()));

        connectCacheLibrary.put(serverName + desc.id(),jmxConnectionInfo);
        return jmxConnectionInfo;
    }

    /**
     * JMXConnectionInfo的初始化动作
     * @param connector
     * @param commandInfo
     * @return
     * @throws IOException
     */
    private JMXConnectionInfo initJMXConnectionInfo(JMXConnector connector,JMXExecuteCommandInfo commandInfo) throws IOException {
        JMXConnectionInfo jmxConnectionInfo = new JMXConnectionInfo();
        jmxConnectionInfo.setJmxConnector(connector);
        jmxConnectionInfo.setCacheKeyId(commandInfo.getAppName());
        jmxConnectionInfo.setConnectionServerName(commandInfo.getAppName());
        jmxConnectionInfo.setConnectionQualifiedServerName(commandInfo.getAppName());
        jmxConnectionInfo.setMBeanServerConnection(connector.getMBeanServerConnection());
        jmxConnectionInfo.setValid(true,null);
        jmxConnectionInfo.setPid(-1);

        connectCacheLibrary.put(commandInfo.getAppName(),jmxConnectionInfo);
        return jmxConnectionInfo;
    }

    /**
     * 连接失败的JMX的初始化动作
     * @param desc
     */
    private JMXConnectionInfo initBadJMXConnect(VirtualMachineDescriptor desc){
        JMXConnectionInfo jmxConnectionInfo = new JMXConnectionInfo();
        jmxConnectionInfo.setValid(false,JMXUnavailabilityType.connectionFailed);
        jmxConnectionInfo.setConnectionServerName(serverName);
        jmxConnectionInfo.setConnectionQualifiedServerName(desc.displayName());
        jmxConnectionInfo.setPid(Integer.parseInt(desc.id()));
        connectCacheLibrary.put(serverName + desc.id(),jmxConnectionInfo);
        return jmxConnectionInfo;
    }

    /**
     * 连接失败的JMX的初始化动作
     * @param commandInfo
     */
    private JMXConnectionInfo initBadJMXConnect(JMXExecuteCommandInfo commandInfo){
        JMXConnectionInfo jmxConnectionInfo = new JMXConnectionInfo();
        jmxConnectionInfo.setValid(false,JMXUnavailabilityType.connectionFailed);
        jmxConnectionInfo.setConnectionServerName(commandInfo.getAppName());
        jmxConnectionInfo.setConnectionQualifiedServerName(commandInfo.getAppName());
        jmxConnectionInfo.setPid(-1);
        connectCacheLibrary.put(commandInfo.getAppName(),jmxConnectionInfo);
        return jmxConnectionInfo;
    }

}
