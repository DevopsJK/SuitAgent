/*
 * www.yiji.com Inc.
 * Copyright (c) 2016 All Rights Reserved
 */
package com.falcon.suitagent.plugins.plugin.docker;
/*
 * 修订记录:
 * guqiu@yiji.com 2016-08-10 11:15 创建
 */

import com.falcon.suitagent.config.AgentConfiguration;
import com.falcon.suitagent.falcon.CounterType;
import com.falcon.suitagent.plugins.DetectPlugin;
import com.falcon.suitagent.util.CommandUtilForUnix;
import com.falcon.suitagent.util.HostUtil;
import com.falcon.suitagent.util.OSUtil;
import com.falcon.suitagent.util.StringUtils;
import com.falcon.suitagent.vo.detect.DetectResult;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Docker的监控插件
 * @author guqiu@yiji.com
 */
@Slf4j
public class DockerPlugin implements DetectPlugin {

    private String cAdvisorPath;
    private int cAdvisorStartPort = 0;
    private int cAdvisorPort = 0;
    private int step;
    private static final List<String> addressesCache = new ArrayList<>();
    private CAdvisorRunner cadvisorRunner;

    /**
     * 自动探测地址的实现
     * 若配置文件已配置地址,将不会调用此方法
     * 若配置文件未配置探测地址的情况下,将会调用此方法,若该方法返回非null且有元素的集合,则启动运行插件,使用该方法返回的探测地址进行监控
     *
     * @return
     */
    @Override
    public Collection<String> autoDetectAddress() {
        //只在linux下启动
        if(OSUtil.isLinux()){
            if(!addressesCache.isEmpty()){
                return addressesCache;
            }

            if (AgentConfiguration.INSTANCE.isDockerRuntime()){
                String cAdvisorConfPort = System.getenv("cadvisor_port");
                if (StringUtils.isNotEmpty(cAdvisorConfPort)){//检查容器的环境变量：cadviosr.port
                    //传递cAdvisor监听端口为启动地址
                    addressesCache.add(String.valueOf(cAdvisorConfPort));
                }else if (this.cAdvisorPort != 0){//检查指定的配置项
                    //传递cAdvisor监听端口为启动地址
                    addressesCache.add(String.valueOf(cAdvisorPort));
                }else{
                    if(startCAdvisor()){// 启动内置的CAdvisor
                        //传递cAdvisor监听端口为启动地址
                        addressesCache.add(String.valueOf(cAdvisorStartPort));
                    }
                }
            }else {
                File docker = new File("/usr/bin/docker");
                if (this.cAdvisorPort != 0){//检查指定的配置项
                    //传递cAdvisor监听端口为启动地址
                    addressesCache.add(String.valueOf(cAdvisorPort));
                }else if(docker.exists()){
                    int cAdvisorPort = getNativeCAdvisorPort();
                    if(cAdvisorPort == 0){
                        if(startCAdvisor()){
                            cAdvisorPort = this.cAdvisorStartPort;
                        }
                    }
                    if(cAdvisorPort != 0){
                        //传递cAdvisor监听端口为启动地址
                        addressesCache.add(String.valueOf(cAdvisorPort));
                    }
                }
            }

            return addressesCache;
        }else {
            log.warn("Docker插件只在Linux环境下有效");
            return new ArrayList<>();
        }
    }

    /**
     * 获取本机启动的cAdvisor连接端口
     * @return
     * 0 ：获取失败（本地未启动cAdvisor服务或获取失败）
     */
    private int getNativeCAdvisorPort(){
        String warnMsg = "尝试获取本机启动的cAdvisor连接端口时失败，这将使SuitAgent尝试启动内置的cAdvisor服务";
        try {
            CommandUtilForUnix.ExecuteResult executeResult = CommandUtilForUnix.execWithReadTimeLimit("docker ps",false,7);
            if(!executeResult.isSuccess){
                log.error("{} : {}",warnMsg,executeResult.msg);
                return 0;
            }
            String msg = executeResult.msg;
            StringTokenizer st = new StringTokenizer(msg,"\n",false);
            while( st.hasMoreElements() ){
                String split = st.nextToken();
                if(split.contains("google/cadvisor")){
                    String[] ss = split.split("\\s+");
                    for (String s : ss) {
                        if(s.contains("->")){
                            String[] ss2 = s.trim().split("->");
                            for (String s1 : ss2[0].split(":")) {
                                if(s1.matches("\\d+")){
                                    int port = Integer.parseInt(s1);
                                    log.info("检测到本地启动的cAdvisor服务端口：{}",port);
                                    return port;
                                }
                            }
                        }
                    }
                }
            }
            log.info("未检测到本机有cAdvisor服务");
        } catch (IOException e) {
            log.error("{}",warnMsg,e);
            return 0;
        }
        return 0;
    }

    /**
     * 启动cAdvisor
     * @return
     */
    private boolean startCAdvisor(){
        if(this.cAdvisorStartPort == 0){
            log.error("请配置cAdvisor的端口地址");
            return false;
        }

        if(!new File(this.cAdvisorPath).exists()){
            log.error("{} 不存在，Docker 插件启动失败", cAdvisorPath);
            return false;
        }

        cadvisorRunner = new CAdvisorRunner(cAdvisorPath, cAdvisorStartPort);
        cadvisorRunner.start();

        return true;
    }

    /**
     * 监控的具体服务的agentSignName tag值
     *
     * @param address 被监控的探测地址
     * @return 根据地址提炼的标识, 如域名等
     */
    @Override
    public String agentSignName(String address) {
        return null;
    }

    /**
     * 一次地址的探测结果
     *
     * @param address 被探测的地址,地址来源于方法 {@link DetectPlugin#detectAddressCollection()}
     * @return 返回被探测的地址的探测结果, 将用于上报监控状态
     */
    @Override
    public DetectResult detectResult(String address) {
        DetectResult detectResult = new DetectResult();
        try {
            DockerMetrics dockerMetrics = new DockerMetrics(HostUtil.getHostIp(),Integer.parseInt(address));
            List<DockerMetrics.CollectObject> collectObjectList = dockerMetrics.getMetrics();

            List<DetectResult.Metric> metrics = new ArrayList<>();
            for (DockerMetrics.CollectObject collectObject : collectObjectList) {
                DetectResult.Metric metric = new DetectResult.Metric(collectObject.getMetric(),
                        collectObject.getValue(),
                        CounterType.GAUGE,
                        "containerName=" + collectObject.getContainerName() + (StringUtils.isEmpty(collectObject.getTags()) ? "" : ("," + collectObject.getTags())));
                metrics.add(metric);
            }
            detectResult.setMetricsList(metrics);

            detectResult.setSuccess(true);

        } catch (Exception e) {
            log.error("Docker数据采集异常",e);
            detectResult.setSuccess(false);
        }

        return detectResult;
    }

    /**
     * 被探测的地址集合
     *
     * @return 只要该集合不为空, 就会触发监控
     * pluginActivateType属性将不起作用
     */
    @Override
    public Collection<String> detectAddressCollection() {
        return new ArrayList<>();
    }

    /**
     * 插件初始化操作
     * 该方法将会在插件运行前进行调用
     *
     * @param properties 包含的配置:
     *                   1、插件目录绝对路径的(key 为 pluginDir),可利用此属性进行插件自定制资源文件读取
     *                   2、插件指定的配置文件的全部配置信息(参见 {@link com.falcon.suitagent.plugins.Plugin#configFileName} 接口项)
     *                   3、授权配置项(参见 {@link com.falcon.suitagent.plugins.Plugin#authorizationKeyPrefix} 接口项
     */
    @Override
    public void init(Map<String, String> properties) {
        this.step = Integer.parseInt(properties.get("step"));
        this.cAdvisorPath = properties.get("pluginDir") + File.separator + "cadvisor";
        this.cAdvisorStartPort = Integer.parseInt(properties.get("cadvisor.start.port"));
        if (StringUtils.isNotEmpty(properties.get("cadvisor.port"))){
            this.cAdvisorPort = Integer.parseInt(properties.get("cadvisor.port"));
        }
    }

    /**
     * 该插件监控的服务名
     * 该服务名会上报到Falcon监控值的tag(service)中,可用于区分监控值服务
     *
     * @return
     */
    @Override
    public String serverName() {
        return "docker";
    }

    /**
     * 监控值的获取和上报周期(秒)
     *
     * @return
     */
    @Override
    public int step() {
        return this.step;
    }

    /**
     * Agent关闭时的调用钩子
     * 如，可用于插件的资源释放等操作
     */
    @Override
    public void agentShutdownHook() {
        if(cadvisorRunner != null){
            try {
                cadvisorRunner.shutdownCAdvisor();
            } catch (IOException e) {
                log.error("",e);
            }
        }
    }
}
