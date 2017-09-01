/*
 * www.yiji.com Inc.
 * Copyright (c) 2016 All Rights Reserved
 */
package com.falcon.suitagent.plugins.util;
/*
 * 修订记录:
 * guqiu@yiji.com 2016-06-24 11:12 创建
 */

import com.falcon.suitagent.common.AgentJobHelper;
import com.falcon.suitagent.plugins.*;
import com.falcon.suitagent.plugins.job.DetectPluginJob;
import com.falcon.suitagent.plugins.job.JDBCPluginJob;
import com.falcon.suitagent.plugins.job.SNMPPluginJob;
import com.falcon.suitagent.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDataMap;
import org.quartz.SchedulerException;

import java.sql.DriverManager;
import java.util.Set;

/**
 * @author guqiu@yiji.com
 */
@Slf4j
public class PluginExecute {

    /**
     * 启动插件
     * @throws SchedulerException
     */
    public static void start() throws SchedulerException {
        //根据配置启动自发现功能
        AgentJobHelper.agentFlush();
        run();
    }

    /**
     * 运行插件
     */
    public static void run(){

        Set<Plugin> jmxPlugins = PluginLibraryHelper.getJMXPlugins();
        Set<Plugin> jdbcPlugins = PluginLibraryHelper.getJDBCPlugins();
        Set<Plugin> snmpv3Plugins = PluginLibraryHelper.getSNMPV3Plugins();
        Set<Plugin> detectPlugins = PluginLibraryHelper.getDetectPlugins();

        jmxPlugins.forEach(plugin -> {
            try {
                JMXPlugin jmxPlugin = (JMXPlugin) plugin;
                JobDataMap jobDataMap = new JobDataMap();
                //指定jmxServerName
                if (jmxPlugin.jmxServerName() != null){
                    //若jmxServerName有多个值,分别进行job启动
                    for (String jmxServerName : ((JMXPlugin) plugin).jmxServerName().split(",")) {
                        if(!StringUtils.isEmpty(jmxServerName)){
                            String pluginName = String.format("%s-%s",jmxPlugin.pluginName(),jmxServerName);
                            jobDataMap.put("pluginName",pluginName);
                            jobDataMap.put("jmxServerName",jmxServerName);
                            jobDataMap.put("pluginObject",jmxPlugin);

                            AgentJobHelper.pluginWorkForJMX(pluginName,
                                    jmxPlugin,
                                    pluginName,
                                    jmxPlugin.serverName() + "-" + jmxServerName,
                                    jmxServerName,
                                    jobDataMap);
                        }
                    }
                }

                //Java启动命令行
                if (!jmxPlugin.commandInfoList().isEmpty()){
                    jobDataMap.put("pluginName",jmxPlugin.pluginName());
                    //jmxServerName设值null，只采集该插件的commandInfos监控
                    jobDataMap.put("jmxServerName",null);
                    jobDataMap.put("pluginObject",jmxPlugin);
                    AgentJobHelper.pluginWorkForJMX(jmxPlugin.pluginName(),
                            jmxPlugin,
                            jmxPlugin.pluginName(),
                            jmxPlugin.serverName() + "-" + "JMXCommandInfos",
                            null,
                            jobDataMap);
                }
            } catch (Exception e) {
                log.error("插件启动异常",e);
            }
        });
        snmpv3Plugins.forEach(plugin -> {
            try{
                SNMPV3Plugin snmpv3Plugin = (SNMPV3Plugin) plugin;
                JobDataMap jobDataMap = new JobDataMap();
                String pluginName = String.format("%s-%s",snmpv3Plugin.pluginName(),snmpv3Plugin.serverName());
                jobDataMap.put("pluginName",pluginName);
                jobDataMap.put("pluginObject",snmpv3Plugin);
//                List<SNMPV3UserInfo> jobUsers = new ArrayList<>();
//                Collection<SNMPV3UserInfo> userInfoCollection = snmpv3Plugin.userInfo();
//                StringBuilder sb = new StringBuilder();
//                int count = 1;
//                for (SNMPV3UserInfo snmpv3UserInfo : userInfoCollection) {
//                    //每5个SNMP连接为一个job
//                    if(jobUsers.size() == 5){
//                        jobDataMap.put("userInfoList",jobUsers);
//                        AgentJobHelper.pluginWorkForSNMPV3(snmpv3Plugin,pluginName,SNMPPluginJob.class,pluginName + "-" + count + "-" + sb.toString(),snmpv3Plugin.serverName() + "-" + count + "-" + sb.toString(),jobDataMap);
//                        jobUsers = new ArrayList<>();
//                        jobUsers.add(snmpv3UserInfo);
//                        sb = new StringBuilder();
//                        sb.append(snmpv3UserInfo.getAddress()).append(" | ");
//                        count++;
//                    }else{
//                        sb.append(snmpv3UserInfo.getAddress()).append(" | ");
//                        jobUsers.add(snmpv3UserInfo);
//                    }
//                }
//                jobDataMap.put("userInfoList",jobUsers);
//                AgentJobHelper.pluginWorkForSNMPV3(snmpv3Plugin,pluginName,SNMPPluginJob.class,pluginName + "-" + count + "-" + sb.toString(),snmpv3Plugin.serverName() + "-" + count + "-" + sb.toString(),jobDataMap);
                jobDataMap.put("userInfoList",snmpv3Plugin.userInfo());
                AgentJobHelper.pluginWorkForSNMPV3(snmpv3Plugin,pluginName,SNMPPluginJob.class,pluginName,snmpv3Plugin.serverName(),jobDataMap);
            }catch (Exception e){
                log.error("插件启动异常",e);
            }
        });

        detectPlugins.forEach(plugin -> {
            try {
                DetectPlugin detectPlugin = (DetectPlugin) plugin;
                JobDataMap jobDataMap = new JobDataMap();
                String pluginName = plugin.pluginName();
                jobDataMap.put("pluginName",pluginName);
                jobDataMap.put("pluginObject",detectPlugin);
                AgentJobHelper.pluginWorkForDetect(detectPlugin,pluginName, DetectPluginJob.class,jobDataMap);
            }catch (Exception e){
                log.error("插件启动异常",e);
            }
        });

        //设置JDBC超时为5秒
        DriverManager.setLoginTimeout(5);
        jdbcPlugins.forEach(plugin -> {
            try {
                JDBCPlugin jdbcPlugin = (JDBCPlugin) plugin;
                JobDataMap jobDataMap = new JobDataMap();
                String pluginName = String.format("%s-%s",jdbcPlugin.pluginName(),jdbcPlugin.serverName());
                jobDataMap.put("pluginName",pluginName);
                jobDataMap.put("pluginObject",jdbcPlugin);
                AgentJobHelper.pluginWorkForJDBC(jdbcPlugin,pluginName,JDBCPluginJob.class,pluginName,jdbcPlugin.serverName(),jobDataMap);
            } catch (Exception e) {
                log.error("插件启动异常",e);
            }
        });

    }

}
