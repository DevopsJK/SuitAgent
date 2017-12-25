/*
 * www.yiji.com Inc.
 * Copyright (c) 2016 All Rights Reserved
 */
package com.falcon.suitagent.config;

import com.falcon.suitagent.util.StringUtils;
import lombok.Getter;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/*
 * 修订记录:
 * guqiu@yiji.com 2016-06-22 17:48 创建
 */

/**
 * 系统配置
 * @author guqiu@yiji.com
 */
@Getter
public enum  AgentConfiguration {

    /**
     * 唯一实例
     */
    INSTANCE;

    //版本不能大于 x.9
    public static final float VERSION = (float)  18.9;

    /**
     * quartz配置文件路径
     */
    private String quartzConfPath = null;
    /**
     * push到falcon的地址
     */
    private String agentPushUrl = null;

    /**
     * 插件配置的目录r
     */
    private String pluginConfPath;

    /**
     * 授权配置文件路径
     */
    private String authorizationConfPath;

    /**
     * 集成的Falcon文件夹路径
     */
    private String falconDir;

    /**
     * Falcon的配置文件目录路径
     */
    private String falconConfDir;

    /**
     * 服务配置文件路径
     */
    private String agentConfPath = null;
    private String jmxCommonMetricsConfPath = null;

    /**
     * agent监控指标的主体说明
     */
    private String agentEndpoint = "";

    /**
     * agent启动端口
     */
    private int agentPort = 4518;

    /**
     * mock的有效时间
     */
    private int mockValidTime = 10800;

    /**
     * agent web 监听端口
     */
    private int agentWebPort = 4519;

    /**
     * 是否启用web服务
     */
    private boolean webEnable = true;

    /**
     * agent自动发现服务刷新周期
     */
    private int agentFlushTime = 300;

    /**
     * agent最大线程数
     */
    private int agentMaxThreadCount = 200;

    /**
     * JMX连接是否支持本地连接
     */
    private boolean agentJMXLocalConnect = false;

    private String agentUpdateUrl = null;

    private String agentHomeDir;

    /**
     * 是否为Docker容器的运行环境
     */
    private boolean dockerRuntime = false;


    private static final String CONF_AGENT_ENDPOINT = "agent.endpoint";
    private static final String CONF_AGENT_HOME = "agent.home.dir";
    private static final String CONF_AGENT_FLUSH_TIME = "agent.flush.time";
    private static final String CONF_AGENT_MAX_THREAD = "agent.thread.maxCount";

    private static final String CONF_AGENT_FALCON_PUSH_URL = "agent.falcon.push.url";
    private static final String CONF_AGENT_PORT = "agent.port";
    private static final String CONF_AGENT_WEB_PORT = "agent.web.port";
    private static final String CONF_AGENT_WEB_ENABLE = "agent.web.enable";
    private static final String CONF_AGENT_MOCK_VALID_TIME = "agent.mock.valid.time";
    private static final String AUTHORIZATION_CONF_PATH = "authorization.conf.path";
    private static final String FALCON_DIR_PATH = "agent.falcon.dir";
    private static final String FALCON_CONF_DIR_PATH = "agent.falcon.conf.dir";
    private static final String CONF_AGENT_JMX_LOCAL_CONNECT = "agent.jmx.localConnectSupport";
    private static final String CONF_AGENT_UPDATE_URL = "agent.update.pack.url";

    private Properties agentConf = null;

    /**
     * 初始化agent配置
     */
    AgentConfiguration() {
        instance();
    }

    private void instance(){
        if(StringUtils.isEmpty(System.getProperty("agent.conf.path"))){
            System.err.println("agent agent.properties 配置文件位置读取失败,请确定系统配置项:" + "agent.conf.path");
            System.exit(0);
        }else{
            this.agentConfPath = System.getProperty("agent.conf.path");
        }

        if(StringUtils.isEmpty(System.getProperty(CONF_AGENT_HOME))){
            System.err.println("agent home dir 读取失败,请确定系统配置项:" + CONF_AGENT_HOME);
            System.exit(0);
        }else{
            this.agentHomeDir = System.getProperty(CONF_AGENT_HOME);
        }

        if(StringUtils.isEmpty(System.getProperty(AUTHORIZATION_CONF_PATH))){
            System.err.println("agent authorization.properties 配置文件位置读取失败,请确定系统配置项:" + AUTHORIZATION_CONF_PATH);
            System.exit(0);
        }else{
            this.authorizationConfPath = System.getProperty(AUTHORIZATION_CONF_PATH);
        }

        if(StringUtils.isEmpty(System.getProperty(FALCON_DIR_PATH))){
            System.err.println("falcon 目录位置读取失败,请确定系统配置项:" + FALCON_DIR_PATH);
            System.exit(0);
        }else{
            this.falconDir = System.getProperty(FALCON_DIR_PATH);
        }

        if(StringUtils.isEmpty(System.getProperty(FALCON_CONF_DIR_PATH))){
            System.err.println("falcon conf 目录位置读取失败,请确定系统配置项:" + FALCON_CONF_DIR_PATH);
            System.exit(0);
        }else{
            this.falconConfDir = System.getProperty(FALCON_CONF_DIR_PATH);
        }

        if(StringUtils.isEmpty(System.getProperty("agent.plugin.conf.dir"))){
            System.err.println("agent 配置文件位置读取失败,请确定系统配置项:" + "agent.plugin.conf.dir");
            System.exit(0);
        }else{
            this.pluginConfPath = System.getProperty("agent.plugin.conf.dir");
        }

        if(StringUtils.isEmpty(System.getProperty("agent.quartz.conf.path"))){
            System.err.println("quartz 配置文件位置读取失败,请确定系统配置项:" + "agent.quartz.conf.path");
            System.exit(0);
        }else{
            this.quartzConfPath = System.getProperty("agent.quartz.conf.path");
        }

        agentConf = new Properties();
        try(FileInputStream in = new FileInputStream(this.agentConfPath)){
            agentConf.load(in);
        }catch (IOException e) {
            System.err.println(this.agentConfPath + " 配置文件读取失败 Agent启动失败");
            System.exit(0);
        }
        if ("true".equals(System.getProperty("runtime.docker"))){
            this.dockerRuntime = true;
        }
        init();
        initJMXCommon();
    }

    private void init(){

        if (!StringUtils.isEmpty(System.getProperty(CONF_AGENT_ENDPOINT))){
            this.agentEndpoint = System.getProperty(CONF_AGENT_ENDPOINT);
        }else {
            if(!StringUtils.isEmpty(agentConf.getProperty(CONF_AGENT_ENDPOINT))){
                this.agentEndpoint = agentConf.getProperty(CONF_AGENT_ENDPOINT);
            }
        }

        try {
            if (!StringUtils.isEmpty(System.getProperty(CONF_AGENT_WEB_PORT))){
                this.agentWebPort = Integer.parseInt(System.getProperty(CONF_AGENT_WEB_PORT));
            }else {
                if(!StringUtils.isEmpty(agentConf.getProperty(CONF_AGENT_WEB_PORT))){
                    this.agentWebPort = Integer.parseInt(agentConf.getProperty(CONF_AGENT_WEB_PORT));
                }
            }
        } catch (Exception e) {
            System.err.println(String.format("Agent启动失败,端口配置%s无效:%s", CONF_AGENT_WEB_PORT, agentConf.getProperty(CONF_AGENT_WEB_PORT)));
            System.exit(0);
        }

        if (!StringUtils.isEmpty(System.getProperty(CONF_AGENT_WEB_ENABLE))){
            this.webEnable = "true".equals(System.getProperty(CONF_AGENT_WEB_ENABLE));
        }else {
            if(!StringUtils.isEmpty(agentConf.getProperty(CONF_AGENT_WEB_ENABLE))){
                this.webEnable = "true".equals(agentConf.getProperty(CONF_AGENT_WEB_ENABLE));
            }
        }

        try {
            if (!StringUtils.isEmpty(System.getProperty(CONF_AGENT_MOCK_VALID_TIME))){
                this.mockValidTime = Integer.parseInt(System.getProperty(CONF_AGENT_MOCK_VALID_TIME));
            }else {
                if(!StringUtils.isEmpty(agentConf.getProperty(CONF_AGENT_MOCK_VALID_TIME))){
                    this.mockValidTime = Integer.parseInt(agentConf.getProperty(CONF_AGENT_MOCK_VALID_TIME));
                }
            }
        } catch (NumberFormatException e) {
            System.err.println(String.format("Agent启动失败,mock有效时间配置 %s 无效: %s", CONF_AGENT_MOCK_VALID_TIME, agentConf.getProperty(CONF_AGENT_MOCK_VALID_TIME)));
            System.exit(0);
        }

        if (!StringUtils.isEmpty(System.getProperty(CONF_AGENT_JMX_LOCAL_CONNECT))){
            this.agentJMXLocalConnect = "true".equals(System.getProperty(CONF_AGENT_JMX_LOCAL_CONNECT));
        }else {
            if(!StringUtils.isEmpty(agentConf.getProperty(CONF_AGENT_JMX_LOCAL_CONNECT))){
                this.agentJMXLocalConnect = "true".equals(agentConf.getProperty(CONF_AGENT_JMX_LOCAL_CONNECT));
            }
        }

        if (!StringUtils.isEmpty(System.getProperty(CONF_AGENT_UPDATE_URL))){
            this.agentUpdateUrl = System.getProperty(CONF_AGENT_UPDATE_URL);
        }else {
            if(!StringUtils.isEmpty(agentConf.getProperty(CONF_AGENT_UPDATE_URL))){
                this.agentUpdateUrl = agentConf.getProperty(CONF_AGENT_UPDATE_URL);
            }
        }

        try {
            if (!StringUtils.isEmpty(System.getProperty(CONF_AGENT_PORT))){
                    this.agentPort = Integer.parseInt(System.getProperty(CONF_AGENT_PORT));
            }else {
                if(!StringUtils.isEmpty(agentConf.getProperty(CONF_AGENT_PORT))){
                    this.agentPort = Integer.parseInt(agentConf.getProperty(CONF_AGENT_PORT));
                }
            }
        } catch (NumberFormatException e) {
            System.err.println(String.format("Agent启动失败,端口配置%s无效:%s", CONF_AGENT_PORT, agentConf.getProperty(CONF_AGENT_PORT)));
            System.exit(0);
        }

        if(StringUtils.isEmpty(agentConf.getProperty(CONF_AGENT_FALCON_PUSH_URL))){
            System.err.println("Agent启动失败,未定义falcon push地址配置:" + CONF_AGENT_FALCON_PUSH_URL);
            System.exit(0);
        }
        this.agentPushUrl = agentConf.getProperty(CONF_AGENT_FALCON_PUSH_URL);

        try {
            if (!StringUtils.isEmpty(System.getProperty(CONF_AGENT_FLUSH_TIME))){
                this.agentFlushTime = Integer.parseInt(System.getProperty(CONF_AGENT_FLUSH_TIME));
            }else {
                if(StringUtils.isEmpty(agentConf.getProperty(CONF_AGENT_FLUSH_TIME))){
                    System.err.println("Agent启动失败,未指定配置:" + CONF_AGENT_FLUSH_TIME);
                    System.exit(0);
                }
                this.agentFlushTime = Integer.parseInt(agentConf.getProperty(CONF_AGENT_FLUSH_TIME));
                if(this.agentFlushTime <= 0){
                    System.err.println(String.format("Agent启动失败,自动发现服务的刷新周期配置 %s 必须大于0: %s",CONF_AGENT_FLUSH_TIME,agentConf.getProperty(CONF_AGENT_FLUSH_TIME)));
                    System.exit(0);
                }
            }
        } catch (NumberFormatException e) {
            System.err.println(String.format("Agent启动失败,自动发现服务的刷新周期配置%s无效:%s",CONF_AGENT_FLUSH_TIME,agentConf.getProperty(CONF_AGENT_FLUSH_TIME)));
            System.exit(0);
        }

        try {
            if (!StringUtils.isEmpty(System.getProperty(CONF_AGENT_MAX_THREAD))){
                this.agentMaxThreadCount = Integer.parseInt(System.getProperty(CONF_AGENT_MAX_THREAD));
            }else {
                if(!StringUtils.isEmpty(agentConf.getProperty(CONF_AGENT_MAX_THREAD))){

                        this.agentMaxThreadCount = Integer.parseInt(agentConf.getProperty(CONF_AGENT_MAX_THREAD));
                        if(this.agentMaxThreadCount <= 5){
                            System.err.println(String.format("Agent启动失败,最大线程数 %s 必须大于5: %s",CONF_AGENT_MAX_THREAD,agentConf.getProperty(CONF_AGENT_MAX_THREAD)));
                            System.exit(0);
                        }
                }
            }
        } catch (NumberFormatException e) {
            System.err.println(String.format("Agent启动失败,最大线程数%s无效:%s",CONF_AGENT_MAX_THREAD,agentConf.getProperty(CONF_AGENT_MAX_THREAD)));
            System.exit(0);
        }

    }

    private void initJMXCommon(){
        String property = System.getProperty("agent.jmx.metrics.common.path");
        if(StringUtils.isEmpty(property)){
            System.err.println("jmx 的公共配置系统属性文件未定义:agent.jmx.metrics.common.path");
            System.exit(0);
        }
        this.jmxCommonMetricsConfPath = property;
    }

}
