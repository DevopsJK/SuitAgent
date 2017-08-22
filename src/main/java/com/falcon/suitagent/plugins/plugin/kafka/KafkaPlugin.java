/*
 * www.yiji.com Inc.
 * Copyright (c) 2017 All Rights Reserved
 */
package com.falcon.suitagent.plugins.plugin.kafka;
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
 * long.qian@msxf.com 2017-05-03 10:44 创建
 */

import com.falcon.suitagent.falcon.FalconReportObject;
import com.falcon.suitagent.jmx.JMXUtil;
import com.falcon.suitagent.jmx.vo.JMXMetricsValueInfo;
import com.falcon.suitagent.plugins.JMXPlugin;
import com.falcon.suitagent.plugins.util.PluginActivateType;
import com.falcon.suitagent.util.CommandUtilForUnix;
import com.falcon.suitagent.util.OSUtil;
import com.falcon.suitagent.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;

/**
 *
 * @author long.qian@msxf.com
 */
@Slf4j
public class KafkaPlugin implements JMXPlugin {

    private String basePropertiesKey;
    private String jmxServerName;
    private int step;
    private PluginActivateType pluginActivateType;

    /**
     * 自定义的监控属性的监控值基础配置名
     *
     * @return 若无配置文件, 可返回null
     */
    @Override
    public String basePropertiesKey() {
        return basePropertiesKey;
    }

    /**
     * 该插件所要监控的服务在JMX连接中的displayName识别名
     * 若有该插件监控的相同类型服务,但是displayName不一样,可用逗号(,)进行分隔,进行统一监控
     *
     * @return
     */
    @Override
    public String jmxServerName() {
        return jmxServerName;
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
     * 仅非容器环境会被调用
     * 该插件监控的服务标记名称,目的是为能够在操作系统中准确定位该插件监控的是哪个具体服务
     * 可用变量:
     * {jmxServerName} - 代表直接使用当前服务的jmxServerName
     * 如该服务运行的端口号等
     *
     * @param jmxMetricsValueInfo 该服务连接的jmx对象信息
     * @param pid                 该服务当前运行的进程id
     * @return 若不实现，默认返回NO NAME，代表该插件无特定的agentSignName
     */
    @Override
    public String agentSignName(JMXMetricsValueInfo jmxMetricsValueInfo, int pid) {
        if (OSUtil.isLinux()){
            //linux系统取kafka的两层目录作为标识名
            try {
                String cmd = String.format("ls -al /proc/%d/fd | grep guava",pid);
                CommandUtilForUnix.ExecuteResult executeResult = CommandUtilForUnix.execWithReadTimeLimit(cmd,false,7);
                String msg = executeResult.msg;
                if (StringUtils.isNotEmpty(msg)){
                    String[] split = msg.split("\n");
                    String path = split[0].substring(split[0].indexOf("->") + 2).trim();
                    Path p = Paths.get(path);
                    String name = p.getParent().getParent().toFile().getName();
                    if (!p.getParent().getParent().getParent().toFile().getName().equals(File.separator)){
                        name = p.getParent().getParent().getParent().toFile().getName() + "-" + name;
                    }
                    return name;
                }
            } catch (IOException e) {
                log.error("",e);
                //获取异常，忽略监控
                return null;
            }
        }
        if (OSUtil.isMac()){
            //mac系统使用JMX端口号作为标识名
            String jmxPort = JMXUtil.getJMXPort(pid);
            if (jmxPort == null){
                //没有开启JMX的kafka，忽略监控
                return null;
            }
            return jmxMetricsValueInfo.getJmxConnectionInfo().getConnectionServerName() + "-JP_" + jmxPort;
        }
        //其他系统
        return "NO NAME";
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
        basePropertiesKey = properties.get("basePropertiesKey");
        jmxServerName = properties.get("jmxServerName");
        step = Integer.parseInt(properties.get("step"));
        pluginActivateType = PluginActivateType.valueOf(properties.get("pluginActivateType"));
    }

    /**
     * 该插件监控的服务名
     * 该服务名会上报到Falcon监控值的tag(service)中,可用于区分监控值服务
     *
     * @return
     */
    @Override
    public String serverName() {
        return "kafka";
    }

    /**
     * 监控值的获取和上报周期(秒)
     *
     * @return
     */
    @Override
    public int step() {
        return step;
    }

    /**
     * 插件运行方式
     *
     * @return
     */
    @Override
    public PluginActivateType activateType() {
        return pluginActivateType;
    }

    /**
     * Agent关闭时的调用钩子
     * 如，可用于插件的资源释放等操作
     */
    @Override
    public void agentShutdownHook() {

    }
}
