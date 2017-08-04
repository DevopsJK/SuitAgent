/*
 * www.msxf.com Inc.
 * Copyright (c) 2017 All Rights Reserved
 */
package com.falcon.suitagent.jmx;
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
 * long.qian@msxf.com 2017-08-04 14:47 创建
 */

import com.falcon.suitagent.util.StringUtils;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author long.qian@msxf.com
 */
@Slf4j
public class JMXUtil {

    /**
     * 获取本地是否已开启指定的JMX服务
     * @param serverName
     * @return
     */
    public static boolean hasJMXServerInLocal(String serverName){
        if(!StringUtils.isEmpty(serverName)){
            List<VirtualMachineDescriptor> vms = VirtualMachine.list();
            for (VirtualMachineDescriptor desc : vms) {
                File file = new File(desc.displayName());
                if(file.exists()){
                    //java -jar 形式启动的Java应用
                    if(file.toPath().getFileName().toString().equals(serverName)){
                        return true;
                    }
                }else if(hasContainsServerName(desc.displayName(),serverName)){
                    return true;
                }

            }
        }
        return false;
    }

    /**
     * 获取指定服务名的本地JMX VM 描述对象
     * @param serverName
     * @return
     */
    public static List<VirtualMachineDescriptor> getVmDescByServerName(String serverName){
        List<VirtualMachineDescriptor> vmDescList = new ArrayList<>();
        if (!StringUtils.isEmpty(serverName)){
            List<VirtualMachineDescriptor> vms = VirtualMachine.list();
            for (VirtualMachineDescriptor desc : vms) {
                File file = new File(desc.displayName());
                if(file.exists()){
                    //java -jar 形式启动的Java应用
                    if(file.toPath().getFileName().toString().equals(serverName)){
                        vmDescList.add(desc);
                    }
                }else if(hasContainsServerName(desc.displayName(),serverName)){
                    vmDescList.add(desc);
                }
            }
        }
        return vmDescList;
    }

    /**
     * 判断指定的目标服务名是否在探测的展示名中
     * @param displayName
     * @param serverName
     * @return
     */
    public static boolean hasContainsServerName(String displayName,String serverName){
        if (StringUtils.isEmpty(serverName)){
            return false;
        }
        boolean has = true;
        List<String> displaySplit = Arrays.asList(displayName.split("\\s+"));
        for (String s : serverName.split("\\s+")) {
            //boot  start
            if (!displaySplit.contains(s)){
                has = false;
                break;
            }
        }
        return has;
    }

}
