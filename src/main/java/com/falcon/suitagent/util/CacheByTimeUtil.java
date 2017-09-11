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
 * long.qian@msxf.com 2017-09-11 14:29 创建
 */

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.math.NumberUtils;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author long.qian@msxf.com
 */
@Slf4j
public class CacheByTimeUtil {

    private static final ConcurrentHashMap<String,ConcurrentHashMap<Long,CacheValue>> CATCH = new ConcurrentHashMap<>();
    private static int CACHE_TIME = 360;//缓存失效时间，默认6分钟

    @Getter
    @Setter
    @AllArgsConstructor
    private static class CacheValue{
        private int validTime;
        private Object value;
    }

    static {
        /*
            缓存时间设置：
            优先级：系统变量 > 系统环境变量
        */
        String cacheTime = System.getProperty("CACHE_VALID_TIME");
        if (StringUtils.isEmpty(cacheTime)){
            cacheTime = System.getenv("CACHE_VALID_TIME");
        }
        if (StringUtils.isNotEmpty(cacheTime)){
            if (NumberUtils.isNumber(cacheTime)){
                CACHE_TIME = Integer.parseInt(cacheTime);
            }else {
                log.error("DOCKET_CACHE_TIME只能数字：{}",cacheTime);
            }
        }

        //缓存周期检查任务
        Thread cacheCheck = new Thread(new CacheCheck());
        cacheCheck.setName("DockerUtil Cache Check Thread");
        cacheCheck.setDaemon(true);
        cacheCheck.start();
    }

    /**
     * 缓存检查，自动清除无效的缓存，设置为守护线程，随JVM消亡而自动消亡
     */
    private static class CacheCheck implements Runnable{
        @Override
        public void run() {
            while (true){
                try {
                    Thread.sleep(CACHE_TIME * 1000 * 3 + 5000);//检查周期
                    Set<String> keys = CATCH.keySet();
                    for (String key : keys) {
                        if (getCache(key) == null){
                            CATCH.remove(key);
                        }
                    }
                } catch (Exception e) {
                    log.error("",e);
                }
            }
        }
    }

    /**
     * 获取缓存数据
     * @param key
     * @return
     * 未设置或缓存有效时间已过都会返回null
     */
    public static Object getCache(String key) {
        if (StringUtils.isNotEmpty(key)){
            ConcurrentHashMap<Long,CacheValue> cache = CATCH.get(key);
            if (cache != null){
                Optional<Long> cacheTime = cache.keySet().stream().findFirst();
                if (cacheTime.isPresent()){
                    CacheValue cacheValue = cache.get(cacheTime.get());
                    long now = System.currentTimeMillis();
                    if ((now - cacheTime.get()) < (cacheValue.getValidTime() * 1000)){
                        return cacheValue.getValue();
                    }else {
                        //缓存失效
                        cache.clear();
                        CATCH.put(key,cache);
                    }
                }
            }
        }
        return null;
    }

    /**
     * 设置缓存
     * @param key
     * @param value
     */
    public static void setCache(String key,Object value) {
        setCache(key,value,CACHE_TIME);
    }

    /**
     * 设置缓存（指定缓存有效时间）
     * @param key
     * @param value
     * @param validTime
     */
    public static void setCache(String key,Object value,int validTime) {
        if (StringUtils.isNotEmpty(key) && value != null){
            ConcurrentHashMap<Long,CacheValue> cache = CATCH.get(key);
            if (cache == null){
                cache = new ConcurrentHashMap<>();
            }
            cache.clear();
            cache.put(System.currentTimeMillis(),new CacheValue(validTime,value));
            CATCH.put(key,cache);
        }
    }

}
