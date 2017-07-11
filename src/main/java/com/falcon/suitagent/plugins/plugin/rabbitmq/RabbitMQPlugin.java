/*
 * www.msxf.com Inc.
 * Copyright (c) 2017 All Rights Reserved
 */
package com.falcon.suitagent.plugins.plugin.rabbitmq;
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
 * long.qian@msxf.com 2017-05-29 19:57 创建
 */

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.falcon.suitagent.falcon.CounterType;
import com.falcon.suitagent.plugins.DetectPlugin;
import com.falcon.suitagent.util.HttpUtil;
import com.falcon.suitagent.util.StringUtils;
import com.falcon.suitagent.vo.detect.DetectResult;
import com.github.kevinsawicki.http.HttpRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.math.NumberUtils;

import java.io.IOException;
import java.util.*;

/**
 * @author long.qian@msxf.com
 */
@Slf4j
public class RabbitMQPlugin implements DetectPlugin {
    private int step;
    private List<String> addresses;
    private static String AUTH_PREFIX = "rabbitmq.api.url";

    /**
     * 插件初始化操作
     * 该方法将会在插件运行前进行调用
     *
     * @param properties 包含的配置:
     *                   1、插件目录绝对路径的(key 为 pluginDir),可利用此属性进行插件自定制资源文件读取
     *                   2、插件指定的配置文件的全部配置信息(参见 {@link com.falcon.suitagent.plugins.Plugin#configFileName} 接口项)
     *                   3、授权配置项(参见 {@link com.falcon.suitagent.plugins.Plugin#authorizationKeyPrefix} 接口项
     */
    @Override
    public void init(Map<String, String> properties) {
        step = Integer.parseInt(properties.get("step"));
        Set<String> keys = properties.keySet();
        addresses = new ArrayList<>();
        keys.stream().filter(key -> key.contains(AUTH_PREFIX)).forEach(key -> {
            addresses.add(properties.get(key));
        });
    }

    /**
     * 授权登陆配置的key前缀(配置在authorization.properties文件中)
     * 将会通过init方法的map属性中,将符合该插件的授权配置传入,以供插件进行初始化操作
     * <p>
     * 如 authorizationKeyPrefix = authorization.prefix , 并且在配置文件中配置了如下信息:
     * authorization.prefix.xxx1 = xxx1
     * authorization.prefix.xxx2 = xxx2
     * 则init中的map中将会传入该KV:
     * authorization.prefix.xxx1 : xxx1
     * authorization.prefix.xxx2 : xxx2
     *
     * @return 若不覆盖此方法, 默认返回空, 既该插件无需授权配置
     */
    @Override
    public String authorizationKeyPrefix() {
        return AUTH_PREFIX;
    }

    /**
     * 该插件监控的服务名
     * 该服务名会上报到Falcon监控值的tag(service)中,可用于区分监控值服务
     *
     * @return
     */
    @Override
    public String serverName() {
        return "rabbitmq";
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
     * 该插件在指定插件配置目录下的配置文件名
     *
     * @return 返回该插件对应的配置文件名
     * 默认值:插件简单类名第一个字母小写 加 .properties 后缀
     */
    @Override
    public String configFileName() {
        return "rabbitMQPlugin.properties";
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
        RabbitMQ rabbitMQ = getRabbitMQFromAddress(address);
        if (rabbitMQ != null){
            return getClusterName(rabbitMQ);
        }
        return null;
    }

    private String getClusterName(RabbitMQ rabbitMQ) {
        String apiBody = getAPIResult(rabbitMQ,"api/cluster-name");
        if (apiBody != null){
            JSONObject jsonObject = JSONObject.parseObject(apiBody);
            if (jsonObject != null){
                return jsonObject.getString("name");
            }
        }
        return null;
    }

    private String getAPIResult(RabbitMQ rabbitMQ,String suffix){
        HttpRequest httpRequest = new HttpRequest(String.format("http://%s:%s/%s",rabbitMQ.getIp(),rabbitMQ.getPort(),suffix),"GET")
                .connectTimeout(10000).readTimeout(10000).trustAllCerts().trustAllHosts()
                .authorization("Basic " + Base64.getEncoder().encodeToString(String.format("%s:%s",rabbitMQ.getUsername(),rabbitMQ.getPassword()).getBytes()));
        if (httpRequest.code() != 200){
            log.error("获取API接口数据失败，code：{} ， body：{}",httpRequest.code(),httpRequest.body());
           return null;
        }
        return httpRequest.body();
    }


    /**
     * 一次地址的探测结果
     *
     * @param address 被探测的地址,地址来源于方法 {@link DetectPlugin#detectAddressCollection()}
     * @return 返回被探测的地址的探测结果, 将用于上报监控状态
     */
    @Override
    public DetectResult detectResult(String address) {
        DetectResult detectResult = new DetectResult();
        RabbitMQ rabbitMQ = getRabbitMQFromAddress(address);
        if (rabbitMQ != null){
            try {
                detectResult.setSuccess(HttpUtil.get(String.format("http://%s:%s/api",rabbitMQ.getIp(),rabbitMQ.getPort())).getStatus() == 200);

                List<DetectResult.Metric> metrics = new ArrayList<>();

                //overview信息提取
                List<DetectResult.Metric> overviewMetric = overviewInfo(rabbitMQ);
                if (overviewMetric != null){
                    metrics.addAll(overviewMetric);
                }

                //向每个vHost发送测试的消息并消费，测试是否通过的情况（除了 / vHost）
                List<DetectResult.Metric> alivenessMetric = vHostsAliveness(rabbitMQ);
                if (alivenessMetric != null){
                    metrics.addAll(alivenessMetric);
                }

                String nodeName = getClusterName(rabbitMQ);
                if (nodeName != null){
                    //当前节点的节点健康状况检查
                    DetectResult.Metric nodeHealthMetric = currentNodeHealthCheck(rabbitMQ,nodeName);
                    if (nodeHealthMetric != null){
                        metrics.add(nodeHealthMetric);
                    }

                    //当前节点的节点信息监控数据
                    List<DetectResult.Metric> nodeInfoMetric = currentNodeInfo(rabbitMQ,nodeName);
                    if (nodeInfoMetric != null){
                        metrics.addAll(nodeInfoMetric);
                    }
                }


                //所有的队列监控数据
                List<DetectResult.Metric> allQueuesMetrics = allQueueData(rabbitMQ);
                if (allQueuesMetrics != null){
                    metrics.addAll(allQueuesMetrics);
                }

                detectResult.setMetricsList(metrics);

            } catch (IOException e) {
                log.error("RabbitMQ API数据获取发生异常",e);
            }
        }
        return detectResult;
    }

    /**
     * 从指定的JSONObject对象解析指定路径的值设置到Metric对象中
     * @param result
     * 存放解析后的Metric结果的结合
     * @param jsonObject
     * 需要解析的JSONObject对象
     * @param metricName
     * 指定的Metric指标名字
     * @param path
     * 用->分隔路径，沿途路径必须都是JSONObject对象，叶子节点为最终的值
     */
    private void utilForParsingJSON(List<DetectResult.Metric> result,String metricName,String path,JSONObject jsonObject,CounterType counterType,String tag){
        if (jsonObject != null){
            if (!path.contains("->")){
                String value = jsonObject.getString(path);
                result.add(new DetectResult.Metric(metricName,value,counterType,tag));
                System.out.println(metricName);
            }else {
                String firstPath = path.split("->")[0];
                JSONObject firstJSONObj = jsonObject.getJSONObject(firstPath);
                if (firstJSONObj != null && !firstJSONObj.isEmpty()){
                    String newPath = "";
                    String[] split =path.split("->");
                    for (int i = 1;i<split.length;i++){
                        if ("".equals(newPath)){
                            newPath += split[i];
                        }else {
                            newPath += "->" + split[i];
                        }
                    }
                    utilForParsingJSON(result,metricName,newPath,firstJSONObj,counterType,tag);
                }
            }


        }
    }

    /**
     * overview信息提取
     * @param rabbitMQ
     * @return
     */
    private List<DetectResult.Metric> overviewInfo(RabbitMQ rabbitMQ) {
        List<DetectResult.Metric> result = new ArrayList<>();
        String apiBody = getAPIResult(rabbitMQ,"api/overview");
        if (apiBody != null){
            JSONObject jsonObject = JSONObject.parseObject(apiBody);
            utilForParsingJSON(result,"publish-count","message_stats->publish",jsonObject,CounterType.GAUGE,"");
            utilForParsingJSON(result,"publish-rate","message_stats->publish_details->rate",jsonObject,CounterType.GAUGE,"");
            utilForParsingJSON(result,"confirm-count","message_stats->confirm",jsonObject,CounterType.GAUGE,"");
            utilForParsingJSON(result,"confirm-rate","message_stats->confirm_details->rate",jsonObject,CounterType.GAUGE,"");
            utilForParsingJSON(result,"return-unroutable-count","message_stats->return_unroutable",jsonObject,CounterType.GAUGE,"");
            utilForParsingJSON(result,"return-unroutable-rate","message_stats->return_unroutable_details->rate",jsonObject,CounterType.GAUGE,"");
            utilForParsingJSON(result,"disk_reads-count","message_stats->disk_reads",jsonObject,CounterType.GAUGE,"");
            utilForParsingJSON(result,"disk_reads-rate","message_stats->disk_reads_details->rate",jsonObject,CounterType.GAUGE,"");
            utilForParsingJSON(result,"disk_writes-count","message_stats->disk_writes",jsonObject,CounterType.GAUGE,"");
            utilForParsingJSON(result,"disk_writes-rate","message_stats->disk_writes_details->rate",jsonObject,CounterType.GAUGE,"");
            utilForParsingJSON(result,"get-count","message_stats->get",jsonObject,CounterType.GAUGE,"");
            utilForParsingJSON(result,"get-rate","message_stats->get_details->rate",jsonObject,CounterType.GAUGE,"");
            utilForParsingJSON(result,"get_no_ack-count","message_stats->get_no_ack",jsonObject,CounterType.GAUGE,"");
            utilForParsingJSON(result,"get_no_ack-rate","message_stats->get_no_ack_details->rate",jsonObject,CounterType.GAUGE,"");
            utilForParsingJSON(result,"deliver-count","message_stats->deliver",jsonObject,CounterType.GAUGE,"");
            utilForParsingJSON(result,"deliver-rate","message_stats->deliver_details->rate",jsonObject,CounterType.GAUGE,"");
            utilForParsingJSON(result,"deliver_no_ack-count","message_stats->deliver_no_ack",jsonObject,CounterType.GAUGE,"");
            utilForParsingJSON(result,"deliver_no_ack-rate","message_stats->deliver_no_ack_details->rate",jsonObject,CounterType.GAUGE,"");
            utilForParsingJSON(result,"redeliver-count","message_stats->redeliver",jsonObject,CounterType.GAUGE,"");
            utilForParsingJSON(result,"redeliver-rate","message_stats->redeliver_details->rate",jsonObject,CounterType.GAUGE,"");
            utilForParsingJSON(result,"ack-count","message_stats->ack",jsonObject,CounterType.GAUGE,"");
            utilForParsingJSON(result,"ack-rate","message_stats->ack_details->rate",jsonObject,CounterType.GAUGE,"");
            utilForParsingJSON(result,"deliver_get-count","message_stats->deliver_get",jsonObject,CounterType.GAUGE,"");
            utilForParsingJSON(result,"deliver_get-rate","message_stats->deliver_get_details->rate",jsonObject,CounterType.GAUGE,"");
        }
        return result;
    }

    /**
     * 获取vHost名称列表，除了/vHost
     * @param rabbitMQ
     * @return
     */
    private List<String> getVHosts(RabbitMQ rabbitMQ){
        List<String> vHosts = new ArrayList<>();
        String apiBody = getAPIResult(rabbitMQ,"api/vhosts");
        if (apiBody != null){
            JSONArray jsonArray = JSONArray.parseArray(apiBody);
            for (Object o : jsonArray) {
                JSONObject jsonObject = (JSONObject) o;
                if (!jsonObject.getString("name").equals("/")){
                    vHosts.add(jsonObject.getString("name"));
                }
            }
        }
        return vHosts;
    }

    /**
     * 向每个vHost发送测试的消息并消费，测试是否通过的情况（除了 / vHost）
     * @param rabbitMQ
     * @return
     */
    private List<DetectResult.Metric> vHostsAliveness(RabbitMQ rabbitMQ) {
        List<DetectResult.Metric> result = new ArrayList<>();
        List<String> vHosts = getVHosts(rabbitMQ);
        for (String vHost : vHosts) {
            String apiBody = getAPIResult(rabbitMQ,"api/aliveness-test/" + vHost);
            if (apiBody != null){
                JSONObject jsonObject = JSONObject.parseObject(apiBody);
                String status = jsonObject.getString("status").toLowerCase();
                result.add(new DetectResult.Metric("vhost-test",("ok".equals(status) ? "1" : "0"),CounterType.GAUGE,"vhost=" + vHost));
            }
        }
        return result;
    }

    /**
     * 当前节点的节点健康状况检查
     * @param rabbitMQ
     * @param nodeName
     * @return
     */
    private DetectResult.Metric currentNodeHealthCheck(RabbitMQ rabbitMQ, String nodeName) {
        String apiBody = getAPIResult(rabbitMQ,"api/healthchecks/node/" + nodeName);
        if (apiBody != null){
            JSONObject jsonObject = JSONObject.parseObject(apiBody);
            String status = jsonObject.getString("status").toLowerCase();
            return new DetectResult.Metric("node-healthcheck",("ok".equals(status) ? "1" : "0"),CounterType.GAUGE,"");
        }
        return null;
    }

    /**
     * 当前节点的节点信息监控数据
     * @param rabbitMQ
     * @param nodeName
     * @return
     */
    private List<DetectResult.Metric> currentNodeInfo(RabbitMQ rabbitMQ, String nodeName) {
        List<DetectResult.Metric> result = new ArrayList<>();
        String apiBody = getAPIResult(rabbitMQ,"api/nodes/" + nodeName);
        if (apiBody != null){
            JSONObject jsonObject = JSONObject.parseObject(apiBody);
            utilForParsingJSON(result,"fd_total","fd_total",jsonObject,CounterType.GAUGE,"");
            utilForParsingJSON(result,"mem_used","mem_used",jsonObject,CounterType.GAUGE,"");
            utilForParsingJSON(result,"sockets_used","sockets_used",jsonObject,CounterType.GAUGE,"");
            utilForParsingJSON(result,"run_queue","run_queue",jsonObject,CounterType.GAUGE,"");
            JSONArray partitions = jsonObject.getJSONArray("partitions");
            result.add(new DetectResult.Metric("partitions",String.valueOf(partitions.size()),CounterType.GAUGE,""));
        }
        return result;
    }

    /**
     * 所有的队列监控数据
     * @param rabbitMQ
     * @return
     */
    private List<DetectResult.Metric> allQueueData(RabbitMQ rabbitMQ) {
        List<DetectResult.Metric> result = new ArrayList<>();
        String apiBody = getAPIResult(rabbitMQ,"api/queues");
        if (apiBody != null){
            if (apiBody.trim().equals("[]")){
                log.info("当前RabbitMQ({}:{})无队列数据",rabbitMQ.getIp(),rabbitMQ.getPort());
            }else {
                JSONArray jsonArray = JSONArray.parseArray(apiBody);
                //队列数
                result.add(new DetectResult.Metric("queues_count",String.valueOf(jsonArray.size()),CounterType.GAUGE,""));
                long consumersCountTotal = 0;
                for (Object o : jsonArray) {
                    JSONObject jsonObject = (JSONObject) o;
                    String queueName = jsonObject.getString("name");
                    String vHost = jsonObject.getString("vhost");
                    String tag = String.format("queue=%s,vhost=%s",queueName,vHost);
                    consumersCountTotal += jsonObject.getLong("consumers");
                    //队列消费者接收新消息的时间的比例
                    utilForParsingJSON(result,"queues_consumer_utilisation","consumer_utilisation",jsonObject,CounterType.GAUGE,tag);
                    //队列中的消息总数
                    utilForParsingJSON(result,"queues_messages","messages",jsonObject,CounterType.GAUGE,tag);
                    //发送给客户端单但至今还未被确认的消息数量
                    utilForParsingJSON(result,"queues_messages_unacknowledged","messages_unacknowledged",jsonObject,CounterType.GAUGE,tag);
                    //每秒发送给客户端但至今还未被确认的消息数量
                    utilForParsingJSON(result,"queues_messages_unacknowledged_rate","messages_unacknowledged_details->rate",jsonObject,CounterType.GAUGE,tag);
                    //准备发送给客户端的数量
                    utilForParsingJSON(result,"queues_messages_ready","messages_ready",jsonObject,CounterType.GAUGE,tag);
                    //每秒准备发送给客户端的数量
                    utilForParsingJSON(result,"queues_messages_ready_rate","messages_ready_details->rate",jsonObject,CounterType.GAUGE,tag);
                    //发布的消息数量
                    utilForParsingJSON(result,"queues_messages_publish_count","message_stats->publish",jsonObject,CounterType.GAUGE,tag);
                    //每秒发布的消息数量
                    utilForParsingJSON(result,"queues_messages_publish_count_rate","message_stats->publish_details->rate",jsonObject,CounterType.GAUGE,tag);
                    //发送给客户端并确认的消息数量
                    utilForParsingJSON(result,"queues_messages_ack_count","message_stats->ack",jsonObject,CounterType.GAUGE,tag);
                    //每秒发送给客户端并确认的消息数量
                    utilForParsingJSON(result,"queues_messages_ack_count_rate","message_stats->ack_details->rate",jsonObject,CounterType.GAUGE,tag);
                    //消费者接收并响应的消息数量
                    utilForParsingJSON(result,"queues_messages_deliver_count","message_stats->deliver",jsonObject,CounterType.GAUGE,tag);
                    //每秒消费者接收并响应的消息数量
                    utilForParsingJSON(result,"queues_messages_deliver_count_rate","message_stats->deliver_details->rate",jsonObject,CounterType.GAUGE,tag);

                }

                //消费者总数量
                result.add(new DetectResult.Metric("queues_consumers_count_total",String.valueOf(consumersCountTotal),CounterType.GAUGE,""));

            }
        }
        return result;
    }

    /**
     * 被探测的地址集合
     *
     * @return 只要该集合不为空, 就会触发监控
     * pluginActivateType属性将不起作用
     */
    @Override
    public Collection<String> detectAddressCollection() {
        return this.addresses;
    }

    private RabbitMQ getRabbitMQFromAddress(String address){
        if (StringUtils.isEmpty(address)){
            log.error("转换的地址为空");
            return null;
        }
        RabbitMQ rabbitMQ = new RabbitMQ();
        String[] split = address.split(":");
        if (split.length != 4){
            log.error("RabbitMQ监控配置不合规：{}",address);
            return null;
        }
        if (!NumberUtils.isNumber(split[1])){
            log.error("端口号配置不合规：{}",split[1]);
            return null;
        }
        rabbitMQ.setIp(split[0]);
        rabbitMQ.setPort(Integer.parseInt(split[1]));
        rabbitMQ.setUsername(split[2]);
        rabbitMQ.setPassword(split[3]);
        return rabbitMQ;
    }
}
