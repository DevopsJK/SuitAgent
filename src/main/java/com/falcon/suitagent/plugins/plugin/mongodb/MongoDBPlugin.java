/*
 * www.yiji.com Inc.
 * Copyright (c) 2016 All Rights Reserved
 */
package com.falcon.suitagent.plugins.plugin.mongodb;
/*
 * 修订记录:
 * guqiu@yiji.com 2016-10-25 15:09 创建
 */

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.falcon.suitagent.falcon.CounterType;
import com.falcon.suitagent.plugins.DetectPlugin;
import com.falcon.suitagent.util.*;
import com.falcon.suitagent.vo.detect.DetectResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.math.NumberUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author guqiu@yiji.com
 */
@Slf4j
public class MongoDBPlugin implements DetectPlugin {

    private int step;

    private Map<String,String> mongoAuthConf = new HashMap<>();
    private final List<Pattern> patterns = Arrays.asList(
            Pattern.compile(":\\s*\\w*\\s*,(\\s*\\w*\\s*,)+"),
            Pattern.compile(":\\s*\"\\w*\"\\s*,(\\s*\"\\w*\"\\s*,)+")
    );

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
        this.step = Integer.parseInt(properties.get("step"));
        mongoAuthConf.clear();
        for (String key : properties.keySet()) {
            if (key.startsWith("mongodb.auth")) {
                mongoAuthConf.put(key,properties.get(key));
            }
        }
    }

    /**
     * 该插件在指定插件配置目录下的配置文件名
     *
     * @return 返回该插件对应的配置文件名
     * 默认值:插件简单类名第一个字母小写 加 .properties 后缀
     */
    @Override
    public String configFileName() {
        return "mongoDBPlugin.properties";
    }

    /**
     * 该插件监控的服务名
     * 该服务名会上报到Falcon监控值的tag(service)中,可用于区分监控值服务
     *
     * @return
     */
    @Override
    public String serverName() {
        return "mongodb";
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
        String[] split = address.split(":-->:");
        if (split.length >= 2) {
            return split[1];
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
        detectResult.setSuccess(false);

        try {
            //address格式：BinPath:-->:Port
            String[] ss = address.split(":-->:");
            String binPath = ss[0];
            String port = ss[1];
            String user = "";
            boolean hasAuth = false;
            String cmd = String.format("echo 'db.serverStatus()' | %s --port %s",binPath,port);
            if ((user = mongoAuthConf.get(String.format("mongodb.auth.%s.user",port))) != null) {
                //有指定端口的授权配置
                String pwd = mongoAuthConf.get(String.format("mongodb.auth.%s.pwd",port));
                if (StringUtils.isEmpty(pwd)) {
                    log.error("{}：缺少的配置项：{}",this.getClass().getSimpleName(),String.format("mongodb.auth.%s.pwd",port));
                    return null;
                }
                cmd += " -u " + user + " -p " + pwd + " --authenticationDatabase admin";
                hasAuth = true;
            }else if ((user = mongoAuthConf.get("mongodb.auth.user")) != null) {
                //有通用的授权配置
                String pwd = mongoAuthConf.get("mongodb.auth.pwd");
                if (StringUtils.isEmpty(pwd)) {
                    log.error("{}：缺少的配置项：{}",this.getClass().getSimpleName(),"mongodb.auth.pwd");
                    return null;
                }
                cmd += " -u " + user + " -p " + pwd + " --authenticationDatabase admin";
                hasAuth = true;
            }
            CommandUtilForUnix.ExecuteResult executeResult = CommandUtilForUnix.execWithReadTimeLimit(cmd,false,7);
            //若不能访问，添加ip进行访问
            if (!executeResult.msg.contains("bye")){
                executeResult = CommandUtilForUnix.execWithReadTimeLimit(cmd + " --host " + HostUtil.getHostIp(),false,7);
            }
            if(!StringUtils.isEmpty(executeResult.msg)){
                if (hasAuth) {
                    if (executeResult.msg.contains("Authentication failed") || executeResult.msg.contains("login failed")) {
                        log.error("{}:实例端口 {} 授权访问认证失败，请检查MongoDB授权访问配置是否正确",this.getClass().getSimpleName(),port);
                        return null;
                    }
                }
                String msg = executeResult.msg;
                log.debug(msg);
                int startSymbol = msg.indexOf("{");
                int endSymbol = msg.lastIndexOf("}");
                if(startSymbol != -1 && endSymbol != -1){
                    String json = msg.substring(startSymbol,endSymbol + 1);
                    json = transform(json);
                    //修正一些json语法不对的字符串，如
                    // "ts": 1516943429,20, 修改为 "ts": [1516943429,20],
                    // "xxx": "zzz","ccc", 修改为 "xxx": ["zzz","ccc"],
                    for (Pattern pattern : patterns) {
                        Matcher matcher = pattern.matcher(json);
                        while (matcher.find()) {
                            String find = matcher.group();
                            json = json.replace(find,":" + "[" + find.substring(0,find.length() - 1).replace(":","") + "],");
                        }
                    }
                    JSONObject jsonObject = JSON.parseObject(json);
                    Map<String,Object> map = new HashMap<>();
                    JSONUtil.jsonToMap(map,jsonObject,null);
                    List<DetectResult.Metric> metrics = new ArrayList<>();
                    for (Map.Entry<String, Object> entry : map.entrySet()) {
                        if(NumberUtils.isNumber(String.valueOf(entry.getValue()))){
                            DetectResult.Metric metric = new DetectResult.Metric(entry.getKey(),
                                    String.valueOf(entry.getValue()),
                                    CounterType.GAUGE,
                                    "");
                            metrics.add(metric);
                        }
                    }
                    detectResult.setMetricsList(metrics);
                    detectResult.setSuccess(true);
                }
            }
        } catch (Exception e) {
            log.error("MongoDB监控异常",e);
        }

        return detectResult;
    }

    private String transform(String msg){
        return msg.replaceAll("\\w+\\(","")
                .replace(")","");
    }

    /**
     * 被探测的地址集合
     *
     * @return 只要该集合不为空, 就会触发监控
     * pluginActivateType属性将不起作用
     */
    @Override
    public Collection<String> detectAddressCollection() {
        return null;
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
        Set<String> adds = new HashSet<>();
        try {
            String binPath = getMongoBinPath();
            if (binPath != null){
                if (FileUtil.isExist(binPath)) {
                    log.info("自动探测mongo执行文件位置：{}",binPath);
                    //命令行Post信息
                    Set<String> ports = getPortsFromPs();
                    //命令行配置文件的Port信息
                    Set<String> confs = getRunConfFilesFromPs();
                    for (String conf : confs) {
                        String port = getPortFromConfFile(binPath,conf);
                        if (port == null){
                            log.warn("从配置文件{}未找到MongoDB的启动端口，指定默认端口：27017",conf);
                            ports.add("27017");
                        }else {
                            log.info("{}:发现端口 {}",this.getClass().getSimpleName(),port);
                            ports.add(port);
                        }
                    }

                    for (String port : ports) {
                        if (port != null){
                            //返回格式 BinPath:-->:Port
                            adds.add(binPath + ":-->:" + port);
                        }
                    }
                }else {
                    log.error("{}：路径不存在：{}",this.getClass().getSimpleName(),binPath);
                }
            }
        } catch (Exception e) {
            return adds;
        }
        return adds;
    }

    /**
     * 从配置文件读取端口地址
     *
     * @param binPath
     * @param confPath
     * @return
     */
    private String getPortFromConfFile(String binPath, String confPath){
        if (confPath != null){
            if (!confPath.startsWith(File.separator)) {
                //相对路径的配置文件，进行绝对路径转换
                String dir = new File(binPath).toPath().getParent().toFile().getAbsolutePath();
                Path path = Paths.get(dir,confPath);
                confPath = path.toFile().getAbsolutePath();
            }
            try {
                String content = FileUtil.getTextFileContent(confPath);
                String[] cs = content.split("\n");
                String port;
                //properties形式的判断
                for (String s : cs) {
                    if (s.trim().matches("port\\s*=\\s*\\d+")){
                        port = s.trim().replaceAll("port\\s*=\\s*","").trim();
                        if (NumberUtils.isNumber(port)){
                            return port;
                        }
                    }
                }
                //yaml形式的判断
                boolean turnNetConf = false;
                for (String s : cs) {
                    if (!s.trim().startsWith("#")){
                        int end = s.length();
                        if (s.contains("#")){
                            end = s.indexOf("#");
                        }
                        String str = s.substring(0,end);
                        if (turnNetConf){
                            if (str.contains("port:")){
                                port = str.replace("port:","").trim();
                                if (NumberUtils.isNumber(port)){
                                    return port;
                                }
                            }
                        }else {
                            if (str.endsWith("net:")){
                                turnNetConf = true;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("",e);
            }
        }
        return null;
    }

    /**
     * 从ps命令获取端口
     * @return
     */
    private Set<String> getPortsFromPs(){
        Set<String> ports = new HashSet<>();
        try {
            CommandUtilForUnix.ExecuteResult ps = CommandUtilForUnix.execWithReadTimeLimit("ps aux | grep mongo",false,5);
            for (String s : ps.msg.split("\n")) {
                if (s.contains("--port=")){
                    String s1 = s.substring(s.indexOf("--port="));
                    ports.add(s1.split("\\s+")[0].replace("--port=",""));
                }
                if (s.contains("--port ")){
                    String s1 = s.substring(s.indexOf("--port "));
                    ports.add(s1.split("\\s+")[1]);
                }
            }
        } catch (IOException e) {
            log.error("",e);
        }
        return ports;
    }

    /**
     * 从ps命令结果中解析所有启动的MongoDB的配置文件地址
     * @return
     */
    private Set<String> getRunConfFilesFromPs(){
        Set<String> confs = new HashSet<>();
        try {
            CommandUtilForUnix.ExecuteResult ps = CommandUtilForUnix.execWithReadTimeLimit("ps aux | grep mongo",false,5);
            for (String s : ps.msg.split("\n")) {
                if (s.contains("-f ")){
                    String s1 = s.substring(s.indexOf("-f "));
                    confs.add(s1.split("\\s+")[1]);
                }
                if (s.contains("-f=")){
                    String s1 = s.substring(s.indexOf("-f="));
                    confs.add(s1.split("\\s+")[0].replace("-f=",""));
                }
                if (s.contains("--config ")){
                    String s1 = s.substring(s.indexOf("--config "));
                    confs.add(s1.split("\\s+")[1]);
                }
                if (s.contains("--config=")){
                    String s1 = s.substring(s.indexOf("--config="));
                    confs.add(s1.split("\\s+")[0].replace("--config=",""));
                }
            }
        } catch (IOException e) {
            log.error("",e);
        }
        return confs;
    }

    /**
     * 从which或whereis或ps中获取mongo的二进制文件位置
     * @return
     */
    private String getMongoBinPath(){
        String path = null;
        try {
            //which或whereis
            CommandUtilForUnix.ExecuteResult which = CommandUtilForUnix.execWithReadTimeLimit("which mongo",false,5);
            if (which.msg.startsWith("/") && which.msg.endsWith("mongo")){
                path = which.msg;
            }else {
                CommandUtilForUnix.ExecuteResult whereIs = CommandUtilForUnix.execWithReadTimeLimit("whereis mongo",false,5);
                if(!StringUtils.isEmpty(whereIs.msg)){
                    String msg = whereIs.msg;
                    String[] ss = msg.split("\\s+");
                    for (String s : ss) {
                        if(s.startsWith("/") && s.endsWith("mongo")){
                            path = s;
                            break;
                        }
                    }
                }
            }
            //ps
            if (path == null){
                CommandUtilForUnix.ExecuteResult ps = CommandUtilForUnix.execWithReadTimeLimit("ps aux | grep mongo| grep -v grep|awk '{print $11}'",false,5);
                for (String s : ps.msg.split("\n")) {
                    if (s.startsWith("/") && (s.endsWith("mongo") || s.endsWith("mongod"))){
                        path = s.substring(0,s.lastIndexOf("/")) + File.separator + "mongo";
                        break;
                    }
                }
            }
            //PATH
            if (path == null){
                CommandUtilForUnix.ExecuteResult env = CommandUtilForUnix.execWithReadTimeLimit("echo $PATH",false,5);
                for (String s : env.msg.split(":")) {
                    File file = new File(s);
                    if (file.exists() && file.isDirectory()){
                        String[] files = file.list();
                        if (files != null) {
                            for (String file1 : files) {
                                if ("mongo".equals(file1)){
                                    path = s + File.separator + file1;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("",e);
        }
        return path;
    }
}
