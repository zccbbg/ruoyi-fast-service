package com.ruoyi.common.idempotent.aspectj;

import cn.dev33.satoken.SaManager;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.crypto.SecureUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import com.ruoyi.common.core.constant.GlobalConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.MessageUtils;
import com.ruoyi.common.core.utils.ServletUtils;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.common.idempotent.annotation.RepeatSubmit;
import com.ruoyi.common.json.utils.JsonUtils;
import com.ruoyi.common.redis.utils.RedisUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 防止重复提交(参考美团GTIS防重系统)
 *
 * @author Lion Li
 */
@Aspect
public class RepeatSubmitAspect {

    private static final ThreadLocal<String> KEY_CACHE = new ThreadLocal<>();

    /**
     * 提交前写入防重标识，重复请求直接拒绝。
     *
     * @param point 被拦截的方法调用
     * @param repeatSubmit 防重提交配置
     */
    @Before("@annotation(repeatSubmit)")
    public void doBefore(JoinPoint point, RepeatSubmit repeatSubmit) {
        // 如果注解不为0 则使用注解数值
        long interval = repeatSubmit.timeUnit().toMillis(repeatSubmit.interval());

        if (interval < 1000) {
            throw new ServiceException("重复提交间隔时间不能小于'1'秒");
        }
        HttpServletRequest request = ServletUtils.getRequest();
        String nowParams = argsArrayToString(point.getArgs());

        // 请求地址（作为存放cache的key值）
        String url = request.getRequestURI();

        // 唯一值（没有消息头则使用请求地址）
        String submitKey = StringUtils.trimToEmpty(request.getHeader(SaManager.getConfig().getTokenName()));

        submitKey = SecureUtil.md5(submitKey + ":" + nowParams);
        // 唯一标识（指定key + url + 消息头）
        String cacheRepeatKey = GlobalConstants.REPEAT_SUBMIT_KEY + url + submitKey;
        if (RedisUtils.setObjectIfAbsent(cacheRepeatKey, "", Duration.ofMillis(interval))) {
            KEY_CACHE.set(cacheRepeatKey);
        } else {
            String message = repeatSubmit.message();
            if (StringUtils.startsWith(message, "{") && StringUtils.endsWith(message, "}")) {
                message = MessageUtils.message(StringUtils.substring(message, 1, message.length() - 1));
            }
            throw new ServiceException(message);
        }
    }

    /**
     * 请求正常返回后，失败结果释放防重标识并清理线程变量。
     *
     * @param joinPoint 切点
     * @param repeatSubmit 防重提交配置
     * @param jsonResult 方法返回结果
     */
    @AfterReturning(pointcut = "@annotation(repeatSubmit)", returning = "jsonResult")
    public void doAfterReturning(JoinPoint joinPoint, RepeatSubmit repeatSubmit, Object jsonResult) {
        try {
            if (jsonResult instanceof R<?> r && r.getCode() != R.SUCCESS) {
                RedisUtils.deleteObject(KEY_CACHE.get());
            }
        } finally {
            KEY_CACHE.remove();
        }
    }

    /**
     * 请求异常时释放防重标识并清理线程变量。
     *
     * @param joinPoint 切点
     * @param repeatSubmit 防重提交配置
     * @param e         异常
     */
    @AfterThrowing(value = "@annotation(repeatSubmit)", throwing = "e")
    public void doAfterThrowing(JoinPoint joinPoint, RepeatSubmit repeatSubmit, Exception e) {
        try {
            String key = KEY_CACHE.get();
            if (key != null) {
                RedisUtils.deleteObject(key);
            }
        } finally {
            KEY_CACHE.remove();
        }
    }

    /**
     * 序列化需要参与防重判断的方法参数。
     *
     * @param paramsArray 方法参数
     * @return 过滤特殊对象后的参数字符串
     */
    private String argsArrayToString(Object[] paramsArray) {
        StringJoiner params = new StringJoiner(" ");
        if (ArrayUtil.isEmpty(paramsArray)) {
            return params.toString();
        }
        for (Object o : paramsArray) {
            if (ObjectUtil.isNotNull(o) && !isFilterObject(o)) {
                params.add(JsonUtils.toJsonString(o));
            }
        }
        return params.toString();
    }

    /**
     * 判断参数是否为不参与防重判断的请求或上传对象。
     *
     * @param o 对象信息。
     * @return 如果是需要过滤的对象，则返回true；否则返回false。
     */
    @SuppressWarnings("rawtypes")
    public boolean isFilterObject(final Object o) {
        Class<?> clazz = o.getClass();
        if (clazz.isArray()) {
            return MultipartFile.class.isAssignableFrom(clazz.getComponentType());
        } else if (Collection.class.isAssignableFrom(clazz)) {
            Collection collection = (Collection) o;
            for (Object value : collection) {
                return value instanceof MultipartFile;
            }
        } else if (Map.class.isAssignableFrom(clazz)) {
            Map map = (Map) o;
            for (Object value : map.values()) {
                return value instanceof MultipartFile;
            }
        }
        return o instanceof MultipartFile || o instanceof HttpServletRequest || o instanceof HttpServletResponse
               || o instanceof BindingResult;
    }

}
