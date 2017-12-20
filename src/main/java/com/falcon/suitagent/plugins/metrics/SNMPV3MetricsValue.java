/*
 * www.yiji.com Inc.
 * Copyright (c) 2016 All Rights Reserved
 */
package com.falcon.suitagent.plugins.metrics;
/*
 * 修订记录:
 * guqiu@yiji.com 2016-07-01 17:29 创建
 */

import com.falcon.suitagent.exception.AgentArgumentException;
import com.falcon.suitagent.falcon.CounterType;
import com.falcon.suitagent.falcon.FalconReportObject;
import com.falcon.suitagent.plugins.SNMPV3Plugin;
import com.falcon.suitagent.plugins.util.SNMPHelper;
import com.falcon.suitagent.plugins.util.SNMPV3Session;
import com.falcon.suitagent.util.BlockingQueueUtil;
import com.falcon.suitagent.util.CommandUtilForUnix;
import com.falcon.suitagent.util.ExecuteThreadUtil;
import com.falcon.suitagent.util.StringUtils;
import com.falcon.suitagent.vo.snmp.IfStatVO;
import com.falcon.suitagent.vo.snmp.SNMPV3UserInfo;
import lombok.extern.slf4j.Slf4j;
import org.snmp4j.PDU;
import org.snmp4j.smi.VariableBinding;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.falcon.suitagent.plugins.util.SNMPHelper.ignoreIfName;

/**
 * @author guqiu@yiji.com
 */
@Slf4j
public class SNMPV3MetricsValue extends MetricsCommon {

    private SNMPV3Plugin plugin;
    private List<SNMPV3UserInfo> snmpv3UserInfoList;
    private long timestamp;
    private static final int TIMEOUT = 20;
    private final BlockingQueue<Object> blockingQueue = new ArrayBlockingQueue<>(1);

    public SNMPV3MetricsValue(SNMPV3Plugin plugin, List<SNMPV3UserInfo> snmpv3UserInfoList, long timestamp) {
        this.plugin = plugin;
        this.snmpv3UserInfoList = snmpv3UserInfoList;
        this.timestamp = timestamp;
    }

    /**
     * 获取SNMP的session
     *
     * @return
     * @throws IOException
     * @throws AgentArgumentException
     */
    public List<SNMPV3Session> getSessions() throws IOException, AgentArgumentException {
        List<SNMPV3Session> pluginSessions = new ArrayList<>();

        Collection<SNMPV3UserInfo> userInfoCollection = snmpv3UserInfoList;
        if (userInfoCollection != null) {
            for (SNMPV3UserInfo userInfo : userInfoCollection) {
                SNMPV3Session session = new SNMPV3Session(userInfo);
                pluginSessions.add(session);
            }
        }

        return pluginSessions;
    }

    /**
     * 判断传入的接口是否被采集
     *
     * @param metricsKey
     * @return
     */
    private boolean hasIfCollection(String metricsKey) {
        Map<String, Boolean> ifCollectMetricsEnable = plugin.ifCollectMetricsEnable();
        return ifCollectMetricsEnable.get(metricsKey) != null && ifCollectMetricsEnable.get(metricsKey);
    }

    /**
     * 设备的接口监控数据
     *
     * @param session
     * @return
     * @throws IOException
     */
    public Collection<FalconReportObject> getIfStatReports(SNMPV3Session session) throws IOException {
        List<FalconReportObject> reportObjects = new ArrayList<>();
        SNMPV3UserInfo userInfo = session.getUserInfo();
        List<String> ifNameEnables = userInfo.getIfCollectNameEnables();
        if(ifNameEnables == null){
            ifNameEnables = new ArrayList<>();
        }

        //允许采集接口数据且允许采集的接口名称不为空
        if (plugin.hasIfCollect() && !ifNameEnables.isEmpty()) {
            List<PDU> ifNameList = session.walk(SNMPHelper.ifNameOid);

            List<IfStatVO> statVOs = new ArrayList<>();

            for (PDU pdu : ifNameList) {
                VariableBinding ifName = pdu.get(0);
                boolean check = ifNameEnables.contains(ifName.getVariable().toString());
                if (check) {
                    for (String ignore : ignoreIfName) {
                        if (ifName.getVariable().toString().contains(ignore)) {
                            check = false;
                            break;
                        }
                    }
                }
                if (check) {
                    int index = Integer.parseInt(ifName.getOid().toString().replace(SNMPHelper.ifNameOid, "").replace(".", ""));
                    IfStatVO statVO = new IfStatVO();
                    statVO.setIfName(ifName.getVariable().toString());
                    statVO.setIfIndex(index);
                    if (hasIfCollection("if.HCInBroadcastPkts")) {
                        PDU ifHCInBroadcast = session.get(SNMPHelper.ifHCInBroadcastPktsOid + "." + index);
                        statVO.setIfHCInBroadcastPkts(SNMPHelper.getValueFromPDU(ifHCInBroadcast));
                    }
                    if (hasIfCollection("if.HCInMulticastPkts")) {
                        PDU ifHCInMulticast = session.get(SNMPHelper.ifHCInMulticastPktsOid + "." + index);
                        statVO.setIfHCInMulticastPkts(SNMPHelper.getValueFromPDU(ifHCInMulticast));
                    }
                    if (hasIfCollection("if.HCInOctets")) {
                        PDU ifIn = session.get(SNMPHelper.ifHCInOid + "." + index);
                        statVO.setIfHCInOctets(SNMPHelper.getValueFromPDU(ifIn));
                    }
                    if (hasIfCollection("if.HCOutOctets")) {
                        PDU ifOut = session.get(SNMPHelper.ifHCOutOid + "." + index);
                        statVO.setIfHCOutOctets(SNMPHelper.getValueFromPDU(ifOut));
                    }
                    if (hasIfCollection("if.HCInUcastPkts")) {
                        PDU ifHCIn = session.get(SNMPHelper.ifHCInPktsOid + "." + index);
                        statVO.setIfHCInUcastPkts(SNMPHelper.getValueFromPDU(ifHCIn));
                    }
                    if (hasIfCollection("if.getIfHCOutUcastPkts")) {
                        PDU ifHCOut = session.get(SNMPHelper.ifHCOutPktsOid + "." + index);
                        statVO.setIfHCOutUcastPkts(SNMPHelper.getValueFromPDU(ifHCOut));
                    }
                    if (hasIfCollection("if.OperStatus")) {
                        PDU ifOperStatus = session.get(SNMPHelper.ifOperStatusOid + "." + index);
                        statVO.setIfOperStatus(SNMPHelper.getValueFromPDU(ifOperStatus));
                    }
                    if (hasIfCollection("if.HCOutBroadcastPkts")) {
                        PDU ifHCOutBroadcast = session.get(SNMPHelper.ifHCOutBroadcastPktsOid + "." + index);
                        statVO.setIfHCOutBroadcastPkts(SNMPHelper.getValueFromPDU(ifHCOutBroadcast));
                    }
                    if (hasIfCollection("if.HCOutMulticastPkts")) {
                        PDU ifHCOutMulticast = session.get(SNMPHelper.ifHCOutMulticastPktsOid + "." + index);
                        statVO.setIfHCOutMulticastPkts(SNMPHelper.getValueFromPDU(ifHCOutMulticast));
                    }

                    statVOs.add(statVO);
                }
            }

            for (IfStatVO statVO : statVOs) {
                FalconReportObject reportObject = new FalconReportObject();
                setReportCommonValue(reportObject, plugin.step());
                reportObject.appendTags(getTags(session.getAgentSignName(), plugin, plugin.serverName()));
                reportObject.setCounterType(CounterType.COUNTER);
                String endPoint = userInfo.getEndPoint();
                if (!StringUtils.isEmpty(endPoint)) {
                    //设置单独设置的endPoint
                    reportObject.setEndpoint(endPoint);
                    reportObject.appendTags("customerEndPoint=true");
                }

                String ifName = statVO.getIfName();
                reportObject.appendTags("ifName=" + ifName);

                reportObject.setMetric("if.HCInBroadcastPkts");
                reportObject.setValue(statVO.getIfHCInBroadcastPkts());
                reportObject.setTimestamp(timestamp);
                reportObjects.add(reportObject.clone());

                reportObject.setMetric("if.HCInMulticastPkts");
                reportObject.setValue(statVO.getIfHCInMulticastPkts());
                reportObject.setTimestamp(timestamp);
                reportObjects.add(reportObject.clone());

                reportObject.setMetric("if.HCInOctets");
                reportObject.setValue(statVO.getIfHCInOctets());
                reportObject.setTimestamp(timestamp);
                reportObjects.add(reportObject.clone());

                reportObject.setMetric("if.HCInUcastPkts");
                reportObject.setValue(statVO.getIfHCInUcastPkts());
                reportObject.setTimestamp(timestamp);
                reportObjects.add(reportObject.clone());

                reportObject.setMetric("if.HCOutBroadcastPkts");
                reportObject.setValue(statVO.getIfHCOutBroadcastPkts());
                reportObject.setTimestamp(timestamp);
                reportObjects.add(reportObject.clone());

                reportObject.setMetric("if.HCOutMulticastPkts");
                reportObject.setValue(statVO.getIfHCOutMulticastPkts());
                reportObject.setTimestamp(timestamp);
                reportObjects.add(reportObject.clone());

                reportObject.setMetric("if.getIfHCOutUcastPkts");
                reportObject.setValue(statVO.getIfHCOutUcastPkts());
                reportObject.setTimestamp(timestamp);
                reportObjects.add(reportObject.clone());

                reportObject.setMetric("if.HCOutOctets");
                reportObject.setValue(statVO.getIfHCOutOctets());
                reportObject.setTimestamp(timestamp);
                reportObjects.add(reportObject.clone());

                //放在最后，设置GAUGE类型
                reportObject.setCounterType(CounterType.GAUGE);

                reportObject.setMetric("if.OperStatus");
                reportObject.setValue(statVO.getIfOperStatus());
                reportObject.setTimestamp(timestamp);
                reportObjects.add(reportObject.clone());
            }
        }

        return reportObjects.stream().filter(falconReportObject -> falconReportObject.getValue() != null).collect(Collectors.toList());

    }

    /**
     * ping操作
     *
     * @param session
     * @param count
     * @return
     */
    public FalconReportObject ping(SNMPV3Session session, int count) {
        FalconReportObject reportObject = new FalconReportObject();
        setReportCommonValue(reportObject, plugin.step());
        reportObject.appendTags(getTags(session.getAgentSignName(), plugin, plugin.serverName()));
        reportObject.setCounterType(CounterType.GAUGE);
        reportObject.setMetric("pingAvgTime");
        reportObject.setTimestamp(timestamp);

        String address = session.getUserInfo().getAddress();

        try {
            CommandUtilForUnix.PingResult pingResult = CommandUtilForUnix.ping(address, count);
            if (pingResult.resultCode == -2) {
                //命令执行失败
                return null;
            }
            reportObject.setValue(pingResult.avgTime + "");
        } catch (IOException e) {
            log.error("Ping {} 命令执行异常", address, e);
            return null;
        }
        return reportObject;
    }

    /**
     * 获取所有的监控值报告
     *
     * @return
     * @throws IOException
     */
    @Override
    public Collection<FalconReportObject> getReportObjects() {
        Set<FalconReportObject> result = new HashSet<>();
        List<SNMPV3Session> sessionList;
        try {
            sessionList = getSessions();
        } catch (IOException e) {
            log.warn("获取SNMP连接发生异常,push allUnVariability不可用报告", e);
            result.add(generatorVariabilityReport(false, "allUnVariability",timestamp, plugin.step(), plugin, plugin.serverName()));
            return result;

        } catch (AgentArgumentException e) {
            log.error("监控参数异常:{},忽略此监控上报", e.getErr(), e);
            return result;
        }

//        List<Future<List<FalconReportObject>>> futureList = new ArrayList<>();
        for (SNMPV3Session session : sessionList) {
//            futureList.add(ExecuteThreadUtil.execute(new Collect(session)));

            //阻塞队列异步执行
            ExecuteThreadUtil.execute(() -> {
                try {
                    List<FalconReportObject> falconReportObjects = getReports(session);
                    if (!blockingQueue.offer(falconReportObjects)){
                        log.error("SNMP {} 报告对象 offer失败",session.getUserInfo().getEndPoint());
                    }
                } catch (Throwable t) {
                    blockingQueue.offer(t);
                }
            });

            try {
                //超时处理
                Object resultBlocking = BlockingQueueUtil.getResult(blockingQueue, TIMEOUT, TimeUnit.SECONDS);
                blockingQueue.clear();
                if (resultBlocking instanceof List) {
                    result.addAll((List<FalconReportObject>) resultBlocking);
                }else if (resultBlocking == null){
                    log.error("SNMP {} 获取报告对象超时{}秒",session.getUserInfo().getEndPoint(),TIMEOUT);
                }else if (resultBlocking instanceof Throwable){
                    log.error("SNMP {} 报告对象获取异常",session.getUserInfo().getEndPoint(),resultBlocking);
                }else {
                    log.error("SNMP {} 未知结果类型：{}",session.getUserInfo().getEndPoint(),resultBlocking);
                }
            } catch (Exception e) {
                log.error("SNMP {} 获取报告对象异常",session.getUserInfo().getEndPoint(),e);
            }finally {
                try {
                    session.close();
                } catch (Exception e) {
                    log.error("SNMP Session Close Exception",e);
                }
            }

        }
//        for (Future<List<FalconReportObject>> future : futureList) {
//            try {
//                if(future != null){
//                    result.addAll(future.get());
//                }
//            } catch (Exception e) {
//                log.error("SNMP采集异常，target：{}",e);
//            }
//        }

        return result;
    }

    private List<FalconReportObject> getReports(SNMPV3Session session){
        List<FalconReportObject> temp = new ArrayList<>();
        if(!session.isValid()){
            temp.add(generatorVariabilityReport(false, session.getAgentSignName(),timestamp, plugin.step(), plugin, plugin.serverName()));
        }else{
            //ping报告
            FalconReportObject reportObject = ping(session, 5);
            if (reportObject != null) {
                temp.add(reportObject);
            }
            try {
                temp.addAll(getIfStatReports(session));
                //添加可用性报告
                temp.add(generatorVariabilityReport(true, session.getAgentSignName(),timestamp, plugin.step(), plugin, plugin.serverName()));
                //添加插件报告
                Collection<FalconReportObject> inBuildReports = plugin.inbuiltReportObjectsForValid(session);
                if (inBuildReports != null && !inBuildReports.isEmpty()) {
                    for (FalconReportObject inBuildReport : inBuildReports) {
                        //统一时间戳
                        inBuildReport.setTimestamp(timestamp);
                        temp.add(inBuildReport);
                    }
                }
            } catch (Exception e) {
                log.error("设备 {} 通过SNMP获取监控数据发生异常,push 该设备不可用报告", session.toString(), e);
                temp.add(generatorVariabilityReport(false, session.getAgentSignName(),timestamp, plugin.step(), plugin, plugin.serverName()));
            }
        }

        // EndPoint 单独设置
        temp.forEach(report -> {
            String endPoint = session.getUserInfo().getEndPoint();
            if (!StringUtils.isEmpty(endPoint)) {
                //设置单独设置的endPoint
                report.setEndpoint(endPoint);
                report.appendTags("customerEndPoint=true");
            }
        });
        return temp;
    }

//    private class Collect implements Callable<List<FalconReportObject>>{
//
//        private SNMPV3Session session;
//
//        public Collect(SNMPV3Session session) {
//            this.session = session;
//        }
//
//        @Override
//        public List<FalconReportObject> call() throws Exception {
//            return getReports(session);
//        }
//    }

}
