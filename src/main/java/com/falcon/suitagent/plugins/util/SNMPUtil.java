/*
 * www.yiji.com Inc.
 * Copyright (c) 2017 All Rights Reserved
 */
package com.falcon.suitagent.plugins.util;
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
 * guqiu@yiji.com 2017-09-05 10:53 创建
 */

import com.falcon.suitagent.util.StringUtils;
import com.falcon.suitagent.vo.snmp.SNMPV3UserInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author guqiu@yiji.com
 */
@Slf4j
public class SNMPUtil {

    public static List<SNMPV3UserInfo> parseFromUrlList(List<String> uList){
        List<SNMPV3UserInfo> userInfoList = new ArrayList<>();
        for (String switchUrl : uList) {
            if(!StringUtils.isEmpty(switchUrl)){
                String[] urls = switchUrl.split(",");
                for (String url : urls) {
                    if(!StringUtils.isEmpty(url) && url.contains("snmpv3://")){
                        //snmpv3://protocol:address:port:username:authType:authPswd:privType:privPswd
                        url = url.replace("snmpv3://","");
                        String[] props = url.split(":");
                        if(props.length < 8){
                            log.error("snmp v3 的连接URL格式错误,请检查URL:{} 是否符合格式:snmpv3://protocol:address:port:username:authType:authPswd:privType:privPswd:endPoint(option)",url);
                            continue;
                        }
                        SNMPV3UserInfo userInfo = new SNMPV3UserInfo();
                        userInfo.setProtocol(props[0]);
                        userInfo.setAddress(props[1]);
                        userInfo.setPort(props[2]);
                        userInfo.setUsername(props[3]);
                        userInfo.setAythType(props[4]);
                        userInfo.setAuthPswd(props[5]);
                        userInfo.setPrivType(props[6]);
                        userInfo.setPrivPswd(props[7]);
                        if(props.length >= 9){
                            userInfo.setEndPoint(props[8].trim());
                        }
                        if(props.length >= 10){
                            String ifEnable = props[9].trim();
                            List<String> enableList = new ArrayList<>();
                            Collections.addAll(enableList, ifEnable.split("&"));
                            userInfo.setIfCollectNameEnables(enableList.stream().filter(str -> !StringUtils.isEmpty(str)).collect(Collectors.toList()));
                        }

                        userInfoList.add(userInfo);
                    }
                }
            }
        }
        return userInfoList;
    }

}
