package com.ruoyi.common.idempotent.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 自定义注解防止表单重复提交
 *
 * @author Lion Li
 */
@Inherited
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RepeatSubmit {

    /**
     * 配置防重提交的间隔时间。
     *
     * @return 间隔时间，默认单位为毫秒
     */
    int interval() default 5000;

    /**
     * 配置防重提交间隔的时间单位。
     *
     * @return 时间单位
     */
    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;

    /**
     * 配置重复提交时的提示消息，支持 {code} 格式的国际化键。
     *
     * @return 提示消息或国际化键
     */
    String message() default "{repeat.submit.message}";

}
