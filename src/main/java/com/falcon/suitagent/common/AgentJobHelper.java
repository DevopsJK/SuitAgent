/*
 * www.yiji.com Inc.
 * Copyright (c) 2016 All Rights Reserved
 */
package com.falcon.suitagent.common;

import com.falcon.suitagent.config.AgentConfiguration;
import com.falcon.suitagent.jmx.JMXConnection;
import com.falcon.suitagent.plugins.DetectPlugin;
import com.falcon.suitagent.plugins.JDBCPlugin;
import com.falcon.suitagent.plugins.JMXPlugin;
import com.falcon.suitagent.plugins.SNMPV3Plugin;
import com.falcon.suitagent.plugins.job.JMXPluginJob;
import com.falcon.suitagent.plugins.util.PluginActivateType;
import com.falcon.suitagent.util.CronUtil;
import com.falcon.suitagent.util.SchedulerUtil;
import com.falcon.suitagent.util.StringUtils;
import com.falcon.suitagent.vo.jdbc.JDBCConnectionInfo;
import com.falcon.suitagent.vo.sceduler.ScheduleJobResult;
import com.falcon.suitagent.vo.sceduler.ScheduleJobStatus;
import com.falcon.suitagent.vo.snmp.SNMPV3UserInfo;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;

import static org.quartz.TriggerBuilder.newTrigger;

/*
 * 修订记录:
 * guqiu@yiji.com 2016-06-22 17:48 创建
 */

/**
 * @author guqiu@yiji.com
 */
@Slf4j
public class AgentJobHelper {

    //正在work的job记录
    private static final ConcurrentSkipListSet<String> worked = new ConcurrentSkipListSet<>();

    public static final List<ScheduleJobResult> scheduleResults = new ArrayList<>();

    /**
     * 添加work记录
     * @param serverName
     */
    private static void addWorkJob(String serverName){
        if(!StringUtils.isEmpty(serverName)){
            worked.add(serverName);
        }
    }

    /**
     * 判断指定job是否已经work
     * @param serverName
     * @return
     */
    private static boolean isHasWorked(String serverName){
        return worked.contains(serverName);
    }

    /**
     * Agent监控服务自动发现定时刷新功能
     * @throws SchedulerException
     */
    public static void agentFlush() throws SchedulerException {
        String agentFlush = "AgentFlush";
        if(AgentConfiguration.INSTANCE.getAgentFlushTime() != 0 &&
                !isHasWorked(agentFlush)){
            //开启服务自动发现
            JobDetail job = getJobDetail(AgentFlushJob.class,agentFlush,"Agent监控服务自动发现定时刷新功能Job",new JobDataMap());

            Trigger trigger = getTrigger(AgentConfiguration.INSTANCE.getAgentFlushTime(),agentFlush,"Agent监控服务自动发现定时刷新push调度任务");
            ScheduleJobResult scheduleJobResult = SchedulerUtil.executeScheduleJob(job,trigger);
            scheduleResults.add(scheduleJobResult);
            workResult(scheduleJobResult,agentFlush);
        }else{
            log.info("Agent监控服务自动发现定时刷新功能未开启");
        }
    }

    /**
     * JMX服务的监控启动
     * jmxServerName指定为null时才会进行commandInfos监控服务
     * @param pluginName
     * @param jmxPlugin
     * @param desc
     * @param jobServerName
     * @param jmxServerName
     * @param jobDataMap
     * @throws SchedulerException
     */
    public synchronized static void pluginWorkForJMX(String pluginName, JMXPlugin jmxPlugin, String desc, String jobServerName, String jmxServerName, JobDataMap jobDataMap) throws SchedulerException {
        //只有指定job未启动过的情况下才进行work开启
        if(!isHasWorked(jobServerName)){
            if(jmxPlugin.activateType() == PluginActivateType.AUTO){
                if(JMXConnection.hasJMXServerInLocal(jmxServerName)){
                    //开启本地Java服务监控
                    log.info("发现服务 {} , 启动插件 {} ",jobServerName,pluginName);
                    doJob(JMXPluginJob.class,desc,jmxPlugin.step(),jobDataMap,jobServerName);
                }
                if (jmxServerName == null && !jmxPlugin.commandInfos().isEmpty()){
                    //开启K8S Java服务监控
                    log.info("发现服务 {} , 启动插件 {} ",jmxPlugin.serverName() + "-JMXCommandInfos",pluginName);
                    doJob(JMXPluginJob.class,desc,jmxPlugin.step(),jobDataMap,jobServerName);
                }
            }else if(jmxPlugin.activateType() == PluginActivateType.FORCE){
                doJob(JMXPluginJob.class,desc,jmxPlugin.step(),jobDataMap,jobServerName);
            }
        }
    }

    /**
     * JDBC服务的监控启动
     * @param jdbcPlugin
     * @param pluginName
     * @param jobClazz
     * @param jobServerName
     * @param desc
     * @param jobDataMap
     * @throws SchedulerException
     */
    public synchronized static void pluginWorkForJDBC(JDBCPlugin jdbcPlugin ,String pluginName, Class<? extends Job> jobClazz, String jobServerName,String desc, JobDataMap jobDataMap) throws SchedulerException {
        PluginActivateType pluginActivateType = jdbcPlugin.activateType();
        int step = jdbcPlugin.step();
        //只有指定job未启动过的情况下才进行work开启
        if(!isHasWorked(jobServerName)){
            if(pluginActivateType == PluginActivateType.AUTO){
                try {
                    Collection<JDBCConnectionInfo> connectionInfos = jdbcPlugin.getConnectionInfos();
                    if(connectionInfos != null && !connectionInfos.isEmpty()){
                        //无异常且连接正常,代表连接获取成功,开启服务监控
                        log.info("发现服务 {} , 启动插件 {} ",jobServerName,pluginName);
                        doJob(jobClazz,desc,step,jobDataMap,jobServerName);
                    }
                } catch (Exception ignored) {
                }
            }else if(!StringUtils.isEmpty(jdbcPlugin.jdbcConfig()) && pluginActivateType == PluginActivateType.FORCE){
                doJob(jobClazz,desc,step,jobDataMap,jobServerName);
            }
        }
    }

    /**
     * 探测服务的监控启动
     * @param plugin
     * @param pluginName
     * @param jobClazz
     * @param jobDataMap
     * @throws SchedulerException
     */
    public synchronized static void pluginWorkForDetect(DetectPlugin plugin , String pluginName, Class<? extends Job> jobClazz, JobDataMap jobDataMap) throws SchedulerException {
        String serverName = plugin.serverName();
        //只有指定job未启动过的情况下才进行work开启
        if(!isHasWorked(serverName)){
            //只要配置了监控地址,就启动监控
            Collection<String> addresses = plugin.detectAddressCollection();
            boolean start = false;
            if(addresses != null && !addresses.isEmpty()){
                start = true;
            }else{
                //未配置探测地址,尝试自动探测地址
                addresses = plugin.autoDetectAddress();
                if(addresses != null && !addresses.isEmpty()){
                    start = true;
                }
            }
            if(start){
                //开启监控服务
                log.info("发现服务 {} , 启动插件 {} ",serverName,pluginName);
                doJob(jobClazz,pluginName,plugin.step(),jobDataMap,serverName);
            }
        }
    }

    /**
     * SNMPV3服务的监控启动
     * @param snmpv3Plugin
     * @param pluginName
     * @param jobClazz
     * @param serverName
     * @param desc
     * @param jobDataMap
     * @throws SchedulerException
     */
    public synchronized static void pluginWorkForSNMPV3(SNMPV3Plugin snmpv3Plugin , String pluginName, Class<? extends Job> jobClazz, String serverName, String desc, JobDataMap jobDataMap) throws SchedulerException {
        PluginActivateType pluginActivateType = snmpv3Plugin.activateType();
        int step = snmpv3Plugin.step();
        //只有指定job未启动过的情况下才进行work开启
        if(!isHasWorked(serverName)){
            if(pluginActivateType == PluginActivateType.AUTO){
                try {
                    Collection<SNMPV3UserInfo> snmpv3UserInfoList = snmpv3Plugin.userInfo();
                    if(snmpv3UserInfoList != null && !snmpv3UserInfoList.isEmpty()){
                        //无异常且连接正常,代表连接获取成功,开启服务监控
                        log.info("发现服务 {} , 启动插件 {} ",serverName,pluginName);
                        doJob(jobClazz,desc,step,jobDataMap,serverName);
                    }
                } catch (Exception ignored) {
                }
            }else if(!snmpv3Plugin.userInfo().isEmpty() && pluginActivateType == PluginActivateType.FORCE){
                doJob(jobClazz,desc,step,jobDataMap,serverName);
            }
        }
    }

    private static void doJob(Class<? extends Job> jobClazz,String desc,int step,JobDataMap jobDataMap,String jobServerName) throws SchedulerException {
        JobDetail job = getJobDetail(jobClazz,desc,desc + "的监控数据push调度JOB",jobDataMap);
        Trigger trigger = getTrigger(step,desc,desc + "的监控数据push调度任务");
        ScheduleJobResult scheduleJobResult = SchedulerUtil.executeScheduleJob(job,trigger);
        scheduleResults.add(scheduleJobResult);
        workResult(scheduleJobResult,jobServerName);
    }

    /**
     * 启动结果处理并记录work
     * @param scheduleJobResult
     */
    public static void workResult(ScheduleJobResult scheduleJobResult,String jobServerName){
        if(scheduleJobResult.getScheduleJobStatus() == ScheduleJobStatus.SUCCESS){
            log.info("{} 启动成功",scheduleJobResult.getTrigger().getDescription());
            //记录work
            addWorkJob(jobServerName);
        }else if(scheduleJobResult.getScheduleJobStatus() == ScheduleJobStatus.FAILED){
            log.error("{} 启动失败",scheduleJobResult.getTrigger().getDescription());
        }
    }

    /**
     * 获取计划任务JOB
     * @param job
     * @param id
     * @param description
     * @return
     */
    public static JobDetail getJobDetail(Class <? extends Job> job, String id, String description,JobDataMap jobDataMap){
        return JobBuilder.newJob(job)
                .withIdentity(id + "-scheduler-job", "job-metricsScheduler")
                .withDescription(description)
                .setJobData(jobDataMap)
                .build();
    }

    /**
     * 获取调度器
     * @param step
     * @param id
     * @param description
     * @return
     */
    public static Trigger getTrigger(int step, String id, String description){
        String cron = CronUtil.getCronBySecondScheduler(step);
        Trigger trigger = null;
        if(cron != null){
            log.info("启动{ " + description + " }调度:" + cron);
            trigger = newTrigger()
                    .withIdentity(id + "-agent-scheduler-trigger", "trigger-metricsScheduler")
                    .withSchedule(CronScheduleBuilder.cronSchedule(cron))
                    .startNow()
                    .withDescription(description)
                    .build();
        }else{
            log.error("agent 启动失败. 调度时间配置失败");
            System.exit(0);
        }
        return trigger;
    }
}
