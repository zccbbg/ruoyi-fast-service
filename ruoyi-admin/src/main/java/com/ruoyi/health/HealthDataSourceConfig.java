package com.ruoyi.health;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class HealthDataSourceConfig {

    /** 用途：将原主库配置接入单数据源；参数：应用配置环境；返回值：MySQL 连接池。 */
    @Bean
    public DataSource dataSource(Environment environment) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setJdbcUrl(environment.getRequiredProperty("spring.datasource.dynamic.datasource.master.url"));
        dataSource.setUsername(environment.getRequiredProperty("spring.datasource.dynamic.datasource.master.username"));
        dataSource.setPassword(environment.getRequiredProperty("spring.datasource.dynamic.datasource.master.password"));
        dataSource.setMaximumPoolSize(environment.getProperty("spring.datasource.dynamic.hikari.maxPoolSize", Integer.class, 20));
        dataSource.setMinimumIdle(environment.getProperty("spring.datasource.dynamic.hikari.minIdle", Integer.class, 10));
        return dataSource;
    }
}
