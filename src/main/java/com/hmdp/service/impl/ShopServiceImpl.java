package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisCacheUtil;
import com.hmdp.utils.RedisConstants;

import com.hmdp.utils.RedisData;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    /**
     * 根据商铺ID查询商铺信息
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        //Shop shop = queryWithPassThrough(id);
        // 调用CacheClient工具类
        Shop shop = cacheClient.queryWithPassThrough(
                RedisConstants.CACHE_SHOP_KEY,
                id,
                Shop.class,
                this::getById,
                RedisConstants.CACHE_SHOP_TTL,
                TimeUnit.MINUTES
        );
        // 互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);
        // 逻辑过期解决缓存击穿
        //Shop shop = queryWithLogicalExpire(id);
        if(shop==null){
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

    /**
     * 缓存重建线程池
     */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 逻辑过期时间解决击穿
     * @param id
     * @return
     */
    public Shop queryWithLogicalExpire(Long id){
        // 1、建立key
        String shopKey=RedisConstants.CACHE_SHOP_KEY+id;

        // 2、查询redis
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);

        // 3、判断redis是否存在
        // 存在，返回
        if(StrUtil.isEmpty(shopJson)){
            return null;
        }

        // 命中，把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            // 未过期，直接返回
            return shop;
        }

        // 已过期，需要缓存重建
        // 获取互斥锁
        String lockKey=RedisConstants.LOCK_SHOP_KEY+id;
        Boolean flag = tryLock(lockKey);
        // 判断是否获取锁成功
        if(flag){
            // 成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShop2Redis(id,30L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    unLock(lockKey);
                }
            });
        }

        // 返回过期店铺信息
        return shop;
    }

    /**
     * 互斥锁解决缓存击穿
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id){
        // 1、建立key
        String key=RedisConstants.CACHE_SHOP_KEY+id;

        // 2、查询redis
        String cacheShop = stringRedisTemplate.opsForValue().get(key);

        // 3、判断redis是否存在
        if(StrUtil.isNotEmpty(cacheShop)){
            // 4、存在，返回
            return JSONUtil.toBean(cacheShop, Shop.class);
        }

        // 4、判断redis的店铺信息cacheShop是否为空值
        if (cacheShop!=null){
            // 空值，返回
            return null;
        }

        // 5、实现缓存重建
        // 5.1、获取互斥锁
        String lockKey=RedisConstants.LOCK_SHOP_KEY+id;
        Shop dbShop = null;
        try {
            Boolean isLock = tryLock(lockKey);

            // 5.2、判断是否获取成功
            if (!isLock){
                // 5.3、失败，则休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            // 5.4、成功，查询数据库
            dbShop = getById(id);
            // 6、判断数据库中是否存在该数据
            if(dbShop==null){
                // 数据库中不存在该店铺信息，存空值
                // 解决雪崩--调用RedisCacheUtil工具类生成随机过期时间
                long cacheNullTtl = RedisCacheUtil.getRandomTtl(RedisConstants.CACHE_NULL_TTL, RedisConstants.CACHE_NULL_RANDOM_OFFSET);
                stringRedisTemplate.opsForValue().set(key,"",cacheNullTtl,TimeUnit.MINUTES);
                return null;
            }

            // 7、存在，写入redis
            long cacheShopTtl = RedisCacheUtil.getRandomTtl(RedisConstants.CACHE_SHOP_TTL);
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(dbShop),cacheShopTtl,TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 8、释放互斥锁
            unLock(lockKey);
        }

        // 9、返回
        return dbShop;
    }

    ///**
    // * 存空值解决缓存穿透
    // * @param id
    // * @return
    // */
    //public Shop queryWithPassThrough(Long id){
    //    // 1、建立key
    //    String key=RedisConstants.CACHE_SHOP_KEY+id;
    //
    //    // 2、查询redis
    //    String cacheShop = stringRedisTemplate.opsForValue().get(key);
    //
    //    // 3、判断redis是否存在
    //    if(StrUtil.isNotEmpty(cacheShop)){
    //        // 4、存在，返回
    //        return JSONUtil.toBean(cacheShop, Shop.class);
    //    }
    //
    //    // 判断redis的店铺信息cacheShop是否为空值
    //    if (cacheShop!=null){
    //        return null;
    //    }
    //
    //    // 5、不存在，查询数据库
    //    Shop dbShop = getById(id);
    //    // 6、判断数据库中是否存在该数据
    //    if(dbShop==null){
    //        // 数据库中不存在该店铺信息，存空值
    //        // 解决雪崩--调用RedisCacheUtil工具类生成随机过期时间
    //        long cacheNullTtl = RedisCacheUtil.getRandomTtl(RedisConstants.CACHE_NULL_TTL, RedisConstants.CACHE_NULL_RANDOM_OFFSET);
    //        stringRedisTemplate.opsForValue().set(key,"",cacheNullTtl,TimeUnit.MINUTES);
    //        return null;
    //    }
    //
    //    // 8、存在，写入redis，然后返回
    //    long cacheShopTtl = RedisCacheUtil.getRandomTtl(RedisConstants.CACHE_SHOP_TTL);
    //    stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(dbShop),cacheShopTtl,TimeUnit.MINUTES);
    //    return dbShop;
    //}

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

    /**
     * 根据店铺 ID和逻辑过期时间 保存店铺信息到redis
     * @param id
     * @param expireTime
     */
    public void saveShop2Redis(Long id,Long expireTime){
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireTime));
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id, JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional
    public Result updateWithCache(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺ID不能为空！");
        }
        // 1、先更新数据库
        this.updateById(shop);
        // 2、再删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
