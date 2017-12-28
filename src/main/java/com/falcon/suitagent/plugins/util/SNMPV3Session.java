/*
 * www.yiji.com Inc.
 * Copyright (c) 2016 All Rights Reserved
 */
package com.falcon.suitagent.plugins.util;
/*
 * 修订记录:
 * guqiu@yiji.com 2016-07-12 10:47 创建
 */

import com.falcon.suitagent.exception.AgentArgumentException;
import com.falcon.suitagent.util.HostUtil;
import com.falcon.suitagent.util.StringUtils;
import com.falcon.suitagent.vo.snmp.SNMPV3UserInfo;
import lombok.extern.slf4j.Slf4j;
import org.snmp4j.MessageException;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.UserTarget;
import org.snmp4j.mp.MPv3;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.security.*;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author guqiu@yiji.com
 */
@Slf4j
public class SNMPV3Session {

//    private static final String cacheKey_equipmentName = "equipmentName";
    private static final String CACHE_KEY_SYS_DESC = "sysDesc";
    private static final String CACHE_KEY_SYS_VENDOR = "sysVendor";
    private static final String CACHE_KEY_VERSION = "version";
    private static final int TIMEOUT = 8000;

    private Snmp snmp;
    private UserTarget target;
    private SNMPV3UserInfo userInfo;
    /**
     * 信息缓存
     */
    private final ConcurrentHashMap<String,Object> infoCache = new ConcurrentHashMap<>();

    @Override
    public String toString() {
        return "SNMPV3Session{" +
                "snmp=" + snmp +
                ", target=" + target +
                ", userInfo=" + userInfo +
                '}';
    }

    /**
     * 创建SNMPV3会话
     * @param userInfo
     * @throws IOException
     * @throws AgentArgumentException
     */
    public SNMPV3Session(SNMPV3UserInfo userInfo) throws IOException, AgentArgumentException {
        if(StringUtils.isEmpty(userInfo.getAddress())){
            throw new AgentArgumentException("SNMPV3Session创建失败:snmp v3协议的访问地址不能为空");
        }
        if(StringUtils.isEmpty(userInfo.getUsername())){
            throw new AgentArgumentException("SNMPV3Session创建失败:snmp v3协议的访问用户名不能为空");
        }
        if(!StringUtils.isEmpty(userInfo.getAythType()) && StringUtils.isEmpty(userInfo.getAuthPswd())){
            throw new AgentArgumentException("SNMPV3Session创建失败:snmp v3协议指定了认证算法 aythType,就必须要指定认证密码");
        }
        if(!StringUtils.isEmpty(userInfo.getPrivType()) && StringUtils.isEmpty(userInfo.getPrivPswd())){
            throw new AgentArgumentException("SNMPV3Session创建失败:snmp v3协议指定了加密算法 privType,就必须要指定加密密码");
        }

        this.userInfo = userInfo;
        snmp = new Snmp(new DefaultUdpTransportMapping());
        USM usm = new USM(SecurityProtocols.getInstance(), new OctetString(MPv3.createLocalEngineID(
                new OctetString(HostUtil.getHostIp() + UUID.randomUUID().toString())
        )), 0);
        SecurityModels.getInstance().addSecurityModel(usm);
        snmp.listen();

        UsmUser user = new UsmUser(
                new OctetString(userInfo.getUsername()),
                getAuthProtocol(userInfo.getAythType()), new OctetString(userInfo.getAuthPswd()),
                getPrivProtocol(userInfo.getPrivType()), new OctetString(userInfo.getPrivPswd()));

        snmp.getUSM().addUser(new OctetString(userInfo.getUsername()), user);

        target = new UserTarget();
        target.setSecurityName(new OctetString(userInfo.getUsername()));
        target.setVersion(SnmpConstants.version3);
        target.setSecurityLevel(SecurityLevel.AUTH_PRIV);
        target.setAddress(GenericAddress.parse(userInfo.getProtocol() + ":" + userInfo.getAddress() + "/" + userInfo.getPort()));
        target.setTimeout(TIMEOUT);
        target.setRetries(1);
    }

    /**
     * 获取该会话是否可用
     * @return
     */
    public boolean isValid(){
        try {
            //超时将会返回null，标记不可用
            return SNMPHelper.snmpGet(snmp,target,SNMPHelper.SYS_DESC_OID) != null;
        } catch (Exception e) {
            if (e instanceof MessageException && e.getMessage().contains("Unknown security name")) {
                //mark：经部分设备测试，即使username（securityName）不对，也能得到snmp的响应，只是返回结果为数字，非设备的描述信息
                log.warn("SNMP连接({}:{}-{})可用性检测警告，预期内的错误，返回 可用：{}",userInfo.getAddress(),userInfo.getPort(),userInfo.getEndPoint(),e.getMessage());
                return true;
            }
            log.error("SNMP连接({}:{}-{})可用性检测为失败(不可用)",userInfo.getAddress(),userInfo.getPort(),userInfo.getEndPoint(),e);
            return false;
        }
    }

    private String getInfoCacheKey(){
        return this.getUserInfo().getAddress() + "-" + this.getUserInfo().getPort();
    }

    /**
     * 获取设备名称
     * ip-vendor-version
     *
     * @return
     */
    public String getAgentSignName() {
        //去掉agentSignName，使用配置（authorization.properties）中的自定义endPoint来识别设备
        return "";
//        String key = getInfoCacheKey() + cacheKey_equipmentName;
//
//        if(infoCache.get(key) != null){
//            //已有有效的设备名称缓存,直接返回
//            return (String) infoCache.get(getInfoCacheKey());
//        }
//        String prefix = this.getUserInfo().getAddress() + "-" + this.getUserInfo().getPort();
//        try {
//            if(isValid()){
//                String name = prefix + "-" + getSysVendor() + "-" + getSysVersion();
//                infoCache.put(key,name);
//                return name;
//            }else{
//                return prefix;
//            }
//        } catch (IOException e) {
//            log.error("设备描述信息获取失败",e);
//            return prefix;
//        }
    }

    /**
     * 获取设备描述字符串
     *
     * @return
     * @throws IOException
     */
    public String getSysDesc() throws IOException {
        String key = getInfoCacheKey() + CACHE_KEY_SYS_DESC;
        String sysDesc = (String) infoCache.get(key);
        if(sysDesc != null){
            return sysDesc;
        }
        PDU pdu = this.get(SNMPHelper.SYS_DESC_OID);
        if(pdu != null){
            sysDesc = pdu.get(0).getVariable().toString();
            infoCache.put(key,sysDesc);
            return sysDesc;
        }else {
            return "";
        }

    }

    /**
     * 获取设备的供应商
     * @return
     * null : 获取失败
     * @throws IOException
     */
    public VendorType getSysVendor() throws IOException {
        String key = getInfoCacheKey() + CACHE_KEY_SYS_VENDOR;
        VendorType sysVendor = (VendorType) infoCache.get(key);
        if(sysVendor != null){
            return sysVendor;
        }
        sysVendor = SNMPHelper.getVendorBySysDesc(getSysDesc());
        if(sysVendor == null){
            return null;
        }
        infoCache.put(key,sysVendor);
        return sysVendor;
    }

    /**
     * 获取设备版本
     * @return
     * 0 : 获取失败
     * @throws IOException
     */
    public float getSysVersion() throws IOException {
        String key = getInfoCacheKey() + CACHE_KEY_VERSION;
        Float version = (Float) infoCache.get(key);
        if(version != null){
            return version;
        }
        version = SNMPHelper.getVersionBySysDesc(getSysDesc());
        if(version == 0){
            return 0;
        }
        infoCache.put(key,version);
        return version;
    }

    /**
     * get方法获取指定OID
     * @param oid
     * @return
     * @throws IOException
     */
    public PDU get(String oid) throws IOException {
        return SNMPHelper.snmpGet(snmp,target,oid);
    }

    /**
     * getNext方法获取指定OID
     * @param oid
     * @return
     * @throws IOException
     */
    public PDU getNext(String oid) throws IOException {
        return SNMPHelper.snmpGetNext(snmp,target,oid);
    }

    /**
     * 对指定的OID进行子树walk,并返回所有的walk结果
     * @param oid
     * @return
     * @throws IOException
     */
    public List<PDU> walk(String oid) throws IOException {
        return SNMPHelper.snmpWalk(snmp,target,oid);
    }

    /**
     * 获取指定的认证算法
     * @param privType
     * @return
     * @throws AgentArgumentException
     */
    private OID getPrivProtocol(String privType) throws AgentArgumentException {

        if (privType == null
                || "none".equalsIgnoreCase(privType)
                || privType.length() == 0) {
            return null;
        }

        switch (privType) {
            case "DES":
                return PrivDES.ID;
            case "3DES":
                return Priv3DES.ID;
            case "AES128":
            case "AES-128":
            case "AES":
                return PrivAES128.ID;
            case "AES192":
            case "AES-192":
                return PrivAES192.ID;
            case "AES256":
            case "AES-256":
                return PrivAES256.ID;
            default:
                throw new AgentArgumentException("Privacy protocol " + privType + " not supported");
        }
    }

    /**
     * 获取指定的加密算法
     * @param authMethod
     * @return
     * @throws AgentArgumentException
     */
    private OID getAuthProtocol(String authMethod) throws AgentArgumentException {
        if (authMethod == null
                || "none".equalsIgnoreCase(authMethod)
                || authMethod.length() == 0) {
            return null;
        } else if ("md5".equalsIgnoreCase(authMethod)) {
            return AuthMD5.ID;
        } else if ("sha".equalsIgnoreCase(authMethod)) {
            return AuthSHA.ID;
        } else {
            throw new AgentArgumentException("unknown authentication protocol: " + authMethod);
        }
    }

    public void close() throws IOException {
        log.debug("关闭snmp连接{}",userInfo.getAddress());
        snmp.close();
    }

    public SNMPV3UserInfo getUserInfo() {
        return userInfo;
    }
}
