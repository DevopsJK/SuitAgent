/*
 * www.yiji.com Inc.
 * Copyright (c) 2016 All Rights Reserved
 */
package com.falcon.suitagent.plugins;
/*
 * 修订记录:
 * guqiu@yiji.com 2016-06-23 14:17 创建
 */

import com.falcon.suitagent.falcon.FalconReportObject;
import com.falcon.suitagent.jmx.vo.JMXMetricsValueInfo;
import com.falcon.suitagent.vo.jmx.JavaExecCommandInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * JMX 插件接口
 * @author guqiu@yiji.com
 */
public interface JMXPlugin extends Plugin{

    /**
     * 自定义的监控属性的监控值基础配置名
     * @return
     * 若无配置文件,可返回null
     */
    String basePropertiesKey();

    /**
     * 该插件所要监控的服务在JMX连接中的displayName识别名
     * 若有该插件监控的相同类型服务,但是displayName不一样,可用逗号(,)进行分隔,进行统一监控
     * @return
     */
    String jmxServerName();

    /**
     * Java应用启动时的命令信息的列表集合
     * 若实现该接口，则该方法中返回描述的Java应用将会自动建立JMX连接进行监控
     * @return
     * 请勿返回null
     * 默认返回空集合
     */
    default List<JavaExecCommandInfo> commandInfos(){
        return new ArrayList<>();
    }

    /**
     * 仅非容器环境会被调用
     * 该插件监控的服务标记名称,目的是为能够在操作系统中准确定位该插件监控的是哪个具体服务
     * 可用变量:
     * {jmxServerName} - 代表直接使用当前服务的jmxServerName
     * 如该服务运行的端口号等
     * 若不需要指定则可返回null
     * @param jmxMetricsValueInfo
     * 该服务连接的jmx对象信息
     * @param pid
     * 该服务当前运行的进程id
     * @return
     * 若不实现，默认返回NO NAME，代表该插件无特定的agentSignName
     */
    default String agentSignName(JMXMetricsValueInfo jmxMetricsValueInfo, int pid){
        return "NO NAME";
    }

    /**
     * 插件监控的服务正常运行时的內建监控报告
     * 若有些特殊的监控值无法用配置文件进行配置监控,可利用此方法进行硬编码形式进行获取
     * 注:此方法只有在监控对象可用时,才会调用,并加入到监控值报告中,一并上传
     * @param metricsValueInfo
     * 当前的JMXMetricsValueInfo信息
     * @return
     */
    Collection<FalconReportObject> inbuiltReportObjectsForValid(JMXMetricsValueInfo metricsValueInfo);

    /**
     * 仅非容器环境会被调用
     * 能够代表该JMX服务的绝对路径
     * 若实现此方法,则若该JMX连接不可用时,将会检查该JMX服务的目录是否存在,若不存在,将会清除此连接,并不再监控此JMX。
     * 否则,若JMX连接不可用,将会上报不可用的报告,且不会清除
     * @param pid
     * 服务的进程id
     * @param serverName
     * jmx 服务名
     * @return
     */
    default String serverPath(int pid,String serverName){
        return null;
    }

    /**
     * 仅非容器环境会被调用
     * JMX服务器的目录名称
     * @param pid
     * 服务的进程id
     * @return
     * 默认返回null,既不打目录名称的tag
     */
    default String serverDirName(int pid){
        return null;
    }

}
