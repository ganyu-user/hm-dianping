package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     *
     * @param key
     * @param value
     * @param expireTime
     * @param timeUnit
     */
    public void set(String key, Object value, Long expireTime, TimeUnit timeUnit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), expireTime, timeUnit);
    }

    public void setWithLogicalExpire(String key, Object value, Long expireTime, TimeUnit timeUnit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(expireTime)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 存空值解决缓存穿透
     * @param keyPreFix 缓存key的前缀
     * @param id 数据唯一标识ID，泛型兼容Long/Integer等所有ID类型
     * @param type 目标实体Class类型，用于JSON反序列化
     * @param dbFallBack 数据库查询回调函数：传入ID，返回对应实体；无数据返回null
     * @param time 过期时间
     * @param unit 过期时间单位
     * @return
     * @param <R> 返实体返回值泛型，对应传入的Class类型
     * @param <ID> ID主键泛型，支持Long、Integer、String等主键类型
     */
    public <R,ID> R queryWithPassThrough(
            String keyPreFix,
            ID id,
            Class<R> type,
            Function<ID,R> dbFallBack,
            Long time,
            TimeUnit unit
    ){
        // 1、建立key
        String key=keyPreFix+id;

        // 2、查询redis
        String cacheJson = stringRedisTemplate.opsForValue().get(key);

        // 3、判断redis是否存在
        if(StrUtil.isNotEmpty(cacheJson)){
            // 4、存在，返回
            return JSONUtil.toBean(cacheJson, type);
        }

        // 判断redis的店铺信息cacheShop是否为空值
        if (cacheJson !=null){
            return null;
        }

        // 5、不存在，查询数据库
        R dbData = dbFallBack.apply(id);

        // 6、判断数据库中是否存在该数据
        if(dbData ==null){
            // 数据库中不存在该店铺信息，存空值
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }

        // 数据库中存在数据，写入redis
        this.set(key, dbData,time,unit);
        // 返回
        return dbData;
    }

    /**
     * 缓存重建专用线程池，固定10个线程异步更新热点缓存，避免同步阻塞业务请求
     */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     *
     * @param keyPreFix 业务缓存Key前缀，示例：shop:、voucher:
     * @param lockPrefix 分布式锁Key前缀，示例：lock:shop:、lock:voucher:，避免不同业务锁冲突
     * @param id 数据唯一主键ID，泛型兼容Long/Integer/String等类型
     * @param type 业务实体Class，用于JSON反序列化data数据
     * @param dbFallBack 数据库查询回调函数：传入ID，查询并返回完整实体；无数据返回null
     * @param time 逻辑有效时长数值
     * @param unit 逻辑有效时长单位（SECONDS/MINUTES/HOURS）
     * @return 业务实体；Redis无缓存时返回null；缓存逻辑过期时返回过期旧数据
     * @param <R> 业务实体泛型，对应传入的Class类型
     * @param <ID> 主键ID泛型，支持Long、Integer、String各类主键
     */
    public <R,ID> R queryWithLogicalExpire(
            String keyPreFix,
            String lockPrefix,
            ID id,
            Class<R> type,
            Function<ID,R> dbFallBack,
            Long time,
            TimeUnit unit
    ){
        // 1、建立key
        String key =keyPreFix+id;

        // 2、查询redis
        String cacheJson = stringRedisTemplate.opsForValue().get(key);

        // 3、判断redis是否存在
        // 存在，返回
        if(StrUtil.isEmpty(cacheJson)){
            return null;
        }

        // 命中，把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(cacheJson, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            // 未过期，直接返回
            return r;
        }

        // 已过期，需要缓存重建
        // 获取互斥锁
        String lockKey=lockPrefix+id;
        Boolean flag = tryLock(lockKey);
        // 判断是否获取锁成功
        if(flag){
            // 成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查数据库
                    R dbData = dbFallBack.apply(id);
                    // 存入redis
                    this.setWithLogicalExpire(key, dbData,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    unLock(lockKey);
                }
            });
        }

        // 返回过期店铺信息
        return r;
    }

    /**
     * 获取互斥锁
     * @param key
     * @return
     */
    private Boolean tryLock(String key){
        //  setnx key 1
        //  expire key 10
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtils.isTrue(flag);
    }

    /**
     * 释放互斥锁
     * @param key
     */
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    // TODO 封装互斥锁解决缓存击穿工具类
    // TODO 整合解决雪崩方案进解决缓存穿透、击穿方法
    // TODO 整合一个同时解决穿透、击穿的方法
}
