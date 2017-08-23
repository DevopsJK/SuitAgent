/*
 * www.yiji.com Inc.
 * Copyright (c) 2016 All Rights Reserved
 */
package com.falcon.suitagent.plugins.metrics;
/*
 * 修订记录:
 * guqiu@yiji.com 2016-07-22 10:56 创建
 */

import com.falcon.suitagent.plugins.DetectPlugin;
import com.falcon.suitagent.util.StringUtils;
import com.falcon.suitagent.vo.detect.DetectResult;
import com.falcon.suitagent.falcon.FalconReportObject;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author guqiu@yiji.com
 */
public class DetectMetricsValue extends MetricsCommon {

    private DetectPlugin detectPlugin;
    private long timestamp;

    public DetectMetricsValue(DetectPlugin detectPlugin, long timestamp) {
        this.detectPlugin = detectPlugin;
        this.timestamp = timestamp;
    }

    /**
     * 获取所有的监控值报告
     *
     * @return
     */
    @Override
    public Collection<FalconReportObject> getReportObjects() {
        Set<FalconReportObject> result = new HashSet<>();
        Collection<String> addressCollection = detectPlugin.detectAddressCollection();
        if(addressCollection == null || addressCollection.isEmpty()){
            //若无配置地址,获取自动探测的地址
            addressCollection = detectPlugin.autoDetectAddress();
        }
        if(addressCollection != null && !addressCollection.isEmpty()){
            addressCollection.forEach(address -> {
                DetectResult detectResult = detectPlugin.detectResult(address);
                if(detectResult != null){
                    //可用性
                    if(detectResult.isSuccess()){
                        FalconReportObject falconReportObject = generatorVariabilityReport(true,detectPlugin.agentSignName(address),timestamp,detectPlugin.step(),detectPlugin,detectPlugin.serverName());
                        addCommonTagFromDetectResult(detectResult,falconReportObject);
                        result.add(falconReportObject);
                    }else{
                        FalconReportObject falconReportObject = generatorVariabilityReport(false,detectPlugin.agentSignName(address),timestamp,detectPlugin.step(),detectPlugin,detectPlugin.serverName());
                        addCommonTagFromDetectResult(detectResult,falconReportObject);
                        result.add(falconReportObject);
                    }
                    //自定义Metrics
                    List<DetectResult.Metric> metricsList = detectResult.getMetricsList();
                    if(metricsList != null && !metricsList.isEmpty()){
                        metricsList.forEach(metric -> {
                            FalconReportObject reportObject = new FalconReportObject();
                            reportObject.setMetric(getMetricsName(metric.metricName));
                            reportObject.setCounterType(metric.counterType);
                            reportObject.setValue(metric.value);
                            reportObject.setTimestamp(timestamp);
                            //打默认tag
                            reportObject.appendTags(getTags(detectPlugin.agentSignName(address),detectPlugin,detectPlugin.serverName()))
                                    //打该监控值指定的tag
                                    .appendTags(metric.tags);
                            setReportCommonValue(reportObject,detectPlugin.step());
                            addCommonTagFromDetectResult(detectResult,reportObject);
                            if (metric.step > 0){
                                reportObject.setStep(metric.step);
                            }
                            result.add(reportObject);
                        });
                    }
                }
            });
        }
        return result;
    }

    /**
     * 设置探测结果的公共的tag
     * @param detectResult
     * @param reportObject
     */
    private void addCommonTagFromDetectResult(DetectResult detectResult, FalconReportObject reportObject){
       String tag = detectResult.getCommonTag();
        if(!StringUtils.isEmpty(tag)){
            reportObject.appendTags(tag);
        }
    }
}
