package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

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
    private ShopMapper shopMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 根据商铺ID查询商铺信息
     * @param id
     * @return
     */
    @Override
    public Shop queryById(Long id) {
        // 1、拼接缓存key
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;

        // 2、查询Redis Hash
        Map<Object, Object> shopMap = stringRedisTemplate.opsForHash().entries(shopKey);

        // 3、Redis无数据，查询数据库
        if (shopMap.isEmpty()) {
            // 4、查询数据库商铺
            Shop dbShop = getById(id);
            // 5、数据库无数据，返回失败
            if (dbShop == null) {
                return null;
            }

            dbShop.setDistance(null);

            // 6、Shop实体转Map，过滤临时distance字段
            Map<String, Object> beanMap = BeanUtil.beanToMap(dbShop, new HashMap<>(),
                    CopyOptions.create()
                            .setIgnoreNullValue(true)
                            .setFieldValueEditor((fieldName, fieldValue) -> {
                                if (fieldValue == null) {
                                    return null;
                                }
                                return fieldValue.toString();
                            })
            );
            // 7、写入Redis Hash
            stringRedisTemplate.opsForHash().putAll(shopKey, beanMap);
            // 直接返回数据库查到的商铺
            return dbShop;
        }

        // Redis有缓存，把Map转为Shop实体再返回（修复你直接返回Map的类型错误）
        Shop cacheShop = BeanUtil.fillBeanWithMap(shopMap, new Shop(), false);
        return cacheShop;
    }
}
