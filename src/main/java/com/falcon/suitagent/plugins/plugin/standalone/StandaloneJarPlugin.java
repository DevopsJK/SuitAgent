/*
 * www.yiji.com Inc.
 * Copyright (c) 2016 All Rights Reserved
 */
package com.falcon.suitagent.plugins.plugin.standalone;
/*
 * 修订记录:
 * guqiu@yiji.com 2016-06-27 16:13 创建
 */

import com.falcon.suitagent.falcon.FalconReportObject;
import com.falcon.suitagent.jmx.vo.JMXMetricsValueInfo;
import com.falcon.suitagent.plugins.JMXPlugin;
import com.falcon.suitagent.plugins.util.PluginActivateType;
import com.falcon.suitagent.util.CommandUtilForUnix;
import com.falcon.suitagent.util.OSUtil;
import com.falcon.suitagent.util.StringUtils;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.falcon.suitagent.jmx.AbstractJmxCommand.getJMXConfigValueForLinux;
import static com.falcon.suitagent.jmx.AbstractJmxCommand.getJMXConfigValueForMac;

/**
 * @author guqiu@yiji.com
 */
@Slf4j
public class StandaloneJarPlugin implements JMXPlugin {

    private String jmxServerDir;
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
        StringBuilder sb = new StringBuilder();

        //jsvc方式的应用，匹配只有一个 -cp xxx.jar 形式的Java应用
        String cmd = "ps aux | grep jsvc";
        try {
            CommandUtilForUnix.ExecuteResult executeResult = CommandUtilForUnix.execWithReadTimeLimit(cmd,false,7);
            String msg = executeResult.msg;
            for (String s : msg.split("\n")) {
                Pattern pattern = Pattern.compile("-cp\\s+(/.*).jar");
                Matcher matcher = pattern.matcher(s);
                if (matcher.find()){
                    String find = matcher.group();
                    //将第一个-cp去掉
                    find = find.replaceFirst("\\s*-cp\\s*","");
                    //将后面的-cp全部换成:
                    find = find.replaceAll("\\s*-cp\\s*",":");
                    if (!find.contains(":")){
                        File file = new File(find);
                        if (jmxServerName == null || !jmxServerName.contains(file.getName())){
                            sb.append(",").append(file.getName());
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("",e);
        }

        //遍历当前运行的应用
        List<VirtualMachineDescriptor> vms = VirtualMachine.list();
        for (VirtualMachineDescriptor desc : vms) {
            //去除--参数
            String displayName = desc.displayName().replaceAll("(\\s+--.*)*","");
            //java -jar 形式启动的Java应用
            if(displayName.matches(".*\\.jar")){
                Pattern pattern = Pattern.compile(".*\\.jar");
                Matcher matcher = pattern.matcher(displayName);
                if (matcher.find()){
                    displayName = matcher.group();
                }
                File file = new File(displayName);
                if (file.exists()){
                    //文件全路径形式只取文件名
                    displayName = file.getName();
                    if (jmxServerName == null || !jmxServerName.contains(displayName)){
                        sb.append(",").append(displayName);
                    }
                }else {
                    if (jmxServerName == null || !jmxServerName.contains(displayName)){
                        sb.append(",").append(displayName);
                    }
                }
            }
        }

        //遍历配置目录
        if(!StringUtils.isEmpty(jmxServerDir)){
            for (String dir : jmxServerDir.split(",")) {
                if(!StringUtils.isEmpty(dir)){
                    Path path = Paths.get(dir);
                    try {
                        Files.walkFileTree(path,new SimpleFileVisitor<Path>(){
                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                String fileName = file.getFileName().toString();
                                String fineNameLower = fileName.toLowerCase();
                                if(!fineNameLower.contains("-sources") && fineNameLower.endsWith(".jar")){
                                    if (jmxServerName == null || !jmxServerName.contains(fileName)){
                                        sb.append(",").append(fileName);
                                    }
                                }

                                return super.visitFile(file, attrs);
                            }
                        });
                    } catch (IOException e) {
                        log.error("遍历目录 {} 发生异常",jmxServerDir,e);
                    }
                }
            }
        }
        sb.append(jmxServerName == null ? "" : "," + jmxServerName);
        return sb.toString();
    }

    /**
     * 该插件监控的服务标记名称,目的是为能够在操作系统中准确定位该插件监控的是哪个具体服务
     * 可用变量:
     * {jmxServerName} - 代表直接使用当前服务的jmxServerName
     * 如该服务运行的端口号等
     * 若不需要指定则可返回null
     *
     * @param jmxMetricsValueInfo 该服务连接的jmx对象
     * @param pid                   该服务当前运行的进程id
     * @return
     */
    @Override
    public String agentSignName(JMXMetricsValueInfo jmxMetricsValueInfo, int pid) {
        String jmxPortOpt = "-Dcom.sun.management.jmxremote.port";
        String cmdForMac = "ps u -p " + pid;
        String cmdForLinux = "cat /proc/" + pid + "/cmdline";
        try {
            CommandUtilForUnix.ExecuteResult result;
            if (OSUtil.isLinux()){
                result = CommandUtilForUnix.execWithReadTimeLimit(cmdForLinux,false,7);
            }else if (OSUtil.isMac()){
                result = CommandUtilForUnix.execWithReadTimeLimit(cmdForMac,false,7);
            }else {
                log.error("只支持Linux和Mac平台");
                return null;
            }

            String msg = result.msg;
            String port = null;
            if (OSUtil.isLinux()){
                port = getJMXConfigValueForLinux(msg,jmxPortOpt + "=\\d+",jmxPortOpt + "=");
            }else if (OSUtil.isMac()){
                port = getJMXConfigValueForMac(msg,jmxPortOpt);
            }
            if (port == null){
                log.warn("未找到JMX端口号");
                return "{jmxServerName}";
            }
            return jmxMetricsValueInfo.getJmxConnectionInfo().getConnectionServerName() + "-JP_" + port;
        } catch (IOException e) {
            log.error("",e);
            return "{jmxServerName}";
        }
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
        return new ArrayList<>();
    }

    /**
     * 插件初始化操作
     * 该方法将会在插件运行前进行调用
     * @param properties
     * 包含的配置:
     * 1、插件目录绝对路径的(key 为 pluginDir),可利用此属性进行插件自定制资源文件读取
     * 2、插件指定的配置文件的全部配置信息(参见 {@link com.falcon.suitagent.plugins.Plugin#configFileName} 接口项)
     * 3、授权配置项(参见 {@link com.falcon.suitagent.plugins.Plugin#authorizationKeyPrefix} 接口项
     */
    @Override
    public void init(Map<String, String> properties) {
        if (!StringUtils.isEmpty(System.getProperty("plugin.jar.jmxServerDir"))){
            jmxServerDir = System.getProperty("plugin.jar.jmxServerDir");
        }else {
            jmxServerDir = properties.get("jmxServerDir");
        }
        if (!StringUtils.isEmpty(System.getProperty("plugin.jar.jmxServerName"))){
            jmxServerName = System.getProperty("plugin.jar.jmxServerName");
        }else {
            jmxServerName = properties.get("jmxServerName");
        }
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
        return "standaloneJar";
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

    @Override
    public String serverPath(int pid, String serverName) {
        String dirPath = "";

        if(StringUtils.isEmpty(dirPath)){
            try {
                String cmd = "ls -al /proc/" + pid + "/fd/" + " | grep " + serverName;
                CommandUtilForUnix.ExecuteResult executeResult = CommandUtilForUnix.execWithReadTimeLimit(cmd,false,7);
                String msg = executeResult.msg;
                String[] ss = msg.split("\\s+");
                for (String s : ss) {
                    if(!StringUtils.isEmpty(s) && s.contains(serverName)){
                        dirPath = s;
                        break;
                    }
                }

                if(!dirPath.toLowerCase().endsWith(".jar")){
                    dirPath += File.separator + serverName;
                }
            } catch (IOException e) {
                log.error("standaloneJar serverDirPath获取异常",e);
            }
        }
        return dirPath;
    }
}
