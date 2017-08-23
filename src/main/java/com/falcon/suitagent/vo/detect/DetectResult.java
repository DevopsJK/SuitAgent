/*
 * www.yiji.com Inc.
 * Copyright (c) 2016 All Rights Reserved
 */
package com.falcon.suitagent.vo.detect;
/*
 * 修订记录:
 * guqiu@yiji.com 2016-07-22 11:49 创建
 */

import com.falcon.suitagent.falcon.CounterType;
import lombok.Data;
import lombok.ToString;

import java.util.List;

/**
 * 一次探测的结果
 * @author guqiu@yiji.com
 */
@Data
public class DetectResult {

    /**
     * 探测结果,Agent将根据此属性进行探测地址的可用性上报
     */
    private boolean success;

    /**
     * 自定义的监控值
     * Agent会将此Map中的key当做metrics名称,{@link DetectResult.Metric} 对象组建上报值进行上报
     */
    private List<Metric> metricsList;


    /**
     * 自定义的公共的tag信息
     * 形如tag1={tag1},tag2={tag2}
     * 设置后,将会对每个监控值都会打上此tag
     */
    private String commonTag;

    @ToString
    public static class Metric{
        /**
         * 自定义监控名
         */
        public String metricName;
        /**
         * 自定义监控值的value
         */
        public String value;
        /**
         * 自定义监控值的上报类型
         */
        public CounterType counterType;
        /**
         * 自定义监控值的tag
         */
        public String tags;

        /**
         * 自定义监控值的tag
         */
        public int step;

        /**
         * @param metricName
         * @param value
         * @param counterType
         * @param tags
         */
        public Metric(String metricName,String value, CounterType counterType, String tags) {
            this.metricName = metricName;
            this.value = value;
            this.counterType = counterType;
            this.tags = tags;
        }

        /**
         * @param metricName
         * @param value
         * @param counterType
         * @param tags
         * @param step
         */
        public Metric(String metricName,String value, CounterType counterType, String tags,int step) {
            this.metricName = metricName;
            this.value = value;
            this.counterType = counterType;
            this.tags = tags;
            this.step = step;
        }
    }
}
