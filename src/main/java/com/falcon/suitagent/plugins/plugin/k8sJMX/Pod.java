/*
 * www.msxf.com Inc.
 * Copyright (c) 2017 All Rights Reserved
 */
package com.falcon.suitagent.plugins.plugin.k8sJMX;
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
 * long.qian@msxf.com 2017-07-21 09:54 创建
 */

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author long.qian@msxf.com
 */
@ToString
public class Pod {

    @Getter
    @Setter
    private String name;
    @Getter
    @Setter
    private String namespace;
    @Getter
    @Setter
    private String hostIP;
    @Getter
    @Setter
    private String podIP;
    @Getter
    @Setter
    private String phase;
    @Getter
    private Map<String,String> labels;
    @Getter
    private List<Container> containers = new ArrayList<>();


    @Getter
    @Setter
    @ToString
    public static class Container{
        private String name;
        private String cmd;
        private String args;
    }

    /**
     * 从spec的JSON对象赋值
     * @param specJson
     */
    public void setFromSpecJSON(JSONObject specJson){
        if (specJson != null){
            JSONArray containersJSONArray = specJson.getJSONArray("containers");
            if (containersJSONArray != null){
                for (Object container : containersJSONArray) {
                    JSONObject jsonObject = (JSONObject) container;
                    Container c = new Container();
                    c.setName(jsonObject.getString("name"));
                    c.setArgs(String.valueOf(jsonObject.get("args")));
                    c.setCmd(String.valueOf(jsonObject.get("command")));
                    containers.add(c);
                }
            }
        }
    }

    /**
     * 从metadata的JSON对象赋值
     * @param metadataJson
     */
    public void setFromMetadataJSON(JSONObject metadataJson){
        if (metadataJson != null){
            this.namespace = metadataJson.getString("namespace");
            this.setName(metadataJson.getString("name"));
            JSONObject labelsJSON = metadataJson.getJSONObject("labels");
            Map<String,String> map = new HashMap<>();
            labelsJSON.keySet().forEach(key -> {
                map.put(key,labelsJSON.getString(key));
            });
            this.labels = map;
        }
    }

    /**
     * 从status的JSON对象赋值
     * @param statusJson
     */
    public void setFromStatusJSON(JSONObject statusJson){
        if (statusJson != null){
            this.hostIP = statusJson.getString("hostIP");
            this.podIP = statusJson.getString("podIP");
            this.phase = statusJson.getString("phase");
        }
    }

    /**
     * @return
     * namespace-name
     */
    public String getFullName() {
        return namespace + "-" + name;
    }
}
