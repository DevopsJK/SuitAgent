/*
 * www.yiji.com Inc.
 * Copyright (c) 2016 All Rights Reserved
 */
package com.falcon.suitagent.plugins.plugin.mysql;
/*
 * 修订记录:
 * guqiu@yiji.com 2016-07-19 15:39 创建
 */

import com.falcon.suitagent.falcon.CounterType;
import com.falcon.suitagent.falcon.FalconReportObject;
import com.falcon.suitagent.plugins.JDBCPlugin;
import com.falcon.suitagent.plugins.metrics.MetricsCommon;
import com.falcon.suitagent.util.CommandUtilForUnix;
import com.falcon.suitagent.util.FileUtil;
import com.falcon.suitagent.util.HostUtil;
import com.falcon.suitagent.util.StringUtils;
import com.mysql.jdbc.JDBC4Connection;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.math.NumberUtils;

import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * 监控值收集
 * @author guqiu@yiji.com
 */
@Slf4j
class Metrics {

    private JDBCPlugin plugin;
    private Connection connection;

    Metrics(JDBCPlugin plugin,Connection connection) {
        this.plugin = plugin;
        this.connection = connection;
    }

    /**
     * 获取监控值
     * @return
     */
    Collection<FalconReportObject> getReports() throws SQLException, ClassNotFoundException {
        Set<FalconReportObject> reportObjectSet = new HashSet<>();

        reportObjectSet.addAll(getGlobalStatus());
        reportObjectSet.addAll(getGlobalVariables());
//        reportObjectSet.addAll(getInnodbStatus());
        reportObjectSet.addAll(getSalveStatus());
        try {
            reportObjectSet.addAll(getDBFilesSize());
        } catch (Exception e) {
            log.error("",e);
        }

        return reportObjectSet;
    }

    private Collection<? extends FalconReportObject> getSalveStatus() throws SQLException, ClassNotFoundException {
        Set<FalconReportObject> reportObjectSet = new HashSet<>();
        String sql = "show slave status";
        PreparedStatement pstmt = connection.prepareStatement(sql);
        ResultSet rs = pstmt.executeQuery();
        while (rs.next()){
            String value_Slave_IO_Running = rs.getString("Slave_IO_Running");
            String value_Slave_SQL_Running = rs.getString("Slave_SQL_Running");
            String value_Seconds_Behind_Master = rs.getString("Seconds_Behind_Master");
            String value_Connect_Retry = rs.getString("Connect_Retry");

            FalconReportObject falconReportObject = new FalconReportObject();
            MetricsCommon.setReportCommonValue(falconReportObject,plugin.step());
            falconReportObject.setCounterType(CounterType.GAUGE);
            //时间戳会统一上报
//            falconReportObject.setTimestamp(System.currentTimeMillis() / 1000);
            falconReportObject.appendTags(MetricsCommon.getTags(plugin.agentSignName(),plugin,plugin.serverName()));

            //Slave_IO_Running
            falconReportObject.setMetric("Slave_IO_Running");
            if(value_Slave_IO_Running.equals("No") || value_Slave_IO_Running.equals("Connecting")){
                falconReportObject.setValue("0");
            }else{
                falconReportObject.setValue("1");
            }
            reportObjectSet.add(falconReportObject.clone());

            //Slave_SQL_Running
            falconReportObject.setMetric("Slave_SQL_Running");
            falconReportObject.setValue("yes".equals(value_Slave_SQL_Running.toLowerCase()) ? "1" : "0");
            reportObjectSet.add(falconReportObject.clone());

            //Seconds_Behind_Master
            falconReportObject.setMetric("Seconds_Behind_Master");
            falconReportObject.setValue(value_Seconds_Behind_Master == null ? "0" : value_Seconds_Behind_Master);
            reportObjectSet.add(falconReportObject.clone());

            //Connect_Retry
            falconReportObject.setMetric("Connect_Retry");
            falconReportObject.setValue(value_Connect_Retry);
            reportObjectSet.add(falconReportObject.clone());

        }
        return reportObjectSet;
    }

    private Collection<? extends FalconReportObject> getGlobalVariables() throws SQLException, ClassNotFoundException {
        Set<FalconReportObject> reportObjectSet = new HashSet<>();
        String sql = "SHOW /*!50001 GLOBAL */ VARIABLES";
        PreparedStatement pstmt = connection.prepareStatement(sql);
        ResultSet rs = pstmt.executeQuery();
        while (rs.next()){
            String metric = rs.getString(1);
            String value = rs.getString(2);
            if (NumberUtils.isNumber(value)){
                //收集值为数字的结果
                FalconReportObject falconReportObject = new FalconReportObject();
                MetricsCommon.setReportCommonValue(falconReportObject,plugin.step());
                falconReportObject.setCounterType(CounterType.GAUGE);
                //时间戳会统一上报
//                falconReportObject.setTimestamp(System.currentTimeMillis() / 1000);
                falconReportObject.setMetric(metric);
                falconReportObject.setValue(value);
                falconReportObject.appendTags(MetricsCommon.getTags(plugin.agentSignName(),plugin,plugin.serverName()));
                reportObjectSet.add(falconReportObject);
            }
        }
        rs.close();
        pstmt.close();
        return reportObjectSet;
    }

//    private Collection<? extends FalconReportObject> getInnodbStatus() throws SQLException{
//        Set<FalconReportObject> reportObjectSet = new HashSet<>();
//        return reportObjectSet;
//    }

    private Collection<? extends FalconReportObject> getGlobalStatus() throws SQLException, ClassNotFoundException {
        Set<FalconReportObject> reportObjectSet = new HashSet<>();
        String sql = "SHOW /*!50001 GLOBAL */ STATUS";
        PreparedStatement pstmt = connection.prepareStatement(sql);
        ResultSet rs = pstmt.executeQuery();
        while (rs.next()){
            String value = rs.getString(2);
            if (NumberUtils.isNumber(value)){
                String metric = rs.getString(1);
                FalconReportObject falconReportObject = new FalconReportObject();
                MetricsCommon.setReportCommonValue(falconReportObject,plugin.step());
                falconReportObject.setCounterType(CounterType.GAUGE);
                //时间戳会统一上报
//                falconReportObject.setTimestamp(System.currentTimeMillis() / 1000);
                falconReportObject.setMetric(metric);
                falconReportObject.setValue(value);
                falconReportObject.appendTags(MetricsCommon.getTags(plugin.agentSignName(),plugin,plugin.serverName()));
                reportObjectSet.add(falconReportObject);
            }
        }
        rs.close();
        pstmt.close();
        return reportObjectSet;
    }

    /**
     * 获取宿主机对应连接的数据库文件大小信息的采集
     * @return
     * @throws SQLException
     * @throws SocketException
     * @throws UnknownHostException
     */
    private Collection<FalconReportObject> getDBFilesSize() throws Exception {
        Set<FalconReportObject> reportObjectSet = new HashSet<>();
        //jdbc:mysql://10.250.140.104:3306
        String url = ((JDBC4Connection) connection).getURL();
        List<String> ips = HostUtil.getHostIps();
        ips.add("127.0.0.1");
        ips.add("localhost");
        ips.add("0.0.0.0");
        for (String ip : ips) {
            url = url.replace(String.format("jdbc:mysql://%s:",ip),"");
        }
        if (NumberUtils.isNumber(url)){ //若有得到该连接下本机有效的端口地址
            String dataDir = getDataDirByPort(url);
            if (dataDir != null){
                List<String> filter = Arrays.asList("mysql","performance_schema","temp","information_schema");
                File dataDirFile = new File(dataDir);
                String[] dirList = dataDirFile.list();
                if (dirList != null){
                    for (String dbName : dirList) {
                        if (!filter.contains(dbName)){
                            try {
                                Path path = Paths.get(dataDir + File.separator + dbName);
                                if (path.toFile().isDirectory()){
                                    Files.walkFileTree(path,new SimpleFileVisitor<Path>(){
                                        @Override
                                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                            String fileName = file.getFileName().toString();
                                            String fileNameLower = fileName.toLowerCase();
                                            if(fileNameLower.endsWith(".myi") || fileNameLower.endsWith(".ibd")){
                                                //TODO 上报文件大小
                                                FalconReportObject falconReportObject = new FalconReportObject();
                                                MetricsCommon.setReportCommonValue(falconReportObject,plugin.step());
                                                falconReportObject.setCounterType(CounterType.GAUGE);
                                                //时间戳会统一上报
//                                                falconReportObject.setTimestamp(System.currentTimeMillis() / 1000);
                                                falconReportObject.setMetric("mysql-file-size");
                                                falconReportObject.setValue(String.valueOf(file.toFile().length()));
                                                falconReportObject.appendTags("database=" + dbName);
                                                falconReportObject.appendTags("table=" + fileName.split("\\.")[0]);
                                                falconReportObject.appendTags("type=" + fileName.split("\\.")[1].toUpperCase());
                                                falconReportObject.appendTags(MetricsCommon.getTags(plugin.agentSignName(),plugin,plugin.serverName()));
                                                reportObjectSet.add(falconReportObject);
                                            }

                                            return super.visitFile(file, attrs);
                                        }
                                    });
                                }
                            } catch (IOException e) {
                                log.error("遍历目录 {} 发生异常",dbName,e);
                            }
                        }
                    }
                }
            }
        }
        return reportObjectSet;
    }

    /**
     * 通过Mysql端口号获取宿主机的数据库文件夹路径
     * @param port
     * @return
     */
    private String getDataDirByPort(String port) throws IOException {
        String cmd = "ps aux | grep mysqld";
        CommandUtilForUnix.ExecuteResult executeResult = CommandUtilForUnix.execWithReadTimeLimit(cmd,false,7);
        if (executeResult.isSuccess){
            String msg = executeResult.msg;
            String target = "";
            for (String s : msg.split("\n")) {
                if (s.contains("--datadir=")){
                    if ("3306".equals(port)){
                        if (s.contains("--port=3306") || !s.contains("--port=")){
                            target = s;
                            break;
                        }
                    }else {
                        if (s.contains("--port=" + port)){
                            target = s;
                            break;
                        }
                    }
                }
            }

            if (!"".equals(target)){
                for (String s : target.split("--")) {
                    s = s.trim();
                    if (!StringUtils.isEmpty(s)){
                        if (s.startsWith("datadir=")){
                            String dataDir = s.replace("datadir=","");
                            if (FileUtil.isExist(dataDir)){
                                return dataDir;
                            }else {
                                log.warn("目录不存在：{}",dataDir);
                            }
                        }
                    }
                }
            }
        }else {
            log.error("命令{}执行失败：{}",cmd,executeResult);
        }
        return null;
    }

}
