package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * token刷新拦截器
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1、获取请求头中的token
        String token = request.getHeader("authorization");
        String LoginUserInfoKey=RedisConstants.LOGIN_USER_KEY+token;

        // 2、判断用户是否存在
        if (StrUtil.isBlank(token)) {
            return true;
        }

        // 3、获取基于token为 key从redis中获取的用户
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(LoginUserInfoKey);
        if (userMap.isEmpty()) {
            return true;
        }

        // 5、将获取到的Hash数据转换为UserDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        // ---封装token进userDTO，放到ThreadLocal，供登出使用
        userDTO.setLoginUserToken(token);

        // 6、存在 保存用户信息到ThreadLocal
        UserHolder.saveUser(userDTO);

        // 7、刷新token有效期
        stringRedisTemplate.expire(LoginUserInfoKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 8、放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }

}
