package com.hmdp.config;

import com.github.xiaoymin.knife4j.spring.annotations.EnableKnife4j;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableKnife4j
public class Knife4jConfig {

    /**
     * 文档全局基础信息
     * @return
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("HMDP-API接口文档")
                        .version("1.0")
                        .description("包含用户登录、商铺查询、秒杀、博客点赞、关注等全部业务接口，内置缓存防雪崩、防穿透逻辑")
                        .contact(new Contact().name("甘宇").email("hc60095982@163.com"))
                );
    }

    /**
     * 分组1：商铺模块接口 /shop/**
     */
    @Bean
    public GroupedOpenApi shopGroupApi(){
        return GroupedOpenApi.builder().group("商铺模块").pathsToMatch("/shop/**","/shop-type/**").build();
    }

    /**
     * 分组2：用户登录、个人信息模块 /user /login
     */
    @Bean
    public GroupedOpenApi userGroupApi(){
        return GroupedOpenApi.builder().group("用户模块").pathsToMatch("/user/**","/login/**").build();
    }

    /**
     * 分组3：秒杀、优惠券模块 /voucher /seckill
     */
    @Bean
    public GroupedOpenApi seckillGroupApi(){
        return GroupedOpenApi.builder()
                .group("秒杀优惠券模块")
                .pathsToMatch("/voucher/**", "/seckill/**", "/voucher-order/**")
                .build();
    }

    /**
     * 分组4：博客、点赞、关注
     */
    @Bean
    public GroupedOpenApi blogGroupApi() {
        return GroupedOpenApi.builder()
                .group("博客互动模块")
                .pathsToMatch("/blog/**", "/blog/comments/**", "/follow/**")
                .build();
    }
}
