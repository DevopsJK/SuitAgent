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
 * long.qian@msxf.com 2017-07-20 16:09 创建
 */

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * @author long.qian@msxf.com
 */
@Slf4j
public class K8sApiServerUtil {

    /**
     * 获取Pod
     * @param k8sApiServerConnector
     * @param host
     * @param namespace
     * @param podName
     * @return
     * @throws Exception
     */
    public static Pod getPod(K8sApiServerConnector k8sApiServerConnector, String host, String namespace, String podName){
        if (k8sApiServerConnector == null){
            return null;
        }
        host = host.replace("https://","").replace("http://","");
        JSONObject jsonObject = null;
        try {
            String result = k8sApiServerConnector.requestForGet(String.format("https://%s/api/v1/namespaces/%s/pods/%s",host,namespace,podName));
            jsonObject = JSON.parseObject(result);
            Pod pod = new Pod();
            pod.setFromMetadataJSON(jsonObject.getJSONObject("metadata"));
            pod.setFromSpecJSON(jsonObject.getJSONObject("spec"));
            pod.setFromStatusJSON(jsonObject.getJSONObject("status"));
            return pod;
        } catch (Exception e) {
            log.error("",e);
        }
        return null;
    }

    public static List<Pod> getAllPods(K8sApiServerConnector k8sApiServerConnector, String host) throws Exception{
        if (k8sApiServerConnector == null){
            return null;
        }
        host = host.replace("https://","").replace("http://","");
        String result = k8sApiServerConnector.requestForGet(String.format("https://%s/api/v1/pods",host));
        JSONObject jsonObject = JSON.parseObject(result);
        JSONArray items = jsonObject.getJSONArray("items");
        List<Pod> pods = new ArrayList<>();
        for (Object item : items) {
            JSONObject it = (JSONObject) item;
            Pod pod = new Pod();
            pod.setFromMetadataJSON(it.getJSONObject("metadata"));
            pod.setFromSpecJSON(it.getJSONObject("spec"));
            pod.setFromStatusJSON(it.getJSONObject("status"));
            pods.add(pod);
        }

        return pods;
    }
}
