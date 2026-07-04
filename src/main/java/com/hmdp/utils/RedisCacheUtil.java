package com.hmdp.utils;

import cn.hutool.core.util.RandomUtil;

import java.util.Random;

public class RedisCacheUtil {

    private static final Random RANDOM = new Random();

    /**
     * 生成防雪崩随机过期时间（全局默认偏移值）
     * @param baseTtl
     * @return
     */
    public static long getRandomTtl(Long baseTtl){
        long randomAdd = RandomUtil.randomLong(0,RedisConstants.CACHE_RANDOM_OFFSET);
        return baseTtl + randomAdd;
    }

    /**
     * 生成防雪崩随机过期时间（自定义偏移值）
     * @param baseTtl
     * @param expireTtl 自定义偏移值
     * @return
     */
    public static long getRandomTtl(Long baseTtl, Long expireTtl){
        // 删除多余的 bound:
        long randomAdd = RandomUtil.randomLong(0,expireTtl);
        return baseTtl + randomAdd;
    }
}