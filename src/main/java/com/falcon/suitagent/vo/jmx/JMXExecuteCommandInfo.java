/*
 * www.msxf.com Inc.
 * Copyright (c) 2017 All Rights Reserved
 */
package com.falcon.suitagent.vo.jmx;
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
 * long.qian@msxf.com 2017-07-25 14:57 创建
 */

import lombok.Data;

/**
 * Java应用的启动的JMX命令行信息
 * @author long.qian@msxf.com
 */
@Data
public class JMXExecuteCommandInfo {

    /**
     * 应用名识别名，用于上报和示例识别，不可为空
     */
    private String appName;
    /**
     * Java应用所在机器的IP
     */
    private String ip;
    /**
     * Java应用启动时的JMX命令行信息
     * 如包含以下命令的启动信息：
     * -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=4444 -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false
     */
    private String command;
}
