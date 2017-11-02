/*
 * www.msxf.com Inc.
 * Copyright (c) 2017 All Rights Reserved
 */
package com.falcon.suitagent.plugins.plugin.redis;
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
 * long.qian@msxf.com 2017-06-19 15:02 创建
 */

import com.falcon.suitagent.falcon.CounterType;
import com.falcon.suitagent.plugins.DetectPlugin;
import com.falcon.suitagent.plugins.Plugin;
import com.falcon.suitagent.util.*;
import com.falcon.suitagent.vo.detect.DetectResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.math.NumberUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * @author long.qian@msxf.com
 */
@Slf4j
public class RedisPlugin implements DetectPlugin {

    /**
     * 缓存被探测到的地址，防止Redis进程关闭是无法上报不可用的状态
     */
    private static final ConcurrentSkipListSet<String> CACHE_ADDRESS = new ConcurrentSkipListSet<>();

    /**
     * 缓存客户端地址
     */
    private static final String[] CACHE_CLIENT = new String[0];

    private int step;
    /**
     * 配置文件配置的端口和对应配置文件路径
     */
    private Map<String,String> redisPortToConfMap = new HashMap<>();

    /**
     * 需要采集相对变化量的指标
     */
    private static List<String> REDIS_RELATIVE_METRICS =
            Arrays.asList(
                    "connected_clients",
                    "blocked_clients",
                    "total_connections_received",
                    "total_commands_processed",
                    "rejected_connections",
                    "expired_keys",
                    "evicted_keys",
                    "keyspace_hits",
                    "keyspace_misses",
                    "pubsub_channels",
                    "pubsub_patterns",
                    "role"
            );
    /**
     * 相对变化量数据记录
     */
    private static ConcurrentHashMap<String,Number> metricsHistoryValueForRelative = new ConcurrentHashMap<>();

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
        this.step = Integer.parseInt(properties.get("step"));
        Set<String> keys = properties.keySet();
        keys.stream().filter(key -> key.startsWith("redis.conf")).forEach(key -> {
            String port = key.replace("redis.conf.","");
            redisPortToConfMap.put(port,properties.get(key));
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
        return "redisPlugin.properties";
    }

    /**
     * 该插件监控的服务名
     * 该服务名会上报到Falcon监控值的tag(service)中,可用于区分监控值服务
     *
     * @return
     */
    @Override
    public String serverName() {
        return "redis";
    }

    /**
     * 监控值的获取和上报周期(秒)
     *
     * @return
     */
    @Override
    public int step() {
        return step;
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
        try {
            String addr = address.split(" ")[1];
            String agentSignName = addr.replace(":","-");
            String ip = HostUtil.getHostIp();
            agentSignName = agentSignName.
                    replace("0.0.0.0",ip).
                    replace("127.0.0.1",ip).
                    replace("localhost",ip).
                    replace("*",ip);
            return agentSignName;
        } catch (Exception e) {
            log.error("",e);
        }
        return null;
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
        try {
            String redisCli;
            if (FileUtil.isExist(CACHE_CLIENT[0])) {
                //使用缓存的客户端位置
                redisCli = CACHE_CLIENT[0];
            }else {
                //探测客户端位置
                CommandUtilForUnix.ExecuteResult cli = CommandUtilForUnix.execWithReadTimeLimit("whereis redis-cli",false,7);
                redisCli = cli.msg.replace("redis-cli:","").trim();
                if (StringUtils.isEmpty(redisCli) || !FileUtil.isExist(redisCli)){
                    String cliPath = address.split(" ")[0].replace("redis-server","redis-cli");
                    if(FileUtil.isExist(cliPath)){
                        redisCli = cliPath;
                        CACHE_CLIENT[0] = redisCli;
                    }else {
                        log.warn("未找到redis-cli的执行路径（系统PATH中未找到 redis-cli 命令，且 redis-server 没有用绝对路径方式启动：{}。请将redis-cli加入系统PATH或以️绝对路径方式启动redis-server），使用默认的redis-cli命令",address.split(" ")[0]);
                        redisCli = "redis-cli";
                    }
                }else if (FileUtil.isExist(redisCli)) {
                    CACHE_CLIENT[0] = redisCli;
                }
            }

//            String ip = address.split("\\:")[0];
            String port = address.split("\\:")[1];

            String cmd4Ping;
            if (redisPortToConfMap.get(port) == null){
                cmd4Ping = String.format("%s -p %s ping",redisCli,port);
            }else {
                cmd4Ping = String.format("%s -a %s -p %s ping",redisCli,getRedisPasswordFromConfFile(redisPortToConfMap.get(port)),port);
            }

            String cmd4Info;
            if (redisPortToConfMap.get(port) == null){
                cmd4Info = String.format("%s -p %s info",redisCli,port);
            }else {
                cmd4Info = String.format("%s -a %s -p %s info",redisCli,getRedisPasswordFromConfFile(redisPortToConfMap.get(port)),port);
            }

            boolean detectSuccess = false;

            CommandUtilForUnix.ExecuteResult pingResult = CommandUtilForUnix.execWithReadTimeLimit(cmd4Ping,false,7);
            if (pingResult.isSuccess){
                String msg = pingResult.msg;
                detectSuccess = "PONG".equals(msg.trim());
            }else {
                log.error("Redis采集命令{}执行失败：{}",cmd4Ping,pingResult);
            }

            detectResult.setSuccess(detectSuccess);
            if (!detectSuccess){
                //ping失败，直接返回不可用信息
                return detectResult;
            }

            CommandUtilForUnix.ExecuteResult infoResult = CommandUtilForUnix.execWithReadTimeLimit(cmd4Info,false,7);
            if (infoResult.isSuccess){
                String msg = infoResult.msg;
                Map<String,String> map = new HashMap<>();
                for (String prop : msg.split("\n")) {
                    prop = prop.trim();
                    if (!StringUtils.isEmpty(prop) && !prop.startsWith("#")){
                        String[] split = prop.split("\\:");
                        String key = split[0];
                        String value = split[1];
                        map.put(key,value);
                    }
                }
                detectResult.setMetricsList(getInfoMetrics(map,address));

            }else {
                log.error("Redis采集命令{}执行失败：{}",cmd4Info,infoResult);
            }
        } catch (Exception e) {
            log.error("Redis监控数据采集异常");
        }
        return detectResult;
    }

    /**
     * 从采集的info结果集中过滤需要监控的指标
     * @param map
     * @param address
     * @return
     */
    private List<DetectResult.Metric> getInfoMetrics(Map<String, String> map, String address){
        List<DetectResult.Metric> metricList = new ArrayList<>();
        //运行天数
        addMetrics(metricList,map,"uptime_in_days",CounterType.GAUGE,"");
        //已连接客户端的数量以及相对值
        addMetrics(metricList,map,"connected_clients",CounterType.GAUGE,"");
        //当前的客户端连接中，最长的输出列表
        addMetrics(metricList,map,"client_longest_output_list",CounterType.GAUGE,"");
        //当前连接的客户端中，最大的输入缓存
        addMetrics(metricList,map,"client_biggest_input_buf",CounterType.GAUGE,"");
        //正在等待阻塞命令（BLOP、BRPOP、BRPOPLPUSH）的客户端的数量以及相对值
        addMetrics(metricList,map,"blocked_clients",CounterType.GAUGE,"");
        //Redis分配器分配给Redis的内存
        addMetrics(metricList,map,"used_memory",CounterType.GAUGE,"");
        //操作系统分配给Redis的内存。也就是Redis占用的内存大小
        addMetrics(metricList,map,"used_memory_rss",CounterType.GAUGE,"");
        //Redis的内存消耗峰值
        addMetrics(metricList,map,"used_memory_peak",CounterType.GAUGE,"");
        //Lua引擎所使用的内存大小
        addMetrics(metricList,map,"used_memory_lua",CounterType.GAUGE,"");
        //used_memory_rss和used_memory之间的比率,内存碎片比率
        addMetrics(metricList,map,"mem_fragmentation_ratio",CounterType.GAUGE,"");
        //距离最后一次成功创建持久化文件之后，改变了多少个键值
        addMetrics(metricList,map,"rdb_changes_since_last_save",CounterType.GAUGE,"");
        if (map.get("rdb_last_bgsave_status") != null){
            //一个标志值，记录了最后一次创建RDB文件的结果是成功还是失败
            String value = map.get("rdb_last_bgsave_status");
            metricList.add(new DetectResult.Metric("rdb_last_bgsave_status","ok".equals(value.toLowerCase())?"1":"0",CounterType.GAUGE,""));
        }
        //最后一次创建RDB文件耗费的秒数
        addMetrics(metricList,map,"rdb_last_bgsave_time_sec",CounterType.GAUGE,"");
        //最后一次AOF重写操作的耗时
        addMetrics(metricList,map,"aof_last_rewrite_time_sec",CounterType.GAUGE,"");
        if (map.get("aof_last_bgrewrite_status") != null){
            //一个标志值，记录了最后一次重写AOF文件的结果是成功还是失败
            String value = map.get("aof_last_bgrewrite_status");
            metricList.add(new DetectResult.Metric("aof_last_bgrewrite_status","ok".equals(value.toLowerCase())?"1":"0",CounterType.GAUGE,""));
        }
        //AOF文件目前的大小(AOF持久化功能处于开启状态)
        addMetrics(metricList,map,"aof_current_size",CounterType.GAUGE,"");
        //AOF缓冲区的大小(AOF持久化功能处于开启状态)
        addMetrics(metricList,map,"aof_buffer_length",CounterType.GAUGE,"");
        //AOF重写缓冲区的大小(AOF持久化功能处于开启状态)
        addMetrics(metricList,map,"aof_rewrite_buffer_length",CounterType.GAUGE,"");
        //在后台I/0队列里面，等待执行的fsync数量(AOF持久化功能处于开启状态)
        addMetrics(metricList,map,"aof_pending_bio_fsync",CounterType.GAUGE,"");
        //被延迟执行的fsync数量(AOF持久化功能处于开启状态)
        addMetrics(metricList,map,"aof_delayed_fsync",CounterType.GAUGE,"");
        //服务器已经接受的连接请求数量以及相对值
        addMetrics(metricList,map,"total_connections_received",CounterType.GAUGE,"");
        //服务器已经执行的命令数量以及相对值
        addMetrics(metricList,map,"total_commands_processed",CounterType.GAUGE,"");
        //服务器每秒中执行的命令数量
        addMetrics(metricList,map,"instantaneous_ops_per_sec",CounterType.GAUGE,"");
        //因为最大客户端数量限制而被拒绝的连接请求数量以及相对值
        addMetrics(metricList,map,"rejected_connections",CounterType.GAUGE,"");
        //因为过期而被自动删除的数据库键数量以及相对量
        addMetrics(metricList,map,"expired_keys",CounterType.GAUGE,"");
        //因为最大内存容量限制而被驱逐（evict）的键数量以及相对量
        addMetrics(metricList,map,"evicted_keys",CounterType.GAUGE,"");
        //查找数据库键成功的次数以及相对量
        String hits = addMetrics(metricList,map,"keyspace_hits",CounterType.GAUGE,"");
        //查找数据库键失败的次数以及相对量
        String misses = addMetrics(metricList,map,"keyspace_misses",CounterType.GAUGE,"");
        //命中率百分比 hits / (hits + misses) * 100
        metricList.add(new DetectResult.Metric("keyspace_hit_ratio",
                "0".equals(hits)?"0":String.valueOf(Maths.mul(Maths.div(NumberUtils.createDouble(hits),
                        Maths.add(NumberUtils.createDouble(hits),
                                NumberUtils.createDouble(misses))),100))
                ,CounterType.GAUGE,""));
        //目前被订阅的频道数量以及相对值
        addMetrics(metricList,map,"pubsub_channels",CounterType.GAUGE,"");
        //目前被订阅的模式数量以及相对值
        addMetrics(metricList,map,"pubsub_patterns",CounterType.GAUGE,"");
        //最近一次fork()操作耗费的时间(毫秒)
        addMetrics(metricList,map,"latest_fork_usec",CounterType.GAUGE,"");

        if (map.get("role") != null){
            //在主从复制中，充当的角色。1:master
            String value = map.get("role");
            metricList.add(new DetectResult.Metric("role","master".equals(value.toLowerCase())?"1":"0",CounterType.GAUGE,""));
        }
        //连接的从库数量
        addMetrics(metricList,map,"connected_slaves",CounterType.GAUGE,"");

        if (map.get("master_link_status") != null){
            //复制连接当前的状态 1:正常 0：断开(如果当前服务器是从服务器)
            String value = map.get("master_link_status");
            metricList.add(new DetectResult.Metric("master_link_status","up".equals(value.toLowerCase())?"1":"0",CounterType.GAUGE,""));
        }
        //距离最近一次与主服务器进行通信已经过去了多少秒(如果当前服务器是从服务器)
        addMetrics(metricList,map,"master_last_io_seconds_ago",CounterType.GAUGE,"");
        //主从服务器连接断开了多少秒(如果主从服务器之间的连接处于断线状态)
        addMetrics(metricList,map,"master_link_down_since_seconds",CounterType.GAUGE,"");

        /*
        数据库健监控统计
         */
        Set<String> keys = map.keySet();
        keys.stream().filter(key -> key.matches("db\\d+")).forEach(key -> {
            String tag = "db=" + key;
            String redisKeys = "";
            String expires = "";
            String avgTtl = "";
            String[] split = map.get(key).split(",");
            redisKeys = split[0].split("=")[1];
            expires = split[1].split("=")[1];
            avgTtl = split[2].split("=")[1];
            metricList.add(new DetectResult.Metric("keyspace_keys",redisKeys,CounterType.GAUGE,tag));
            metricList.add(new DetectResult.Metric("keyspace_expires",expires,CounterType.GAUGE,tag));
            metricList.add(new DetectResult.Metric("keyspace_avg_ttl",avgTtl,CounterType.GAUGE,tag));
        });

        metricList.addAll(getRelativeMetrics(metricList,address));
        return metricList;
    }

    /**
     * 添加Metric
     * @param metricList
     * @param map
     * @param key
     * @param counterType
     * @param tag
     * @return
     * 监控值
     */
    private static String addMetrics(List<DetectResult.Metric> metricList,Map<String, String> map,String key,CounterType counterType,String tag){
        if (map.get(key) != null){
            metricList.add(new DetectResult.Metric(key,map.get(key),counterType,tag));
            return map.get(key);
        }
        return "0";
    }

    /**
     * 采集可见性指标
     * @param metricList
     * @param address
     * @return
     */
    private List<DetectResult.Metric> getRelativeMetrics(List<DetectResult.Metric> metricList, String address){
        List<DetectResult.Metric> relativeMetrics = new ArrayList<>();
        if (metricList != null){
            metricList.forEach(metric -> {
                String metricKey = metric.metricName + address;
                if (REDIS_RELATIVE_METRICS.contains(metric.metricName)){
                    Number previousValue = metricsHistoryValueForRelative.get(metricKey);
                    if (previousValue == null){
                        previousValue = NumberUtils.createNumber(metric.value);
                        //保存此次的值
                        metricsHistoryValueForRelative.put(metricKey,previousValue);
                    }else {
                        String value = String.valueOf(Maths.sub(NumberUtils.createDouble(metric.value),previousValue.doubleValue()));
                        metricsHistoryValueForRelative.put(metricKey,NumberUtils.createDouble(metric.value));
                        relativeMetrics.add(new DetectResult.Metric(metric.metricName + "_relative",value,CounterType.GAUGE,""));
                    }
                }
            });
        }
        return relativeMetrics;
    }

    /**
     * 从配置文件获取密码
     * @param filePath
     * @return
     */
    private String getRedisPasswordFromConfFile(String filePath){
        String content = FileUtil.getTextFileContent(filePath);
        String password = "";
        if (!"".equals(content)){
            for (String s : content.split("\n")) {
                if (s.trim().startsWith("requirepass")){
                    String[] ss = s.trim().split("\\s+");
                    if (ss.length == 2){
                        password = ss[1];
                    }else {
                        log.error("密码格式配置错误：{}",s);
                    }
                }
            }
        }
        if ("".equals(password)){
            log.warn("从配置文件{}中未找到密码配置",filePath);
        }
        return password;
    }

    /**
     * 被探测的地址集合
     *
     * @return 只要该集合不为空, 就会触发监控
     * pluginActivateType属性将不起作用
     */
    @Override
    public Collection<String> detectAddressCollection() {
        return new ArrayList<>();
    }

    /**
     * 自动探测地址的实现
     * 若配置文件已配置地址,将不会调用此方法
     * 若配置文件未配置探测地址的情况下,将会调用此方法,若该方法返回非null且有元素的集合,则启动运行插件,使用该方法返回的探测地址进行监控
     *
     * @return
     */
    @Override
    public Collection<String> autoDetectAddress() {
        String cmd = "ps aux | grep redis-server| grep -v grep|awk '{print $11 \" \" $12}'";
        try {
            CommandUtilForUnix.ExecuteResult executeResult = CommandUtilForUnix.execWithReadTimeLimit(cmd,false,7);
            if(executeResult.isSuccess){
                for (String result : executeResult.msg.split("\n")) {
                    if (result.split(" ").length != 2 && result.split(":").length != 2){
                        log.warn("探测到的Redis地址格式非{Cmd IP:Port}形式，将跳过此采集：{}",result.trim());
                        continue;
                    }
                    CACHE_ADDRESS.add(result.trim());
                }
            }else {
                log.error("命令执行失败：{}",executeResult);
            }
        } catch (Exception e) {
            log.error("Redis插件命令执行异常",e);
        }
        return CACHE_ADDRESS;
    }
}
