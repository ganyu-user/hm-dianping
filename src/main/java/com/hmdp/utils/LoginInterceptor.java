package com.hmdp.utils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 登录拦截器--判断ThreadLocal中是否有用户
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1、判断是否需要拦截（ThreadLocal中是否有用户）
        if(UserHolder.getUser()==null){
            // 状态码设置为 401
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        // 有用户 放行
        return true;
    }

}
