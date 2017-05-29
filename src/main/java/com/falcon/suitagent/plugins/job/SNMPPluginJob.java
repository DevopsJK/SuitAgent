/*
 * www.yiji.com Inc.
 * Copyright (c) 2016 All Rights Reserved
 */
package com.falcon.suitagent.plugins.job;
/*
 * 修订记录:
 * guqiu@yiji.com 2016-07-13 11:27 创建
 */

import com.falcon.suitagent.plugins.metrics.SNMPV3MetricsValue;
import com.falcon.suitagent.plugins.SNMPV3Plugin;
import com.falcon.suitagent.plugins.metrics.MetricsCommon;
import com.falcon.suitagent.util.ExecuteThreadUtil;
import com.falcon.suitagent.vo.snmp.SNMPV3UserInfo;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.List;

/**
 * @author guqiu@yiji.com
 */
@Slf4j
public class SNMPPluginJob implements Job {

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        long timestamp = System.currentTimeMillis() / 1000;
        JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
        String pluginName = jobDataMap.getString("pluginName");
        try {
            SNMPV3Plugin plugin = (SNMPV3Plugin) jobDataMap.get("pluginObject");
            List<SNMPV3UserInfo> jobUsers = (List<SNMPV3UserInfo>) jobDataMap.get("userInfoList");
            MetricsCommon metricsValue = new SNMPV3MetricsValue(plugin,jobUsers,timestamp);
            //SNMP监控数据获取时间较长,采用异步方式
            ExecuteThreadUtil.execute(new JobThread(metricsValue,"snmp v3 job thread"));
        } catch (Exception e) {
            log.error("插件 {} 运行异常",pluginName,e);
        }
    }

}
