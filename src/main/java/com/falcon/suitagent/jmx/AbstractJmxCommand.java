/*
 * www.yiji.com Inc.
 * Copyright (c) 2016 All Rights Reserved
 */
package com.falcon.suitagent.jmx;

import com.falcon.suitagent.util.CommandUtilForUnix;
import com.falcon.suitagent.util.OSUtil;
import com.falcon.suitagent.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * 修订记录:
 * guqiu@yiji.com 2016-06-22 17:48 创建
 */

/**
 * @author guqiu@yiji.com
 */
@Slf4j
public class AbstractJmxCommand {

    private static final String CONNECTOR_ADDRESS =
        "com.sun.management.jmxremote.localConnectorAddress";

    public static String getJVM() {
        return System.getProperty("java.vm.specification.vendor");
    }

    public static boolean isSunJVM() {
        return getJVM().equals("Sun Microsystems Inc.") || getJVM().startsWith("Oracle");
    }

    /**
     * 通过进程id查找JMX的remote连接地址
     * @param command
     * 指定命令行信息（若传null，则从pid中自动获取命令行）
     * @param pid
     * 查找的进行id
     * @param ip
     * 应用所在的IP地址
     * @return
     * 返回查找的JMX连接地址对象或查找失败返回Null
     */
    public static JMXConnectUrlInfo findJMXRemoteUrlByProcessId(String command,int pid, String ip){
        if (StringUtils.isEmpty(command)){
            log.info("JMX Remote Target Pid:{}", pid);
        }else {
            log.info("已指定JMX Command:{}",command);
        }
        String cmdForMac = "ps u -p " + pid;
        String cmdForLinux = "cat /proc/" + pid + "/cmdline";
        String jmxPortOpt = "-Dcom.sun.management.jmxremote.port";
        String authPortOpt = "-Dcom.sun.management.jmxremote.authenticate";
        String jmxRemoteAccessOpt = "-Dcom.sun.management.jmxremote.access.file";
        String jmxRemotePasswordOpt = "-Dcom.sun.management.jmxremote.password.file";

        try {
            JMXConnectUrlInfo remoteUrlInfo = new JMXConnectUrlInfo();

            String msg;

            if (StringUtils.isEmpty(command)){
                CommandUtilForUnix.ExecuteResult result;
                if (OSUtil.isLinux()){
                    result = CommandUtilForUnix.execWithReadTimeLimit(cmdForLinux,false,7);
                }else if (OSUtil.isMac()){
                    result = CommandUtilForUnix.execWithReadTimeLimit(cmdForMac,false,7);
                }else {
                    log.error("JMX连接自动获取只支持Linux和Mac平台");
                    return null;
                }
                if (!result.isSuccess){
                    log.error("命令 {} 执行失败",cmdForLinux);
                    return null;
                }
                msg = result.msg;
            }else {
                msg = command;
            }

            String port = getJMXConfigValueForLinux(msg,jmxPortOpt + "=\\d+",jmxPortOpt + "=");
            if(port == null){
                log.warn("从启动命令未找到JMX Remote 配置:{}",msg);
                return null;
            }

            String accessFile;
            String passwordFile;
            String fileRegex = "(/\\w*[\\u2E80-\\u9FFF]*\\d*-*\\w*[\\u2E80-\\u9FFF]*\\d*_*\\w*[\\u2E80-\\u9FFF]*\\d*\\.*\\w*[\\u2E80-\\u9FFF]*\\d*/){1}(\\w*[\\u2E80-\\u9FFF]*\\d*-*\\w*[\\u2E80-\\u9FFF]*\\d*_*\\w*[\\u2E80-\\u9FFF]*\\d*\\.*\\w*[\\u2E80-\\u9FFF]*\\d*/)*\\w*[\\u2E80-\\u9FFF]*\\d*-*\\w*[\\u2E80-\\u9FFF]*\\d*_*\\w*[\\u2E80-\\u9FFF]*\\d*(\\.*\\w*[\\u2E80-\\u9FFF]*\\d*)*";
            accessFile = getJMXConfigValueForLinux(msg,jmxRemoteAccessOpt + "=" + fileRegex,jmxRemoteAccessOpt + "=");
            passwordFile = getJMXConfigValueForLinux(msg,jmxRemotePasswordOpt + "=" + fileRegex,jmxRemotePasswordOpt + "=");

            boolean isAuth = "true".equalsIgnoreCase(getJMXConfigValueForLinux(msg,authPortOpt + "=(false|true|FALSE|TRUE|False|True){1}",authPortOpt + "="));

            remoteUrlInfo.setRemoteUrl("service:jmx:rmi:///jndi/rmi://" + ip + ":" + port + "/jmxrmi");
            remoteUrlInfo.setAuthentication(isAuth);

            if(isAuth){
                //寻找JMX的认证信息
                if(accessFile == null || passwordFile == null){
                    String javaHome = CommandUtilForUnix.getJavaHomeFromEtcProfile();

                    if(StringUtils.isEmpty(javaHome)){
                        log.error("JAVA_HOME 读取失败");
                        return null;
                    }

                    log.info("JAVA_HOME : {}",javaHome);
                    if(accessFile == null){
                        accessFile = javaHome + "/" + "jre/lib/management/jmxremote.access";
                    }
                    if(passwordFile == null){
                        passwordFile = javaHome + "/" + "jre/lib/management/jmxremote.password";
                    }

                }

                String contentForAccess = CommandUtilForUnix.execWithReadTimeLimit(String.format("cat %s",accessFile),false,7).msg;
                String user = getJmxUser(contentForAccess);
                String contentForPassword = CommandUtilForUnix.execWithReadTimeLimit(String.format("cat %s",passwordFile),false,7).msg;
                String password = getJmxPassword(contentForPassword,user);

                if(StringUtils.isEmpty(user) || StringUtils.isEmpty(password)){
                    log.error("JMX Remote 的认证User {} 或 Password {} 获取失败",user,password);
                }

                remoteUrlInfo.setJmxUser(user);
                remoteUrlInfo.setJmxPassword(password);

            }
            return remoteUrlInfo;
        } catch (Exception e) {
            log.error("JMX Remote Url 获取异常",e);
            return null;
        }

    }


    public static String getJMXConfigValueForLinux(String msg, String regex, String replace){
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(msg);
        String result = null;
        if (matcher.find()){
            result = matcher.group().replace(replace,"");
        }
        return result;
    }

    /**
     * 获取JMX授权用户
     * JMX 用户经过测试,必须是readwrite权限的
     * @param content
     * @return
     */
    private static String getJmxUser(String content){
        content = getRidOfCommend(content);
        String[] users = content.split("\n");
        if(users.length < 1){
            log.error("请配置jmxremote.access");
            return null;
        }
        for (String user : users) {
            if(user.contains("readwrite")){
                String[] ss = user.split("\\s");
                return ss[0].trim();
            }
        }
        log.error("请在 jmxremote.access 中配置 readwrite 用户");
        return null;
    }

    /**
     * 获取JMX授权密码
     * @param content
     * @param user
     * @return
     */
    private static String getJmxPassword(String content,String user){
        if(user == null){
            return null;
        }
        content = getRidOfCommend(content);
        String[] passwords = content.split("\n");
        if(passwords.length < 1){
            return null;
        }

        for (String password : passwords) {
            String[] passwordConf = password.trim().split("\\s");
            if(user.equals(passwordConf[0].trim())){
                if(passwordConf.length != 2){
                    return passwordConf[passwordConf.length - 1];
                }else{
                    return passwordConf[1].trim();
                }
            }
        }

        return null;
    }

    /**
     * 去掉注释行
     * @param content
     * @return
     */
    private static String getRidOfCommend(String content){
        StringBuilder sb = new StringBuilder();
        StringTokenizer st = new StringTokenizer(content,"\n",false);
        while( st.hasMoreElements() ){
            String split = st.nextToken().trim();
            if(!StringUtils.isEmpty(split)){
                if(split.indexOf("#") != 0){
                    sb.append(split).append("\r\n");
                }
            }
        }
        return sb.toString();
    }

    /**
     * 通过进程id查找JMX的本地连接地址
     *
     * @param pid 查找的进行id
     * @return
     * 返回查找的JMX本地连接地址或查找失败返回Null
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static String findJMXLocalUrlByProcessId(int pid) {

        if (isSunJVM()) {
            try {
                String javaHome = System.getProperty("java.home");
                String tools = javaHome + File.separator +
                        ".." + File.separator + "lib" + File.separator + "tools.jar";
                URLClassLoader loader = new URLClassLoader(new URL[]{new File(tools).toURI().toURL()});

                Class virtualMachine = Class.forName("com.sun.tools.attach.VirtualMachine", true, loader);
                Class virtualMachineDescriptor = Class.forName("com.sun.tools.attach.VirtualMachineDescriptor", true, loader);

                Method getVMList = virtualMachine.getMethod("list", (Class[])null);
                Method attachToVM = virtualMachine.getMethod("attach", String.class);
                Method getAgentProperties = virtualMachine.getMethod("getAgentProperties", (Class[])null);
                Method getVMId = virtualMachineDescriptor.getMethod("id",  (Class[])null);



                List allVMs = (List)getVMList.invoke(null, (Object[])null);

                for(Object vmInstance : allVMs) {
                    String id = (String)getVMId.invoke(vmInstance, (Object[])null);
                    if (id.equals(Integer.toString(pid))) {

                        Object vm = attachToVM.invoke(null, id);

                        Properties agentProperties = (Properties)getAgentProperties.invoke(vm, (Object[])null);
                        String connectorAddress = agentProperties.getProperty(CONNECTOR_ADDRESS);

                        if (connectorAddress != null) {
                            return connectorAddress;
                        } else {
                            break;
                        }
                    }
                }

                //上面的尝试都不成功，则尝试让agent加载management-agent.jar
                Method getSystemProperties = virtualMachine.getMethod("getSystemProperties", (Class[])null);
                Method loadAgent = virtualMachine.getMethod("loadAgent", String.class, String.class);
                Method detach = virtualMachine.getMethod("detach", (Class[])null);
                for(Object vmInstance : allVMs) {
                    String id = (String)getVMId.invoke(vmInstance, (Object[])null);
                    if (id.equals(Integer.toString(pid))) {

                        Object vm = attachToVM.invoke(null, id);

                        Properties systemProperties = (Properties)getSystemProperties.invoke(vm, (Object[])null);
                        String home = systemProperties.getProperty("java.home");

                        // Normally in ${java.home}/jre/lib/management-agent.jar but might
                        // be in ${java.home}/lib in build environments.

                        String agent = home + File.separator + "jre" + File.separator +
                                           "lib" + File.separator + "management-agent.jar";
                        File f = new File(agent);
                        if (!f.exists()) {
                            agent = home + File.separator +  "lib" + File.separator +
                                        "management-agent.jar";
                            f = new File(agent);
                            if (!f.exists()) {
                                throw new IOException("Management agent not found");
                            }
                        }

                        agent = f.getCanonicalPath();

                        loadAgent.invoke(vm, agent, "com.sun.management.jmxremote");

                        Properties agentProperties = (Properties)getAgentProperties.invoke(vm, (Object[])null);
                        String connectorAddress = agentProperties.getProperty(CONNECTOR_ADDRESS);

                        //detach 这个vm
                        detach.invoke(vm, (Object[])null);

                        if (connectorAddress != null) {
                            return connectorAddress;
                        } else {
                            break;
                        }
                    }
                }
            } catch (Exception e) {
            	return null;
            }
        }

        return null;
    }


}
