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
 * long.qian@msxf.com 2017-07-21 14:40 创建
 */

import com.falcon.suitagent.util.HostUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author long.qian@msxf.com
 */
@Slf4j
public class PodsLibrary {

    private static final Map<String,Pod> PODS = new ConcurrentHashMap<>();

    public static boolean isExist(Pod pod){
        return PODS.containsKey(pod.getFullName());
    }

    public static boolean isExist(String fullName){
        return PODS.containsKey(fullName);
    }

    public static void addOrSetPod(Pod pod,boolean filterByCurrentNode) throws Exception {
        if (pod != null){
            if (!filterByCurrentNode){
                if (isExist(pod)){
                    log.info("已存在Pod：{}，进行更新",pod.getFullName());
                }else {
                    log.info("新增Pod：{}",pod.getFullName());
                }
                PODS.put(pod.getFullName(),pod);
            }else {
                if (isCurrentNodePod(pod)){
                    if (isExist(pod)){
                        log.info("已存在Pod：{}，进行更新",pod.getFullName());
                    }else {
                        log.info("新增Pod：{}",pod.getFullName());
                    }
                    PODS.put(pod.getFullName(),pod);
                }else {
                    log.debug("非当前节点Pod，忽略入库:{}({})",pod.getFullName(),pod.getHostIP());
                }
            }
        }
    }

    public static void addOrSetPods(Collection<Pod> pods,boolean filterByCurrentNode) throws Exception {
        if (pods != null){
            for (Pod pod : pods) {
                addOrSetPod(pod,filterByCurrentNode);
            };
        }
    }

    public static Pod getPod(String fullName){
        return PODS.get(fullName);
    }

    public static Collection<Pod> getCurrentNodePods(){
        try {
            return PODS.values().stream().filter(PodsLibrary::isCurrentNodePod).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("",e);
            return new ArrayList<>();
        }
    }

    /**
     * 判断Pod是否为当前节点的Pod
     * @param pod
     * @return
     * @throws Exception
     */
    private static boolean isCurrentNodePod(Pod pod){
        if (pod == null){
            return false;
        }
        List<String> ips;
        try {
            ips = HostUtil.getHostIps();
//            ips = Arrays.asList("10.250.140.84");
        } catch (Exception e) {
            log.error("",e);
            return false;
        }
        return ips.contains(pod.getHostIP());
    }

    public static Collection<Pod> getPods(){
        return PODS.values();
    }

    public static void removePod(String fullName){
        Pod pod = PODS.remove(fullName);
        if (pod != null){
            log.info("已删除Pod：{}",pod);
        }
    }
}
