package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisCacheUtil;
import com.hmdp.utils.RedisConstants;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
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

    /**
     * 根据商铺ID查询商铺信息
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        // ---实现Hash数据结构缓存商铺信息---//

        //// 1、拼接缓存key
        //String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
        //
        //// 2、查询Redis Hash
        //Map<Object, Object> shopMap = stringRedisTemplate.opsForHash().entries(shopKey);
        //
        //// 3、Redis无数据，查询数据库
        //if (shopMap.isEmpty()) {
        //    // 4、查询数据库商铺
        //    Shop dbShop = getById(id);
        //
        //    // 5、数据库无数据，返回空值失败
        //    if (dbShop == null) {
        //        HashMap<Object, Object> map = new HashMap<>();
        //        stringRedisTemplate.opsForHash().putAll(shopKey,map);
        //        stringRedisTemplate.expire(shopKey, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
        //        return Result.fail("店铺不存在！");
        //    }
        //
        //    // 数据库有数据
        //    dbShop.setDistance(null);
        //
        //    // 6、Shop实体转Map，过滤临时distance字段
        //    Map<String, Object> beanMap = BeanUtil.beanToMap(dbShop, new HashMap<>(),
        //            CopyOptions.create()
        //                    .setIgnoreNullValue(true)
        //                    .setFieldValueEditor((fieldName, fieldValue) -> {
        //                        if (fieldValue == null) {
        //                            return null;
        //                        }
        //                        return fieldValue.toString();
        //                    })
        //    );
        //
        //    // 7、写入Redis Hash
        //    stringRedisTemplate.opsForHash().putAll(shopKey, beanMap);
        //    stringRedisTemplate.expire(shopKey, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //    // 直接返回数据库查到的商铺
        //    return Result.ok(dbShop);
        //}
        //
        //if (shopMap.get(shopKey) == null) {}
        //
        //// Redis有缓存，把Map转为Shop实体再返回（修复你直接返回Map的类型错误）
        //Shop cacheShop = BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);
        //return Result.ok(cacheShop);

        // 1、建立key
        String key=RedisConstants.CACHE_SHOP_KEY+id;

        // 2、查询redis
        String cacheShop = stringRedisTemplate.opsForValue().get(key);

        // 3、判断redis是否存在
        if(StrUtil.isNotEmpty(cacheShop)){
            // 4、存在，返回
            Shop shop = JSONUtil.toBean(cacheShop, Shop.class);
            return Result.ok(shop);
        }

        // 判断redis的店铺信息cacheShop是否为空值
        if (cacheShop!=null){
            return Result.fail("店铺信息不存在！");
        }

        // 5、不存在，查询数据库
        Shop dbShop = getById(id);
        // 6、判断数据库中是否存在该数据
        if(dbShop==null){
            // 数据库中不存在该店铺信息，存空值
            // 解决雪崩--调用RedisCacheutil工具类生成随机过期时间
            long cacheNullTtl = RedisCacheUtil.getRandomTtl(RedisConstants.CACHE_NULL_TTL, RedisConstants.CACHE_NULL_RANDOM_OFFSET);
            stringRedisTemplate.opsForValue().set(key,"",cacheNullTtl,TimeUnit.MINUTES);
            return Result.fail("店铺信息不存在！");
        }

        // 8、存在，写入redis，然后返回
        long cacheShopTtl = RedisCacheUtil.getRandomTtl(RedisConstants.CACHE_SHOP_TTL);
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(dbShop),cacheShopTtl,TimeUnit.MINUTES);
        return Result.ok(dbShop);
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
