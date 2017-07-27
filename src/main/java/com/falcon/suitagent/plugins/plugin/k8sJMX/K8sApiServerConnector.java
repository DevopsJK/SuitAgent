/*
 * www.msxf.com Inc.
 * Copyright (c) 2017 All Rights Reserved
 */
package com.falcon.suitagent.plugins.plugin.k8sJMX;
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
 * long.qian@msxf.com 2017-07-17 17:01 创建
 */

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.net.ssl.*;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * @author long.qian@msxf.com
 */
@Slf4j
public class K8sApiServerConnector {

    private OkHttpClient okHttpClient;

    /**
     * Kubernetes的APIServer接口请求
     *
     * @param p12ClientCertificatePath
     * p12的客户端证书位置
     * @param certificatePassword
     * 客户端证书的密码
     */
    public K8sApiServerConnector(String p12ClientCertificatePath, String certificatePassword) throws Exception{
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        FileInputStream keyStoreFileStream = new FileInputStream(p12ClientCertificatePath);
        keyStore.load(keyStoreFileStream,certificatePassword.toCharArray());
        keyStoreFileStream.close();

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore,certificatePassword.toCharArray());

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(),new SecureRandom());
        trustAllHttpsCertificates(sslContext, keyManagerFactory.getKeyManagers());
        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

        X509TrustManager defaultTrustManager = (X509TrustManager) trustManagerFactory.getTrustManagers()[0];
        okHttpClient = new OkHttpClient.Builder()
                .hostnameVerifier((urlHostName, session) -> true)
                .sslSocketFactory(sslSocketFactory,defaultTrustManager)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .connectTimeout(10000,TimeUnit.MILLISECONDS)
                .build();

    }

    /**
     * get请求
     *
     * 例如：<pre>{@code
     *
     *       K8sApiServerConnector k8sApiServerConnector = new K8sApiServerConnector("/root/admin.p12","111111");
     *       k8sApiServerConnector.requestForGet("https://10.250.140.11:6443",new Callback() {
     *           @Override
     *           public void onFailure(Call call, IOException e) {
     *               System.out.println("请求失败：" + call);
     *           }
     *
     *           @Override
     *           public void onResponse(Call call, Response response) throws IOException {
     *               System.out.println(response.body().string());
     *           }
     *       });
     *
     * }</pre>
     *
     * @param requestUrl
     * 请求地址
     * @param callback
     * 请求后的回调处理
     * @throws Exception
     */
    public void requestForGet(String requestUrl, Callback callback)throws Exception{
        URL url = new URL(requestUrl);
        Request request = new Request.Builder()
                .header("User-Agent","SuitAgent/1.0 (Macintosh; Linux) GCDHTTPRequest")
                .url(url)
                .get()
                .build();

        okHttpClient.newCall(request).enqueue(callback);
    }

    /**
     * get请求
     * @param requestUrl
     * @return
     * 请求结果
     * @throws Exception
     */
    public String requestForGet(String requestUrl)throws Exception{
        URL url = new URL(requestUrl);
        Request request = new Request.Builder()
                .header("User-Agent","SuitAgent/1.0 (Macintosh; Linux) GCDHTTPRequest")
                .url(url)
                .get()
                .build();

        return okHttpClient.newCall(request).execute().body().string();
    }

    /**
     * HTTP 长连接有响应时的结果回调处理
     */
    public interface LongRequestCallback{
        /**
         * 响应处理
         * @param json
         * 收到一个响应JSON结果
         */
        void response(String json);
    }

    /**
     * get请求HTTP长连接
     *
     * 例如：<pre>{@code
     *
     *  K8sApiServerConnector k8sApiServerConnector = new K8sApiServerConnector("/root/admin.p12","111111");
     *  k8sApiServerConnector.longRequestForGet(
     *      "https://10.250.140.11:6443/api/v1/watch/pods",
     *      true,
     *      line -> System.out.println(line)
     *  );
     *
     * }</pre>
     *
     * @param requestUrl
     * 请求地址
     * @param format
     * 是否格式化响应字符串
     * @param callback
     * 处理响应结果
     * @throws Exception
     */
    public void longRequestForGet(String requestUrl,boolean format, LongRequestCallback callback)throws Exception{
        URL url = new URL(requestUrl);
        Request request = new Request.Builder()
                .addHeader("User-Agent","SuitAgent/1.0 (Macintosh; Linux) GCDHTTPRequest")
                .addHeader("connection","Keep-Alive")
                .url(url)
                .get()
                .build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.error("连接异常",e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(response.body().byteStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    sb.append(line);
                    JSONObject jsonObject = null;
                    JSONArray jsonArray = null;
                    try {
                        jsonObject = JSON.parseObject(sb.toString());
                        jsonArray = JSON.parseArray(sb.toString());
                    } catch (Exception ignored) {}
                    if (jsonArray != null || jsonObject != null){
                        if (jsonArray != null){
                            callback.response(jsonArray.toJSONString());
                        }else {
                            callback.response(jsonObject.toJSONString());
                        }
                        sb.delete(0,sb.length());
                    }
                }
            }
        });
    }

    private void trustAllHttpsCertificates(SSLContext sc, KeyManager[] keyManagers) throws Exception {
        javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[1];
        javax.net.ssl.TrustManager tm = new miTM();
        trustAllCerts[0] = tm;
        sc.init(keyManagers, trustAllCerts, new SecureRandom());
        javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc
                .getSocketFactory());
    }

    private static class miTM implements javax.net.ssl.TrustManager,
            javax.net.ssl.X509TrustManager {

        @Override
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return null;
        }

        @Override
        public void checkServerTrusted(
                java.security.cert.X509Certificate[] certs, String authType)
                throws java.security.cert.CertificateException {
        }

        @Override
        public void checkClientTrusted(
                java.security.cert.X509Certificate[] certs, String authType)
                throws java.security.cert.CertificateException {
        }
    }

}
