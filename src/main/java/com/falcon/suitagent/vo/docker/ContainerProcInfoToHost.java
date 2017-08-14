/*
 * www.msxf.com Inc.
 * Copyright (c) 2017 All Rights Reserved
 */
package com.falcon.suitagent.vo.docker;
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
 * long.qian@msxf.com 2017-08-04 16:54 创建
 */

import lombok.Data;

/**
 * Docker容器的proc信息（相对于主机）
 * @author long.qian@msxf.com
 */
@Data
public class ContainerProcInfoToHost {

    /**
     * 容器ID
     */
    private String containerId;
    /**
     * 容器proc信息在主机上的路径
     */
    private String procPath;
    /**
     * 容器在主机上的PID
     */
    private String pid;

    public ContainerProcInfoToHost() {
    }

    public ContainerProcInfoToHost(String containerId, String procPath, String pid) {
        this.containerId = containerId;
        this.procPath = procPath;
        this.pid = pid;
    }
}
