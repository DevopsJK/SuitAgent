/*
 * www.yiji.com Inc.
 * Copyright (c) 2016 All Rights Reserved
 */
package com.falcon.suitagent.jmx.vo;

import com.falcon.suitagent.vo.jmx.JMXMetricsConfiguration;
import lombok.Data;

import java.util.List;
import java.util.Set;

/*
 * 修订记录:
 * guqiu@yiji.com 2016-06-22 17:48 创建
 */

/**
 * JMX mBean值的info类
 * @author guqiu@yiji.com
 */
@Data
public class JMXMetricsValueInfo {

    private long timestamp;
    /**
     * 需要监控的配置项
     */
    Set<JMXMetricsConfiguration> jmxMetricsConfigurations;

    /**
     * 此jmx 连接的对象信息
     */
    private List<JMXObjectNameInfo> jmxObjectNameInfoList;

    /**
     * jmx 连接信息
     */
    private JMXConnectionInfo jmxConnectionInfo;

}
