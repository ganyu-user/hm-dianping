package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送验证码
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendcode(String phone, HttpSession session) {
        // 1、校检手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2、如果不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }

        // 3、如果符合，生成验证码
        String phoneCode = RandomUtil.randomNumbers(6);

        // 4、保存验证码到 redis
        stringRedisTemplate.opsForValue().set(
                 RedisConstants.LOGIN_CODE_KEY +phone,
                    phoneCode,
                    RedisConstants.LOGIN_CODE_TTL,
                    TimeUnit.MINUTES
        );
        //session.setAttribute("phone_code", number);
        //session.setMaxInactiveInterval(5*60);

        // 5、发送验证码
        log.debug("发送验证码成功，验证码为："+phoneCode);

        return Result.ok();
    }

    /**
     * 登录功能
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1、校检手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }

        // 2、校检验证码
        // 从redis中获取
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY +phone);
        String code = loginForm.getCode();

        // 判断校检
        if (cacheCode==null || !cacheCode.equals(code)) {
            // 3、不一致，报错
            return Result.fail("验证码错误");
        }

        // 4、一致，根据手机号查用户
        User user = query().eq("phone", phone).one();

        // 5、判断用户是否存在
        if(user==null){
            // 6、不存在，创建新用户注册保存
            user=createUserWithPhone(phone);
        }

        // 7、保存用户信息到redis中
        // 7.1.随机生成token令牌作为key
        String token = UUID.randomUUID().toString(true);
        String LoginUserInfoKey = RedisConstants.LOGIN_USER_KEY+token;

        // 7.2.将User对象转为hash存储
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user,userDTO);  // 把user的属性复制到userDTO

        userDTO.setLoginUserToken(null);

        // 7.3.存储
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fieldValue)->{
                            if(fieldValue==null){
                                return null;
                            }
                            return fieldValue.toString();
                        }));

        stringRedisTemplate.opsForHash().putAll(LoginUserInfoKey, userMap);
        stringRedisTemplate.expire(LoginUserInfoKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 8.返回token到客户端
        return Result.ok(token);
    }

    /**
     * 根据手机号创建并保存用户
     * @param phone
     * @return
     */
    private User createUserWithPhone(String phone) {
        // 1、创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));

        // 2、保存用户
        save(user);
        return user;
    }

    /**
     * 登出功能
     * @return
     */
    @Override
    public Result logout() {
        // 1、从ThreadLocal中获取用户
        UserDTO userDTO = UserHolder.getUser();
        // 2、从userDTO获取redis中存放用户信息的key信息：token
        String token = userDTO.getLoginUserToken();
        // 3、拼接key
        String LoginUserInfoKey = RedisConstants.LOGIN_USER_KEY+token;
        // 4、删除redis中的用户信息
        stringRedisTemplate.delete(LoginUserInfoKey);
        // 5、删除ThreadLocal中的用户信息
        UserHolder.removeUser();
        return Result.ok();
    }
}
