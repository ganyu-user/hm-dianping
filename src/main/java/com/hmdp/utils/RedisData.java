package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实体扩展-逻辑过期时间
 * @param
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
