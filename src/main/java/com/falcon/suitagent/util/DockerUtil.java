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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.falcon.suitagent.config.AgentConfiguration;
import com.falcon.suitagent.vo.docker.ContainerProcInfoToHost;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author long.qian@msxf.com
 */
@Slf4j
public class DockerUtil {

    public static final String DOCKER_VOLUME = "/var/lib/docker_host";
    public static final String DOCKER_CONTAINER_VOLUME = DOCKER_VOLUME + "/containers";
    public static final String PROC_HOST_VOLUME = "/proc_host";

    /**
     * 判断当前容器环境是否有有效的目录挂载
     * @return
     */
    public static boolean hasValidDockerVolume(){
        if (!AgentConfiguration.INSTANCE.isDockerRuntime()){
            return false;
        }
        File file_dockerContainerDir = new File(DOCKER_CONTAINER_VOLUME);
        if (!file_dockerContainerDir.exists()){
            log.error("必须指定docker run参数：-v /var/lib/docker:/var/lib/docker_host:ro");
            return false;
        }
        return true;
    }

    /**
     * 判断是否存在容器目录
     * @return
     */
    private static boolean isExistContainerDir(){
        if (hasValidDockerVolume()){
            File file_containers = new File(DOCKER_CONTAINER_VOLUME);
            if (file_containers.exists()){
                return true;
            }else {
                log.error("目录不存在：{}",DOCKER_CONTAINER_VOLUME);
            }
        }
        return false;
    }

    /**
     * 获取主机上所有运行容器的proc信息
     * @return
     */
    public static List<ContainerProcInfoToHost> getAllHostContainerProcInfos(){
        List<ContainerProcInfoToHost> procInfoToHosts = new ArrayList<>();
        if (isExistContainerDir()){
            File file_containers = new File(DOCKER_CONTAINER_VOLUME);
            String[] containerIds = file_containers.list();
            if (containerIds != null) {
                for (String containerId : containerIds) {
                    String configContent = getConfigFileContent(containerId);
                    if (StringUtils.isNotEmpty(configContent)){
                        JSONObject config = JSON.parseObject(configContent);
                        String pid = config.getJSONObject("State").getString("Pid");
                        procInfoToHosts.add(new ContainerProcInfoToHost(containerId,PROC_HOST_VOLUME + "/" + pid + "/root",pid));
                    }
                }
            }
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
    public static String getJavaContainerAppName(String containerId){
        String configContent = getConfigFileContent(containerId);
        if (StringUtils.isNotEmpty(configContent)){
            if (!StringUtils.isEmpty(configContent)){
                JSONObject config = JSON.parseObject(configContent);
                JSONArray env = config.getJSONObject("Config").getJSONArray("Env");
                if (env != null){
                    for (Object o : env) {
                        String e = (String) o;
                        String[] split = e.split(e.contains("=") ? "=" : ":");
                        if (split.length == 2){
                            String key = split[0];
                            String value = split[1];
                            if ("appName".equals(key)){
                                return value;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 获取容器配置内容
     * @param containerId
     * @return
     */
    private static String getConfigFileContent(String containerId){
        String configPath = DOCKER_CONTAINER_VOLUME + "/" + containerId + "/" + "config.v2.json";
        return FileUtil.getTextFileContent(configPath);
    }

    /**
     * 容器网络模式是否为host模式
     * @param containerId
     * @return
     */
    public static boolean isHostNet(String containerId){
        String configContent = getConfigFileContent(containerId);
        if (StringUtils.isNotEmpty(configContent)){
            JSONObject config = JSON.parseObject(configContent);
            JSONObject net = config.getJSONObject("NetworkSettings").getJSONObject("Networks");
            return net.get("host") != null;
        }
        return false;
    }

    /**
     * 获取容器IP地址
     * @param containerId
     * 容器ID
     * @return
     * 1、获取失败返回null
     * 2、host网络模式直接返回宿主机IP
     */
    public static String getContainerIp(String containerId) throws Exception {
        if (isHostNet(containerId)){
            return HostUtil.getHostIp();
        }
        String configContent = getConfigFileContent(containerId);
        if (StringUtils.isNotEmpty(configContent)){
            JSONObject config = JSON.parseObject(configContent);
            JSONObject net = config.getJSONObject("NetworkSettings").getJSONObject("Networks");
            return net.keySet().stream().map(net::getJSONObject).findFirst().map(netConfig -> netConfig.getString("IPAddress")).orElse(null);
        }
        return null;
    }
}
