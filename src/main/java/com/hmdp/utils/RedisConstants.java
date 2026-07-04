package com.hmdp.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 30L;

    public static final Long SHOPTYPE_LIST_TTL = 120L;
    public static final String SHOPTYPE_LIST_KEY = "shoptype:list";

    public static final Long CACHE_NULL_TTL = 2L;
    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";

    // 用于生成存空值的随机过期时间的偏移量
    public static final Long CACHE_NULL_RANDOM_OFFSET = 1L;
    // 用于生成随机过期时间的默认偏移量
    public static final Long CACHE_RANDOM_OFFSET = 10L;
    // 用于生成店铺类型随机过期时间的偏移量
    public static final Long SHOPTYPE_LIST_RANDOM_OFFSET = 10L;
}
