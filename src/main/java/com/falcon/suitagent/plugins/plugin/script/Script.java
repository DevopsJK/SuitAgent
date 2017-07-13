/*
 * www.msxf.com Inc.
 * Copyright (c) 2017 All Rights Reserved
 */
package com.falcon.suitagent.plugins.plugin.script;
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
 * long.qian@msxf.com 2017-07-12 16:21 创建
 */

import com.falcon.suitagent.util.StringUtils;
import lombok.Data;

/**
 * @author long.qian@msxf.com
 */
@Data
class Script {
    private ScriptType scriptType;
    private int stepCycle = 1;
    private String path;
    private String metric;
    private ScriptResultType resultType;
    private String tags = "";
    private String counterType = "GAUGE";

    /**
     * 是否有效
     * @return
     */
    public boolean isValid(){
        return scriptType != null && stepCycle != 0 && !StringUtils.isEmpty(path) && !StringUtils.isEmpty(metric) && resultType != null;
    }
}
