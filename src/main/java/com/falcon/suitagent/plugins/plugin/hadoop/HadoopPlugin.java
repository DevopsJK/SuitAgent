/*
 * www.msxf.com Inc.
 * Copyright (c) 2017 All Rights Reserved
 */
package com.falcon.suitagent.plugins.plugin.hadoop;
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
 * long.qian@msxf.com 2017-08-17 15:01 创建
 */

import com.falcon.suitagent.falcon.FalconReportObject;
import com.falcon.suitagent.jmx.vo.JMXMetricsValueInfo;
import com.falcon.suitagent.plugins.JMXPlugin;
import com.falcon.suitagent.plugins.Plugin;
import com.falcon.suitagent.plugins.util.PluginActivateType;

import java.util.Collection;
import java.util.Map;

/**
 * @author long.qian@msxf.com
 */
public class HadoopPlugin implements JMXPlugin {
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
        return this.basePropertiesKey;
    }

    /**
     * 仅非容器环境会被调用
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
        String name = jmxMetricsValueInfo.getJmxConnectionInfo().getConnectionServerName();
        if (name.contains("JournalNode")){
            return "JournalNode";
        }else if (name.contains("HRegionServer")){
            return "HRegionServer";
        }else if (name.contains("DataNode")){
            return "DataNode";
        }else if (name.contains("SecondaryNameNode")){
            return "SecondaryNameNode";
        }else if (name.contains("NameNode")){
            return "NameNode";
        }
        return "{jmxServerName}";
    }

    /**
     * 该插件所要监控的服务在JMX连接中的displayName识别名
     * 若有该插件监控的相同类型服务,但是displayName不一样,可用逗号(,)进行分隔,进行统一监控
     *
     * @return
     */
    @Override
    public String jmxServerName() {
        return this.jmxServerName;
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
        basePropertiesKey = properties.get("basePropertiesKey");
        jmxServerName = properties.get("jmxServerName");
        step = Integer.parseInt(properties.get("step"));
        pluginActivateType = PluginActivateType.valueOf(properties.get("pluginActivateType"));
    }

    /**
     * 该插件在指定插件配置目录下的配置文件名
     *
     * @return 返回该插件对应的配置文件名
     * 默认值:插件简单类名第一个字母小写 加 .properties 后缀
     */
    @Override
    public String configFileName() {
        return "hadoopPlugin.properties";
    }

    /**
     * 该插件监控的服务名
     * 该服务名会上报到Falcon监控值的tag(service)中,可用于区分监控值服务
     *
     * @return
     */
    @Override
    public String serverName() {
        return "hadoop";
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
