/*
 * www.yiji.com Inc.
 * Copyright (c) 2016 All Rights Reserved
 */
package com.falcon.suitagent.vo.jmx;

import lombok.Data;

/*
 * 修订记录:
 * guqiu@yiji.com 2016-06-22 17:48 创建
 */

/**
 * JMX监控方式 Agent配置文件配置的需要进行获取的监控参数
 * @author guqiu@yiji.com
 */
@Data
public class JMXMetricsConfiguration {

    private String objectName;
    private String metrics;
    private String valueExpress;
    private String alias;
    private String counterType;
    private String tag;
    private boolean hasCollect = false;

}
