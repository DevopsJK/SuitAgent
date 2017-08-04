/*
 * www.msxf.com Inc.
 * Copyright (c) 2017 All Rights Reserved
 */
package com.falcon.suitagent.plugins.plugin.k8sJMX;
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
 * long.qian@msxf.com 2017-07-25 14:46 创建
 */

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.falcon.suitagent.falcon.FalconReportObject;
import com.falcon.suitagent.jmx.vo.JMXMetricsValueInfo;
import com.falcon.suitagent.plugins.JMXPlugin;
import com.falcon.suitagent.plugins.Plugin;
import com.falcon.suitagent.plugins.util.PluginActivateType;
import com.falcon.suitagent.util.StringUtils;
import com.falcon.suitagent.vo.jmx.JavaExecCommandInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author long.qian@msxf.com
 */
@Slf4j
public class K8sJmxPlugin implements JMXPlugin {

    private int step;
    private PluginActivateType pluginActivateType;
    private String apiServer;
    private String p12ClientCertificatePath;
    private String certificatePassword;
    private boolean hasWatched = false;
    private K8sApiServerConnector k8sApiServerConnector;

    private static final List<JavaExecCommandInfo> commandInfos = new ArrayList<>();

    /**
     * 自定义的监控属性的监控值基础配置名
     *
     * @return 若无配置文件, 可返回null
     */
    @Override
    public String basePropertiesKey() {
        return null;
    }

    /**
     * 该插件所要监控的服务在JMX连接中的displayName识别名
     * 若有该插件监控的相同类型服务,但是displayName不一样,可用逗号(,)进行分隔,进行统一监控
     *
     * @return
     */
    @Override
    public String jmxServerName() {
        return null;
    }

    /**
     * Java应用启动时的命令信息的列表集合
     * 若实现该接口，则该方法中返回描述的Java应用将会自动建立JMX连接进行监控
     *
     * @return
     * 请勿返回null
     * 默认返回空集合
     */
    @Override
    public List<JavaExecCommandInfo> commandInfos() {
        synchronized (commandInfos){
            return commandInfos;
        }
    }

    /**
     * 该插件监控的服务标记名称,目的是为能够在操作系统中准确定位该插件监控的是哪个具体服务
     * 可用变量:
     * {jmxServerName} - 代表直接使用当前服务的jmxServerName
     * 如该服务运行的端口号等
     * 若不需要指定则可返回null
     *
     * @param jmxMetricsValueInfo 该服务连接的jmx对象信息
     * @param pid                 该服务当前运行的进程id
     * @return 若不实现，默认返回NO NAME，代表该插件无特定的agentSignName
     */
    @Override
    public String agentSignName(JMXMetricsValueInfo jmxMetricsValueInfo, int pid) {
        String appName = jmxMetricsValueInfo.getJmxConnectionInfo().getConnectionServerName();//该值为JMXExecuteCommandInfo.appName
        for (JavaExecCommandInfo javaExecCommandInfo : commandInfos()) {
            if (javaExecCommandInfo.getAppName().equals(appName)){
                //若需要监控的配置中存在此appName，返回appName作为标识名
                return appName;
            }
        }
        //返回null，将会清除此appName的JMX监控
        return null;
    }

    /**
     * 插件监控的服务正常运行时的內建监控报告
     * 若有些特殊的监控值无法用配置文件进行配置监控,可利用此方法进行硬编码形式进行获取
     * 注:此方法只有在监控对象可用时,才会调用,并加入到监控值报告中,一并上传
     *
     * @param metricsValueInfo 当前的JMXMetricsValueInfo信息
     * @return
     */
    @Override
    public Collection<FalconReportObject> inbuiltReportObjectsForValid(JMXMetricsValueInfo metricsValueInfo) {
        return null;
    }

    /**
     * 插件初始化操作
     * 该方法将会在插件运行前进行调用
     *
     * @param properties 包含的配置:
     *                   1、插件目录绝对路径的(key 为 pluginDir),可利用此属性进行插件自定制资源文件读取
     *                   2、插件指定的配置文件的全部配置信息(参见 {@link Plugin#configFileName} 接口项)
     *                   3、授权配置项(参见 {@link Plugin#authorizationKeyPrefix} 接口项
     */
    @Override
    public void init(Map<String, String> properties) {
        step = Integer.parseInt(properties.get("step"));
        p12ClientCertificatePath = properties.get("p12ClientCertificatePath");
        if (p12ClientCertificatePath != null){
            p12ClientCertificatePath = p12ClientCertificatePath.replace("\"","");
        }
        certificatePassword = properties.get("certificatePassword");
        if (certificatePassword != null){
            certificatePassword = certificatePassword.replace("\"","");
        }
        pluginActivateType = PluginActivateType.valueOf(properties.get("pluginActivateType"));
        apiServer = properties.get("apiServer");
        if (apiServer != null){
            apiServer = apiServer.replace("\"","").replace("https://","").replace("http://","");
        }
        try {
            if (!StringUtils.isEmpty(apiServer)){
                reloadCommandInfos();
                watchK8s();
            }
        } catch (Exception e) {
            log.error("{}插件初始化失败",this.getClass().getSimpleName(),e);
        }
    }

    private void reloadCommandInfos() throws Exception {
        synchronized (commandInfos){
            commandInfos.clear();
            k8sApiServerConnector = new K8sApiServerConnector(p12ClientCertificatePath,certificatePassword);
            PodsLibrary.addOrSetPods(K8sApiServerUtil.getAllPods(k8sApiServerConnector,apiServer),true);
            for (Pod pod : PodsLibrary.getPods()) {
                int count = 0;
                for (Pod.Container container : pod.getContainers()) {
                    //若Pod中有多个容器
                    count++;
                    if (container.getCmd().contains("java")){
                        JavaExecCommandInfo commandInfo = new JavaExecCommandInfo();
                        if (count > 1){
                            commandInfo.setAppName(pod.getFullName());
                        }else {
                            commandInfo.setAppName(pod.getFullName() + "-" + count);
                        }
                        commandInfo.setCommand(container.getArgs());
                        commandInfo.setIp(pod.getPodIP());
                        commandInfos.add(commandInfo);
                    }
                }
            }
        }
    }

    private void watchK8s() throws Exception {
        if (!hasWatched){//若还没有进行watch
            k8sApiServerConnector.longRequestForGet("https://" + this.apiServer + "/api/v1/watch/pods", true,
                    line -> {
                        try {
                            JSONObject jsonObject = JSON.parseObject(line);
                            String type = jsonObject.getString("type");
                            JSONObject object = jsonObject.getJSONObject("object");
                            Pod pod = new Pod();
                            pod.setFromMetadataJSON(object.getJSONObject("metadata"));
                            pod.setFromSpecJSON(object.getJSONObject("spec"));
                            pod.setFromStatusJSON(object.getJSONObject("status"));
                            if ("Running".equals(pod.getPhase())){
                                switch (type){
                                    case "ADDED":{
                                        PodsLibrary.addOrSetPod(pod,true);
                                        reloadCommandInfos();
                                        break;
                                    }
                                    case "MODIFIED":{
                                        PodsLibrary.addOrSetPod(pod,true);
                                        reloadCommandInfos();
                                        break;
                                    }
                                    case "DELETED":{
                                        PodsLibrary.removePod(pod.getFullName());
                                        reloadCommandInfos();
                                        break;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.error("",e);
                        }
                    });
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
        return "k8sJavaApp";
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
     * 插件运行方式
     *
     * @return
     */
    @Override
    public PluginActivateType activateType() {
        return this.pluginActivateType;
    }

    /**
     * Agent关闭时的调用钩子
     * 如，可用于插件的资源释放等操作
     */
    @Override
    public void agentShutdownHook() {

    }
}
