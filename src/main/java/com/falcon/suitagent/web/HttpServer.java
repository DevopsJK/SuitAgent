/*
 * www.yiji.com Inc.
 * Copyright (c) 2016 All Rights Reserved
 */
package com.falcon.suitagent.web;
/*
 * 修订记录:
 * guqiu@yiji.com 2016-07-26 13:45 创建
 */

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author guqiu@yiji.com
 */
@Slf4j
public class HttpServer extends Thread {

    private static final String SHUTDOWN_COMMAND = "/__SHUTDOWN__";
    private boolean shutdown = false;
    /**
     * 0 : 服务未启动
     * 1 : 服务已启动
     * -1 : 服务正在关闭
     */
    public static int status = 0;

    private int port;
    private static ServerSocket serverSocket;

    public HttpServer(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        try {
            startServer();
        } catch (IOException e) {
            log.error("web服务启动异常", e);
            status = 0;
            System.exit(0);
        }
    }

    public void startServer() throws IOException {
        serverSocket = new ServerSocket(port, 10, InetAddress.getByName("0.0.0.0"));
        log.info("Web服务启动地址:http://{}:{}", InetAddress.getByName("0.0.0.0").getHostName(), serverSocket.getLocalPort());
        status = 1;
        while (!shutdown) {
            try (Socket socket = serverSocket.accept();
                 InputStream input = socket.getInputStream();
                 OutputStream output = socket.getOutputStream();) {
                Request request = new Request(input);
                request.parse();
                Response response = new Response(output);
                response.setRequest(request);
                shutdown = SHUTDOWN_COMMAND.equals(request.getUri());
                if (shutdown) {
                    status = -1;
                    response.send("Shutdown OK");
                } else {
                    response.doRequest();
                }
            } catch (Exception e) {
                log.error("Web处理异常", e);
            }
        }
        try {
            close();
        } catch (Exception e) {
            log.error("web服务关闭异常", e);
        }
    }

    public static void close() throws IOException {
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
            status = 0;
            log.info("Web 服务已关闭");
        }
    }
}
