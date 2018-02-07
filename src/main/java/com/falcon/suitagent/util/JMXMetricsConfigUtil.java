/*
 * www.yiji.com Inc.
 * Copyright (c) 2017 All Rights Reserved
 */
package com.falcon.suitagent.util;
/*
 * 修订记录:
 * guqiu@yiji.com 2017-01-04 10:55 创建
 */

import com.falcon.suitagent.config.AgentConfiguration;
import com.falcon.suitagent.plugins.JMXPlugin;
import com.falcon.suitagent.vo.jmx.JMXMetricsConfiguration;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author guqiu@yiji.com
 */
@Slf4j
public class JMXMetricsConfigUtil {


    /**
     * 获取需要采集的监控项配置
     * @param jmxPlugin
     * @return
     */
    public static Set<JMXMetricsConfiguration> getMetricsConfig(JMXPlugin jmxPlugin){
        Set<JMXMetricsConfiguration> jmxMetricsConfigurations = new HashSet<>();
        setMetricsConfig("agent.common.metrics.type.", AgentConfiguration.INSTANCE.getJmxCommonMetricsConfPath(), jmxMetricsConfigurations);
        setMetricsConfig(jmxPlugin.basePropertiesKey(),
                AgentConfiguration.INSTANCE.getPluginConfPath() + File.separator + jmxPlugin.configFileName(), jmxMetricsConfigurations);

        return jmxMetricsConfigurations;
    }

    /**
     * 设置配置的jmx监控属性
     *
     * @param basePropertiesKey        配置属性的前缀key值
     * @param propertiesPath           监控属性的配置文件路径
     * @param jmxMetricsConfigurations 需要保存的集合对象
     * @throws IOException
     */
    private static void setMetricsConfig(String basePropertiesKey, String propertiesPath, Set<JMXMetricsConfiguration> jmxMetricsConfigurations) {

        if (!StringUtils.isEmpty(basePropertiesKey) &&
                !StringUtils.isEmpty(propertiesPath)) {
            try (FileInputStream in = new FileInputStream(propertiesPath)) {
                Properties properties = new Properties();
                properties.load(in);
                for (Object o : properties.keySet()) {
                    String key = (String) o;
                    if (key != null && key.matches(basePropertiesKey + "\\.?\\w+\\.objectName")) {
                        Pattern prefixPattern = Pattern.compile(basePropertiesKey + "\\.?\\w+\\.");
                        Matcher prefixMatcher = prefixPattern.matcher(key);
                        if (prefixMatcher.find()) {
                            String prefix = prefixMatcher.group();
                            JMXMetricsConfiguration metricsConfiguration = new JMXMetricsConfiguration();
                            //设置ObjectName
                            metricsConfiguration.setObjectName(properties.getProperty(prefix + "objectName"));
                            //设置counterType
                            metricsConfiguration.setCounterType(properties.getProperty(prefix + "counterType"));
                            //设置metrics
                            metricsConfiguration.setMetrics(properties.getProperty(prefix + "metrics"));
                            //设置metrics
                            metricsConfiguration.setValueExpress(properties.getProperty(prefix + "valueExpress"));
                            String tag = properties.getProperty(prefix + "tag");
                            //设置tag
                            metricsConfiguration.setTag(StringUtils.isEmpty(tag) ? "" : tag);
                            String alias = properties.getProperty(prefix + "alias");
                            metricsConfiguration.setAlias(StringUtils.isEmpty(alias) ? metricsConfiguration.getMetrics() : alias);

                            jmxMetricsConfigurations.add(metricsConfiguration);
                        }
                    }
                }
            } catch (IOException e) {
                log.error("配置文件读取失败", e);
            }
        }
    }
}
