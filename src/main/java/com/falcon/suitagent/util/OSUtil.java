/*
 * www.msxf.com Inc.
 * Copyright (c) 2017 All Rights Reserved
 */
package com.falcon.suitagent.util;
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
 * long.qian@msxf.com 2017-05-27 17:51 创建
 */

/**
 * @author long.qian@msxf.com
 */
public class OSUtil {

    private static String OS_NAME = System.getProperty("os.name").toLowerCase();

    public static boolean isLinux(){
        return OS_NAME.contains("linux");
    }

    public static boolean isMac(){
        return OS_NAME.contains("mac");
    }

    public static boolean isWindows(){
        return OS_NAME.contains("window");
    }
}
