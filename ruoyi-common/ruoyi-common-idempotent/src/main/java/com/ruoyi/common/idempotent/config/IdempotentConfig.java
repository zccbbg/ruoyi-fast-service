package com.ruoyi.common.idempotent.config;

import com.ruoyi.common.idempotent.aspectj.RepeatSubmitAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConfiguration;

/**
 * 幂等功能配置
 *
 * @author Lion Li
 */
@AutoConfiguration(after = RedisConfiguration.class)
public class IdempotentConfig {

    /**
     * 注册防重提交切面。
     *
     * @return 防重提交切面实例
     */
    @Bean
    public RepeatSubmitAspect repeatSubmitAspect() {
        return new RepeatSubmitAspect();
    }

}
