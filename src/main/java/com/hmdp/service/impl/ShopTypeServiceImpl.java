package com.hmdp.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    public StringRedisTemplate stringRedisTemplate;
    
    /**
     * 查询店铺类型
     * @return
     */
    @Override
    public List<ShopType> queryTypeList() {
        // 1、建立key
        String key= RedisConstants.SHOPTYPE_LIST_KEY;
        // 2、查询redis
        String jsonList = stringRedisTemplate.opsForValue().get(key);

        // 3、判断redis的数据是否为空
        if(StrUtil.isNotBlank(jsonList)){
            // 4、非空，返回数据
            return JSONUtil.toList(jsonList, ShopType.class);
        }

        // 5、为空，查询数据库
        List<ShopType> dbShopTypeList = list();

        // 6、判断数据库的数据是否为空
        if (CollUtil.isNotEmpty(dbShopTypeList)) {
            // 7、非空，写入redis，返回数据
            stringRedisTemplate.opsForValue().set(
                    key,
                    JSONUtil.toJsonStr(dbShopTypeList),
                    RedisConstants.SHOPTYPE_LIST_TTL,
                    TimeUnit.MINUTES);
            return dbShopTypeList;
        }

        // 8、为空，返回fail
        return null;
    }
}
