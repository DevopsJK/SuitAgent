/*
 * www.msxf.com Inc.
 * Copyright (c) 2017 All Rights Reserved
 */
package com.falcon.suitagent.plugins.plugin.script;
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
 * long.qian@msxf.com 2017-07-10 10:36 创建
 */

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.falcon.suitagent.falcon.CounterType;
import com.falcon.suitagent.plugins.DetectPlugin;
import com.falcon.suitagent.plugins.Plugin;
import com.falcon.suitagent.util.CommandUtilForUnix;
import com.falcon.suitagent.util.SchedulerUtil;
import com.falcon.suitagent.util.StringUtils;
import com.falcon.suitagent.vo.detect.DetectResult;
import com.falcon.suitagent.vo.sceduler.ScheduleJobResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.math.NumberUtils;
import org.quartz.Trigger;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author long.qian@msxf.com
 */
@Slf4j
public class ScriptPlugin implements DetectPlugin {

    private static final Map<String,Long> lastScriptExecTime = new ConcurrentHashMap<>();
    private int step;
    private List<String> scripts = new ArrayList<>();

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
        step = Integer.parseInt(properties.get("step"));
        Set<String> keys = properties.keySet();
        keys.stream().filter(Objects::nonNull).filter(key -> key.contains("script")).forEach(key -> {
            scripts.add(properties.get(key));
        });
    }

    /**
     * 该插件在指定插件配置目录下的配置文件名
     *
     * @return 返回该插件对应的配置文件名
     * 默认值:插件简单类名第一个字母小写 加 .properties 后缀
     */
    @Override
    public String configFileName() {
        return "scriptPlugin.properties";
    }

    /**
     * 该插件监控的服务名
     * 该服务名会上报到Falcon监控值的tag(service)中,可用于区分监控值服务
     *
     * @return
     */
    @Override
    public String serverName() {
        return "script";
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
     * Agent关闭时的调用钩子
     * 如，可用于插件的资源释放等操作
     */
    @Override
    public void agentShutdownHook() {

    }

    /**
     * 监控的具体服务的agentSignName tag值
     *
     * @param address 被监控的探测地址
     * @return 根据地址提炼的标识, 如域名等
     */
    @Override
    public String agentSignName(String address) {
        //返回脚本文件名
        return address.substring(address.lastIndexOf(File.separator) + 1);
    }

    /**
     * 一次地址的探测结果
     *
     * @param address 被探测的地址,地址来源于方法 {@link DetectPlugin#detectAddressCollection()}
     * @return 返回被探测的地址的探测结果, 将用于上报监控状态
     */
    @Override
    public DetectResult detectResult(String address) {
        DetectResult result = new DetectResult();
        Script script = parseScript(address);
        boolean success = script != null;
        List<DetectResult.Metric> metrics = new ArrayList<>();
        if (success){
            ScheduleJobResult scheduleJobResult = SchedulerUtil.getResultByPlugin(this);
            if (scheduleJobResult != null){
                Trigger trigger = scheduleJobResult.getTrigger();
                if (trigger != null){
                    long startTime = scheduleJobResult.getTrigger().getStartTime().getTime();
                    long currentTime = System.currentTimeMillis();
                    long interval = 0L;
                    Long lastExecTime = lastScriptExecTime.get(script.toString());
                    if (lastExecTime == null){
                        interval = currentTime - startTime;
                    }else {
                        interval = currentTime - lastExecTime;
                    }
                    long process = script.getStepCycle() * this.step * 1000;
                    long abs = Math.abs(interval - process);
                    if (abs <= this.step * 1000){//脚本距上次执行时间差在最小周期范围内就触发脚本执行
                        if (script.getResultType() == ScriptResultType.NUMBER){
                            DetectResult.Metric metric = executeNumberScript(script);
                            if (metric != null){
                                metrics.add(metric);
                            }
                        }else if (script.getResultType() == ScriptResultType.JSON){
                            metrics.addAll(executeJSONScript(script));
                        }
                        lastScriptExecTime.put(script.toString(),currentTime);
                    }
                }
            }
        }
        result.setMetricsList(metrics);
        result.setSuccess(success);
        return result;
    }

    /**
     * 执行返回数字类型的脚本
     * @param script
     * @return
     */
    private DetectResult.Metric executeNumberScript(Script script) {
        if (script != null && script.isValid()){
            try {
                String cmd = "";
                if (script.getScriptType() == ScriptType.SHELL){
                    cmd = "sh " + script.getPath();
                }
                if (script.getScriptType() == ScriptType.PYTHON){
                    cmd = "python " + script.getPath();
                }
                CommandUtilForUnix.ExecuteResult executeResult = CommandUtilForUnix.execWithReadTimeLimit(cmd,false,5);
                String value = executeResult.msg.trim();
                if (NumberUtils.isNumber(value)){
                    return new DetectResult.Metric(script.getMetric(),value, CounterType.valueOf(script.getCounterType()), script.getTags());
                }
            } catch (Exception e) {
                log.error("脚本执行异常",e);
            }
        }
        return null;
    }

    private List<DetectResult.Metric> executeJSONScript(Script script){
        List<DetectResult.Metric> metrics = new ArrayList<>();
        if (script != null && script.isValid()){
            try {
                String cmd = "";
                if (script.getScriptType() == ScriptType.SHELL){
                    cmd = "sh " + script.getPath();
                }
                if (script.getScriptType() == ScriptType.PYTHON){
                    cmd = "python " + script.getPath();
                }
                CommandUtilForUnix.ExecuteResult executeResult = CommandUtilForUnix.execWithReadTimeLimit(cmd,false,5);
                String json = executeResult.msg.trim();
                if (json.startsWith("{") && json.endsWith("}")){
                    JSONObject jsonObject = JSON.parseObject(json);
                    metrics.add(parseMetricFromJSONObject(jsonObject));
                }else if (json.startsWith("[") && json.endsWith("]")){
                    JSONArray jsonArray = JSON.parseArray(json);
                    if (jsonArray != null){
                        for (Object o : jsonArray) {
                            if (o instanceof JSONObject){
                                metrics.add(parseMetricFromJSONObject((JSONObject) o));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("脚本执行异常",e);
            }

        }
        return metrics;
    }

    private DetectResult.Metric parseMetricFromJSONObject(JSONObject jsonObject){
        if (jsonObject != null){
            return new DetectResult.Metric(jsonObject.getString("metric"),jsonObject.getString("value"), CounterType.valueOf(jsonObject.getString("counterType")), jsonObject.getString("tags"));
        }
        return null;
    }

    /**
     * 解析脚本对象
     * @param address
     * @return
     */
    private Script parseScript(String address){
        if (StringUtils.isEmpty(address)){
            log.error("解析地址为空");
            return null;
        }
        String[] ss1 = address.split(",");
        if (ss1.length < 3){
            log.error("地址不符合,分隔的最低要求的三部分:{}",address);
            return null;
        }
        Script script = new Script();
        for (String s : ss1) {
            String[] ss2 = s.split("=");
            if (ss2.length != 2){
                log.error("局部不符合=分隔的两部分:{}",s);
                return null;
            }
            switch (ss2[0]){
                case "type":{
                    script.setScriptType(ScriptType.valueOf(ss2[1].toUpperCase()));
                    break;
                }
                case "stepCycle":{
                    script.setStepCycle(Integer.parseInt(ss2[1]));
                    break;
                }
                case "path":{
                    script.setPath(ss2[1]);
                    break;
                }
                case "metric":{
                    script.setMetric(ss2[1]);
                    break;
                }
                case "result":{
                    script.setResultType(ScriptResultType.valueOf(ss2[1].toUpperCase()));
                    break;
                }
                case "tags":{
                    script.setTags(ss2[1].replace("->","=").replace("||",","));
                    break;
                }
                default:{
                    log.warn("未知的属性：{}",ss2[0]);
                }
            }
        }
        if (!script.isValid()){
            log.error("无效Script(有元素未赋值):{}", script);
            return null;
        }
        return script;
    }

    /**
     * 被探测的地址集合
     *
     * @return 只要该集合不为空, 就会触发监控
     * pluginActivateType属性将不起作用
     */
    @Override
    public Collection<String> detectAddressCollection() {
        return scripts;
    }
}
