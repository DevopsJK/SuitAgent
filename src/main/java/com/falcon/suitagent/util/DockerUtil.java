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
 * long.qian@msxf.com 2017-08-04 16:53 创建
 */

import com.falcon.suitagent.config.AgentConfiguration;
import com.falcon.suitagent.vo.docker.ContainerProcInfoToHost;
import com.google.common.collect.ImmutableMap;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.messages.AttachedNetwork;
import com.spotify.docker.client.messages.Container;
import com.spotify.docker.client.messages.ContainerInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.math.NumberUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author long.qian@msxf.com
 */
@Slf4j
public class DockerUtil {

    private static final ConcurrentHashMap<String,ConcurrentHashMap<Long,Object>> CATCH = new ConcurrentHashMap<>();
    private static int CACHE_TIME = 600;//缓存失效时间，默认10分钟

    private static final String PROC_HOST_VOLUME = "/proc_host";
    private static DockerClient docker = null;
    static {
        if (AgentConfiguration.INSTANCE.isDockerRuntime()){
            try {
                docker = new DefaultDockerClient("unix:///var/run/docker.sock");
            } catch (Exception e) {
                log.error("docker client初始化失败，可能未挂在/var/run目录或无文件/var/run/docker.sock的访问权限",e);
                throw e;
            }
        }
    }

    public static void closeDockerClient(){
        if (docker != null){
            docker.close();
        }

        /*
            缓存时间设置：
            优先级：系统变量 > 系统环境变量
         */
        String cacheTime = System.getProperty("DOCKET_CACHE_TIME");
        if (StringUtils.isEmpty(cacheTime)){
            cacheTime = System.getenv("DOCKET_CACHE_TIME");
        }
        if (StringUtils.isNotEmpty(cacheTime)){
            if (NumberUtils.isNumber(cacheTime)){
                CACHE_TIME = Integer.parseInt(cacheTime);
            }else {
                log.error("DOCKET_CACHE_TIME只能数字：{}",cacheTime);
            }
        }

        //缓存周期检查任务
        Thread cacheCheck = new Thread(new CacheCheck());
        cacheCheck.setName("DockerUtil Cache Check Thread");
        cacheCheck.setDaemon(true);
        cacheCheck.start();
    }

    /**
     * 缓存检查，设置为守护线程，随JVM消亡而自动消亡
     */
    private static class CacheCheck implements Runnable{
        @Override
        public void run() {
           while (true){
               try {
                   Thread.sleep(CACHE_TIME * 1000 * 3 + 10000);//检查周期
                   Set<String> keys = CATCH.keySet();
                   for (String key : keys) {
                       if (getCache(key) == null){
                           CATCH.remove(key);
                       }
                   }
               } catch (Exception e) {
                   log.error("",e);
               }
           }
        }
    }

    /**
     * 获取缓存数据
     * @param key
     * @return
     * 未设置或缓存有效时间已过都会返回null
     */
    private static Object getCache(String key) {
        return getCache(key,CACHE_TIME);
    }

    /**
     * 获取缓存数据
     * @param key
     * @return
     * 未设置或缓存有效时间已过都会返回null
     */
    private static Object getCache(String key,Integer cacheTime) {
        if (StringUtils.isNotEmpty(key)){
            ConcurrentHashMap<Long,Object> cache = CATCH.get(key);
            if (cache != null){
                Optional<Long> time = cache.keySet().stream().findFirst();
                if (time.isPresent()){
                    long now = System.currentTimeMillis();
                    if ((now - time.get()) < (cacheTime * 1000)){
                        return cache.get(time.get());
                    }else {
                        //缓存失效
                        cache.clear();
                        CATCH.put(key,cache);
                    }
                }
            }
        }
        return null;
    }

    /**
     * 设置缓存
     * @param key
     * @param value
     */
    private static void setCache(String key,Object value) {
        if (StringUtils.isNotEmpty(key) && value != null){
            ConcurrentHashMap<Long,Object> cache = CATCH.get(key);
            if (cache == null){
                cache = new ConcurrentHashMap<>();
            }
            cache.clear();
            cache.put(System.currentTimeMillis(),value);
            CATCH.put(key,cache);
        }
    }


    /**
     * 获取容器信息
     * @param containerId
     * @return
     */
    public static ContainerInfo getContainerInfo(String containerId){
        String cacheKey = "containerInfoCacheKey" + containerId;
        ContainerInfo containerInfo = (ContainerInfo) getCache(cacheKey);
        if (containerInfo != null){
            return containerInfo;
        }else {
            try {
                containerInfo = docker.inspectContainer(containerId);
                setCache(cacheKey,containerInfo);
                return containerInfo;
            } catch (Exception e) {
                log.error("",e);
                return null;
            }
        }
    }

    /**
     * 获取主机上所有运行容器的proc信息
     * @return
     */
    public static List<ContainerProcInfoToHost> getAllHostContainerProcInfos(){
        String cacheKey = "ALL_HOST_CONTAINER_PROC_INFOS";
        List<ContainerProcInfoToHost> procInfoToHosts = (List<ContainerProcInfoToHost>) getCache(cacheKey);
        if (procInfoToHosts != null){
            //返回缓存数据
            return procInfoToHosts;
        }else {
            procInfoToHosts = new ArrayList<>();
        }
        if (docker != null){
            try {
                List<Container> containers = docker.listContainers(DockerClient.ListContainersParam.withStatusRunning());
                for (Container container : containers) {
                    ContainerInfo info = docker.inspectContainer(container.id());
                    String pid = String.valueOf(info.state().pid());
                    procInfoToHosts.add(new ContainerProcInfoToHost(container.id(),PROC_HOST_VOLUME + "/" + pid + "/root",pid));
                }
            } catch (Exception e) {
                log.error("",e);
            }
        }
        if (!procInfoToHosts.isEmpty()) {
            //设置缓存
            setCache(cacheKey,procInfoToHosts);
        }
        return procInfoToHosts;
    }

    /**
     * 获取Java应用容器的应用名称
     * 注：
     * 必须通过docker run命令的-e参数执行应用名，例如 docker run -e "appName=suitAgent"
     * @param containerId
     * 容器id
     * @return
     * 若未指定应用名称或获取失败返回null
     */
    public static String getJavaContainerAppName(String containerId) throws InterruptedException {
        String cacheKey = "appName" + containerId;
        String v = (String) getCache(cacheKey);
        if (StringUtils.isNotEmpty(v)) {
            return v;
        }
        try {
            ContainerInfo containerInfo = getContainerInfo(containerId);
            if (containerInfo != null){
                List<String> env = containerInfo.config().env();
                if (env != null){
                    for (String s : env) {
                        String[] split = s.split(s.contains("=") ? "=" : ":");
                        if (split.length == 2){
                            String key = split[0];
                            String value = split[1];
                            if ("appName".equals(key)){
                                setCache(cacheKey,value);
                                return value;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("",e);
        }

        return null;
    }


    /**
     * 容器网络模式是否为host模式
     * @param containerId
     * @return
     */
    public static boolean isHostNet(String containerId){
        String cacheKey = "isHostNet" + containerId;
        Boolean v = (Boolean) getCache(cacheKey);
        if (v != null){
            return v;
        }
        Boolean value = false;
        try {
            ContainerInfo containerInfo = getContainerInfo(containerId);
            if (containerInfo != null) {
                ImmutableMap<String, AttachedNetwork> networks =  containerInfo.networkSettings().networks();
                if (networks != null && !networks.isEmpty()){
                    value = networks.get("host") != null && StringUtils.isNotEmpty(networks.get("host").ipAddress());
                    setCache(cacheKey,value);
                }else {
                    log.warn("容器{}无Networks配置",containerInfo.name());
                }
            }
        } catch (Exception e) {
            log.error("",e);
        }
        return value;
    }

    /**
     * 获取容器主机名
     * @param containerId
     * @return
     */
    public static String getHostName(String containerId){
        try {
            ContainerInfo containerInfo = getContainerInfo(containerId);
            if (containerInfo != null) {
                return containerInfo.config().hostname();
            }
        } catch (Exception e) {
            log.error("",e);
        }
        return "";
    }

    /**
     * 获取容器IP地址
     * @param containerId
     * 容器ID
     * @return
     * 1、获取失败返回null
     * 2、host网络模式直接返回宿主机IP
     */
    public static String getContainerIp(String containerId){
        String cacheKey = "containerIp" + containerId;
        String v = (String) getCache(cacheKey);
        if (StringUtils.isNotEmpty(v)) {
            return v;
        }
        try {
            if (isHostNet(containerId)){
                return HostUtil.getHostIp();
            }
            ContainerInfo containerInfo = getContainerInfo(containerId);
            if (containerInfo != null) {
                ImmutableMap<String, AttachedNetwork> networks =  containerInfo.networkSettings().networks();
                if (networks != null && !networks.isEmpty()){
                    String ip = networks.get(networks.keySet().asList().get(0)).ipAddress();
                    setCache(cacheKey,ip);
                    return ip;
                }else {
                    log.warn("容器{}无Networks配置",containerInfo.name());
                }
            }
        } catch (Exception e) {
            log.error("",e);
        }

        return null;
    }

    /**
     * 判断本地所有容器（所有状态）中，是否存在容器id前12位字符串包含在指定的名称中
     * @param name
     * @return
     */
    public static boolean has12ContainerIdInName(String name) {
        if (StringUtils.isEmpty(name)){
            return false;
        }
        try {
            for (String id : getAllHostContainerId()) {
                if (name.contains(id.substring(0,12))){
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("",e);
            return false;
        }
        return false;
    }


    private static List<String> getAllHostContainerId(){
        String cacheKey = "allHostContainerIds";
        int cacheTime = 28;//28秒缓存周期，保证一次采集job只需要访问一次docker即可

        List<String> ids = (List<String>) getCache(cacheKey,cacheTime);
        if (ids != null){
            return ids;
        }else {
            ids = new ArrayList<>();
        }
        try {
            List<Container> containerList = docker.listContainers(DockerClient.ListContainersParam.allContainers());
            for (Container container : containerList) {
                ids.add(container.id());
            }
        } catch (Exception e) {
            log.error("",e);
        }
        setCache(cacheKey,ids);
        return ids;
    }

}
