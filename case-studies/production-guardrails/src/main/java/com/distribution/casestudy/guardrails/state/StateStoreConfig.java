package com.distribution.casestudy.guardrails.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * StateStore 条件装配。全部选择逻辑只有三行注解——
 * 「运维的默认路径就是最少组件的路径」由 matchIfMissing 保证。
 *
 * <p>切换档位：{@code app.state.store = mysql（默认）| redis | memory}。
 * 非法值不会静默落到某个实现（无 bean），由 {@code StartupGuard} 在启动期拒绝。</p>
 */
@Configuration
public class StateStoreConfig {

    @Bean
    @ConditionalOnProperty(name = "app.state.store", havingValue = "mysql", matchIfMissing = true)
    public StateStore mysqlStateStore(JdbcTemplate jdbc, ObjectMapper json) {
        return new MysqlStateStore(jdbc, json);
    }

    @Bean
    @ConditionalOnProperty(name = "app.state.store", havingValue = "redis")
    public StateStore redisStateStore(StringRedisTemplate redis, ObjectMapper json) {
        return new RedisStateStore(redis, json);
    }

    @Bean
    @ConditionalOnProperty(name = "app.state.store", havingValue = "memory")
    public StateStore inMemoryStateStore(ObjectMapper json) {
        return new InMemoryStateStore(json);
    }
}
