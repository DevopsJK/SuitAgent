/*
 * www.yiji.com Inc.
 * Copyright (c) 2016 All Rights Reserved
 */
package com.falcon.suitagent.plugins.plugin.elasticSearch;

import com.alibaba.fastjson.JSONObject;
import com.falcon.suitagent.util.CommandUtilForUnix;
import com.falcon.suitagent.util.HttpUtil;
import com.falcon.suitagent.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.ho.yaml.Yaml;
import org.ho.yaml.exception.YamlException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/*
 * 修订记录:
 * guqiu@yiji.com 2016-06-22 17:48 创建
 */

/**
 * ElasticSearch的服务配置
 * 自动读取es的服务器配置文件
 * @author guqiu@yiji.com
 */
@Slf4j
public class ElasticSearchConfig {


    /**
     * 根据进程id获取elasticSearch的配置文件配置
     * @param pid
     * elasticSearch服务的进程id
     * @return
     * @throws IOException
     */
    private static Map<String,Object> getConfig(int pid) throws IOException {
        String key = StringUtils.getStringByInt(pid);

        //读取缓存
        Map<String,Object> result = new HashMap<>();

        String cmd = "ls -al /proc/" + pid + "/fd/" + " | grep elasticsearch";
        CommandUtilForUnix.ExecuteResult executeResult = CommandUtilForUnix.execWithReadTimeLimit(cmd,false,10);

        if(executeResult.isSuccess){
            String path = "";
            String msg = executeResult.msg;
            String[] ss = msg.split("\\s");
            for (String s : ss) {
                if(s.contains("elasticsearch") &&
                        s.contains("jar") &&
                        s.substring(s.lastIndexOf("elasticsearch"),s.lastIndexOf("jar")+3).
                                matches("elasticsearch-[\\w*.]*\\.jar")){
                    path = s;
                    break;
                }
            }
            if(!"".equals(path)){
                path = path.substring(0,path.lastIndexOf(File.separator));
                path = path.substring(0,path.lastIndexOf(File.separator));
                path += File.separator + "config" + File.separator + "elasticsearch.yml";
                try {
                    result = Yaml.loadType(new FileInputStream(path), HashMap.class);
                } catch (YamlException e) {
                    log.warn("配置文件解析失败,配置文件可能未配置任何内容",e);
                }catch (FileNotFoundException e){
                    log.error("elasticSearch配置文件查找失败,请检查是否路径存在空格",e);
                }
            }
        }else{
            log.error("命令 {} 执行失败,错误信息:\r\n{}",cmd,executeResult.msg);
        }
        return result;
    }

    /**
     * 获取es的http.port端口号
     * @param pid
     * es的进程id
     * @return
     * @throws IOException
     */
    static int getHttpPort(int pid) throws IOException {
        Integer port = (Integer) getConfig(pid).get("http.port");
        if(port == null){
            //未配置,返回默认配置值
            return 9200;
        }
        return port;
    }

    /**
     * 获取es绑定的地址
     * @param pid
     * @return
     * @throws IOException
     */
    private static String getNetworkHost(int pid) throws IOException {
        String name = (String) getConfig(pid).get("network.host");
        //未配置,返回默认配置值
        if(StringUtils.isEmpty(name)){
            return "127.0.0.1";
        }else{
            return name;
        }
    }

    /**
     * 获取es的连接地址
     * @param pid
     * @return
     * @throws IOException
     */
    static String getConnectionUrl(int pid) throws IOException {
        return "http://" + getNetworkHost(pid) + ":" + getHttpPort(pid);
    }

    /**
     * 获取es的集群名
     * @param pid
     * es的进程id
     * @return
     * @throws IOException
     */
    public static String getClusterName(int pid) throws IOException {
        String name = (String) getConfig(pid).get("cluster.name");
        if(StringUtils.isEmpty(name)){
            return "elasticsearch";
        }else{
            return name;
        }
    }

    /**
     * 获取es的节点名
     * @param pid
     * es的进程id
     * @return
     * @throws IOException
     */
    static String getNodeName(int pid) throws IOException {
        String name = (String) getConfig(pid).get("node.name");
        if(StringUtils.isEmpty(name)){
            return getNodeNameOrId(pid,2);
        }else{
            return name;
        }
    }

    /**
     * 获取es的节点id
     * @param pid
     * @return
     * @throws IOException
     */
    static String getNodeId(int pid) throws IOException {
        return getNodeNameOrId(pid,1);
    }

    static String getVersion(int pid) throws IOException {
        String url = getConnectionUrl(pid);
        String responseText;
        try {
            responseText = HttpUtil.get(url).getResult();
        } catch (IOException e) {
            log.error("访问{}异常",url,e);
            return "";
        }
        JSONObject responseJSON = JSONObject.parseObject(responseText);
        if (responseJSON == null || responseJSON.isEmpty()){
            return "";
        }
        JSONObject version = responseJSON.getJSONObject("version");
        if (version == null || version.isEmpty()){
            return "";
        }
        return version.getString("number");
    }

    private static String getNodeNameOrId(int pid,int type) throws IOException {
        String selfNodeId = "";
        String selfNodeName = "";
        String netWorkHost = getNetworkHost(pid);
        int port = getHttpPort(pid);
        String url = getConnectionUrl(pid) + "/_nodes";
        String responseText;
        try {
            responseText = HttpUtil.get(url).getResult();
        } catch (IOException e) {
            log.error("访问{}异常",url,e);
            return "";
        }
        JSONObject responseJSON = JSONObject.parseObject(responseText);
        JSONObject nodes = responseJSON.getJSONObject("nodes");
        if(nodes != null){
            for (Map.Entry<String, Object> entry : nodes.entrySet()) {
                String nodeId = entry.getKey();
                JSONObject nodeInfo = (JSONObject) entry.getValue();
                String nodeName = nodeInfo.getString("name");
                String httpAddress = nodeInfo.getString("http_address");
                if (StringUtils.isEmpty(httpAddress)) {
                    selfNodeId = nodeId;
                    selfNodeName = nodeName;
                }else {
                    if("127.0.0.1".equals(netWorkHost) || "localhost".equals(netWorkHost)){
                        if(httpAddress.contains("127.0.0.1:" + port) || httpAddress.contains("localhost:" + port)){
                            selfNodeId = nodeId;
                            selfNodeName = nodeName;
                        }
                    }else{
                        if(httpAddress.contains(netWorkHost + ":" + port)){
                            selfNodeId = nodeId;
                            selfNodeName = nodeName;
                        }
                    }
                }
            }
        }else{
            log.error("elasticSearch json结果解析失败:{}",responseText);
        }
        switch (type){
            case 1:
                return selfNodeId;
            case 2:
                return selfNodeName;
            default:
                return "";
        }
    }
}
